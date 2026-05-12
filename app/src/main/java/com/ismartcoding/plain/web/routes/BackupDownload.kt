package com.ismartcoding.plain.web.routes

import com.ismartcoding.lib.logcat.LogCat
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * One-time (30-minute TTL) backup download manager.
 *
 * Usage:
 *   val token = UUID.randomUUID().toString().take(24)
 *   BackupDownloadManager.register(token, file, "plainapp_backup_20240512.plain")
 *   val url = "https://<host>/backup/dl?t=$token"          // .plain download
 *   val zipUrl = "https://<host>/backup/dl?t=$token&zip=1" // same file, .zip name
 */
object BackupDownloadManager {

    data class Entry(val file: File, val baseName: String, val expiresAt: Long)

    private val tokens = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 30L * 60 * 1000

    fun register(token: String, file: File, baseName: String) {
        purgeExpired()
        tokens[token] = Entry(file, baseName, System.currentTimeMillis() + TTL_MS)
    }

    fun get(token: String): Entry? {
        val e = tokens[token] ?: return null
        if (System.currentTimeMillis() >= e.expiresAt) { tokens.remove(token); return null }
        return e
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }
    }
}

fun Route.addBackupDownload() {
    get("/backup/dl") {
        val token = call.request.queryParameters["t"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing token")
            return@get
        }
        val asZip = call.request.queryParameters["zip"] == "1"

        val entry = BackupDownloadManager.get(token) ?: run {
            call.respond(
                HttpStatusCode.Gone,
                "Download link expired or already used.\nRun /backup in the Telegram bot to get a fresh link."
            )
            return@get
        }

        val file = entry.file
        if (!file.exists()) {
            call.respond(HttpStatusCode.Gone, "Backup file no longer exists on device. Run /backup again.")
            return@get
        }

        val displayName = if (asZip) entry.baseName.removeSuffix(".plain") + ".zip"
                          else entry.baseName
        try {
            call.response.header("Content-Disposition", "attachment; filename=\"$displayName\"")
            call.response.header("Content-Length", file.length().toString())
            call.respondOutputStream(ContentType.Application.OctetStream) {
                file.inputStream().buffered().use { it.copyTo(this) }
            }
        } catch (e: Exception) {
            LogCat.e("BackupDownload: error serving ${file.path}: ${e.message}")
        }
    }
}
