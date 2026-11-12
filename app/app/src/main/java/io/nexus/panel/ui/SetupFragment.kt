package io.nexus.panel.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.nexus.panel.core.Settings

/** Page 1 — connection & device setup. */
class SetupFragment : androidx.fragment.app.Fragment() {

    private val fields = mutableMapOf<String, EditText>()
    private var ctx: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        ctx = inflater.context
        val c = ctx!!
        fields.clear()

        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        col.addView(headline(c, "Setup",
            "Where this panel connects. Everything else is a module you can switch on or off."))

        // ---- device identity ----
        col.addView(section(c, "Device"))
        col += text("device_name", "Panel name", Settings.deviceName,
            "How Home Assistant sees this panel, e.g. NSPanel Kitchen.")

        // ---- MQTT ----
        col.addView(section(c, "MQTT"))
        col += text("mqtt_host", "Broker host", Settings.mqttHost,
            "MQTT broker address. The panel's own broker is 127.0.0.1; use your Home Assistant broker for real discovery.")
        col += text("mqtt_port", "Broker port", Settings.mqttPort.toString(),
            "Usually 1883.", InputType.TYPE_CLASS_NUMBER)
        col += text("mqtt_user", "Username", Settings.mqttUser ?: "",
            "Leave empty if the broker is open.")
        col += text("mqtt_pass", "Password", Settings.mqttPass ?: "",
            "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        col += text("topic_prefix", "Topic prefix", Settings.topicPrefix,
            "Topic base for this panel's events and commands (default nspanelpro).")

        // ---- Home Assistant ----
        col.addView(section(c, "Home Assistant"))
        col += text("ha_url", "HA URL", Settings.haUrl,
            "Home Assistant base URL, e.g. http://192.168.1.10:8123. Used by the voice satellite.")
        col += text("ha_token", "Long-lived access token", Settings.haToken,
            "Profile → Security → Long-lived access tokens.",
            InputType.TYPE_TEXT_VARIATION_PASSWORD)
        col += text("dashboard_url", "Dashboard URL", Settings.dashboardUrl,
            "The HA dashboard to show on the wall. Leave empty to keep the setup screen.")

        // ---- actions ----
        col.addView(button(c, "Save", "Apply and restart the modules") {
            Settings.lastDashboardError = ""   // retest on next load
            save()
        })
        col.addView(button(c, "Test dashboard URL", "Check whether the dashboard loads (HTTP code)") {
            testDashboardUrl(c)
        })
        if (Settings.lastDashboardError.isNotBlank()) {
            col.addView(errorBanner(c, Settings.lastDashboardError))
        }

        return ScrollView(c).apply { addView(col) }
    }

    private fun save() {
        val c = ctx ?: return
        fields.forEach { (key, et) ->
            val v = et.text.toString()
            if (key == "mqtt_port") {
                Settings.set(key, v.toIntOrNull() ?: 1883)
            } else {
                Settings.set(key, v)
            }
        }
        Toast.makeText(c, "Saved — restarting modules", Toast.LENGTH_SHORT).show()
        NexusUi.restartModules(c)
    }

    // ---- view builders ---------------------------------------------------

    private fun dp(v: Int): Int = (v * (ctx!!.resources.displayMetrics.density)).toInt()

    private operator fun LinearLayout.plusAssign(v: View) { addView(v) }

    private fun headline(c: Context, title: String, sub: String): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
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

    private fun section(c: Context, title: String): View =
        TextView(c).apply {
            text = title.uppercase()
            setTextColor(0xFFFF8C28.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(18), 0, dp(8))
        }

    private fun text(
        key: String, label: String, value: String,
        description: String, inputType: Int = InputType.TYPE_CLASS_TEXT
    ): View {
        val c = ctx!!
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(c).apply {
                text = label
                setTextColor(0xFFF0F4F8.toInt())
                textSize = 14f
            })
            if (description.isNotBlank()) addView(TextView(c).apply {
                text = description
                setTextColor(0xFF8B97A5.toInt())
                textSize = 11f
                setPadding(0, dp(2), 0, dp(6))
            })
            val et = EditText(c).apply {
                setText(value)
                this.inputType = inputType
                setTextColor(0xFFF0F4F8.toInt())
                setHintTextColor(0xFF5B6672.toInt())
                setBackgroundColor(0xFF161B21.toInt())
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            fields[key] = et
            addView(et, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun testDashboardUrl(c: Context) {
        Thread {
            try {
                val url = fields["dashboard_url"]?.text?.toString()?.trim()?.let {
                    if (it.isBlank()) "" else if (it.startsWith("http")) it else {
                        val ha = (fields["ha_url"]?.text?.toString()?.trim() ?: "").let { h -> if (h.isBlank()) "" else if (h.startsWith("http")) h else "http://$h" }.trimEnd('/')
                        if (ha.isBlank()) it else "$ha/${it.trimStart('/')}"
                    }
                } ?: Settings.dashboardUrl
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                conn.disconnect()
                android.os.Handler(c.mainLooper).post {
                    android.widget.Toast.makeText(c,
                        "HTTP $code for $url" + when {
                            code in 200..299 -> " ✓"
                            code in 300..399 -> " (redirect)"
                            else -> " ✗ (check the url_path)"
                        }, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.os.Handler(c.mainLooper).post {
                    android.widget.Toast.makeText(c, "Failed: ${e.message}",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun errorBanner(c: Context, text: String): View =
        android.widget.TextView(c).apply {
            this.text = text
            setTextColor(0xFFFF8A8A.toInt())
            textSize = 13f
            setBackgroundColor(0xFF2A1616.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

    private fun button(c: Context, label: String, desc: String, onClick: () -> Unit): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, 0)
            addView(TextView(c).apply {
                text = desc
                setTextColor(0xFF8B97A5.toInt())
                textSize = 11f
            })
            addView(TextView(c).apply {
                text = label
                setTextColor(Color.BLACK)
                textSize = 16f
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
}
