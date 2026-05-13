package com.ismartcoding.plain.web.routes

import com.ismartcoding.lib.logcat.LogCat
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * One-time browser-upload endpoint for restoring a PlainApp backup.
 *
 * Flow:
 *  1. Bot calls [RestoreUploadManager.register] to get a token + CompletableDeferred.
 *  2. Bot sends the user: http://<host>/restore/upload?t=<token>
 *  3. User opens URL → sees a simple HTML form, picks .plain/.zip, submits.
 *  4. POST handler saves the file and calls [RestoreUploadManager.complete].
 *  5. Bot's suspended coroutine (awaiting the deferred) wakes up and runs performRestore().
 */
object RestoreUploadManager {

    data class Entry(
        val deferred: CompletableDeferred<File?>,
        val expiresAt: Long,
    )

    private val pending = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 30L * 60 * 1000

    fun register(token: String): CompletableDeferred<File?> {
        purgeExpired()
        val d = CompletableDeferred<File?>()
        pending[token] = Entry(d, System.currentTimeMillis() + TTL_MS)
        return d
    }

    fun isValid(token: String): Boolean {
        val e = pending[token] ?: return false
        if (System.currentTimeMillis() >= e.expiresAt) { expire(token); return false }
        return true
    }

    fun complete(token: String, file: File): Boolean {
        val e = pending.remove(token) ?: return false
        e.deferred.complete(file)
        return true
    }

    fun cancel(token: String) {
        pending.remove(token)?.deferred?.complete(null)
    }

    private fun expire(token: String) {
        pending.remove(token)?.deferred?.complete(null)
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { (_, v) ->
            if (v.expiresAt < now) { v.deferred.complete(null); true } else false
        }
    }
}

fun Route.addRestoreUpload() {

    val html = { extra: String ->
        """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Restore PlainApp Backup</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
         background:#0f1117;color:#e8eaf0;display:flex;align-items:center;
         justify-content:center;min-height:100vh;padding:20px}
    .card{background:#1a1d27;border:1px solid #2a2d3a;border-radius:16px;
          padding:32px;max-width:480px;width:100%;text-align:center}
    .icon{font-size:48px;margin-bottom:16px}
    h1{font-size:22px;font-weight:700;margin-bottom:8px}
    p{color:#9aa0b4;font-size:14px;line-height:1.6;margin-bottom:20px}
    .file-label{display:block;border:2px dashed #3a3d50;border-radius:10px;
                padding:24px 16px;cursor:pointer;transition:border-color .2s}
    .file-label:hover{border-color:#6c8ef7}
    .file-label input{display:none}
    .file-name{margin-top:10px;font-size:13px;color:#9aa0b4}
    .btn{display:block;width:100%;margin-top:20px;padding:14px;
         background:#4f6ef7;color:#fff;border:none;border-radius:10px;
         font-size:16px;font-weight:600;cursor:pointer;transition:background .2s}
    .btn:hover{background:#6c8ef7}
    .btn:disabled{background:#2a2d3a;color:#555;cursor:not-allowed}
    .note{margin-top:16px;font-size:12px;color:#555}
    $extra
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">📥</div>
    <h1>Restore PlainApp Backup</h1>
    <p>Select your <b>.plain</b> or <b>.zip</b> backup file.<br>
       The phone will restore and restart automatically.</p>
    <form method="POST" enctype="multipart/form-data" id="f">
      <label class="file-label" for="file">
        <span>📂 Tap to choose file</span>
        <input type="file" name="file" id="file" accept=".plain,.zip" required>
        <div class="file-name" id="fname">No file chosen</div>
      </label>
      <button class="btn" type="submit" id="btn">Upload &amp; Restore</button>
    </form>
    <p class="note">⚠️ All current app data will be overwritten.<br>The app restarts when restore is complete.</p>
  </div>
  <script>
    document.getElementById('file').addEventListener('change',function(){
      document.getElementById('fname').textContent=this.files[0]?.name||'No file chosen';
    });
    document.getElementById('f').addEventListener('submit',function(){
      var btn=document.getElementById('btn');
      btn.disabled=true; btn.textContent='Uploading…';
    });
  </script>
</body>
</html>"""
    }

    val successHtml = """<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Upload complete</title>
<style>*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
     background:#0f1117;color:#e8eaf0;display:flex;align-items:center;
     justify-content:center;min-height:100vh;padding:20px}
.card{background:#1a1d27;border:1px solid #2a2d3a;border-radius:16px;
      padding:32px;max-width:480px;width:100%;text-align:center}
.icon{font-size:56px;margin-bottom:16px}
h1{font-size:22px;font-weight:700;margin-bottom:12px}
p{color:#9aa0b4;font-size:14px;line-height:1.6}</style>
</head>
<body>
  <div class="card">
    <div class="icon">✅</div>
    <h1>Upload complete!</h1>
    <p>Your backup is being restored on the phone.<br>
       Check the Telegram bot for the restore summary.<br><br>
       The app will restart automatically when done.</p>
  </div>
</body>
</html>"""

    get("/restore/upload") {
        val token = call.request.queryParameters["t"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@get
        }
        if (!RestoreUploadManager.isValid(token)) {
            call.respondText(
                ContentType.Text.Html,
                HttpStatusCode.Gone
            ) {
                html(".gone{color:#f77;font-size:15px;margin-top:12px}").replace(
                    "<form",
                    "<p class='gone'>⏰ This upload link has expired or already been used.<br>Send /restore in the Telegram bot to get a new one.</p><form style='display:none'"
                )
            }
            return@get
        }
        call.respondText(ContentType.Text.Html) { html("") }
    }

    post("/restore/upload") {
        val token = call.request.queryParameters["t"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@post
        }
        if (!RestoreUploadManager.isValid(token)) {
            call.respond(HttpStatusCode.Gone, "Upload link expired. Send /restore in the bot to get a new one.")
            return@post
        }

        val ctx = com.ismartcoding.plain.MainApp.instance
        val destFile = File(ctx.cacheDir, "restore_incoming_upload.plain")
        var fileName = "restore.plain"
        var saved = false

        try {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file") {
                    fileName = part.originalFileName?.takeIf { it.isNotBlank() } ?: "restore.plain"
                    part.streamProvider().use { input ->
                        destFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    saved = true
                }
                part.dispose()
            }
        } catch (e: Exception) {
            LogCat.e("RestoreUpload POST error: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
            return@post
        }

        if (!saved || !destFile.exists() || destFile.length() == 0L) {
            call.respond(HttpStatusCode.BadRequest, "No file received. Please select a .plain or .zip file.")
            return@post
        }

        val completed = RestoreUploadManager.complete(token, destFile)
        if (!completed) {
            destFile.delete()
            call.respond(HttpStatusCode.Gone, "Upload link expired. Send /restore in the bot to get a new one.")
            return@post
        }

        call.respondText(ContentType.Text.Html) { successHtml }
    }
}
