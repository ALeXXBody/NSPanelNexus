package io.nexus.panel.core

import android.content.Context
import org.json.JSONObject

/** Persisted settings for the whole app. JSON-backed so remote config can patch it. */
object Settings {
    private const val FILE = "nexus_settings.json"

    private lateinit var ctx: Context
    private lateinit var json: JSONObject

    fun init(context: Context) {
        ctx = context.applicationContext
        json = try {
            JSONObject(ctx.openFileInput(FILE).bufferedReader().readText())
        } catch (e: Exception) {
            JSONObject()
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

    fun set(key: String, value: Any) {
        json.put(key, value)
        persist()
    }

    fun setModuleEnabled(name: String, enabled: Boolean) {
        set("module.$name.enabled", enabled)
    }

    /** Apply a partial JSON patch (remote config over MQTT). */
    fun patch(patch: JSONObject) {
        patch.keys().forEach { k ->
            json.put(k, patch.get(k))
        }
        persist()
    }

    fun all(): JSONObject = JSONObject(json.toString())

    private fun persist() {
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
            it.write(json.toString().toByteArray())
        }
    }

    // Convenience accessors
    val deviceName: String get() = get("device_name", android.os.Build.MODEL)
    val mqttHost: String get() = get("mqtt_host", "")
    val mqttPort: Int get() = getInt("mqtt_port", 1883)
    val mqttUser: String? get() = get("mqtt_user", "").takeIf { it.isNotEmpty() }
    val mqttPass: String? get() = get("mqtt_pass", "").takeIf { it.isNotEmpty() }
    val topicPrefix: String get() = get("topic_prefix", "nspanelpro")
    val haUrl: String get() = get("ha_url", "")
    val haToken: String get() = get("ha_token", "")
    val dashboardUrl: String get() = get("dashboard_url", "")
}
