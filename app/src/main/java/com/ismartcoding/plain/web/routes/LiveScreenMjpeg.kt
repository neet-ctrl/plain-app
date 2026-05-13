package com.ismartcoding.plain.web.routes

import com.ismartcoding.plain.services.LiveScreenCapturer
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages one-time tokens for the live screen stream.
 * Each token is valid for 2 hours and is cancelled when the stream is stopped.
 */
object LiveStreamTokenManager {
    data class Entry(val expiresAt: Long)
    private val tokens = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 2L * 60 * 60 * 1000

    fun issue(): String {
        purgeExpired()
        val t = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        tokens[t] = Entry(System.currentTimeMillis() + TTL_MS)
        return t
    }

    fun isValid(token: String): Boolean {
        val e = tokens[token] ?: return false
        if (System.currentTimeMillis() >= e.expiresAt) { tokens.remove(token); return false }
        return true
    }

    fun revokeAll() { tokens.clear() }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }
    }
}

fun Route.addLiveScreenStream() {

    // ── HTML viewer page ─────────────────────────────────────────────────────
    // GET /screen/view?t=<token>  → full-page browser view with FPS controls
    get("/screen/view") {
        val token = call.request.queryParameters["t"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@get
        }
        if (!LiveStreamTokenManager.isValid(token)) {
            call.respond(
                HttpStatusCode.Gone,
                "Stream link expired (2 h TTL). Run /livescreen in the Telegram bot to get a fresh link.",
            )
            return@get
        }
        val html = buildString {
            append("""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live Screen · PlainApp</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0e0e0e;display:flex;flex-direction:column;align-items:center;
     min-height:100vh;font-family:system-ui,sans-serif;color:#ccc}
header{padding:14px 0 6px;font-size:13px;letter-spacing:.8px;color:#666;text-transform:uppercase}
#wrap{width:100%;max-width:420px;padding:0 10px 24px}
img{width:100%;height:auto;border-radius:10px;border:1px solid #2a2a2a;
    background:#111;display:block;min-height:120px}
#bar{display:flex;gap:6px;margin:10px 0 4px;flex-wrap:wrap;justify-content:center}
button{background:#1e1e1e;color:#aaa;border:1px solid #3a3a3a;border-radius:7px;
       padding:7px 16px;cursor:pointer;font-size:12px;transition:.15s}
button:hover{background:#2a2a2a}
button.on{background:#163816;color:#7ec87e;border-color:#3a7a3a}
#st{font-size:11px;color:#555;text-align:center;margin-top:6px}
</style>
</head>
<body>
<header>📱 live screen — plainapp</header>
<div id="wrap">
  <img id="s" src="/screen/live?t=$token" alt="loading…">
  <div id="bar">
    <button onclick="setFps(2,this)">2 fps</button>
    <button onclick="setFps(5,this)" class="on">5 fps</button>
    <button onclick="setFps(10,this)">10 fps</button>
    <button onclick="setFps(15,this)">15 fps</button>
  </div>
  <div id="st">Connecting…</div>
</div>
<script>
var tok="${token}",fps=5,img=document.getElementById('s'),st=document.getElementById('st');
function setFps(f,btn){
  fps=f;
  document.querySelectorAll('#bar button').forEach(b=>b.classList.remove('on'));
  btn.classList.add('on');
  img.src='/screen/live?t='+tok+'&fps='+fps+'&_='+Date.now();
  st.textContent='Switched to '+f+' fps…';
}
img.onload=function(){st.textContent='Streaming · '+fps+' fps'};
img.onerror=function(){st.textContent='⚠ Stream error or link expired'};
</script>
</body>
</html>""")
        }
        call.response.header("Content-Type", "text/html; charset=utf-8")
        call.response.header("Cache-Control", "no-store")
        call.respond(html)
    }

    // ── Raw MJPEG stream ──────────────────────────────────────────────────────
    // GET /screen/live?t=<token>[&fps=5]
    // Standard multipart/x-mixed-replace — plays natively in Chrome/Firefox/Safari.
    get("/screen/live") {
        val token = call.request.queryParameters["t"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@get
        }
        if (!LiveStreamTokenManager.isValid(token)) {
            call.respond(
                HttpStatusCode.Gone,
                "Stream link expired. Run /livescreen in the Telegram bot to get a new link.",
            )
            return@get
        }

        val requestedFps = call.request.queryParameters["fps"]
            ?.toIntOrNull()?.coerceIn(1, 15) ?: 5
        LiveScreenCapturer.fps = requestedFps

        call.response.header("Content-Type", "multipart/x-mixed-replace; boundary=--mjpegframe")
        call.response.header("Cache-Control", "no-cache, no-store, must-revalidate")
        call.response.header("Pragma", "no-cache")
        call.response.header("Connection", "close")

        LiveScreenCapturer.connect()
        try {
            call.respondBytesWriter(contentLength = null) {
                LiveScreenCapturer.frames.collect { jpeg ->
                    try {
                        val header =
                            "--mjpegframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
                        writeFully(header.toByteArray(Charsets.US_ASCII))
                        writeFully(jpeg)
                        writeFully("\r\n".toByteArray(Charsets.US_ASCII))
                        flush()
                    } catch (_: Throwable) {
                        // Write failed → client disconnected. Cancel the job so
                        // collect() stops and the finally block below runs.
                        currentCoroutineContext()[Job]?.cancel()
                    }
                }
            }
        } catch (_: Throwable) {
            // CancellationException or channel close — normal path on disconnect.
        } finally {
            LiveScreenCapturer.disconnect()
        }
    }
}
