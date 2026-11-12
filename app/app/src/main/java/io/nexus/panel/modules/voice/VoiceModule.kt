package io.nexus.panel.modules.voice

import android.content.Context
import android.media.*
import okhttp3.*
import okio.ByteString
import io.nexus.panel.core.Module
import io.nexus.panel.core.Settings
import org.json.JSONObject

/**
 * Home Assistant **Assist satellite** over the panel's own microphone and
 * speaker.
 *
 * Phase 1: push-to-talk — an intent/trigger starts a pipeline run, audio is
 * streamed to HA, the TTS reply plays on the panel speaker.
 * Phase 2: on-device wake word (openWakeWord / microWakeWord) starts the
 * pipeline automatically.
 *
 * Protocol: Home Assistant WebSocket API. Authenticate with the long-lived
 * token, then run an Assist pipeline with start_stage=STT, end_stage=TTS and
 * stream 16 kHz mono PCM16 as binary frames.
 */
class VoiceModule : Module {

    override val name = "voice"

    private var ws: WebSocket? = null
    private var client: OkHttpClient? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var streaming = false
    private var nextId = java.util.concurrent.atomic.AtomicInteger(1)
    @Volatile private var reconnectAt = 0L

    private var appContext: Context? = null

    override fun status(): String {
        val granted = appContext?.let {
            it.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        } == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return "needs RECORD_AUDIO (adb: pm grant io.nexus.panel android.permission.RECORD_AUDIO)"
        return if (ws != null) "ready" else "ready (idle)"
    }

    override fun start(context: Context) {
        appContext = context.applicationContext
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // the module cannot ask for a runtime permission without an activity;
            // surface it in status() so the user knows the one command to run
            return
        }
        val url = Settings.haUrl
        val token = Settings.haToken
        if (url.isBlank() || token.isBlank()) return
        client = OkHttpClient.Builder().build()
        val httpUrl = url.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/') + "/api/websocket"
        val req = Request.Builder().url(httpUrl).build()
        client?.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // auth required -> send token
                webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                when (msg.optString("type")) {
                    "auth_required" -> { /* waiting for token */ }
                    "auth_ok" -> {
                        ws = webSocket
                        publishEntities()
                    }
                    "event" -> handleEvent(msg.optJSONObject("event") ?: return)
                    "result" -> { /* pipeline result */ }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ws = null
                // reconnect with backoff while the module is still wanted
                reconnectAt = System.currentTimeMillis() + 5000
                Thread {
                    try { Thread.sleep(5000) } catch (_: InterruptedException) { return@Thread }
                    if (System.currentTimeMillis() >= reconnectAt - 1000 && appContext != null) {
                        try { start(appContext!!) } catch (_: Exception) {}
                    }
                }.apply { isDaemon = true }.start()
            }
        })
    }

    override fun stop() {
        stopStreaming()
        try { ws?.close(1000, "module stopped") } catch (_: Exception) {}
        ws = null
        reconnectAt = 0L
        try { client?.dispatcher?.executorService?.shutdownNow() } catch (_: Exception) {}
        client = null
    }

    // ---- HA entities ----------------------------------------------------

    private fun publishEntities() {
        // Assist satellite entity so HA shows it under the panel device
        // (implementation in phase 2 when the wake word is in)
    }

    // ---- pipeline -------------------------------------------------------

    /** Start an Assist pipeline run: STT -> TTS. */
    fun startListening() {
        val w = ws ?: return
        val id = nextId.getAndIncrement()
        w.send(JSONObject()
            .put("id", id)
            .put("type", "assist_pipeline/run")
            .put("start_stage", "stt")
            .put("end_stage", "tts")
            .toString())
        startStreaming()
    }

    /** 16 kHz mono PCM16 — the format HA's STT expects. */
    private fun startStreaming() {
        if (streaming) return
        streaming = true
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf
        )
        audioRecord?.startRecording()
        val buf = ByteArray(2048)
        Thread {
            while (streaming) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: break
                if (n > 0) {
                    ws?.send(ByteString.of(*buf.copyOf(n)))
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun stopStreaming() {
        streaming = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun handleEvent(event: JSONObject) {
        // HA assist_pipeline events: stt-end / tts-end (audio at data.url) / run-end / error
        val data = event.optJSONObject("data") ?: return
        val type = data.optString("type")
        when (type) {
            "tts-end" -> {
                val url = data.optString("url")
                if (url.isNotBlank()) playUrl(url)
            }
            "stt-end" -> stopStreaming()
            "run-end" -> stopStreaming()
            "error" -> stopStreaming()
        }
    }

    private fun playUrl(url: String) {
        Thread {
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer()
                mp.setDataSource(url)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { _, _, _ -> mp?.release(); true }
                mp.prepareAsync()
            } catch (_: Exception) {
                mp?.release()
            }
        }.start()
    }
}
