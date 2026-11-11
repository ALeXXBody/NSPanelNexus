package io.nexus.panel.modules.dashboard

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject

/**
 * Dashboard kiosk.
 *
 * Phase 1: WebView renderer of an HA dashboard (the Fully-Kiosk path, works
 * today). Phase 2: a native renderer that draws the `nspanel-*` cards the
 * Flutter app proved feasible on this hardware — no WebView in the loop.
 *
 * The module itself only owns the *configuration*; the WebView lives in the
 * MainActivity so it can be foregrounded and keep the app responsive.
 */
class DashboardModule : Module {

    override val name = "dashboard"

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String {
        val url = Settings.dashboardUrl
        return if (url.isBlank()) "no dashboard configured" else "url=$url"
    }

    override fun start(context: Context) {
        // the WebView lives in MainActivity; this module owns the config + events
        val m = mqtt ?: return
        m.onCommand["dashboard:url"] = { p ->
            val url = p.optString("url")
            if (url.isNotBlank()) {
                Settings.set("dashboard_url", url)
                onUrlChanged?.invoke(url)
            }
        }
        m.onCommand["dashboard:wake"] = { _ -> onWake?.invoke() }
    }

    override fun stop() {}

    /** Called when the dashboard URL changes (hot-reload the WebView). */
    var onUrlChanged: ((String) -> Unit)? = null
    /** Called when HA wants the dashboard back on screen. */
    var onWake: (() -> Unit)? = null

    fun reportCurrent(url: String) {
        mqtt?.state("dashboard", JSONObject().put("url", url))
    }
}
