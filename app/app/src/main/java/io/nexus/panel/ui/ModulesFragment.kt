package io.nexus.panel.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import io.nexus.panel.R
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings

/** Page 2 — every module with its description and an on/off switch. */
class ModulesFragment : androidx.fragment.app.Fragment() {

    private var ctx: Context? = null
    private val rows = mutableMapOf<String, View>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        ctx = inflater.context
        val c = ctx!!
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        col += headline(c, "Modules",
            "Turn each function on or off. What is off costs nothing at runtime.")

        val order = listOf(
            "mqtt", "sensors", "display", "dashboard",
            "voice", "zigbee", "firmware", "fleet", "remote_desktop"
        )
        order.forEach { name ->
            ModuleManager.get(name)?.let { mod ->
                col += moduleCard(c, name, NexusUi.description(name), mod.status())
            }
        }

        col += button(c, "Restart modules", "Apply the switches above right now") {
            NexusUi.restartModules(c)
            refresh()
        }
        col += note(c, "Changes are applied immediately; the Restart button is only needed if a module failed to start.")

        return ScrollView(c).apply { addView(col) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val c = ctx ?: return
        ModuleManager.all().forEach { mod ->
            rows[mod.name]?.let { row ->
                row.findViewById<TextView>(R.id.module_status)?.text = mod.status()
                row.findViewById<Switch>(R.id.module_switch)?.isChecked =
                    Settings.moduleEnabled(mod.name)
            }
        }
    }

    // ---- card builder ----------------------------------------------------

    private fun moduleCard(c: Context, name: String, description: String, status: String): View {
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF161B21.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        // header row: name + switch
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        head += TextView(c).apply {
            id = R.id.module_name
            text = NexusUi.title(name)
            setTextColor(0xFFF0F4F8.toInt())
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        head += Switch(c).apply {
            id = R.id.module_switch
            isChecked = Settings.moduleEnabled(name)
            setOnCheckedChangeListener { _, checked ->
                Settings.setModuleEnabled(name, checked)
                ModuleManager.applyDesired(c, name)
                postDelayed({ refresh() }, 400)
            }
        }
        card += head
        // description
        card += TextView(c).apply {
            text = description
            setTextColor(0xFF8B97A5.toInt())
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        // live status
        card += TextView(c).apply {
            id = R.id.module_status
            text = status
            setTextColor(0xFF6FCB7B.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(6), 0, 0)
        }
        rows[name] = card
        // spacing
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            setPadding(0, 0, 0, dp(10))
        }
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

    private fun button(c: Context, label: String, desc: String, onClick: () -> Unit): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(TextView(c).apply {
                text = desc
                setTextColor(0xFF8B97A5.toInt())
                textSize = 11f
            })
            addView(TextView(c).apply {
                text = label
                setTextColor(Color.BLACK)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(0xFFFF8C28.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(8)
            })
        }

    private fun note(c: Context, text: String): View =
        TextView(c).apply {
            this.text = text
            setTextColor(0xFF5B6672.toInt())
            textSize = 11f
            setPadding(0, dp(10), 0, 0)
        }
}
