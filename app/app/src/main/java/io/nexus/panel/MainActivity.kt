package io.nexus.panel

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.content.Context
import android.content.res.Configuration
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
    private var firmwareShown = false
    private var darkFixSettle: Runnable? = null
    private val darkFixHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Decide once per settled page (never a polling loop — that fought itself). */
    private fun applyImmersive(hide: Boolean) {
        try {
            val decor = window.decorView
            if (hide) {
                decor.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            } else {
                decor.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        } catch (_: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !firmwareShown) applyImmersive(true)
    }

    private fun scheduleDarkFix() {
        try { darkFixSettle?.let { darkFixHandler.removeCallbacks(it) } } catch (_: Exception) {}
        if (!Settings.getBool("dark_mode", true)) return
        val r = Runnable {
            val wv = dashboardView
            if (wv == null || wv.visibility != View.VISIBLE) return@Runnable
            try {
                wv.evaluateJavascript(DARK_CHECK_JS) { result ->
                    try {
                        try { android.util.Log.i("NexusDark", "probe=" + result) } catch (_: Exception) {}
                        val parts = (result ?: "").trim().trim('"').split("|")
                        val sysDark = parts.getOrNull(0) == "dark"
                        val filt = parts.getOrNull(3) == "f"
                        fun lum(i: Int): Double? = parts.getOrNull(i)?.toDoubleOrNull()
                        val b = lum(1); val h = lum(2)
                        val v = if (b == null) h else if (h == null) b else maxOf(b, h)
                        // Our own filter must not count: it inverts rendering, not styles,
                        // so a dark reading with the filter present means native dark.
                        val light = v != null && v > 0.5
                        if (light) {
                            wv.evaluateJavascript(DARK_ON_JS, null)
                            try { android.util.Log.i("NexusDark", "page is light -> filter ON") } catch (_: Exception) {}
                        } else {
                            wv.evaluateJavascript(DARK_OFF_JS, null)
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        darkFixSettle = r
        darkFixHandler.postDelayed(r, 3000)
    }

    private fun cancelDarkFix() {
        try {
            darkFixSettle?.let { darkFixHandler.removeCallbacks(it) }
            darkFixSettle = null
        } catch (_: Exception) {}
    }

    private val pages = listOf(
        "Setup" to { SetupFragment() },
        "Modules" to { ModulesFragment() },
        "Diagnostics" to { DiagnosticsFragment() },
    )

    override fun attachBaseContext(newBase: Context) {
        // Dark dashboard: HA follows prefers-color-scheme when its theme is auto.
        // Wrapping the base context guarantees the WebView sees NIGHT_YES from birth
        // (mutating resources later in onCreate is too late for some WebView versions).
        try {
            Settings.init(newBase.applicationContext)
            if (Settings.getBool("dark_mode", true)) {
                val conf = Configuration(newBase.resources.configuration)
                conf.uiMode = conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                    Configuration.UI_MODE_NIGHT_YES
                super.attachBaseContext(newBase.createConfigurationContext(conf))
                return
            }
        } catch (_: Exception) {}
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        // clearStaleHomePreference() — see below
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
                    scheduleDarkFix()
                }
                override fun onReceivedError(view: WebView, req: WebResourceRequest,
                                             err: WebResourceError) {
                    if (req.isForMainFrame) {
                        val desc = try { err.description.toString() } catch (_: Exception) { "unknown" }
                        android.util.Log.e("NexusDash", "onReceivedError url=" + req.url + " desc=" + desc)
                        runOnUiThread { dashboardLoadFailed(req.url.toString() + " — " + desc) }
                    }
                }

                override fun onReceivedHttpError(view: WebView, req: WebResourceRequest,
                                                 resp: WebResourceResponse) {
                    if (req.isForMainFrame) {
                        val desc = "HTTP " + resp.statusCode + " " + (resp.reasonPhrase ?: "")
                        android.util.Log.e("NexusDash", "onReceivedHttpError url=" + req.url + " " + desc)
                        runOnUiThread { dashboardLoadFailed(req.url.toString() + " — " + desc) }
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
            visibility = View.GONE
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
        pagesContainer.visibility = View.VISIBLE
        titleRow?.visibility = View.VISIBLE
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

    /** Dark dashboard: HA follows prefers-color-scheme when its theme is auto. */


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

    companion object {
        // Transparent bodies report rgba(0,0,0,0) (looks dark, is not) — probe <html> too
        // and take the lightest opaque layer. Also reports the WebView's own
        // prefers-color-scheme verdict so we can tell a broken signal apart
        // from an explicit light theme (format: "scheme|body|html|filtered").
        private const val DARK_CHECK_JS = "(function(){function lum(el){try{var c=getComputedStyle(el).backgroundColor.match(/[\\d.]+/g);if(!c||c.length<3)return 'null';if(c.length>3&&parseFloat(c[3])<0.5)return 'null';return ''+(0.299*parseFloat(c[0])+0.587*parseFloat(c[1])+0.114*parseFloat(c[2]))/255;}catch(e){return 'null';}}try{var m=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';var f=!!document.getElementById('nexus-force-dark');return m+'|'+lum(document.body)+'|'+lum(document.documentElement)+'|'+(f?'f':'n');}catch(e){return 'error';}})()"
        private const val DARK_ON_JS = "(function(){if(!document.getElementById('nexus-force-dark')){var st=document.createElement('style');st.id='nexus-force-dark';st.textContent=\"body{filter:invert(1) hue-rotate(180deg)!important}img,video,picture,canvas,svg,[role='img']{filter:invert(1) hue-rotate(180deg)!important}\";document.documentElement.appendChild(st);}})()"
        private const val DARK_OFF_JS = "(function(){var st=document.getElementById('nexus-force-dark');if(st)st.remove();})()"
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
        pagesContainer.visibility = View.GONE
        titleRow?.visibility = View.GONE
        firmwareShown = false
        titleBar?.text = "NSPanel Nexus — dashboard (6 taps to exit)"
        updateReturnButton()
        applyImmersive(true)
        scheduleDarkFix()
    }

    private fun hideDashboard() {
        cancelDarkFix()
        dashboardView?.visibility = View.GONE
        pagesContainer.visibility = View.VISIBLE
        titleRow?.visibility = View.VISIBLE
        titleBar?.text = "NSPanel Nexus"
        firmwareShown = true
        updateReturnButton()
        applyImmersive(false)
    }



    private fun updateReturnButton() {
        returnBtn?.visibility =
            if (firmwareShown) View.VISIBLE else View.GONE
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
        // no-op: clearing preferred activities on every boot is not needed and
        // was tripping the WebView initializer on some Android 8.1 firmwares.
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
                        if (adx0 < 24 && ady0 < 24 && touchDownX < 90f && touchDownY < 90f &&
                            dashboardShown() && pinView?.visibility != View.VISIBLE) {
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
                                toastOnUi("Tap ${6 - exitTaps.size} more times in the top-left corner to exit")
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
