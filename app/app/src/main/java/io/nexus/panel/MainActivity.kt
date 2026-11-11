package io.nexus.panel

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.dashboard.DashboardModule
import io.nexus.panel.service.NexusService
import io.nexus.panel.ui.DiagnosticsFragment
import io.nexus.panel.ui.ModulesFragment
import io.nexus.panel.ui.SetupFragment

/**
 * Two modes, switched by what is configured on the Setup page:
 *
 *  - **Dashboard mode** — a `dashboard_url` is set: the panel shows that Home
 *    Assistant dashboard full screen (kiosk). Double-tap the title bar to come
 *    back to the settings.
 *  - **Settings mode** — no dashboard configured: the three pages (Setup,
 *    Modules, Diagnostics) are shown.
 */
class MainActivity : FragmentActivity() {

    private lateinit var root: LinearLayout
    private lateinit var pagesContainer: LinearLayout
    private var dashboardView: WebView? = null
    private var titleBar: TextView? = null

    private val pages = listOf(
        "Setup" to { SetupFragment() },
        "Modules" to { ModulesFragment() },
        "Diagnostics" to { DiagnosticsFragment() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        NexusService.start(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1016.toInt())
        }

        // ---- title bar: tap twice to leave the dashboard ------------------
        titleBar = TextView(this).apply {
            text = "NSPanel Nexus"
            setTextColor(0xFFFF8C28.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundColor(0xFF161B21.toInt())
        }
        titleBar?.setOnClickListener {
            if (dashboardShown()) hideDashboard() else lastTap += System.currentTimeMillis().also {
                if (it - lastTap < 400) { /* two taps in settings: nothing */ }
            }
        }

        // ---- dashboard layer (full screen, over everything) ---------------
        val dash = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            visibility = View.GONE
        }
        dashboardView = dash

        // ---- pages layer ----
        pagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = R.id.pages
        }
        buildPages(pagesContainer)

        root.addView(pagesContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // dashboard goes on top of the pages
        root.addView(dash, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // let the Dashboard module drive this view
        (ModuleManager.get("dashboard") as? DashboardModule)?.let { mod ->
            mod.onUrlChanged = { runOnUiThread { loadDashboard(it) } }
            mod.onWake = { runOnUiThread { loadDashboard(Settings.dashboardUrl) } }
        }

        if (savedInstanceState == null) showPage(0)
        // after setup: show the dashboard if one is configured
        if (Settings.dashboardUrl.isNotBlank()) loadDashboard(Settings.dashboardUrl)
    }

    private fun buildPages(container: LinearLayout) {
        // title bar
        container.addView(titleBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // page content
        val content = FrameLayout(this).apply { id = R.id.content }
        container.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF161B21.toInt())
        }
        pages.forEachIndexed { i, (label, _) ->
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFFF0F4F8.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { showPage(i) }
            }
            tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            nav += tv
        }
        container.addView(nav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun showPage(index: Int) {
        val (label, factory) = pages[index]
        titleBar?.text = "NSPanel Nexus — $label"
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, factory() as Fragment)
            .commit()
    }

    // ---- dashboard mode ---------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadDashboard(url: String) {
        if (url.isBlank()) return hideDashboard()
        dashboardView?.let { wv ->
            if (wv.url != url) wv.loadUrl(url)
            wv.visibility = View.VISIBLE
        }
        titleBar?.text = "NSPanel Nexus — dashboard (double-tap for settings)"
    }

    private fun hideDashboard() {
        dashboardView?.visibility = View.GONE
        titleBar?.text = "NSPanel Nexus"
    }

    private fun dashboardShown(): Boolean = dashboardView?.visibility == View.VISIBLE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var lastTap = 0L

    private operator fun LinearLayout.plusAssign(v: View) { addView(v) }
}
