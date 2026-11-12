package io.nexus.panel.modules.zigbee

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject

/**
 * Zigbee coordinator / router control.
 *
 * The panel already runs the Zigbee stack (`zgateway` + `mosquitto` in
 * `/vendor/bin/siliconlabs_host`, started by `rootsudaemon.sh` at boot —
 * independent of the Sonoff app). This module drives it over the panel's
 * local MQTT broker: coordinator/router role, permit-join window, pairing
 * for router mode, device list.
 */
class ZigbeeModule : Module {

    override val name = "zigbee"

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    // The panel's own broker is where zgateway talks.
    private val localBrokerBase: String
        get() = Settings.get("zigbee_topic_base", "zigbee")

    override fun status(): String {
        val role = Settings.get("zigbee_role", "coordinator")
        return "role=$role online=${isGatewayRunning()}"
    }

    override fun start(context: Context) {
        registerCommands()
    }

    override fun stop() { /* nothing to tear down — the gateway process is owned by init */ }

    private fun registerCommands() {
        val m = mqtt ?: return
        m.onCommand["zigbee:role"] = { p -> setRole(p.optString("role", "coordinator")) }
        m.onCommand["zigbee:permit_join"] = { p -> permitJoin(p.optInt("seconds", 180)) }
        m.onCommand["zigbee:reset"] = { _ -> factoryReset() }
    }

    // ---- gateway control (over the panel's local broker) ----------------

    fun setRole(role: String) {
        Settings.set("zigbee_role", role)
        // Sonoff/Z2M ZStack packages switch role via a marker file + restart of zgateway
        // (implemented by the Nexus firmware tooling / package installer in phase 2)
    }

    fun permitJoin(seconds: Int) {
        publishLocal("$localBrokerBase/bridge/request/permit_join",
            JSONObject().put("time", seconds).toString())
    }

    /** Publish to the panel-local broker (zgateway lives on 127.0.0.1, not the HA broker). */
    private fun publishLocal(topic: String, payload: String) {
        // First try the shared client in case a local bridge is configured...
        try { mqtt?.publish(topic, payload) } catch (_: Exception) {}
        // ...then deliver directly to the panel-local broker.
        Thread {
            var c: org.eclipse.paho.client.mqttv3.MqttClient? = null
            try {
                c = org.eclipse.paho.client.mqttv3.MqttClient(
                    "tcp://127.0.0.1:1883",
                    "nexus-zigbee-" + System.currentTimeMillis(),
                    org.eclipse.paho.client.mqttv3.persist.MemoryPersistence())
                val opts = org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 3
                    keepAliveInterval = 10
                }
                c.connect(opts)
                c.publish(topic, payload.toByteArray(), 0, false)
            } catch (_: Exception) {
                android.util.Log.w("NexusZigbee", "local publish failed: $topic")
            } finally {
                try { c?.disconnect() } catch (_: Exception) {}
                try { c?.close() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
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
        publishLocal("$localBrokerBase/bridge/request/device_list", "")
    }
}
