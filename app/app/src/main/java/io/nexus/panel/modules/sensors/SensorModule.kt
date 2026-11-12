package io.nexus.panel.modules.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject
import kotlin.math.abs

/**
 * Proximity + light sensors.
 *
 * The NSPanel Pro's proximity sensor reports a *graded* value (~10 Hz), not
 * near/far, and the direction of the change when someone approaches depends on
 * the firmware (some report 0/1, some 1000–4000, and which way is inverted).
 * The resting level also depends on the wall the panel hangs on, and the
 * display's own light contaminates the reading.
 *
 * So: learn the resting level (skipping the first 2 s after a screensaver
 * change), then wake when two consecutive readings depart from it by
 * `proximity_delta` or 15 % of the resting level, whichever is larger. That is
 * the approach the native Flutter app proved works on this hardware.
 */
class SensorModule : Module, SensorEventListener {

    override val name = "sensors"

    private var sm: SensorManager? = null
    private var proxSensor: Sensor? = null
    private var lightSensor: Sensor? = null

    // learned proximity baseline
    @Volatile private var baseline = -1f
    @Volatile private var baselineSamples = 0
    @Volatile private var lastWakeAt = 0L
    @Volatile private var consecutiveAway = 0

    // last values for MQTT reporting
    @Volatile private var lastProx = 0f
    @Volatile private var lastLux = 0f

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String =
        "prox=%.0f lux=%.0f baseline=%.0f".format(lastProx, lastLux, baseline)

    override fun start(context: Context) {
        sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proxSensor = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        proxSensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun stop() {
        sm?.unregisterListener(this)
        sm = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val v = event.values[0]
                lastProx = v
                updateBaseline(v)
                checkProximity(v)
            }
            Sensor.TYPE_LIGHT -> {
                val now = System.currentTimeMillis()
                if (now - lastLuxPublish >= 1000) {   // max 1 publish/second
                    lastLuxPublish = now
                    lastLux = event.values[0]
                    checkLight(lastLux)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateBaseline(v: Float) {
        if (baseline < 0) {
            baselineSamples++
            if (baselineSamples >= 20) {   // ~2 s at 10 Hz
                baseline = v
                baselineSamples = 0
            }
        } else if (abs(v - baseline) < 0.02f * baseline.coerceAtLeast(1f)) {
            baseline = 0.98f * baseline + 0.02f * v   // slow drift follow
        }
    }

    private fun checkProximity(v: Float) {
        if (baseline < 0) return
        val threshold = (Settings.getInt("proximity_delta", 12).toFloat()).coerceAtLeast(0.15f * baseline)
        val away = abs(v - baseline) >= threshold
        if (away) {
            consecutiveAway++
            if (consecutiveAway == 2) {
                consecutiveAway = 0
                val now = System.currentTimeMillis()
                if (now - lastWakeAt > 2000) {
                    lastWakeAt = now
                    baseline = -1f      // re-learn after wake (display light changed)
                    mqtt?.event("proximity", JSONObject().put("event_type", "triggered"))
                    // let the display module react
                    onProximity?.invoke()
                }
            }
        } else {
            consecutiveAway = 0
        }
    }

    private fun checkLight(lux: Float) {
        val below = Settings.getInt("light_below", 0)
        val above = Settings.getInt("light_above", 0)
        val kind = when {
            above > 0 && lux >= above -> "light_above"
            below > 0 && lux <= below -> "light_below"
            else -> "light_normal"
        }
        if (kind != "light_normal" && lastLuxKind != kind) {
            mqtt?.event("light", JSONObject().put("event_type", kind))
            onLight?.invoke(kind, lux)
        }
        lastLuxKind = kind
        mqtt?.state("illuminance", JSONObject().put("illuminance", lux))
    }

    private var lastLuxKind = ""
    private var lastLuxPublish = 0L

    /** Hook for the display module: "someone walked up, wake the screen". */
    var onProximity: (() -> Unit)? = null
    /** Hook for the display module: light event kind + lux. */
    var onLight: ((kind: String, lux: Float) -> Unit)? = null
}
