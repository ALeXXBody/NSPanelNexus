package io.nexus.panel

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
        clearStaleHomePreference()
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
        // double-tap the top bar to leave the dashboard (no-op in settings mode)
        titleBar?.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < 400 && dashboardShown()) hideDashboard()
            lastTap = now
        }

        // ---- dashboard layer (full screen, over everything) ---------------
        val dash = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, req: WebResourceRequest,
                                             err: WebResourceError) {
                    if (req.isForMainFrame) {
                        runOnUiThread { dashboardLoadFailed(req.url.toString()) }
                    }
                }

                override fun onReceivedHttpError(view: WebView, req: WebResourceRequest,
                                                 resp: WebResourceResponse) {
                    if (req.isForMainFrame) {
                        runOnUiThread { dashboardLoadFailed(req.url.toString()) }
                    }
                }
            }
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
        try { (ModuleManager.get("dashboard") as? io.nexus.panel.modules.dashboard.DashboardModule)?.reportCurrent(url) } catch (_: Exception) {}
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

    private fun dashboardLoadFailed(url: String) {
        wvStop()
        hideDashboard()
        Settings.lastDashboardError = "Dashboard URL failed: $url"
        // bring the user back to Setup where the URL can be fixed
        showPage(0)
        toastOnUi("Dashboard URL failed — fix it in Setup")
    }

    private fun wvStop() {
        dashboardView?.let { wv ->
            wv.stopLoading()
            wv.loadDataWithBaseURL(null, "<html><body style='display:none'></body></html>",
                "text/html", "utf-8", null)
            wv.visibility = View.GONE
        }
    }

    private fun toastOnUi(msg: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun dashboardShown(): Boolean = dashboardView?.visibility == View.VISIBLE

    /**
     * Earlier builds declared a HOME intent-filter; the system keeps that
     * preference even after it is removed, which made every Home press show a
     * resolver dialog. Clear our own preferred-activity record once per start.
     */
    private fun clearStaleHomePreference() {
        try {
            packageManager.clearPackagePreferredActivities(packageName)
        } catch (_: Exception) {}
    }


    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        try {
            if (ev != null) {
                when (ev.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchDownX = ev.x; touchDownY = ev.y
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTouchEvent > 800) {
                            lastTouchEvent = now
                            val dx = ev.x - touchDownX
                            val dy = ev.y - touchDownY
                            val adx = kotlin.math.abs(dx); val ady = kotlin.math.abs(dy)
                            val type = if (adx < 24 && ady < 24) "tap"
                                else if (adx > ady) (if (dx > 0) "swipe_right" else "swipe_left")
                                else (if (dy > 0) "swipe_down" else "swipe_up")
                            try {
                                val mqtt = ModuleManager.get("mqtt") as? io.nexus.panel.modules.mqtt.MqttModule
                                mqtt?.event("touch", org.json.JSONObject().put("event_type", type))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return super.dispatchTouchEvent(ev)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try {
            (ModuleManager.get("dashboard") as? io.nexus.panel.modules.dashboard.DashboardModule)?.let {
                it.onUrlChanged = null
                it.onWake = null
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private var lastTap = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchEvent = 0L

    private operator fun LinearLayout.plusAssign(v: View) { addView(v) }
}
