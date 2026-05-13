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
 * Each token is valid for 2 hours and is revoked when the stream is stopped.
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
    // GET /screen/view?t=<token>
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
        val html = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Live Screen · PlainApp</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0e0e0e;display:flex;flex-direction:column;align-items:center;
     min-height:100vh;font-family:system-ui,sans-serif;color:#ccc}
header{padding:14px 0 4px;font-size:12px;letter-spacing:.8px;color:#555;text-transform:uppercase}
#wrap{width:100%;max-width:440px;padding:0 10px 28px}
img{width:100%;height:auto;border-radius:10px;border:1px solid #222;
    background:#111;display:block;min-height:140px}
.row{display:flex;gap:5px;margin:9px 0 0;flex-wrap:wrap;justify-content:center}
.label{width:100%;text-align:center;font-size:10px;color:#444;
       letter-spacing:.6px;text-transform:uppercase;margin-top:10px}
button{background:#1c1c1c;color:#999;border:1px solid #333;border-radius:7px;
       padding:6px 14px;cursor:pointer;font-size:12px;transition:.12s}
button:hover{background:#252525}
button.on{background:#0f2a0f;color:#7ec87e;border-color:#305030}
#st{font-size:11px;color:#555;text-align:center;margin-top:8px;min-height:16px}
#ping{display:inline-block;margin-left:6px;color:#3a7a3a}
</style>
</head>
<body>
<header>📱 live screen &mdash; plainapp</header>
<div id="wrap">
  <img id="s" alt="connecting…">
  <div class="label">Speed</div>
  <div class="row" id="fps-bar">
    <button onclick="set('fps',2,this)">2 fps</button>
    <button onclick="set('fps',5,this)" class="on">5 fps</button>
    <button onclick="set('fps',10,this)">10 fps</button>
    <button onclick="set('fps',15,this)">15 fps</button>
  </div>
  <div class="label">Resolution <span style="color:#333;font-size:9px">(lower = less lag)</span></div>
  <div class="row" id="res-bar">
    <button onclick="set('res',360,this)">360p</button>
    <button onclick="set('res',540,this)">540p</button>
    <button onclick="set('res',720,this)" class="on">720p ★</button>
    <button onclick="set('res',1080,this)">Full</button>
  </div>
  <div class="label">Quality <span style="color:#333;font-size:9px">(lower = faster)</span></div>
  <div class="row" id="q-bar">
    <button onclick="set('q',30,this)">30</button>
    <button onclick="set('q',55,this)" class="on">55 ★</button>
    <button onclick="set('q',75,this)">75</button>
  </div>
  <div id="st">Connecting…</div>
</div>
<script>
var tok="${token}",fps=5,res=720,q=55,img=document.getElementById('s'),st=document.getElementById('st');
function buildUrl(){return'/screen/live?t='+tok+'&fps='+fps+'&res='+res+'&q='+q+'&_='+Date.now();}
function set(type,val,btn){
  if(type==='fps') fps=val;
  else if(type==='res') res=val;
  else q=val;
  var barId=type==='fps'?'fps-bar':type==='res'?'res-bar':'q-bar';
  document.querySelectorAll('#'+barId+' button').forEach(function(b){b.classList.remove('on');});
  btn.classList.add('on');
  reload();
}
var t0=0;
function reload(){
  t0=Date.now();
  st.textContent='Reconnecting…';
  img.src=buildUrl();
}
img.onload=function(){
  var ms=Date.now()-t0;
  st.innerHTML='Streaming &middot; '+fps+' fps &middot; '+res+'p &middot; q'+q
    +(ms>0?'<span id="ping"> &middot; first frame '+ms+'ms</span>':'');
};
img.onerror=function(){st.textContent='⚠ Stream error or link expired. Refresh to retry.';};
reload();
</script>
</body>
</html>"""
        call.response.header("Content-Type", "text/html; charset=utf-8")
        call.response.header("Cache-Control", "no-store")
        call.respond(html)
    }

    // ── Raw MJPEG stream ──────────────────────────────────────────────────────
    // GET /screen/live?t=<token>[&fps=5][&res=720][&q=55]
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

        val p = call.request.queryParameters
        LiveScreenCapturer.fps     = p["fps"]?.toIntOrNull()?.coerceIn(1, 15)  ?: 5
        LiveScreenCapturer.maxWidth = p["res"]?.toIntOrNull()?.coerceIn(0, 2560) ?: 720
        LiveScreenCapturer.quality  = p["q"]?.toIntOrNull()?.coerceIn(1, 95)   ?: 55

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
                        // Write failed → client disconnected.
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
