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
    private val thread = ThreadLocal<Boolean>()
    private var nextId = 1

    override fun status(): String = if (ws != null) "ready" else "stopped"

    override fun start(context: Context) {
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
                // reconnect handled by a scheduled re-start from ModuleManager
            }
        })
    }

    override fun stop() {
        stopStreaming()
        ws?.close(1000, "module stopped")
        ws = null
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
        val id = nextId++
        w.send(JSONObject()
            .put("id", id)
            .put("type", "assist_pipeline/run")
            .put("start_stage", JSONObject().put("type", "stt").put("input", JSONObject()))
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
        // pipeline events: stt -> (intent -> tts audio) -> done
        val data = event.optJSONObject("data") ?: return
        val type = data.optString("type")
        when (type) {
            "tts-start" -> {
                val url = data.optString("tts_input")   // HA gives a media URL for TTS
                if (url.isNotBlank()) playUrl(url)
            }
            "run-end", "stt-end" -> if (type == "stt-end") stopStreaming()
        }
    }

    private fun playUrl(url: String) {
        Thread {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(url)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { it.release() }
                mp.prepareAsync()
            } catch (_: Exception) {}
        }.start()
    }
}
