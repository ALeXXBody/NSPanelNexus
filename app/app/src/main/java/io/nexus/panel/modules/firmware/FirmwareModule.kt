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
 * Nexus OTA channel.
 *
 * The panel's own recovery enforces Sonoff's signing key, so a stock-OTA path
 * is impossible without it. Nexus ships its own: the module checks a manifest,
 * downloads the image, verifies size + sha256, backs up the current partition,
 * writes the new one and reboots.
 */
class FirmwareModule : Module {

    override val name = "firmware"

    private val updateLock = Any()

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String = "version=${Settings.get("nexus_version", "unknown")}"

    override fun start(context: Context) {
        val m = mqtt ?: return
        m.onCommand["firmware:check"] = { _ -> checkForUpdate() }
        m.onCommand["firmware:install"] = { _ -> installPending() }
    }

    override fun stop() {}

    // ---- manifest -------------------------------------------------------

    fun checkForUpdate() {
        val manifestUrl = Settings.get("nexus_manifest_url", "")
        if (manifestUrl.isBlank()) return
        Thread {
            try {
                val manifest = JSONObject(URL(manifestUrl).readText())
                val version = manifest.optString("version")
                val url = manifest.optString("url")
                val size = manifest.optLong("size")
                val sha = manifest.optString("sha256")
                mqtt?.state("firmware", JSONObject()
                    .put("current", Settings.get("nexus_version", "unknown"))
                    .put("available", version))
                if (version != Settings.get("nexus_version", "")) {
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
