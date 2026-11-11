package io.nexus.panel.ui

import android.content.Context
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.service.NexusService

/** Shared UI helpers: module titles, descriptions, restart. */
object NexusUi {

    fun title(name: String): String = when (name) {
        "mqtt" -> "MQTT"
        "sensors" -> "Sensors"
        "display" -> "Display"
        "dashboard" -> "Dashboard"
        "voice" -> "Voice satellite"
        "zigbee" -> "Zigbee"
        "firmware" -> "Firmware / OTA"
        "fleet" -> "Fleet"
        "remote_desktop" -> "Remote desktop"
        else -> name
    }

    fun description(name: String): String = when (name) {
        "mqtt" ->
            "Talks to Home Assistant over MQTT: publishes panel events (touch, proximity, light), " +
            "accepts commands (wake, sleep, reboot…) and lets you change settings remotely. " +
            "Registers this panel in Home Assistant with MQTT discovery."
        "sensors" ->
            "Reads the panel's proximity and light sensors. Proximity wakes the display when " +
            "someone walks up (it learns the resting level, so it works on any wall and any firmware). " +
            "Light drives the brightness presets."
        "display" ->
            "Wakes and sleeps the screen, sets brightness (manually and from the light sensor) and " +
            "keeps screen-on schedules (weekday/weekend)."
        "dashboard" ->
            "Shows a Home Assistant dashboard on the wall. Point it at any Lovelace dashboard URL. " +
            "You can change the URL remotely, from Home Assistant."
        "voice" ->
            "Turns the panel into a Home Assistant Assist satellite: the microphone streams to HA, " +
            "the reply plays through the speaker. Needs HA URL + token on the Setup page."
        "zigbee" ->
            "Drives the Zigbee gateway that already runs on the panel: coordinator/router role, " +
            "permit-join window, device list, factory reset."
        "firmware" ->
            "The Nexus OTA channel: checks a manifest, verifies size and sha256, backs up the " +
            "current system and installs the new one. Independent of Sonoff's signing key."
        "fleet" ->
            "Announces this panel on the LAN so the host tooling can find and manage every panel " +
            "in the house from one place."
        "remote_desktop" ->
            "A browser window that shows the panel's live screen and lets you click to tap, " +
            "press HOME/BACK/POWER and type text. Handy for setup and support. " +
            "Off costs nothing."
        else -> ""
    }

    /** Apply every module switch and (re)start the foreground service. */
    fun restartModules(context: Context?) {
        if (context == null) return
        ModuleManager.stopAll()
        NexusService.start(context)
    }

    /** Short one-line summary shown on the Setup page footer. */
    fun setupSummary(): String {
        val mqtt = if (Settings.mqttHost.isBlank()) "not configured" else
            "${Settings.mqttHost}:${Settings.mqttPort}"
        val ha = if (Settings.haUrl.isBlank()) "not configured" else Settings.haUrl
        return "MQTT $mqtt   ·   HA $ha"
    }
}
