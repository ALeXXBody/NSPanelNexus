package io.nexus.panel.core

import android.content.Context
import org.json.JSONObject

/** Persisted settings for the whole app. JSON-backed so remote config can patch it. */
object Settings {
    private const val FILE = "nexus_settings.json"
    private val lock = Any()

    private lateinit var ctx: Context
    private lateinit var json: JSONObject

    fun init(context: Context) {
        synchronized(lock) {
            if (this::json.isInitialized) return   // one load per process
            ctx = context.applicationContext
            json = try {
                JSONObject(ctx.openFileInput(FILE).bufferedReader().readText())
            } catch (e: Exception) {
                JSONObject()
            }
        }
    }

    fun get(key: String, def: String): String = json.optString(key, def)
    fun getInt(key: String, def: Int): Int = json.optInt(key, def)
    fun getBool(key: String, def: Boolean): Boolean = json.optBoolean(key, def)

    /** Module enable flags: "module.<name>.enabled" */
    fun moduleEnabled(name: String): Boolean = getBool("module.$name.enabled", defaultEnabled(name))

    private fun defaultEnabled(name: String) = when (name) {
        "mqtt", "sensors", "display" -> true
        else -> false
    }

    fun set(key: String, value: Any) = synchronized(lock) {
        json.put(key, value)
        persist()
    }

    fun setModuleEnabled(name: String, enabled: Boolean) {
        set("module.$name.enabled", enabled)
    }

    /** Apply a partial JSON patch (remote config over MQTT). */
    fun patch(patch: JSONObject) = synchronized(lock) {
        patch.keys().forEach { k ->
            json.put(k, patch.get(k))
        }
        persist()
    }

    fun all(): JSONObject = synchronized(lock) { JSONObject(json.toString()) }

    private fun persist() {
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
            it.write(json.toString().toByteArray())
        }
    }

    /** Last dashboard load failure, shown on the Setup page until fixed. */
    var lastDashboardError: String
        get() = get("last_dashboard_error", "")
        set(v) = synchronized(lock) { json.put("last_dashboard_error", v); persist() }

    // Convenience accessors (normalised so a bare host/path also works)
    val deviceName: String get() = get("device_name", android.os.Build.MODEL)
    val mqttHost: String get() = get("mqtt_host", "")
    val mqttPort: Int get() = getInt("mqtt_port", 1883)
    val mqttUser: String? get() = get("mqtt_user", "").takeIf { it.isNotEmpty() }
    val mqttPass: String? get() = get("mqtt_pass", "").takeIf { it.isNotEmpty() }
    val topicPrefix: String get() = get("topic_prefix", "nspanelpro")

    /** HA base URL — "192.168.1.10:8123" becomes "http://192.168.1.10:8123". */
    val haUrl: String
        get() = get("ha_url", "").let {
            if (it.isBlank()) "" else if (it.startsWith("http")) it else "http://$it"
        }

    val haToken: String get() = get("ha_token", "")

    /**
     * Topic-safe identifier derived from the device name: spaces and special
     * characters break some MQTT brokers (and HA entity ids), so the MQTT
     * client id and the topic base use this, while HA shows the friendly name.
     */
    val topicId: String
        get() = deviceName.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_', ' ').ifEmpty { "nspanel" }


    /**
     * Dashboard URL — accepts a full URL or a dashboard path relative to the
     * HA URL ("NSPanel" → "http://<ha>/NSPanel").
     */
    val dashboardUrl: String
        get() {
            val v = get("dashboard_url", "").trim()
            if (v.isBlank()) return ""
            if (v.startsWith("http")) return v
            val ha = haUrl.trimEnd('/')
            if (ha.isBlank()) return v
            return "$ha/${v.trimStart('/')}"
        }

}
