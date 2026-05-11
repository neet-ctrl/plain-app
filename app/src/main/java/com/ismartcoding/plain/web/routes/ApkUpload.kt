package com.ismartcoding.plain.web.routes

import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.ApkUpdateHelper
import com.ismartcoding.plain.web.HttpServerManager
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
private data class ApkUploadResponse(
    val deviceOwner: Boolean,
    val message: String = "",
)

/**
 * POST /apk_upload — receive an .apk file from the web panel browser and trigger installation.
 *
 * Auth: standard `c-id` header (same as every other authenticated Ktor route).
 * Body: multipart/form-data with a single `file` part containing the APK binary.
 *
 * Response 200 JSON: { "deviceOwner": true/false }
 *   - deviceOwner=true  → silent install was triggered (Device Owner mode, Android 12+).
 *   - deviceOwner=false → system Install dialog was opened on the device; user needs one tap.
 */
fun Route.addApkUpload() {
    post("/apk_upload") {
        val clientId = call.request.header("c-id") ?: ""
        if (clientId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "c-id header is missing")
            return@post
        }
        if (HttpServerManager.tokenCache[clientId] == null) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@post
        }
        try {
            val ctx = MainApp.instance
            var savedFile: File? = null
            call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> if (part.name == "file") {
                        val destDir = File(ctx.cacheDir, "apk_updates")
                        destDir.mkdirs()
                        val destFile = File(destDir, "apk_upload_${System.currentTimeMillis()}.apk")
                        FileOutputStream(destFile).use { fos ->
                            part.provider().copyTo(fos)
                            fos.fd.sync()
                        }
                        savedFile = destFile
                    }
                    else -> {}
                }
                part.dispose()
            }
            val apk = savedFile
            if (apk == null || !apk.exists() || apk.length() == 0L) {
                call.respond(HttpStatusCode.BadRequest, "no file part or empty file")
                return@post
            }
            val isOwner = ApkUpdateHelper.isDeviceOwner(ctx)
            ApkUpdateHelper.install(ctx, apk) { _, _ -> }
            val json = Json.encodeToString(ApkUploadResponse(deviceOwner = isOwner))
            call.respond(HttpStatusCode.OK, json)
        } catch (ex: Exception) {
            LogCat.e("ApkUpload error: ${ex.message}", ex)
            call.respond(HttpStatusCode.InternalServerError, ex.message ?: "upload failed")
        }
    }
}
