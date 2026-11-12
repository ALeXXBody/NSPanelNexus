package io.nexus.panel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.nexus.panel.MainActivity
import io.nexus.panel.R
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.dashboard.DashboardModule
import io.nexus.panel.modules.desktop.RemoteDesktopModule
import io.nexus.panel.modules.display.DisplayModule
import io.nexus.panel.modules.firmware.FirmwareModule
import io.nexus.panel.modules.fleet.FleetModule
import io.nexus.panel.modules.mqtt.MqttModule
import io.nexus.panel.modules.sensors.SensorModule
import io.nexus.panel.modules.voice.VoiceModule
import io.nexus.panel.modules.zigbee.ZigbeeModule

/**
 * The single foreground service that hosts every module.
 *
 * Start/stop is driven by ModuleManager + the persisted settings, so the user
 * can enable or disable exactly what the panel should do — from the app's UI
 * or remotely over MQTT.
 */
class NexusService : Service() {

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        registerModules()
        startForeground(1, buildNotification())
        ModuleManager.startDesired(this)
        registerRemoteCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ModuleManager.startDesired(this)
        registerRemoteCommands()
        return START_STICKY
    }

    override fun onDestroy() {
        ModuleManager.stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerModules() {
        ModuleManager.register(MqttModule())
        ModuleManager.register(SensorModule())
        ModuleManager.register(DisplayModule())
        ModuleManager.register(DashboardModule())
        ModuleManager.register(RemoteDesktopModule())
        ModuleManager.register(VoiceModule())
        ModuleManager.register(ZigbeeModule())
        ModuleManager.register(FirmwareModule())
        ModuleManager.register(FleetModule())
    }

    private fun registerRemoteCommands() {
        val mqtt = ModuleManager.get("mqtt") as? MqttModule ?: return
        val display = ModuleManager.get("display") as? DisplayModule
        val voice = ModuleManager.get("voice") as? VoiceModule
        val fleet = ModuleManager.get("fleet") as? FleetModule

        // Pro-Tools-compatible device commands
        mqtt.onCommand["wake_up"] = { _ -> display?.wake() }
        mqtt.onCommand["sleep"] = { _ -> display?.sleep() }
        mqtt.onCommand["default_app"] = { _ -> launchDashboard() }
        mqtt.onCommand["listen"] = { _ -> voice?.startListening() }
        mqtt.onCommand["discover"] = { _ -> fleet?.start(this) }
        mqtt.onCommand["reboot"] = { _ -> runCommand("reboot") }
        mqtt.onCommand["adb"] = { _ -> runCommand("settings put global adb_enabled 1") }
        mqtt.onCommand["query"] = { _ ->
            mqtt.publish(mqtt.base + "/config", mqtt.maskedConfig().toString(), qos = 1, retain = true)
        }

        // settings changed remotely: apply brightness + module switches live.
        // Runs on the MQTT callback thread — hop to a worker so stop()/disconnect()
        // never runs on the Paho thread (deadlock risk).
        mqtt.onConfigUpdated = { patch ->
            Thread {
                try {
                    patch.optInt("brightness", -1).takeIf { it >= 0 }?.let { display?.setBrightness(it) }
                    ModuleManager.all().forEach { m ->
                        val key = "module.${m.name}.enabled"
                        if (patch.has(key)) ModuleManager.applyDesired(this, m.name)
                    }
                    val connKeys = arrayOf("mqtt_host", "mqtt_port", "mqtt_user", "mqtt_pass", "topic_prefix", "ha_topic_prefix")
                    if (connKeys.any { patch.has(it) }) {
                        val m = ModuleManager.get("mqtt")
                        if (m != null) {
                            ModuleManager.stop("mqtt")
                            ModuleManager.start("mqtt", this)
                        }
                    }
                    try {
                        val mm = ModuleManager.get("mqtt") as? MqttModule
                        if (mm != null) {
                            val mods = org.json.JSONObject()
                            for (m in ModuleManager.all()) mods.put(m.name, Settings.moduleEnabled(m.name))
                            mm.state("modules", mods)
                        }
                    } catch (_: Exception) {}
                    try {
                        if (patch.has("dashboard_url")) {
                            val url = Settings.dashboardUrl
                            (ModuleManager.get("dashboard") as? DashboardModule)?.onUrlChanged?.invoke(url)
                        }
                    } catch (_: Exception) {}
                    try {
                        if (patch.has("remote_desktop_token") || patch.has("remote_desktop_port")) {
                            ModuleManager.stop("remote_desktop")
                            if (Settings.moduleEnabled("remote_desktop")) ModuleManager.start("remote_desktop", this)
                        }
                    } catch (_: Exception) {}
                } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()
        }
    }

    private fun runCommand(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        } catch (_: Exception) {}
    }

    private fun launchDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("nexus", "NSPanel Nexus", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        return Notification.Builder(this, "nexus")
            .setContentTitle("NSPanel Nexus")
            .setContentText("Panel service running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NexusService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
