package io.nexus.panel.modules.desktop

import android.content.Context
import android.graphics.BitmapFactory
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * **Remote desktop** — view and control the panel's screen from a browser.
 *
 * Enabled/disabled by its own settings switch (`module.remote_desktop.enabled`).
 *
 * - `GET /`               — the remote-desktop web UI (live screen + click to tap)
 * - `GET /stream`         — MJPEG live view of the screen
 * - `GET /screenshot`     — single PNG of the current screen
 * - `POST /input/tap`     — body `x=…&y=…`  → taps at that point
 * - `POST /input/swipe`   — body `x1&y1&x2&y2&ms`
 * - `POST /input/key`     — body `code=…`  (e.g. 3 = HOME, 4 = BACK)
 * - `POST /input/text`    — body `text=…`
 *
 * Screen capture uses `screencap` through the panel's root (`su`), input uses
 * `input` — both are available because the panel is rooted. No ADB needed.
 */
class RemoteDesktopModule : Module {

    override val name = "remote_desktop"

    private var server: ServerSocket? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule
    private val port: Int get() = Settings.getInt("remote_desktop_port", 8080)

    override fun status(): String =
        if (running) "http://<panel>:$port/" else "disabled"

    override fun start(context: Context) {
        running = true
        thread = Thread {
            try {
                val s = ServerSocket(port, 20, InetAddress.getByName("0.0.0.0"))
                server = s
                while (running) {
                    val socket = try { s.accept() } catch (_: Exception) { break }
                    handle(socket)
                }
            } catch (_: Exception) {
                running = false
            }
        }.apply { isDaemon = true; start() }
        mqtt?.state("remote_desktop", JSONObject().put("url", "http://<panel>:$port/"))
    }

    override fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        mqtt?.state("remote_desktop", JSONObject().put("url", ""))
    }

    // ---- tiny HTTP server ----------------------------------------------

    private fun handle(socket: Socket) {
        Thread {
            try {
                socket.tcpNoDelay = true
                val input = socket.getInputStream()
                val requestLine = readLine(input) ?: return@Thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@Thread
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                var body = ""
                var len = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.startsWith("Content-Length:")) len = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    if (line.isEmpty()) break
                }
                if (len > 0) {
                    val buf = ByteArray(len)
                    var off = 0
                    while (off < len) { val n = input.read(buf, off, len - off); if (n < 0) break; off += n }
                    body = String(buf, 0, off)
                }
                val out = socket.getOutputStream()
                when {
                    method == "GET" && path == "/" -> serveIndex(out)
                    method == "GET" && path == "/stream" -> serveStream(out)
                    method == "GET" && path == "/screenshot" -> servePng(out)
                    method == "POST" && path.startsWith("/input/") -> {
                        inject(path.removePrefix("/input/"), body)
                        write(out, "200 OK", "text/plain", "ok".toByteArray())
                    }
                    else -> write(out, "404 Not Found", "text/plain", "not found".toByteArray())
                }
            } catch (_: Exception) { /* drop the connection */ }
            finally { try { socket.close() } catch (_: Exception) {} }
        }.start()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return null
            if (c == '\n'.code) { if (sb.lastOrNull() == '\r') sb.setLength(sb.length - 1); return sb.toString() }
            sb.append(c.toChar())
        }
    }

    private fun write(out: OutputStream, status: String, type: String, body: ByteArray, extra: String = "") {
        val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n$extra\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun params(body: String): Map<String, String> =
        body.split("&").associate {
            val kv = it.split("=", limit = 2)
            kv[0] to (kv.getOrNull(1)?.let { v -> URLDecoder.decode(v, "UTF-8") } ?: "")
        }

    // ---- screen capture -------------------------------------------------

    /** Capture the screen as JPEG via `su -c screencap`. */
    fun captureJpeg(quality: Int = 60): ByteArray? {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
            val png = proc.inputStream.readBytes()
            proc.waitFor()
            if (png.isEmpty()) return null
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
            val bos = ByteArrayOutputStream()
            android.graphics.Bitmap.CompressFormat.JPEG.let {
                android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width, bmp.height, true)
                    .compress(it, quality, bos)
            }
            bos.toByteArray()
        } catch (_: Exception) { null }
    }

    private fun serveStream(out: OutputStream) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=FRAME\r\n\r\n".toByteArray())
        out.flush()
        while (running) {
            val jpg = captureJpeg(50) ?: break
            out.write("--FRAME\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpg.size}\r\n\r\n".toByteArray())
            out.write(jpg)
            out.write("\r\n".toByteArray())
            out.flush()
            Thread.sleep(250)   // ~4 fps — enough to see and tap
        }
    }

    private fun servePng(out: OutputStream) {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "screencap -p"))
        val png = proc.inputStream.readBytes()
        proc.waitFor()
        write(out, "200 OK", "image/png", png)
    }

    private fun serveIndex(out: OutputStream) {
        val html = """
<!doctype html><html><head><meta charset="utf-8"><title>NSPanel Nexus remote desktop</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{margin:0;background:#111;color:#eee;font-family:monospace;text-align:center}
#screen{width:100%;max-width:480px;cursor:crosshair;touch-action:none;user-select:none}
#log{font-size:12px;color:#888;height:2em}
.btn{background:#FF8C28;border:0;color:#000;padding:8px 14px;margin:2px;font-family:monospace;font-size:13px}
input.txt{width:55%;padding:6px}
</style></head><body>
<h3>NSPanel Nexus — remote desktop</h3>
<img id="screen" src="/stream" alt="panel screen">
<p id="log">click to tap · drag to scroll · wheel to scroll</p>
<p>
<button class="btn" onclick="key(3)">HOME</button>
<button class="btn" onclick="key(4)">BACK</button>
<button class="btn" onclick="key(26)">POWER</button>
<button class="btn" onclick="scrollBy(0,-1)">&#8593; up</button>
<button class="btn" onclick="scrollBy(0,1)">&#8595; down</button>
</p>
<form onsubmit="sendText(this.text.value);return false">
<input class="txt" name="text" placeholder="text…">
<button class="btn">send</button></form>
<script>
const img=document.getElementById('screen'), log=document.getElementById('log');
const W=480, H=480;

/* ---- click to tap, drag to scroll ---- */
let down=null;
img.addEventListener('mousedown',e=>{ down={x:e.clientX,y:e.clientY,t:Date.now()}; });
img.addEventListener('mouseup',e=>{
  if(!down) return;
  const r=img.getBoundingClientRect();
  const sx=down.x, sy=down.y, ex=e.clientX, ey=e.clientY;
  const dx=ex-sx, dy=ey-sy;
  const px=(v)=>Math.round(v/W*480);
  const py=(v)=>Math.round(v/H*480);
  if(Math.hypot(dx,dy)<12){ tap(px(sx),py(sy)); }
  else { swipe(px(sx),py(sy),px(ex),py(ey), 400); }
  down=null;
});
/* touch: same idea on touch screens */
img.addEventListener('touchstart',e=>{ const t=e.touches[0]; down={x:t.clientX,y:t.clientY}; }, {passive:false});
img.addEventListener('touchend',e=>{
  if(!down) return;
  const t=e.changedTouches[0];
  const r=img.getBoundingClientRect();
  const dx=t.clientX-down.x, dy=t.clientY-down.y;
  const px=(v)=>Math.round(v/W*480), py=(v)=>Math.round(v/H*480);
  if(Math.hypot(dx,dy)<12){ tap(px(down.x),py(down.y)); }
  else { swipe(px(down.x),py(down.y),px(t.clientX),py(t.clientY),400); }
  down=null;
}, {passive:false});

/* ---- wheel / trackpad scroll ---- */
img.addEventListener('wheel',e=>{
  e.preventDefault();
  scrollBy(0, Math.sign(e.deltaY));
}, {passive:false});

function scrollBy(_,dir){
  // dir>0 = scroll down  (finger moves UP on Android)
  if(dir>0) swipe(240,340,240,140, 260);
  else      swipe(240,140,240,340, 260);
}

/* ---- send ---- */
function tap(x,y){ post('/input/tap','x='+x+'&y='+y); log.textContent='tap '+x+','+y; }
function swipe(x1,y1,x2,y2,ms){ post('/input/swipe','x1='+x1+'&y1='+y1+'&x2='+x2+'&y2='+y2+'&ms='+ms); log.textContent='swipe '+x1+','+y1+' -> '+x2+','+y2; }
function key(c){ post('/input/key','code='+c); log.textContent='key '+c; }
function sendText(t){ post('/input/text','text='+encodeURIComponent(t)); }
function post(url,body){ fetch(url,{method:'POST',body:body}); }
</script></body></html>
""".trimIndent()
        write(out, "200 OK", "text/html", html.toByteArray())
    }

    // ---- input injection ------------------------------------------------

    private fun inject(kind: String, body: String) {
        val p = params(body)
        val cmd = when (kind) {
            "tap" -> "input tap ${p["x"]} ${p["y"]}"
            "swipe" -> "input swipe ${p["x1"]} ${p["y1"]} ${p["x2"]} ${p["y2"]} ${p["ms"] ?: "300"}"
            "key" -> "input keyevent ${p["code"]}"
            "text" -> "input text \"${p["text"]?.replace("\"", "")}\""
            else -> return
        }
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) } catch (_: Exception) {}
    }
}
