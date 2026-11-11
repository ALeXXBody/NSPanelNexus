package io.nexus.panel.modules.mqtt

import android.content.Context
import android.util.Log
import io.nexus.panel.core.Module
import io.nexus.panel.core.Settings
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

/**
 * The MQTT spine.
 *
 * Protocol (compatible with seaky NSPanel Pro Tools, so existing HA configs work):
 *   <prefix>/<device>/event/<kind>        — published panel events
 *   <prefix>/<device>/command/device      — accepted device commands
 *   <prefix>/<device>/command/<switch>    — accepted relay switch commands
 *   <prefix>/<device>/config/query        — query config (empty = all)
 *   <prefix>/<device>/config/update       — patch config (JSON object)
 *   <prefix>/<device>/config              — config answers
 *   <prefix>/<device>/status              — online/offline availability
 *   <prefix>/<device>/state/<key>         — state (started_at, ip_address…)
 *
 * Plus Home Assistant MQTT discovery under <ha_prefix>/<component>/…/config.
 */
class MqttModule : Module {

    override val name = "mqtt"

    private var client: MqttClient? = null
    @Volatile private var stopped = false
    private val publishExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "nexus-mqtt-publish").apply { isDaemon = true }
        }
    private val listeners = mutableMapOf<String, (JSONObject) -> Unit>()

    /** Callbacks for device commands: command -> handler */
    val onCommand = mutableMapOf<String, (JSONObject) -> Unit>()

    val base: String get() = "${Settings.topicPrefix}/${Settings.topicId}"
    val haPrefix: String get() = Settings.get("ha_topic_prefix", "homeassistant")

    override fun status(): String = when {
        client?.isConnected == true -> "connected"
        client != null -> "connecting"
        else -> "stopped"
    }

    override fun start(context: Context) {
        val host = Settings.mqttHost
        Log.d("NexusMQTT", "start(): host=$host port=${Settings.mqttPort}")
        if (host.isBlank()) return
        stopped = false
        // connect() and the qos=1 publishes block — never do them on the main thread
        Thread {
            try {
                connectMqtt()
                Log.d("NexusMQTT", "start(): connect() returned")
            } catch (t: Throwable) {
                Log.e("NexusMQTT", "connect failed", t)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun connectMqtt() {
        val host = Settings.mqttHost
        val id = "nexus-${Settings.topicId}-${System.currentTimeMillis()}"
        val c = MqttClient("tcp://$host:${Settings.mqttPort}", id, MemoryPersistence())
        // assign before connect: onConnected fires from the paho thread and
        // publishes immediately — it must see the client
        client = c
        val opts = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            keepAliveInterval = 60
            Settings.mqttUser?.let { userName = it }
            Settings.mqttPass?.let { password = it.toCharArray() }
            setWill(base + "/status", "offline".toByteArray(), 1, true)
        }
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) = onConnected(c)
            override fun connectionLost(cause: Throwable?) { client?.let { cl -> /* auto-reconnect handles it */ } }
            override fun messageArrived(topic: String, message: MqttMessage) = onMessage(topic, message)
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        c.connect(opts)
        // if the module was stopped while we were connecting, clean up
        if (stopped) {
            try { c.disconnect() } catch (_: Exception) {}
            try { c.close() } catch (_: Exception) {}
        }
    }

    override fun stop() {
        stopped = true
        try { client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
    }

    private fun onConnected(c: MqttClient) {
        if (stopped || client !== c) {
            // stale connection from a connect() that raced with stop()
            try { c.disconnect() } catch (_: Exception) {}
            return
        }
        Log.d("NexusMQTT", "connected to ${Settings.mqttHost}:${Settings.mqttPort}")
        publish(base + "/status", "online", retain = true, qos = 1)
        publish(base + "/state/ip_address",
            JSONObject().put("ip_address", localIp()).toString())
        c.subscribe(base + "/command/#", 1)
        c.subscribe(base + "/config/#", 1)
        publish(base + "/state/started_at",
            JSONObject().put("started_at", System.currentTimeMillis()).toString())
        // re-publish HA discovery so entities exist
        publishDiscovery()
    }

    fun publish(topic: String, payload: String, qos: Int = 0, retain: Boolean = false) {
        // paho publish() with qos>=1 blocks for the ack — single executor,
        // not a new thread per message (illuminance alone fires at ~1 Hz)
        val c = client ?: return
        val body = payload.toByteArray()
        publishExecutor.execute {
            try { c.publish(topic, body, qos, retain) } catch (_: Exception) {}
        }
    }

    fun event(kind: String, payload: JSONObject) {
        publish("$base/event/$kind", payload.toString(), qos = 0)
    }

    fun state(key: String, payload: JSONObject) {
        publish("$base/state/$key", payload.toString(), qos = 0)
    }

    fun discovery(component: String, objectId: String, payload: JSONObject) {
        val uid = "nexus_${Settings.topicId}_$objectId"
        payload.put("unique_id", uid)
        payload.put("device", JSONObject()
            .put("identifiers", org.json.JSONArray().put("nspanelpro_${Settings.topicId}"))
            .put("name", Settings.deviceName)
            .put("manufacturer", "Sonoff")
            .put("model", "NSPanel Pro"))
        publish("$haPrefix/$component/$uid/config", payload.toString(), qos = 0, retain = true)
    }

    /** Called when HA changes the panel's settings over MQTT. */
    var onConfigUpdated: ((JSONObject) -> Unit)? = null

    /** Config with secrets masked — the config topic is readable by anyone on the broker. */
    fun maskedConfig(): JSONObject {
        val out = Settings.all()
        out.opt("ha_token")?.let { if (it.toString().isNotBlank()) out.put("ha_token", "<set>") }
        out.opt("mqtt_pass")?.let { if (it.toString().isNotBlank()) out.put("mqtt_pass", "<set>") }
        return out
    }

    /** The panel's own LAN address (first non-loopback IPv4). */
    fun localIp(): String = try {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress ?: "127.0.0.1"
    } catch (_: Exception) { "127.0.0.1" }

    private fun publishDiscovery() {
        Log.d("NexusMQTT", "publishDiscovery() begin")
        try {
        // built-in diagnostics entities every panel has
        discovery("sensor", "ip_address", JSONObject()
            .put("name", "IP address").put("state_topic", "$base/state/ip_address")
            .put("value_template", "{{ value_json.ip_address }}")
            .put("entity_category", "diagnostic"))
        discovery("sensor", "uptime", JSONObject()
            .put("name", "Uptime").put("state_topic", "$base/state/uptime")
            .put("value_template", "{{ value_json.uptime }}")
            .put("entity_category", "diagnostic"))
        // events every panel supports
        discovery("event", "touch", JSONObject()
            .put("name", "Touch").put("state_topic", "$base/event/touch")
            .put("event_types", org.json.JSONArray()
                .put("tap").put("swipe_up").put("swipe_down").put("swipe_left").put("swipe_right")))
        discovery("event", "proximity", JSONObject()
            .put("name", "Proximity").put("state_topic", "$base/event/proximity")
            .put("event_types", org.json.JSONArray().put("triggered")))
        discovery("event", "light", JSONObject()
            .put("name", "Light").put("state_topic", "$base/event/light")
            .put("event_types", org.json.JSONArray().put("light_above").put("light_below").put("light_normal")))
        } catch (t: Throwable) {
            Log.e("NexusMQTT", "publishDiscovery failed", t)
        }
        Log.d("NexusMQTT", "publishDiscovery() done")
    }

    private fun onMessage(topic: String, message: MqttMessage) {
        val text = String(message.payload)
        val relative = topic.removePrefix("$base/")
        try {
            when {
                relative.startsWith("command/device") -> {
                    val cmd = JSONObject(text).optString("command")
                    onCommand[cmd]?.invoke(JSONObject(text))
                }
                relative.startsWith("command/") -> {
                    val switchId = relative.removePrefix("command/")
                    onCommand["switch:$switchId"]?.invoke(JSONObject(text))
                }
                relative.startsWith("config/update") -> {
                    Settings.patch(JSONObject(text))
                    onConfigUpdated?.invoke(JSONObject(text))
                    publish(base + "/config", maskedConfig().toString(), qos = 1, retain = true)
                }
                relative.startsWith("config/query") -> {
                    val filter = if (text.isBlank()) JSONObject() else JSONObject(text)
                    val answer = if (filter.length() == 0) Settings.all() else {
                        val out = JSONObject()
                        filter.keys().forEach { k -> out.put(k, Settings.all().opt(k)) }
                        out
                    }
                    // never expose secrets over a topic anyone can read
                    answer.opt("ha_token")?.let { if (it.toString().isNotBlank()) answer.put("ha_token", "<set>") }
                    answer.opt("mqtt_pass")?.let { if (it.toString().isNotBlank()) answer.put("mqtt_pass", "<set>") }
                    publish(base + "/config", answer.toString(), qos = 1, retain = true)
                }
            }
        } catch (_: Exception) { /* malformed payload — ignore */ }
    }
}
