package io.nexus.panel.modules.firmware

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * Nexus OTA channel + firmware auto-discovery.
 *
 * The panel's own recovery enforces Sonoff's signing key, so a stock-OTA path
 * is impossible without it. Nexus ships its own: the module checks a manifest,
 * downloads the image, verifies size + sha256, writes it and reboots.
 *
 * On start, the module auto-discovers the running firmware:
 *   - reads /data/nexus/version (written by nexus-updater.sh)
 *   - if absent, probes for Nexus markers (KISS launcher, absence of Sonoff
 *     apps, frp backdoor removal)
 * The discovered version is published as the "firmware" sensor so HA can
 * show it and so the firmware tab can offer updates.
 */
class FirmwareModule : Module {

    override val name = "firmware"

    private val updateLock = Any()

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String = "version=${currentFirmwareVersion()}"

    override fun start(context: Context) {
        val m = mqtt ?: return
        // Auto-discover firmware on start
        val ver = currentFirmwareVersion()
        Settings.set("nexus_version", ver)
        m.state("firmware", JSONObject().put("current", ver))
        m.onCommand["firmware:check"] = { _ -> checkForUpdate() }
        m.onCommand["firmware:install"] = { _ -> installPending() }
    }

    override fun stop() {}

    // ---- firmware auto-discovery ---------------------------------------

    /** Detect the running firmware version. */
    fun currentFirmwareVersion(): String {
        // 1. Written by nexus-updater.sh after a successful OTA
        val versionFile = File("/data/nexus/version")
        if (versionFile.exists()) {
            val v = versionFile.readText().trim()
            if (v.isNotEmpty()) return v
        }
        // 2. Probe for Nexus firmware markers
        if (isNexusFirmware()) return "nexus (unknown version)"
        // 3. Stock Sonoff firmware
        return "stock (sonoff)"
    }

    /** Heuristic: is this panel running Nexus firmware? */
    private fun isNexusFirmware(): Boolean {
        // KISS launcher installed → Nexus
        if (File("/system/priv-app/Kiss").exists() || File("/data/app/fr.neamar.kiss").exists()) return true
        // Sonoff bloat removed → Nexus
        if (!File("/system/app/kz_app").exists() && !File("/system/app/factoryApp").exists()) return true
        // frp backdoor removed → Nexus
        return try {
            val rootsudaemon = File("/system/bin/rootsudaemon.sh")
            !rootsudaemon.exists() || !rootsudaemon.readText().contains("frp")
        } catch (_: Exception) { false }
    }

    // ---- manifest -------------------------------------------------------

    fun checkForUpdate() {
        val manifestUrl = Settings.get("nexus_manifest_url",
            "https://github.com/ALeXXBody/NSPanelNexus/releases/download/firmware-1.0.0/manifest.json")
        if (manifestUrl.isBlank()) return
        Thread {
            try {
                val manifest = JSONObject(URL(manifestUrl).readText())
                val version = manifest.optString("version")
                val url = manifest.optString("url")
                val size = manifest.optLong("size")
                val sha = manifest.optString("sha256")
                mqtt?.state("firmware", JSONObject()
                    .put("current", currentFirmwareVersion())
                    .put("available", version))
                if (version != currentFirmwareVersion() && version != "nexus (unknown version)") {
                    downloadAndWrite(url, size, sha, version)
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun installPending() { checkForUpdate() }

    private fun downloadAndWrite(url: String, size: Long, sha: String, version: String) {
        synchronized(updateLock) {
            downloadAndWriteLocked(url, size, sha, version)
        }
    }

    private fun downloadAndWriteLocked(url: String, size: Long, sha: String, version: String) {
        val tmp = File("/data/nexus/system.img.new")
        val dir = tmp.parentFile ?: return
        dir.mkdirs()
        URL(url).openStream().use { input ->
            tmp.outputStream().use { out ->
                input.copyTo(out, 1024 * 1024)
            }
        }
        if (tmp.length() != size) { tmp.delete(); return }
        if (sha256(tmp) != sha) { tmp.delete(); return }

        // backup current system partition for rollback
        val backup = File("/data/nexus/system.img.bak")
        run("dd", "if=/dev/block/mmcblk1p11", "of=${backup.absolutePath}", "bs=1048576")
        run("sync")

        run("dd", "if=${tmp.absolutePath}", "of=/dev/block/mmcblk1p11", "bs=1048576")
        run("sync")
        tmp.delete()
        Settings.set("nexus_version", version)
        run("reboot")
    }

    private fun run(vararg cmd: String) {
        ProcessBuilder(*cmd).start().waitFor()
    }

    private fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}
