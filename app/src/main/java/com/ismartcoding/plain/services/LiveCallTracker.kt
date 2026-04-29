package com.ismartcoding.plain.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.DNotification
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import com.ismartcoding.plain.helpers.CallRecorderHelper
import com.ismartcoding.plain.telegram.TelegramBotManager
import kotlinx.serialization.Serializable

@Serializable
data class LiveCallStateData(
    val state: String,         // "idle" | "ringing" | "active" | "ended"
    val direction: String,     // "incoming" | "outgoing"
    val source: String,        // "phone" | "whatsapp" | "telegram" | "other"
    val appId: String,
    val appName: String,
    val display: String,       // phone number or contact / title from notification
    val startedAt: Long,
    val acceptedAt: Long,
    val muted: Boolean,
    val silenced: Boolean = false, // true when Android silenced our mic (during another app's call)
)

object LiveCallTracker {
    private val lock = Any()

    @Volatile private var state: String = "idle"
    @Volatile private var direction: String = "incoming"
    @Volatile private var source: String = "phone"
    @Volatile private var appId: String = ""
    @Volatile private var appName: String = ""
    @Volatile private var display: String = ""
    @Volatile private var startedAt: Long = 0
    @Volatile private var acceptedAt: Long = 0
    @Volatile private var muted: Boolean = false
    @Volatile private var silenced: Boolean = false
    @Volatile private var phoneListener: PhoneStateListener? = null
    @Volatile private var prevPhoneState: Int = TelephonyManager.CALL_STATE_IDLE

    private val callApps = setOf(
        "com.whatsapp" to "whatsapp",
        "com.whatsapp.w4b" to "whatsapp",
        "org.telegram.messenger" to "telegram",
        "org.telegram.messenger.web" to "telegram",
        "org.thoughtcrime.securesms" to "signal",
        "com.facebook.orca" to "messenger",
    )

    fun snapshot(): LiveCallStateData = synchronized(lock) {
        LiveCallStateData(state, direction, source, appId, appName, display, startedAt, acceptedAt, muted, silenced)
    }

    private fun publish() {
        try {
            val snap = snapshot()
            sendEvent(WebSocketEvent(EventType.LIVE_CALL_STATE, JsonHelper.jsonEncode(snap)))
            // Forward call state changes to Telegram bot
            if (snap.state in setOf("ringing", "active", "ended")) {
                try { TelegramBotManager.forwardCallState(snap.state, snap.display, snap.source, snap.direction) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    /** Called by LiveMicService when the OS silences/unsilences our recording. */
    fun onMicSilencedChanged(s: Boolean) {
        if (silenced == s) return
        silenced = s
        publish()
    }

    /**
     * Start the live-mic foreground service so the web "Listen" page can
     * negotiate a WebRTC peer connection with us. Idempotent.
     *
     * IMPORTANT: We deliberately do NOT touch AudioManager.mode or
     * isSpeakerphoneOn here. The active call (Phone, WhatsApp, …) holds
     * MODE_IN_CALL/MODE_IN_COMMUNICATION already; a non-system app forcing
     * a different mode is silently rejected on most ROMs but on Samsung /
     * Xiaomi / Realme it triggers an audio-focus + routing reshuffle that
     * also nudges the Wi-Fi radio for a moment — long enough to break
     * the long-lived cloudflared edge connection (so the public domain
     * appears "down" during a call) while leaving localhost reachable.
     * The user is asked to enable speakerphone in the call app instead
     * (see the hint shown on the Live Call page).
     */
    private fun startListeningIfNeeded(forCall: Boolean) {
        try {
            if (LiveMicService.instance?.isRunning() == true) return
            val ctx = MainApp.instance
            val intent = Intent(ctx, LiveMicService::class.java)
                .putExtra(LiveMicService.EXTRA_FOR_CALL, forCall)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Throwable) {
            LogCat.e("LiveCallTracker startListeningIfNeeded failed: ${e.message}")
        }
    }

    /**
     * Block (in a coroutine) until the LiveMicService is fully initialised
     * (instance set AND manager started) or [timeoutMs] elapses. Returns
     * true if the service is ready to handle WebRTC signaling.
     */
    suspend fun awaitListenerReady(timeoutMs: Long = 4000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (LiveMicService.instance?.isRunning() == true) return true
            kotlinx.coroutines.delay(50)
        }
        return LiveMicService.instance?.isRunning() == true
    }

    /** Called by PNotificationListenerService for app-call notifications. */
    fun onNotification(d: DNotification, isOngoing: Boolean, isCallCategory: Boolean) {
        val pkgKey = callApps.firstOrNull { it.first == d.appId }?.second ?: return
        val title = (d.title ?: "").trim()
        val body = (d.body ?: "").trim()

        // detect ringing: title/body contains "incoming" or "calling" — fall back to non-ongoing call category notif
        val txt = ("$title $body").lowercase()
        val isRinging = !isOngoing && (isCallCategory || txt.contains("incoming") || txt.contains("calling"))
        val isActive = isOngoing && (isCallCategory || txt.contains("ongoing") || txt.contains("call in progress"))

        synchronized(lock) {
            if (isRinging && state == "idle") {
                state = "ringing"; direction = "incoming"; source = pkgKey
                appId = d.appId; appName = d.appName
                display = title.ifEmpty { body }
                startedAt = System.currentTimeMillis(); acceptedAt = 0; muted = false
                publish()
                TimelineHelper.add("call", "Incoming $pkgKey call", display, d.appId, d.appName)
            } else if (isActive && state != "active") {
                state = "active"; source = pkgKey
                if (appId.isEmpty()) { appId = d.appId; appName = d.appName }
                if (display.isEmpty()) display = title.ifEmpty { body }
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
                if (acceptedAt == 0L) acceptedAt = System.currentTimeMillis()
                publish()
                // auto-start listener so the web "Listen" page has audio ready
                startListeningIfNeeded(forCall = true)
                // auto-record (controlled by CallRecorderEnabledPreference)
                CallRecorderHelper.onCallActive(display, source, direction, appId, appName)
            }
        }
    }

    /** Called when an app-call notification is removed. */
    fun onNotificationRemoved(packageName: String) {
        if (callApps.none { it.first == packageName }) return
        synchronized(lock) {
            if (state != "idle" && (source != "phone")) {
                end()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun installPhoneStateListener(context: Context) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(s: Int, incomingNumber: String?) {
                    handlePhoneState(s, incomingNumber ?: "")
                }
            }
            phoneListener = listener
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Throwable) {
            LogCat.e("LiveCallTracker installPhoneStateListener failed: ${e.message}")
        }
    }

    private fun handlePhoneState(s: Int, number: String) {
        synchronized(lock) {
            when (s) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    state = "ringing"; direction = "incoming"; source = "phone"
                    appId = "android.phone"; appName = "Phone"
                    display = number.ifEmpty { "Unknown" }
                    startedAt = System.currentTimeMillis(); acceptedAt = 0; muted = false
                    publish()
                    TimelineHelper.add("call", "Incoming phone call", display)
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (state == "idle") {
                        state = "active"; direction = "outgoing"; source = "phone"
                        appId = "android.phone"; appName = "Phone"
                        display = number.ifEmpty { "Outgoing call" }
                        startedAt = System.currentTimeMillis(); acceptedAt = startedAt
                        publish()
                        TimelineHelper.add("call", "Outgoing phone call", display)
                    } else if (state == "ringing") {
                        state = "active"; acceptedAt = System.currentTimeMillis()
                        publish()
                    }
                    startListeningIfNeeded(forCall = true)
                    CallRecorderHelper.onCallActive(display, source, direction, appId, appName)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (state != "idle" && source == "phone") end()
                }
            }
            prevPhoneState = s
        }
    }

    /** Invoked by web panel when user clicks "Answer" — auto-starts mic + speakerphone. */
    fun acceptFromPanel() {
        synchronized(lock) {
            if (state == "ringing") {
                state = "active"; acceptedAt = System.currentTimeMillis()
            }
        }
        startListeningIfNeeded(forCall = true)
        CallRecorderHelper.onCallActive(display, source, direction, appId, appName)
        publish()
    }

    /** Invoked by the web "Listen" page if it loads while a call is already active. */
    fun ensureListening() {
        if (state == "active") startListeningIfNeeded(forCall = true)
    }

    fun setMuted(m: Boolean) {
        muted = m
        try { LiveMicService.instance?.setMuted(m) } catch (_: Throwable) {}
        publish()
    }

    fun end() {
        synchronized(lock) {
            state = "ended"
            publish()
            // Stop the call recorder first so the .m4a is finalized and shows
            // up in the recordings list.
            try { CallRecorderHelper.onCallEnded() } catch (_: Throwable) {}
            // Stop mic. We never touched AudioManager.mode / speakerphone
            // ourselves (see comment in startListeningIfNeeded), so there is
            // nothing to roll back here — leaving the call app fully in
            // control of audio routing.
            try { LiveMicService.instance?.stop(); LiveMicService.instance = null } catch (_: Throwable) {}
            // back to idle after a beat
            state = "idle"; direction = "incoming"; source = "phone"
            appId = ""; appName = ""; display = ""; startedAt = 0; acceptedAt = 0; muted = false
            publish()
        }
    }
}
