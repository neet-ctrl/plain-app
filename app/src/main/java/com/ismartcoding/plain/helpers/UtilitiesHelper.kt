package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Shared helper for every "device utility" surfaced through the web panel.
 */
object UtilitiesHelper {

    // ---------------- TTS ----------------

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady: Boolean = false

    private fun ensureTts(context: Context, onReady: (Boolean) -> Unit) {
        val existing = tts
        if (existing != null && ttsReady) {
            onReady(true); return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
            } else {
                LogCat.e("TTS init failed: status=$status")
            }
            onReady(ttsReady)
        }
    }

    fun speak(text: String, locale: String? = null) {
        if (text.isBlank()) return
        ensureTts(MainApp.instance) { ok ->
            if (!ok) return@ensureTts
            try {
                if (!locale.isNullOrBlank()) {
                    tts?.language = Locale.forLanguageTag(locale)
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "plain-${System.currentTimeMillis()}")
            } catch (e: Exception) {
                LogCat.e("TTS speak failed: ${e.message}", e)
            }
        }
    }

    fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
    }

    // ---------------- Toast ----------------

    /**
     * Show a short Android Toast on the device. Always run on the main looper —
     * Toasts crash with `RuntimeException: Can't toast on a thread that has not
     * called Looper.prepare()` when posted from a worker (Telegram bot polling
     * thread, GraphQL resolver thread, etc.).
     *
     * `longToast=true` uses LENGTH_LONG (~3.5 s) instead of the default LENGTH_SHORT (~2 s).
     */
    fun toast(text: String, longToast: Boolean = false) {
        if (text.isBlank()) return
        val ctx = MainApp.instance
        Handler(Looper.getMainLooper()).post {
            try {
                android.widget.Toast.makeText(
                    ctx,
                    text,
                    if (longToast) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                LogCat.e("Toast failed: ${e.message}", e)
            }
        }
    }

    // ---------------- Vibrate ----------------

    @Suppress("DEPRECATION")
    fun vibrate(durationMs: Long) {
        val ctx = MainApp.instance
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v?.vibrate(VibrationEffect.createOneShot(durationMs.coerceIn(10, 60_000), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            LogCat.e("Vibrate failed: ${e.message}", e)
        }
    }

    // ---------------- Wake screen ----------------

    @SuppressWarnings("WakelockTimeout")
    fun wakeScreen(durationMs: Long = 10_000L) {
        val ctx = MainApp.instance
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "PlainApp:WakeScreen"
        )
        try {
            wl.acquire(durationMs.coerceIn(1_000, 60_000))
            // Release on a delay so the system has time to actually wake the display
            Handler(Looper.getMainLooper()).postDelayed({
                try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
            }, durationMs)
        } catch (e: Exception) {
            LogCat.e("WakeScreen failed: ${e.message}", e)
        }
    }

    // ---------------- Torch ----------------

    @Volatile private var torchOn: Boolean = false

    fun isTorchOn(): Boolean = torchOn

    fun setTorch(on: Boolean): Boolean {
        val ctx = MainApp.instance
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backCamera = cm.cameraIdList.firstOrNull { id ->
            try {
                val cap = cm.getCameraCharacteristics(id)
                cap.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } catch (_: Exception) { false }
        } ?: return false
        return try {
            cm.setTorchMode(backCamera, on)
            torchOn = on
            true
        } catch (e: Exception) {
            LogCat.e("setTorch failed: ${e.message}", e)
            false
        }
    }

    // ---------------- Volume ----------------

    /** stream is one of "ring","music","notification","alarm","call","system" */
    fun setVolume(stream: String, percent: Int): Boolean {
        val am = MainApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = streamId(stream)
        return try {
            val max = am.getStreamMaxVolume(s)
            val target = (percent.coerceIn(0, 100) / 100.0 * max).toInt()
            am.setStreamVolume(s, target, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            LogCat.e("setVolume failed: ${e.message}", e)
            false
        }
    }

    fun getVolumes(): Map<String, Int> {
        val am = MainApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = listOf("ring", "music", "notification", "alarm", "call", "system")
        return streams.associateWith {
            val s = streamId(it)
            val max = am.getStreamMaxVolume(s).coerceAtLeast(1)
            (am.getStreamVolume(s) * 100 / max)
        }
    }

    private fun streamId(name: String): Int = when (name.lowercase()) {
        "ring" -> AudioManager.STREAM_RING
        "music", "media" -> AudioManager.STREAM_MUSIC
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "alarm" -> AudioManager.STREAM_ALARM
        "call", "voice" -> AudioManager.STREAM_VOICE_CALL
        "system" -> AudioManager.STREAM_SYSTEM
        else -> AudioManager.STREAM_MUSIC
    }

    // ---------------- Brightness ----------------

    fun setBrightness(percent: Int): Boolean {
        val ctx = MainApp.instance
        if (!Settings.System.canWrite(ctx)) return false
        return try {
            val target = (percent.coerceIn(0, 100) / 100.0 * 255).toInt().coerceIn(0, 255)
            // Disable auto-brightness so the manual value sticks
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
            true
        } catch (e: Exception) {
            LogCat.e("setBrightness failed: ${e.message}", e)
            false
        }
    }

    fun getBrightness(): Int {
        val ctx = MainApp.instance
        return try {
            val v = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            (v * 100 / 255).coerceIn(0, 100)
        } catch (_: Exception) { 0 }
    }

    // ---------------- Location ----------------

    data class DeviceLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val plusCode: String,
        val provider: String,
        val timestamp: Long,
    )

    suspend fun getLocation(timeoutMs: Long = 10_000L): DeviceLocation? = suspendCancellableCoroutine { cont ->
        val ctx = MainApp.instance
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.allProviders.filter { lm.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        // First try last known
        var best: Location? = null
        for (p in providers) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            } catch (_: SecurityException) {}
        }
        if (best != null && System.currentTimeMillis() - best!!.time < 60_000) {
            cont.resume(toDeviceLocation(best!!))
            return@suspendCancellableCoroutine
        }

        val main = Handler(Looper.getMainLooper())
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                providers.forEach { try { lm.removeUpdates(this) } catch (_: Exception) {} }
                if (cont.isActive) cont.resume(toDeviceLocation(location))
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            providers.forEach { p ->
                lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        main.postDelayed({
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            if (cont.isActive) {
                if (best != null) cont.resume(toDeviceLocation(best!!)) else cont.resume(null)
            }
        }, timeoutMs)

        cont.invokeOnCancellation {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }
    }

    private fun toDeviceLocation(l: Location): DeviceLocation = DeviceLocation(
        latitude = l.latitude,
        longitude = l.longitude,
        accuracyMeters = l.accuracy,
        plusCode = OpenLocationCode.encode(l.latitude, l.longitude),
        provider = l.provider ?: "",
        timestamp = l.time,
    )

    // ---------------- Mobile data deep-link ----------------

    fun openDataSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            MainApp.instance.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                MainApp.instance.startActivity(intent)
                true
            } catch (e: Exception) {
                LogCat.e("openDataSettings failed: ${e.message}", e)
                false
            }
        }
    }

    // ---------------- Play audio (Talk-mode push-to-talk) ----------------

    @Volatile private var playbackPlayer: MediaPlayer? = null

    fun playAudioBase64(base64: String, mime: String): Boolean {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val ext = when {
                mime.contains("ogg") -> "ogg"
                mime.contains("mp4") || mime.contains("aac") -> "m4a"
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                else -> "webm"
            }
            val tmp = File.createTempFile("talk_", ".$ext", MainApp.instance.cacheDir)
            FileOutputStream(tmp).use { it.write(bytes) }
            Handler(Looper.getMainLooper()).post {
                try {
                    playbackPlayer?.runCatching { stop(); release() }
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(tmp.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            playbackPlayer = null
                            tmp.delete()
                        }
                        setOnErrorListener { p, _, _ -> p.release(); playbackPlayer = null; tmp.delete(); true }
                        prepare()
                        start()
                    }
                    playbackPlayer = mp
                } catch (e: Exception) {
                    LogCat.e("playAudioBase64 failed: ${e.message}")
                    tmp.delete()
                }
            }
            true
        } catch (e: Exception) {
            LogCat.e("playAudioBase64 decode failed: ${e.message}")
            false
        }
    }
}
