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
        val url = "${base(token)}/getUpdates?offset=$offset&timeout=$timeout&allowed_updates=%5B%22message%22%5D"
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

    fun sendMessage(token: String, chatId: String, text: String, parseMode: String = "HTML"): Boolean {
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text.take(4096))
            put("parse_mode", parseMode)
        }
        return post(token, "sendMessage", json)
    }

    fun sendPhoto(token: String, chatId: String, file: File, caption: String = ""): Boolean {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .also { if (caption.isNotEmpty()) it.addFormDataPart("caption", caption.take(1024)) }
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
