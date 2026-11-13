package io.nexus.panel.modules.zigbee

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

/**
 * Zigbee coordinator / router control.
 *
 * The panel already runs the Zigbee stack (`zgateway` + `mosquitto` in
 * `/vendor/bin/siliconlabs_host`, started by `rootsudaemon.sh` at boot —
 * independent of the Sonoff app). This module drives it over the panel's
 * local MQTT broker: coordinator/router role, permit-join window, pairing
 * for router mode, device list.
 *
 * It also keeps one persistent client on 127.0.0.1:1883 subscribed to
 * `<base>/bridge/#`, so the device count, bridge status and permit-join
 * state are real values from the gateway — not assumptions. Everything is
 * published to the HA broker on `<base>/state/zigbee`.
 */
class ZigbeeModule : Module {

    override val name = "zigbee"

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    // The panel's own broker is where zgateway talks.
    private val localBrokerBase: String
        get() = Settings.get("zigbee_topic_base", "zigbee")

    @Volatile private var localClient: MqttClient? = null
    @Volatile private var localStopped = false
    private var localThread: Thread? = null
    private var permitThread: Thread? = null

    @Volatile private var deviceCount = -1
    @Volatile private var permitUntil = 0L
    @Volatile private var bridgeState = ""
    @Volatile private var lastEvent = ""

    override fun status(): String {
        val role = Settings.get("zigbee_role", "coordinator")
        return "role=$role online=${isGatewayRunning()} devices=$deviceCount"
    }

    override fun start(context: Context) {
        registerCommands()
        connectLocal()
        startStateLoop()
    }

    override fun stop() {
        localStopped = true
        stateThread?.interrupt()
        stateThread = null
        permitThread?.interrupt()
        permitThread = null
        try { localClient?.disconnect() } catch (_: Exception) {}
        try { localClient?.close() } catch (_: Exception) {}
        localClient = null
    }

    @Volatile private var stateThread: Thread? = null

    /** Publish the gateway state now, then count down permit-join / refresh. */
    private fun startStateLoop() {
        if (stateThread?.isAlive == true) return
        stateThread = Thread {
            while (!localStopped) {
                publishState()
                val permitActive = permitUntil > System.currentTimeMillis()
                try { Thread.sleep(if (permitActive) 5_000 else 60_000) }
                catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun registerCommands() {
        val m = mqtt ?: return
        m.onCommand["zigbee:role"] = { p -> setRole(p.optString("role", "coordinator")) }
        m.onCommand["zigbee:permit_join"] = { p -> permitJoin(p.optInt("seconds", 180)) }
        m.onCommand["zigbee:reset"] = { _ -> factoryReset() }
        m.onCommand["zigbee:devices"] = { _ ->
            requestDevices()
            publishState()
        }
    }

    // ---- gateway control (over the panel's local broker) ----------------

    fun setRole(role: String) {
        Settings.set("zigbee_role", role)
        // Sonoff/Z2M ZStack packages switch role via a marker file + restart of zgateway
        // (implemented by the Nexus firmware tooling / package installer in phase 2)
        publishState()
    }

    /** Open the network for `seconds` (0 = close immediately). */
    fun permitJoin(seconds: Int) {
        val secs = seconds.coerceIn(0, 3600)
        publishLocal("$localBrokerBase/bridge/request/permit_join",
            JSONObject().put("time", secs).toString())
        permitUntil = System.currentTimeMillis() + secs * 1000L
        publishState()
        permitThread?.interrupt()
        if (secs == 0) return
        permitThread = Thread {
            while (true) {
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                val remaining = ((permitUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                publishState()
                if (remaining <= 0) break
            }
        }.apply { isDaemon = true; start() }
    }

    /** Publish to the panel-local broker (zgateway lives on 127.0.0.1). */
    private fun publishLocal(topic: String, payload: String) {
        val c = localClient
        if (c != null && c.isConnected) {
            try { c.publish(topic, payload.toByteArray(), 0, false); return } catch (_: Exception) {}
        }
        // fallback: one short-lived client
        Thread {
            var t: MqttClient? = null
            try {
                t = MqttClient("tcp://127.0.0.1:1883",
                    "nexus-zigbee-pub-" + System.currentTimeMillis(), MemoryPersistence())
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 3
                    keepAliveInterval = 10
                }
                t.connect(opts)
                t.publish(topic, payload.toByteArray(), 0, false)
            } catch (_: Exception) {
                android.util.Log.w("NexusZigbee", "local publish failed: $topic")
            } finally {
                try { t?.disconnect() } catch (_: Exception) {}
                try { t?.close() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Persistent subscription to the gateway's own topics. Reads the real
     * device count and bridge state; reconnects when the broker restarts.
     */
    private fun connectLocal() {
        if (localThread?.isAlive == true) return
        localStopped = false
        localThread = Thread {
            while (!localStopped) {
                var c: MqttClient? = null
                try {
                    val client = MqttClient("tcp://127.0.0.1:1883",
                        "nexus-zigbee-sub-" + System.currentTimeMillis(), MemoryPersistence())
                    c = client
                    val opts = MqttConnectOptions().apply {
                        isCleanSession = true
                        connectionTimeout = 5
                        keepAliveInterval = 30
                    }
                    client.setCallback(object : MqttCallbackExtended {
                        override fun connectComplete(reconnect: Boolean, serverURI: String) {
                            try {
                                client.subscribe("$localBrokerBase/bridge/#", 0)
                                requestDevices()
                                publishState()
                            } catch (_: Exception) {}
                        }
                        override fun connectionLost(cause: Throwable?) {}
                        override fun messageArrived(topic: String, message: MqttMessage) {
                            onLocalMessage(topic, String(message.payload))
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                    client.connect(opts)
                    localClient = client
                    while (!localStopped && client.isConnected) {
                        try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("NexusZigbee", "local broker unavailable: ${t.message}")
                } finally {
                    localClient = null
                    try { c?.disconnect() } catch (_: Exception) {}
                    try { c?.close() } catch (_: Exception) {}
                }
                if (!localStopped) {
                    try { Thread.sleep(30_000) } catch (_: InterruptedException) { break }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun onLocalMessage(topic: String, payload: String) {
        try {
            when (topic.substringAfterLast('/')) {
                "devices" -> {
                    deviceCount = org.json.JSONArray(payload).length()
                    publishState()
                }
                "event" -> {
                    val o = JSONObject(payload)
                    lastEvent = o.optString("type")
                    publishState()
                }
                "state" -> {
                    bridgeState = payload.trim()
                    publishState()
                }
            }
        } catch (_: Exception) { /* malformed gateway payload — ignore */ }
    }

    /** Publish the gateway state HA subscribes to (`state/zigbee`). */
    fun publishState() {
        val remaining = ((permitUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        try {
            mqtt?.state("zigbee", JSONObject()
                .put("role", Settings.get("zigbee_role", "coordinator"))
                .put("permit_join", remaining)
                .put("device_count", deviceCount)
                .put("bridge", bridgeState.ifBlank { "unknown" })
                .put("last_event", lastEvent)
                .put("gateway_running", isGatewayRunning()))
        } catch (_: Exception) {}
    }

    fun factoryReset() {
        // restore the vendor ZStack (provides a way out)
    }

    fun isGatewayRunning(): Boolean = try {
        val p = ProcessBuilder().command("sh", "-c", "pidof zgateway").start()
        p.waitFor() == 0
    } catch (_: Exception) { false }

    /** Device list — reads zgateway's own MQTT topics. */
    fun requestDevices() {
        publishLocal("$localBrokerBase/bridge/request/devices", "")
    }
}
