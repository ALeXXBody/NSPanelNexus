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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.nexus.panel.core.HaAuth
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

    private lateinit var root: FrameLayout
    private lateinit var pagesContainer: LinearLayout
    private var dashboardView: WebView? = null
    private var titleBar: TextView? = null
    private var titleRow: LinearLayout? = null
    private var returnBtn: android.widget.Button? = null
    private val exitTaps = ArrayDeque<Long>()
    private var pinView: LinearLayout? = null
    private var pinDots: TextView? = null
    private val pinEntry = StringBuilder()
    private var loginView: LinearLayout? = null
    private var loginWeb: WebView? = null
    private var pendingInject = false
    private var lastInjectUrl: String? = null

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

        // FrameLayout: pages at the bottom, dashboard and PIN pad as fullscreen
        // overlays above (a vertical LinearLayout would push later children
        // off-screen, which is why the PIN pad never showed).
        root = FrameLayout(this).apply {
            setBackgroundColor(0xFF0D1016.toInt())
        }

        // ---- title bar: title + Return to HA (dashboard is the default state;
        // 6 quick taps anywhere on it drop back here; the button goes back) --
        titleBar = TextView(this).apply {
            text = "NSPanel Nexus"
            setTextColor(0xFFFF8C28.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        returnBtn = android.widget.Button(this).apply {
            text = "Return to HA"
            setTextColor(0xFF000000.toInt())
            textSize = 12f
            setBackgroundColor(0xFFFF8C28.toInt())
            setOnClickListener { loadDashboard(Settings.dashboardUrl) }
            visibility = View.GONE
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF161B21.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        row.addView(titleBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(returnBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        titleRow = row

        // ---- dashboard layer (full screen, over everything) ---------------
        val dash = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    maybeInjectSession(view, url)
                }
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

        root.addView(pagesContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // dashboard goes on top of the pages
        root.addView(dash, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // PIN pad goes above everything (dashboard included)
        pinView = buildPinPad()
        root.addView(pinView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // login overlay goes above everything (dashboard included)
        loginView = buildLoginView()
        root.addView(loginView, FrameLayout.LayoutParams(
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
        // title bar (+ Return to HA)
        container.addView(titleRow, LinearLayout.LayoutParams(
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

    // ---- exit PIN (optional; set in Setup) ----------------------------------

    private fun buildPinPad(): LinearLayout {
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE0D1016.toInt())
            visibility = View.GONE
        }
        pad.addView(TextView(this).apply {
            text = "Enter PIN to exit"
            setTextColor(0xFFF0F4F8.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        val dots = TextView(this).apply {
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF8C28.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        pinDots = dots
        pad.addView(dots)
        listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
            listOf("C", "0", "⌫"),
        ).forEach { rowKeys ->
            val rowL = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { k ->
                rowL.addView(android.widget.Button(this).apply {
                    text = k
                    textSize = 20f
                    setTextColor(0xFFF0F4F8.toInt())
                    setBackgroundColor(0xFF22252B.toInt())
                    setOnClickListener { pinKey(k) }
                }, LinearLayout.LayoutParams(dp(76), dp(56)).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                })
            }
            pad.addView(rowL)
        }
        return pad
    }

    private fun pinRefresh() {
        pinDots?.text = "●".repeat(pinEntry.length) + "○".repeat((4 - pinEntry.length).coerceAtLeast(0))
    }

    private fun setImeHidden(hidden: Boolean) {
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            if (hidden) {
                // The HA login page keeps a text field focused, so its keyboard
                // would cover the pad's lower rows. Hide it while the pad is up.
                dashboardView?.clearFocus()
                pinDots?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
                window.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            } else {
                window.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
            }
        } catch (_: Exception) {}
    }

    private fun showPinPad() {
        pinEntry.clear()
        pinRefresh()
        pinView?.visibility = View.VISIBLE
        setImeHidden(true)
    }

    private fun hidePinPad() {
        pinView?.visibility = View.GONE
        setImeHidden(false)
    }

    private fun pinKey(k: String) {
        when (k) {
            "C" -> hidePinPad()  // cancel: stay on the dashboard
            "⌫" -> if (pinEntry.isNotEmpty()) {
                pinEntry.deleteCharAt(pinEntry.length - 1)
                pinRefresh()
            }
            else -> {
                if (pinEntry.length < 4) {
                    pinEntry.append(k)
                    pinRefresh()
                }
                if (pinEntry.length == 4) {
                    if (pinEntry.toString() == Settings.get("exit_pin", "")) {
                        hidePinPad()
                        hideDashboard()
                        toastOnUi("Firmware — Return to HA when done")
                    } else {
                        toastOnUi("Wrong PIN")
                        pinEntry.clear()
                        pinRefresh()
                    }
                }
            }
        }
    }

    // ---- Home Assistant login (OAuth, Companion-app style) ----------------

    private fun buildLoginView(): LinearLayout {
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1016.toInt())
            visibility = View.GONE
        }
        pad.addView(TextView(this).apply {
            text = "Log in to Home Assistant"
            setTextColor(0xFFF0F4F8.toInt())
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(12))
        })
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val u = req.url.toString()
                    if (u.startsWith(HaAuth.REDIRECT_URI)) {
                        val code = req.url.getQueryParameter("code")
                        if (!code.isNullOrBlank()) finishHaLogin(code) else {
                            toastOnUi("Login approved without a code — try again")
                        }
                        return true
                    }
                    return false
                }
            }
        }
        loginWeb = wv
        pad.addView(wv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        pad.addView(android.widget.Button(this).apply {
            text = "Cancel"
            setTextColor(0xFFF0F4F8.toInt())
            textSize = 14f
            setBackgroundColor(0xFF22252B.toInt())
            setOnClickListener { loginView?.visibility = View.GONE }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return pad
    }

    /** Open the HA login page (called from Setup). */
    fun openHaLogin() {
        val haBase = Settings.haUrl
        if (haBase.isBlank()) {
            Toast.makeText(this, "Set the Home Assistant URL first", Toast.LENGTH_LONG).show()
            return
        }
        loginWeb?.loadUrl(HaAuth.authorizeUrl(haBase))
        loginView?.visibility = View.VISIBLE
    }

    private fun finishHaLogin(code: String) {
        val origin = try {
            HaAuth.originOf(Settings.dashboardUrl.ifBlank { Settings.haUrl })
        } catch (_: Exception) { Settings.haUrl.trimEnd('/') }
        HaAuth.exchangeCode(origin, code) { tokens ->
            runOnUiThread {
                if (tokens == null || tokens.optString("refresh_token").isBlank()) {
                    toastOnUi("Login failed — check the code and try again")
                    return@runOnUiThread
                }
                Settings.set("ha_refresh_token", tokens.optString("refresh_token"))
                loginView?.visibility = View.GONE
                loginWeb?.loadUrl("about:blank")
                lastInjectUrl = null  // force session injection on next dashboard load
                toastOnUi("Logged in to Home Assistant")
                val url = Settings.dashboardUrl
                if (url.isNotBlank()) loadDashboard(url)
            }
        }
    }

    /** Log out: drop the refresh token + cookies so the dashboard shows HA login. */
    fun haLogout() {
        HaAuth.logout()
        lastInjectUrl = null
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        val url = Settings.dashboardUrl
        if (url.isNotBlank()) loadDashboard(url) else hideDashboard()
        Toast.makeText(this, "Logged out of Home Assistant", Toast.LENGTH_LONG).show()
    }

    /** After a dashboard page finishes, install our OAuth session if it needs one. */
    private fun maybeInjectSession(view: WebView, url: String) {
        if (!pendingInject || !HaAuth.isLoggedIn()) {
            pendingInject = false
            return
        }
        pendingInject = false
        lastInjectUrl = url
        val origin = try { HaAuth.originOf(url) } catch (_: Exception) { return }
        val refresh = Settings.get("ha_refresh_token", "")
        if (refresh.isBlank()) return
        HaAuth.refresh(origin, refresh) { tokens ->
            runOnUiThread {
                if (tokens == null || tokens.optString("access_token").isBlank()) {
                    toastOnUi("Session expired — log in to Home Assistant again")
                    return@runOnUiThread
                }
                try {
                    view.evaluateJavascript(HaAuth.injectJs(tokens), null)
                } catch (_: Exception) {}
                view.reload()
            }
        }
    }

    // ---- dashboard mode ---------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadDashboard(url: String) {
        if (url.isBlank()) return hideDashboard()
        pendingInject = HaAuth.isLoggedIn() && url != lastInjectUrl
        try { (ModuleManager.get("dashboard") as? io.nexus.panel.modules.dashboard.DashboardModule)?.reportCurrent(url) } catch (_: Exception) {}
        dashboardView?.let { wv ->
            if (wv.url != url) wv.loadUrl(url)
            wv.visibility = View.VISIBLE
        }
        titleBar?.text = "NSPanel Nexus — dashboard (6 taps to exit)"
        updateReturnButton()
    }

    private fun hideDashboard() {
        dashboardView?.visibility = View.GONE
        titleBar?.text = "NSPanel Nexus"
        updateReturnButton()
    }

    private fun updateReturnButton() {
        returnBtn?.visibility =
            if (Settings.dashboardUrl.isNotBlank() && !dashboardShown()) View.VISIBLE else View.GONE
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
                        val adx0 = kotlin.math.abs(ev.x - touchDownX)
                        val ady0 = kotlin.math.abs(ev.y - touchDownY)
                        if (adx0 < 24 && ady0 < 24 && dashboardShown() && pinView?.visibility != View.VISIBLE) {
                            exitTaps.addLast(now)
                            try { android.util.Log.i("NexusExit", "tap count=" + exitTaps.size) } catch (_: Exception) {}
                            while (exitTaps.isNotEmpty() && now - exitTaps.first() > 3000) exitTaps.removeFirst()
                            if (exitTaps.size >= 6) {
                                try { android.util.Log.i("NexusExit", "six taps, pinSet=" + Settings.get("exit_pin", "").isNotBlank()) } catch (_: Exception) {}
                                exitTaps.clear()
                                if (Settings.get("exit_pin", "").isNotBlank()) showPinPad()
                                else {
                                    hideDashboard()
                                    toastOnUi("Firmware — Return to HA when done")
                                }
                            } else if (exitTaps.size == 3) {
                                toastOnUi("Tap ${6 - exitTaps.size} more times to exit")
                            }
                        }
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

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchEvent = 0L

    private operator fun LinearLayout.plusAssign(v: View) { addView(v) }
}
