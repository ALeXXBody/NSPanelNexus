package io.nexus.panel.modules.dashboard

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject

/**
 * Dashboard kiosk.
 *
 * Phase 1: native WebView kiosk renderer of an HA dashboard (no third-party
 * kiosk app needed — the panel boots straight into the dashboard). Phase 2:
 * a native renderer that draws the `nspanel-*` cards the Flutter app proved
 * feasible on this hardware — no WebView in the loop.
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
        // Remote control of the kiosk (HA services / automations)
        m.onCommand["dashboard:refresh"] = { _ -> onRefresh?.invoke() }
        m.onCommand["dashboard:navigate"] = { p ->
            val path = p.optString("path")
            if (path.isNotBlank()) onNavigate?.invoke(path)
        }
        m.onCommand["dashboard:return"] = { _ -> onWake?.invoke() }
        m.onCommand["screensaver:show"] = { p ->
            onScreensaverShow?.invoke(p.optString("mode", ""))
        }
        m.onCommand["screensaver:hide"] = { _ -> onScreensaverHide?.invoke() }
        m.onCommand["camera:show"] = { p ->
            val url = p.optString("url")
            if (url.isNotBlank()) onCameraShow?.invoke(
                url, p.optString("title", "Camera"), p.optInt("duration", 30))
        }
        m.onCommand["camera:hide"] = { _ -> onCameraHide?.invoke() }
        m.onCommand["tts:speak"] = { p ->
            val text = p.optString("text")
            if (text.isNotBlank()) onSpeak?.invoke(text)
        }
        m.onCommand["notify:toast"] = { p ->
            val msg = p.optString("message")
            if (msg.isNotBlank()) onToast?.invoke(msg)
        }
    }

    override fun stop() {}

    /** Called when the dashboard URL changes (hot-reload the WebView). */
    var onUrlChanged: ((String) -> Unit)? = null
    /** Called when HA wants the dashboard back on screen. */
    var onWake: (() -> Unit)? = null
    /** Reload the current dashboard (recover a hung frontend). */
    var onRefresh: (() -> Unit)? = null
    /** Navigate the dashboard to a path (e.g. "lovelace/cameras") or full URL. */
    var onNavigate: ((String) -> Unit)? = null
    var onScreensaverShow: ((String) -> Unit)? = null
    var onScreensaverHide: (() -> Unit)? = null
    var onCameraShow: ((String, String, Int) -> Unit)? = null
    var onCameraHide: (() -> Unit)? = null
    var onSpeak: ((String) -> Unit)? = null
    var onToast: ((String) -> Unit)? = null

    fun reportCurrent(url: String) {
        mqtt?.state("dashboard", JSONObject().put("url", url))
    }

    /** Which kiosk face is on screen: dashboard / firmware / pin / login / camera. */
    fun reportKiosk(mode: String, screensaver: String = "off", camera: Boolean = false) {
        try {
            mqtt?.state("kiosk", JSONObject()
                .put("kiosk", mode)
                .put("screensaver", screensaver)
                .put("camera", camera))
        } catch (_: Exception) {}
    }

    fun reportScreensaver(mode: String) {
        try { mqtt?.state("screensaver", JSONObject().put("screensaver", mode)) } catch (_: Exception) {}
    }
}
