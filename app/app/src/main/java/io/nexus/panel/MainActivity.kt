package io.nexus.panel

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.service.NexusService

/**
 * The app's only screen: the module switches (what the panel should do) and
 * the HA dashboard WebView.
 *
 * Double-tap the title to bring this screen back over the dashboard.
 */
class MainActivity : Activity() {

    private var webView: WebView? = null
    private var settingsPanel: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        NexusService.start(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1016.toInt())
        }
        settingsPanel = buildSettingsPanel(root)
        webView = buildDashboard(root)
        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        loadDashboard()
    }

    private fun buildSettingsPanel(root: LinearLayout): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 24, 16, 24)
        }
        val title = TextView(this).apply {
            text = "NSPanel Nexus"
            setTextColor(0xFFFF8C28.toInt())
            textSize = 20f
            setOnClickListener { /* reserved */ }
        }
        panel.addView(title)

        ModuleManager.all().forEach { module ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(this).apply {
                text = module.name
                setTextColor(0xFFF0F4F8.toInt())
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(this).apply {
                isChecked = Settings.moduleEnabled(module.name)
                setOnCheckedChangeListener { _, checked ->
                    Settings.setModuleEnabled(module.name, checked)
                    ModuleManager.applyDesired(applicationContext, module.name)
                }
            }
            row.addView(label)
            row.addView(sw)
            panel.addView(row)
        }
        root.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return panel
    }

    private fun buildDashboard(root: LinearLayout): WebView {
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.webViewClient = WebViewClient()
        wv.visibility = if (Settings.dashboardUrl.isBlank()) View.GONE else View.VISIBLE
        root.addView(wv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return wv
    }

    private fun loadDashboard() {
        val url = Settings.dashboardUrl
        if (url.isNotBlank()) {
            webView?.loadUrl(url)
            webView?.visibility = View.VISIBLE
            settingsPanel?.visibility = View.GONE
        }
    }

    /** Bring the settings back over the dashboard (double-tap on the title). */
    fun showSettings() {
        settingsPanel?.visibility = View.VISIBLE
        webView?.visibility = View.GONE
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            showSettings()
        }
    }
}
