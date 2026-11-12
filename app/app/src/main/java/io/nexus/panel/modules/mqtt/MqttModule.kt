package io.nexus.panel.modules.mqtt

import android.content.Context
import android.util.Log
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
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

    @Volatile private var client: MqttClient? = null
    @Volatile private var stopped = false
    private val startMs = System.currentTimeMillis()
    @Volatile private var uptimeThread: Thread? = null
    private val publishExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "nexus-mqtt-publish").apply { isDaemon = true }
        }
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Triple<String, ByteArray, Pair<Int, Boolean>>>()

    /** Callbacks for device commands: command -> handler */
    val onCommand = java.util.concurrent.ConcurrentHashMap<String, (JSONObject) -> Unit>()

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
        // connect() and the qos=1 publishes block — never do them on the main thread.
        // Retry with backoff: at boot the network is often not up yet when the
        // service starts, and paho's auto-reconnect only covers established
        // sessions, not a failed initial connect().
        Thread {
            var delay = 2000L
            while (!stopped) {
                try {
                    connectMqtt()
                    Log.d("NexusMQTT", "start(): connect() returned")
                    return@Thread
                } catch (t: Throwable) {
                    Log.e("NexusMQTT", "connect failed, retry in ${delay}ms", t)
                }
                var waited = 0L
                while (!stopped && waited < delay) {
                    try { Thread.sleep(minOf(1000L, delay - waited)) } catch (_: InterruptedException) { break }
                    waited += 1000L
                }
                delay = minOf(delay * 2, 60000L)
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
        uptimeThread?.interrupt()
        uptimeThread = null
        onCommand.clear()
        onConfigUpdated = null
        try { client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
    }

    private fun onConnected(c: MqttClient) {
        // NEVER block this callback thread: Paho forbids blocking client calls
        // (publish/subscribe wait for acks) from inside callbacks — it deadlocks
        // the CommsCallback thread that must dispatch those very acks.
        // Hand everything to the single publish worker thread instead.
        if (stopped || client !== c) {
            // stale connection from a connect() that raced with stop()
            try { c.disconnect() } catch (_: Exception) {}
            return
        }
        Log.d("NexusMQTT", "connected to ${Settings.mqttHost}:${Settings.mqttPort}")
        publishExecutor.execute {
            try {
                onConnectedAsync(c)
            } catch (t: Throwable) {
                Log.e("NexusMQTT", "post-connect failed", t)
            }
        }
    }

    private fun onConnectedAsync(c: MqttClient) {
        if (stopped || client !== c) return
        // flush anything queued while offline (plain blocking calls are safe here:
        // this is our own worker thread, not Paho's callback thread)
        var q = pending.poll()
        while (q != null) {
            try { c.publish(q.first, q.second, q.third.first, q.third.second) } catch (_: Exception) {}
            q = pending.poll()
        }
        if (stopped || client !== c) return
        try { c.publish(base + "/status", "online".toByteArray(), 1, true) } catch (_: Exception) {}
        try {
            val mods = JSONObject()
            for (m in ModuleManager.all()) mods.put(m.name, Settings.moduleEnabled(m.name))
            state("modules", mods)
        } catch (_: Exception) {}
        state("ip_address", JSONObject().put("ip_address", localIp()))
        try { c.subscribe(base + "/command/#", 1) } catch (_: Exception) {}
        try { c.subscribe(base + "/config/#", 1) } catch (_: Exception) {}
        // periodic uptime publisher
        uptimeThread?.interrupt()
        uptimeThread = Thread {
            while (!stopped) {
                val secs = (System.currentTimeMillis() - startMs) / 1000
                state("uptime", JSONObject().put("uptime", secs))
                // keepalive: re-publish status=online so a dropped retained
                // message (or a broker restart) can't leave us silently offline
                try { c.publish(base + "/status", "online".toByteArray(), 1, true) } catch (_: Exception) {}
                try { Thread.sleep(60000) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
        // re-publish HA discovery so entities exist
        publishDiscovery()
    }

    fun publish(topic: String, payload: String, qos: Int = 0, retain: Boolean = false) {
        // paho publish() with qos>=1 blocks for the ack — single executor,
        // not a new thread per message (illuminance alone fires at ~1 Hz).
        // If not connected yet, queue and flush in onConnected (fixes cold-start drops).
        val body = payload.toByteArray()
        val c = client
        if (c == null || !c.isConnected) {
            if (pending.size < 200) pending.offer(Triple(topic, body, Pair(qos, retain)))
            return
        }
        publishExecutor.execute {
            try {
                val cc = client
                if (cc != null && cc.isConnected) cc.publish(topic, body, qos, retain)
                else if (pending.size < 200) pending.offer(Triple(topic, body, Pair(qos, retain)))
            } catch (e: Exception) { Log.e("NexusMQTT", "publish failed: $topic", e) }
        }
    }

    fun event(kind: String, payload: JSONObject) {
        publish("$base/event/$kind", payload.toString(), qos = 0)
    }

    fun state(key: String, payload: JSONObject) {
        publish("$base/state/$key", payload.toString(), qos = 0, retain = true)
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
    @Volatile var onConfigUpdated: ((JSONObject) -> Unit)? = null

    /** Config with secrets masked — the config topic is readable by anyone on the broker. */
    fun maskedConfig(): JSONObject {
        val out = Settings.all()
        out.opt("ha_token")?.let { if (it.toString().isNotBlank()) out.put("ha_token", "<set>") }
        out.opt("mqtt_pass")?.let { if (it.toString().isNotBlank()) out.put("mqtt_pass", "<set>") }
        out.opt("remote_desktop_token")?.let { if (it.toString().isNotBlank()) out.put("remote_desktop_token", "<set>") }
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
                relative == "command/device" -> {
                    val cmd = JSONObject(text).optString("command")
                    onCommand[cmd]?.invoke(JSONObject(text))
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
                        val all = Settings.all()
                        filter.keys().forEach { k -> if (all.has(k)) out.put(k, all.opt(k)) }
                        out
                    }
                    // never expose secrets over a topic anyone can read
                    answer.opt("ha_token")?.let { if (it.toString().isNotBlank()) answer.put("ha_token", "<set>") }
                    answer.opt("mqtt_pass")?.let { if (it.toString().isNotBlank()) answer.put("mqtt_pass", "<set>") }
                    answer.opt("remote_desktop_token")?.let { if (it.toString().isNotBlank()) answer.put("remote_desktop_token", "<set>") }
                    publish(base + "/config", answer.toString(), qos = 1, retain = true)
                }
            }
        } catch (_: Exception) { /* malformed payload — ignore */ }
    }
}
