package io.nexus.panel.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.nexus.panel.R
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings

/** Page 3 — what the panel is doing right now. */
class DiagnosticsFragment : androidx.fragment.app.Fragment() {

    private var ctx: Context? = null
    private var root: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        ctx = inflater.context
        val c = ctx!!
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        col += headline(c, "Diagnostics", "What the panel is doing right now.")
        col += TextView(c).apply {
            id = R.id.diag_body
            setTextColor(0xFF6FCB7B.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(0xFF161B21.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        col += refreshButton(c)
        root = ScrollView(c).apply { addView(col) }
        return root!!
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val v = root ?: return
        val body = v.findViewById<TextView>(R.id.diag_body) ?: return
        val sb = StringBuilder()

        sb.appendLine("device      ${Settings.deviceName}")
        sb.appendLine("android     ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("model       ${android.os.Build.MODEL}")
        sb.appendLine("nexus app   ${try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }}")
        sb.appendLine("firmware    ${Settings.get("nexus_version", "unknown")}")
        sb.appendLine()
        sb.appendLine("MQTT        ${Settings.mqttHost}:${Settings.mqttPort}  prefix=${Settings.topicPrefix}")
        sb.appendLine("topic base  ${Settings.topicPrefix}/${Settings.topicId}")
        sb.appendLine()
        ModuleManager.all().forEach { m ->
            val on = Settings.moduleEnabled(m.name)
            sb.appendLine("${m.name.padEnd(14)} ${if (on) "ON " else "off"}  ${m.status()}")
        }
        sb.appendLine()
        Runtime.getRuntime().let {
            val used = (it.totalMemory() - it.freeMemory()) / 1048576
            val total = it.totalMemory() / 1048576
            sb.appendLine("memory      $used / $total MB")
        }
        body.text = sb.toString()
    }

    private fun refreshButton(c: Context): View = TextView(c).apply {
        text = "REFRESH"
        setTextColor(0xFF0D1016.toInt())
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(0xFFFF8C28.toInt())
        gravity = android.view.Gravity.CENTER
        setPadding(0, dp(10), 0, dp(10))
        setOnClickListener { refresh() }
    }.also { v ->
        v.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) }
    }

    private fun dp(v: Int): Int = (v * (ctx!!.resources.displayMetrics.density)).toInt()

    private operator fun LinearLayout.plusAssign(v: View) { addView(v) }

    private fun headline(c: Context, title: String, sub: String): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
            addView(TextView(c).apply {
                text = title
                setTextColor(0xFFFF8C28.toInt())
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(c).apply {
                text = sub
                setTextColor(0xFF9AA5B1.toInt())
                textSize = 13f
                setPadding(0, dp(6), 0, 0)
            })
        }
}
