package com.neet.tracker.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies the content behind [uri] into the app's internal files/uploads directory
 * and returns the absolute path of the local copy, or null on failure.
 *
 * Storing the local path instead of the content:// URI means the file is always
 * accessible — no URI permissions needed, no expiry after app restart.
 */
suspend fun copyUriToAppFiles(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            var displayName = ""
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) displayName = c.getString(0) ?: ""
                }
            } catch (_: Exception) {}

            if (displayName.isBlank()) {
                val ext = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(uri) ?: "") ?: "bin"
                displayName = "file_${System.currentTimeMillis()}.$ext"
            }

            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val uploadsDir = File(context.filesDir, "uploads")
            uploadsDir.mkdirs()
            val destFile = File(uploadsDir, "${System.currentTimeMillis()}_$safeName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (destFile.exists() && destFile.length() > 0) destFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }
