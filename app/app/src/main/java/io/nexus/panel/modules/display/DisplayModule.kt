package io.nexus.panel.modules.display

import android.content.Context
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SysSettings
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import io.nexus.panel.modules.sensors.SensorModule
import org.json.JSONObject

/**
 * Display: wake/sleep, brightness (manual + light-triggered), screen-on
 * schedules, and wake-on-proximity.
 */
class DisplayModule : Module {

    override val name = "display"

    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorModule: SensorModule? = null
    private var pm: PowerManager? = null
    private var appContext: Context? = null
    private val scheduler = Handler(Looper.getMainLooper())
    private var scheduling = false

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String {
        val screenOn = pm?.isInteractive == true
        return "screen=${if (screenOn) "on" else "off"} brightness=${currentBrightness()}"
    }

    override fun start(context: Context) {
        appContext = context.applicationContext
        pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        sensorModule = ModuleManager.get("sensors") as? SensorModule
        sensorModule?.onProximity = { wake() }
        sensorModule?.onLight = { kind, _ ->
            val target = when (kind) {
                "light_below" -> Settings.getInt("brightness_on_light_below", -1)
                "light_above" -> Settings.getInt("brightness_on_light_above", -1)
                else -> -1
            }
            if (target >= 0) setBrightness(target)
        }
        setBrightness(Settings.getInt("brightness", currentBrightness()))
        startScheduleLoop()
    }

    override fun stop() {
        scheduling = false
        scheduler.removeCallbacksAndMessages(null)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    /**
     * Screen-on schedules: every 30 s, wake when inside the configured window
     * (weekday/weekend), sleep when outside — only when the user enabled
     * scheduling (any non-00:00 begin/end).
     */
    private fun startScheduleLoop() {
        if (scheduling) return
        scheduling = true
        scheduler.post(object : Runnable {
            override fun run() {
                if (!scheduling) return
                try {
                    val now = java.util.Calendar.getInstance()
                    val enabled = Settings.get("screen_on_weekday_begin", "00:00") != "00:00" ||
                                  Settings.get("screen_on_weekend_begin", "00:00") != "00:00"
                    if (enabled) {
                        if (inScreenOnWindow(now)) {
                            if (!isScreenOn) wake()
                        } else if (isScreenOn) {
                            sleep()
                        }
                    }
                } catch (_: Exception) {}
                scheduler.postDelayed(this, 30_000)
            }
        })
    }

    // ---- wake / sleep --------------------------------------------------

    fun wake() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "nexus:display_wake"
        )
        wakeLock?.acquire(60_000)
        // sysfs backlights on this panel can be forced on as a fallback
        mqtt?.state("screen", JSONObject().put("screen", "on"))
    }

    fun sleep() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        mqtt?.state("screen", JSONObject().put("screen", "off"))
    }

    val isScreenOn: Boolean get() = pm?.isInteractive == true

    // ---- brightness ----------------------------------------------------

    private fun currentBrightness(): Int = try {
        SysSettings.System.getInt(appContext!!.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, 128)
    } catch (_: Exception) { 128 }

    fun setBrightness(value: Int) {
        val v = value.coerceIn(0, 255)
        try {
            SysSettings.System.putInt(appContext!!.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, v)
            mqtt?.state("brightness", JSONObject().put("brightness", v))
        } catch (_: SecurityException) {
            // WRITE_SETTINGS not granted — needs a one-time toggle or `adb shell appops set`
        }
    }

    // ---- schedules -----------------------------------------------------

    fun inScreenOnWindow(now: java.util.Calendar): Boolean {
        val weekday = now.get(java.util.Calendar.DAY_OF_WEEK) in
                java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
        val prefix = if (weekday) "screen_on_weekday" else "screen_on_weekend"
        val begin = Settings.get("${prefix}_begin", "00:00")
        val end = Settings.get("${prefix}_end", "00:00")
        if (begin == "00:00" && end == "00:00") return true
        val minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val b = begin.split(":").map { it.toIntOrNull() ?: 0 }.takeIf { it.size == 2 }
            ?.let { it[0] * 60 + it[1] } ?: 0
        val e = end.split(":").map { it.toIntOrNull() ?: 0 }.takeIf { it.size == 2 }
            ?.let { it[0] * 60 + it[1] } ?: 0
        return if (b <= e) minute in b..e else minute >= b || minute <= e
    }
}
