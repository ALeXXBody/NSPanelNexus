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
        mqtt.onCommand["query"] = { _ -> mqtt.publish(mqtt.base + "/config", Settings.all().toString(), qos = 1, retain = true) }
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
            context.startService(Intent(context, NexusService::class.java))
        }
    }
}
