package com.ismartcoding.plain.telegram

import com.ismartcoding.lib.logcat.LogCat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base(token: String) = "https://api.telegram.org/bot$token"

    fun getUpdates(token: String, offset: Long, timeout: Int = 25): JSONObject? {
        val url = "${base(token)}/getUpdates?offset=$offset&timeout=$timeout&allowed_updates=%5B%22message%22%2C%22callback_query%22%5D"
        return try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null
                if (!resp.isSuccessful) { LogCat.e("TelegramBot getUpdates ${resp.code}: $body"); return null }
                JSONObject(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun sendMessage(
        token: String,
        chatId: String,
        text: String,
        parseMode: String = "HTML",
        replyMarkup: JSONObject? = null,
    ): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text.take(4096))
            put("parse_mode", parseMode)
            if (replyMarkup != null) put("reply_markup", replyMarkup)
        }
        return post(token, "sendMessage", json)
    }

    fun editMessageText(
        token: String,
        chatId: String,
        messageId: Long,
        text: String,
        parseMode: String = "HTML",
        replyMarkup: JSONObject? = null,
    ): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("text", text.take(4096))
            put("parse_mode", parseMode)
            if (replyMarkup != null) put("reply_markup", replyMarkup)
        }
        return post(token, "editMessageText", json)
    }

    fun editMessageReplyMarkup(
        token: String,
        chatId: String,
        messageId: Long,
        replyMarkup: JSONObject?,
    ): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            if (replyMarkup != null) put("reply_markup", replyMarkup)
        }
        return post(token, "editMessageReplyMarkup", json)
    }

    fun answerCallbackQuery(
        token: String,
        callbackQueryId: String,
        text: String = "",
        showAlert: Boolean = false,
    ): Boolean {
        val json = JSONObject().apply {
            put("callback_query_id", callbackQueryId)
            if (text.isNotEmpty()) put("text", text.take(200))
            if (showAlert) put("show_alert", true)
        }
        return post(token, "answerCallbackQuery", json)
    }

    /**
     * Build a single inline keyboard JSON object from rows of (label -> data).
     * `data` is normally a callback_data string, but if it starts with `url:`
     * the rest is interpreted as a URL and Telegram will open it in the browser
     * instead of firing a callback. This keeps the simple Pair API while letting
     * callers mix URL-buttons and callback-buttons in the same keyboard.
     */
    fun inlineKeyboard(rows: List<List<Pair<String, String>>>): JSONObject {
        val kb = JSONArray()
        rows.forEach { row ->
            val r = JSONArray()
            row.forEach { (label, data) ->
                val btn = JSONObject().apply { put("text", label.take(64)) }
                if (data.startsWith("url:")) {
                    btn.put("url", data.removePrefix("url:"))
                } else {
                    btn.put("callback_data", data.take(64))
                }
                r.put(btn)
            }
            kb.put(r)
        }
        return JSONObject().put("inline_keyboard", kb)
    }

    fun sendPhoto(token: String, chatId: String, file: File, caption: String = ""): Boolean {
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("photo", file.name, file.asRequestBody(mime.toMediaType()))
            .also {
                if (caption.isNotEmpty()) {
                    it.addFormDataPart("caption", caption.take(1024))
                    it.addFormDataPart("parse_mode", "HTML")
                }
            }
            .build()
        return multipart(token, "sendPhoto", body)
    }

    fun sendAudio(token: String, chatId: String, file: File, caption: String = "", durationSec: Int = 0): Boolean {
        val mime = when (file.extension.lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            else -> "audio/mpeg"
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("audio", file.name, file.asRequestBody(mime.toMediaType()))
            .also {
                if (caption.isNotEmpty()) it.addFormDataPart("caption", caption.take(1024))
                it.addFormDataPart("parse_mode", "HTML")
                if (durationSec > 0) it.addFormDataPart("duration", durationSec.toString())
            }
            .build()
        return multipart(token, "sendAudio", body)
    }

    fun sendVoice(token: String, chatId: String, file: File, caption: String = "", durationSec: Int = 0): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("voice", file.name, file.asRequestBody("audio/ogg".toMediaType()))
            .also {
                if (caption.isNotEmpty()) it.addFormDataPart("caption", caption.take(1024))
                if (durationSec > 0) it.addFormDataPart("duration", durationSec.toString())
            }
            .build()
        return multipart(token, "sendVoice", body)
    }

    fun sendVideo(token: String, chatId: String, file: File, caption: String = "", durationSec: Int = 0): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("video", file.name, file.asRequestBody("video/mp4".toMediaType()))
            .also {
                if (caption.isNotEmpty()) it.addFormDataPart("caption", caption.take(1024))
                if (durationSec > 0) it.addFormDataPart("duration", durationSec.toString())
            }
            .build()
        return multipart(token, "sendVideo", body)
    }

    /**
     * Send up to 10 photos as a Telegram album (media group).
     * [files] and [captions] are parallel lists; captions may be empty strings.
     * Only the first photo may carry a caption in a real album — subsequent captions are
     * ignored by Telegram, so we put all info in the first item's caption.
     */
    fun sendMediaGroup(token: String, chatId: String, files: List<File>, captions: List<String>): Boolean {
        if (files.isEmpty()) return false
        val mediaArray = JSONArray()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("chat_id", chatId)
        files.forEachIndexed { i, file ->
            val attachName = "file$i"
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            builder.addFormDataPart(attachName, file.name, file.asRequestBody(mime.toMediaType()))
            val cap = captions.getOrNull(i)?.take(1024) ?: ""
            val item = JSONObject().apply {
                put("type", "photo")
                put("media", "attach://$attachName")
                if (cap.isNotBlank()) {
                    put("caption", cap)
                    put("parse_mode", "HTML")
                }
            }
            mediaArray.put(item)
        }
        builder.addFormDataPart("media", mediaArray.toString())
        return multipart(token, "sendMediaGroup", builder.build())
    }

    fun sendDocument(token: String, chatId: String, file: File, caption: String = ""): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
            .also { if (caption.isNotEmpty()) it.addFormDataPart("caption", caption.take(1024)) }
            .build()
        return multipart(token, "sendDocument", body)
    }

    fun sendLocation(token: String, chatId: String, lat: Double, lon: Double): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("latitude", lat)
            put("longitude", lon)
        }
        return post(token, "sendLocation", json)
    }

    fun sendContact(
        token: String,
        chatId: String,
        phoneNumber: String,
        firstName: String,
        lastName: String? = null,
        vcard: String? = null,
        replyMarkup: JSONObject? = null,
    ): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("phone_number", phoneNumber)
            put("first_name", firstName)
            if (!lastName.isNullOrBlank()) put("last_name", lastName)
            if (!vcard.isNullOrBlank()) put("vcard", vcard)
            if (replyMarkup != null) put("reply_markup", replyMarkup)
        }
        return post(token, "sendContact", json)
    }

    fun sendChatAction(token: String, chatId: String, action: String = "typing"): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("action", action)
        }
        return post(token, "sendChatAction", json)
    }

    fun setMyCommands(token: String, commands: List<Pair<String, String>>): Boolean {
        val arr = JSONArray()
        commands.forEach { (cmd, desc) ->
            arr.put(JSONObject().apply { put("command", cmd); put("description", desc) })
        }
        val json = JSONObject().apply { put("commands", arr) }
        return post(token, "setMyCommands", json)
    }

    fun getMe(token: String): JSONObject? {
        return try {
            val req = Request.Builder().url("${base(token)}/getMe").get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null
                if (!resp.isSuccessful) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot getMe: ${e.message}")
            null
        }
    }

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    /**
     * Call Telegram's getFile API to get the download path for a given file_id.
     * Returns the `file_path` string, or null on failure.
     * Note: Telegram Bot API only supports files up to 20 MB via getFile.
     */
    fun getFilePath(token: String, fileId: String): String? {
        return try {
            val url = "${base(token)}/getFile?file_id=${java.net.URLEncoder.encode(fileId, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            downloadClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null
                if (!resp.isSuccessful) { LogCat.e("TelegramBot getFile ${resp.code}: $body"); return null }
                JSONObject(body).optJSONObject("result")?.optString("file_path")?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot getFilePath: ${e.message}")
            null
        }
    }

    /**
     * Download a file from Telegram servers to [destFile].
     * [filePath] is the value returned by [getFilePath].
     */
    fun downloadToFile(token: String, filePath: String, destFile: File): Boolean {
        val url = "https://api.telegram.org/file/bot$token/$filePath"
        return try {
            val req = Request.Builder().url(url).get().build()
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { LogCat.e("TelegramBot downloadToFile ${resp.code}"); return false }
                destFile.parentFile?.mkdirs()
                resp.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                true
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot downloadToFile: ${e.message}")
            false
        }
    }

    /**
     * Download a file from any direct URL to [destFile].
     * Used by the /update <url> command to fetch an APK from an arbitrary HTTPS link.
     */
    fun downloadFromUrl(url: String, destFile: File): Boolean {
        return try {
            val req = Request.Builder().url(url).get().build()
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { LogCat.e("TelegramBot downloadFromUrl ${resp.code}"); return false }
                destFile.parentFile?.mkdirs()
                resp.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                true
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot downloadFromUrl: ${e.message}")
            false
        }
    }

    private fun post(token: String, method: String, json: JSONObject): Boolean {
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url("${base(token)}/$method").post(body).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogCat.e("TelegramBot $method ${resp.code}: ${resp.body?.string()}")
                }
                resp.isSuccessful
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot $method: ${e.message}")
            false
        }
    }

    private fun multipart(token: String, method: String, body: MultipartBody): Boolean {
        val req = Request.Builder().url("${base(token)}/$method").post(body).build()
        return try {
            uploadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LogCat.e("TelegramBot $method ${resp.code}: ${resp.body?.string()}")
                }
                resp.isSuccessful
            }
        } catch (e: Exception) {
            LogCat.e("TelegramBot $method: ${e.message}")
            false
        }
    }
}
