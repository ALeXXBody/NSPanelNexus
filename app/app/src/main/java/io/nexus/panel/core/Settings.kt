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

    fun get(key: String, def: String): String = synchronized(lock) { json.optString(key, def) }
    fun getInt(key: String, def: Int): Int = synchronized(lock) { json.optInt(key, def) }
    fun getBool(key: String, def: Boolean): Boolean = synchronized(lock) { json.optBoolean(key, def) }

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
            val v = patch.get(k)
            // Never wipe a stored value with an empty string for fields that
            // are required for connectivity — a stale/empty patch (e.g. from a
            // sidebar "Save" where the entity was unavailable) must not erase
            // the broker URL, HA URL or topic prefix.
            if (v is String && v.isBlank() && k in setOf(
                    "mqtt_host", "topic_prefix", "ha_url", "dashboard_url",
                    "mqtt_user", "nexus_manifest_url", "zigbee_topic_base",
                    "screen_on_weekday_begin", "screen_on_weekday_end",
                    "screen_on_weekend_begin", "screen_on_weekend_end"
                )) {
                return@forEach
            }
            json.put(k, v)
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

    // ---- kiosk behaviour -------------------------------------------------

    /** Screensaver after `screensaver_timeout` seconds without a touch. */
    val screensaverEnabled: Boolean get() = getBool("screensaver_enabled", false)
    val screensaverTimeout: Int get() = getInt("screensaver_timeout", 60).coerceIn(5, 3600)
    val screensaverMode: String get() = get("screensaver_mode", "clock")
    val screensaverBrightness: Int get() = getInt("screensaver_brightness", 0)

    /** Return to the dashboard after this many idle seconds on firmware pages (0 = off). */
    val autoReturnSeconds: Int get() = getInt("auto_return_seconds", 0).coerceIn(0, 3600)

    /** Reload the dashboard every N minutes (0 = never) — recovers a hung HA frontend. */
    val dashboardReloadMinutes: Int get() = getInt("dashboard_reload_minutes", 0).coerceIn(0, 1440)

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
