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
    @Volatile private var appCtx: Context? = null

    /** Merged firmware state: several sources write this key, so keep one copy. */
    private val fwState = JSONObject()

    private fun publishState(patch: JSONObject) {
        synchronized(fwState) {
            try {
                val keys = patch.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    fwState.put(k, patch.get(k))
                }
                mqtt?.state("firmware", JSONObject(fwState.toString()))
            } catch (_: Exception) {}
        }
    }

    private fun publishState(build: JSONObject.() -> Unit) {
        val patch = JSONObject().apply(build)
        publishState(patch)
    }

    private fun toast(msg: String) {
        try {
            val c = appCtx ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try { android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
            }
        } catch (_: Exception) { }
    }

    override fun status(): String = "version=${currentFirmwareVersion()}"

    override fun start(context: Context) {
        android.util.Log.d("FirmwareModule", "start() called, enabled=${io.nexus.panel.core.Settings.moduleEnabled("firmware")}")
        appCtx = context.applicationContext
        val m = mqtt ?: run { android.util.Log.e("FirmwareModule", "mqtt is null"); return }
        // Auto-discover firmware on start
        val ver = currentFirmwareVersion()
        Settings.set("nexus_version", ver)
        val appVer = try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }
        // Publish after a delay so MQTT has time to connect
        Thread {
            Thread.sleep(5000)
            android.util.Log.d("FirmwareModule", "publishing firmware state: $ver (app $appVer), client connected=${m.status()}")
            try {
                publishState(JSONObject().put("current", ver).put("app", appVer))
                android.util.Log.d("FirmwareModule", "state published")
            } catch (e: Exception) { android.util.Log.e("FirmwareModule", "state publish failed", e) }
            publishCapabilities()
        }.apply { isDaemon = true }.start()
        m.onCommand["firmware:check"] = { _ -> checkForUpdate() }
        m.onCommand["firmware:install"] = { _ -> installPending() }
        m.onCommand["app:update"] = { _ -> installLatestApp() }
        m.onCommand["app:check"] = { _ -> checkAppUpdateAndPublish() }
        m.onCommand["update:check"] = { _ -> checkEverything() }
    }

    /** Ask both channels for their latest versions and publish the answers. */
    fun checkEverything() {
        checkAppUpdateAndPublish()
        checkForUpdate()
    }

    fun checkAppUpdateAndPublish() {
        checkForAppUpdate { cur, latest, _ ->
            publishState(JSONObject().put("app", cur).put("app_available", latest))
        }
    }

    override fun stop() {}

    // ---- firmware auto-discovery ---------------------------------------

    /** Detect the running firmware version. Never throws. */
    fun currentFirmwareVersion(): String {
        // 1. Written by nexus-updater.sh after a successful OTA.
        //    Updater chmods it readable (644); try direct read first, su fallback.
        //    Never call from the UI thread (Diagnostics uses the cached setting).
        try {
            val versionFile = File("/data/nexus/version")
            if (versionFile.exists() && versionFile.canRead()) {
                val v = versionFile.readText().trim()
                if (v.isNotEmpty()) return v
            }
        } catch (_: Exception) {}
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/nexus/version"))
            val finished = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (finished) {
                val v = try { proc.inputStream.bufferedReader().readText().trim() } catch (_: Exception) { "" }
                try { proc.destroy() } catch (_: Exception) {}
                if (v.isNotEmpty()) return v
            } else {
                try { proc.destroy() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        // 2. Probe for Nexus firmware markers
        if (isNexusFirmware()) return "nexus (unknown version)"
        // 3. Stock Sonoff firmware
        return "stock (sonoff)"
    }

    /** Heuristic: is this panel running Nexus firmware? Requires a positive marker. */
    private fun isNexusFirmware(): Boolean {
        // KISS launcher installed → Nexus
        if (File("/system/priv-app/Kiss").exists()) return true
        // Bloat gone AND frp backdoor gone → Nexus (both must hold)
        val bloatGone = !File("/system/app/kz_app").exists() && !File("/system/app/factoryApp").exists()
        val frpGone = try {
            val f = File("/system/bin/rootsudaemon.sh")
            f.exists() && !f.readText().contains("frp")
        } catch (_: Exception) { false }
        return bloatGone && frpGone
    }

    // ---- manifest -------------------------------------------------------

    fun checkForUpdate() {
        val manifestUrl = Settings.get("nexus_manifest_url",
            "https://raw.githubusercontent.com/ALeXXBody/NSPanelNexus/main/firmware/manifest.json")
        if (manifestUrl.isBlank()) return
        Thread {
            try {
                val manifest = JSONObject(URL(manifestUrl).readText())
                val version = manifest.optString("version")
                val url = manifest.optString("url")
                val size = manifest.optLong("size")
                val sha = manifest.optString("sha256")
                val appVer = try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }
                publishState(JSONObject()
                    .put("current", currentFirmwareVersion())
                    .put("app", appVer)
                    .put("available", version))
                val cur = currentFirmwareVersion()
                if (version.isNotBlank() && version != cur && !cur.startsWith("stock") && cur != "nexus (unknown version)") {
                    toast("Downloading system update $version…")
                    downloadAndWrite(url, size, sha, version)
                } else {
                    toast("System firmware is up to date ($cur)")
                }
            } catch (e: Exception) {
                android.util.Log.e("FirmwareModule", "checkForUpdate failed", e)
                toast("Firmware check failed: ${e.message ?: "unknown error"}")
                try {
                    val appVer2 = try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }
                    publishState(JSONObject()
                        .put("current", currentFirmwareVersion())
                        .put("app", appVer2)
                        .put("error", e.message ?: "unknown"))
                } catch (_: Exception) {}
            }
        }.start()
    }

    fun installPending() { checkForUpdate() }

    // ---- Nexus app (APK) update check ---------------------------------

    /**
     * Capability flags so HA knows what this hardware can actually do
     * (Gen1/Gen2, sensors present, OTA channels). Published once at start.
     */
    private fun publishCapabilities() {
        try {
            val c = appCtx ?: return
            val sm = c.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val dm = c.resources.displayMetrics
            val caps = JSONObject()
                .put("model", android.os.Build.MODEL ?: "unknown")
                .put("device", android.os.Build.DEVICE ?: "unknown")
                .put("android", android.os.Build.VERSION.RELEASE ?: "?")
                .put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("width", dm.widthPixels)
                .put("height", dm.heightPixels)
                .put("density", dm.density)
                .put("proximity", sm.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) != null)
                .put("light", sm.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) != null)
                .put("zigbee", File("/vendor/bin/siliconlabs_host").exists() || isNexusFirmware())
                .put("remote_desktop", true)
                .put("screensaver", true)
                .put("camera_popup", true)
                .put("ota_app", true)
                .put("ota_system", !currentFirmwareVersion().startsWith("stock"))
            mqtt?.state("capabilities", caps)
        } catch (_: Exception) {}
    }

    /** Check GitHub for a newer app and install it (system installer confirms). */
    fun installLatestApp() {
        checkForAppUpdate { cur, latest, url ->
            when {
                latest.isBlank() -> toast("App update check failed")
                latest == cur -> toast("Nexus app is up to date ($cur)")
                url == null -> toast("Update $latest exists but the release has no APK attached")
                else -> {
                    toast("Downloading Nexus app $latest…")
                    installApk(url)
                }
            }
        }
    }

    /** Download an APK and hand it to the system installer. */
    fun installApk(url: String) {
        val c = appCtx ?: return
        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 120000
                val tmp = File(c.cacheDir, "nexus-update.apk")
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                conn.disconnect()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val uri = try {
                        androidx.core.content.FileProvider.getUriForFile(
                            c, c.packageName + ".fileprovider", tmp)
                    } catch (_: Exception) {
                        android.net.Uri.fromFile(tmp)
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "application/vnd.android.package-archive")
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    try { c.startActivity(intent) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                toast("App download failed: ${e.message ?: "unknown"}")
            }
        }.apply { isDaemon = true }.start()
    }

    /** Check GitHub releases for a newer Nexus app version. */
    fun checkForAppUpdate(onResult: (current: String, latest: String, downloadUrl: String?) -> Unit) {
        Thread {
            try {
                val conn = java.net.URL("https://api.github.com/repos/ALeXXBody/NSPanelNexus/releases/latest").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trimStart('v')
                val current = try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                onResult(current, tag, apkUrl)
            } catch (e: Exception) {
                android.util.Log.e("FirmwareModule", "app update check failed", e)
                onResult(try { io.nexus.panel.BuildConfig.VERSION_NAME } catch (_: Exception) { "unknown" }, "", null)
            }
        }.start()
    }

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
        if (tmp.length() != size) {
            android.util.Log.e("FirmwareModule", "size mismatch: ${tmp.length()} != $size")
            tmp.delete(); return
        }
        if (sha256(tmp) != sha) {
            android.util.Log.e("FirmwareModule", "sha256 mismatch")
            tmp.delete(); return
        }

        // backup current system partition for rollback (best effort)
        val backup = File("/data/nexus/system.img.bak")
        run("dd", "if=/dev/block/mmcblk1p11", "of=${backup.absolutePath}", "bs=1048576")
        run("sync")

        val rc = run("dd", "if=${tmp.absolutePath}", "of=/dev/block/mmcblk1p11", "bs=1048576")
        run("sync")
        if (rc != 0) { return }
        tmp.delete()
        Settings.set("nexus_version", version)
        run("reboot")
    }

    private fun run(vararg cmd: String): Int {
        return try {
            val full = arrayOf("su", "-c", cmd.joinToString(" "))
            val proc = ProcessBuilder(*full).start()
            val ok = proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!ok) { try { proc.destroy() } catch (_: Exception) {} ; -1 } else proc.exitValue()
        } catch (_: Exception) { -1 }
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
