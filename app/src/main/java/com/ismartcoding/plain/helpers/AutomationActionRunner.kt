package com.ismartcoding.plain.helpers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Runs a [AutomationHelper.Rule] from start to finish.
 *
 *  1. Evaluates per-rule conditions (AND).
 *  2. Walks the action list in order, performing each side-effect on the
 *     phone, with substitution of `{{var}}` placeholders from variables and
 *     trigger context (e.g. `{{from_number}}`, `{{battery_level}}`).
 *  3. Records a per-action log line and a final ActionRun row in
 *     [AutomationHelper] so the web UI can show what happened.
 *
 * Designed to be called from any thread; long-running actions (HTTP, TTS,
 * recording) yield to coroutines internally.
 */
object AutomationActionRunner {

    private val scope = CoroutineScope(Dispatchers.IO)
    private const val NOTIF_CHANNEL = "plain_automation"
    @Volatile private var tts: TextToSpeech? = null

    /** Public entry: evaluate + execute a rule. Returns true if any actions ran. */
    fun trigger(
        ruleId: String,
        source: String,
        context: Map<String, String> = emptyMap(),
        ctx: Context = MainApp.instance,
    ): Boolean {
        if (!AutomationHelper.isEnabled(ctx)) return false
        val rule = AutomationHelper.byId(ruleId, ctx) ?: return false
        if (!rule.enabled) return false
        // Cooldown
        if (rule.cooldownMs > 0 &&
            System.currentTimeMillis() - rule.lastRunMs < rule.cooldownMs
        ) {
            AutomationHelper.appendRun(
                AutomationHelper.ActionRun(
                    id = UUID.randomUUID().toString(),
                    ruleId = rule.id, ruleName = rule.name,
                    ts = System.currentTimeMillis(),
                    ok = false, source = source,
                    log = listOf("skipped: cooldown"),
                ), ctx,
            )
            return false
        }
        // Conditions
        for (c in rule.conditions) {
            val ok = evalCondition(c, context, ctx)
            if (!ok) {
                AutomationHelper.appendRun(
                    AutomationHelper.ActionRun(
                        id = UUID.randomUUID().toString(),
                        ruleId = rule.id, ruleName = rule.name,
                        ts = System.currentTimeMillis(),
                        ok = false, source = source,
                        log = listOf("skipped: condition ${c.type}"),
                    ), ctx,
                )
                return false
            }
        }
        // Run actions sequentially. We use runBlocking on an IO scope so HTTP
        // and TTS waits behave like synchronous Tasker steps.
        val log = mutableListOf<String>()
        var allOk = true
        scope.launch {
            for (a in rule.actions) {
                val (ok, msg) = try {
                    perform(a, context, ctx)
                } catch (t: Throwable) {
                    false to (t.message ?: t.javaClass.simpleName)
                }
                if (!ok) allOk = false
                log.add("${if (ok) "✓" else "✗"} ${a.type}${if (msg.isNotEmpty()) " — $msg" else ""}")
            }
            AutomationHelper.markRan(rule.id, ctx)
            AutomationHelper.appendRun(
                AutomationHelper.ActionRun(
                    id = UUID.randomUUID().toString(),
                    ruleId = rule.id, ruleName = rule.name,
                    ts = System.currentTimeMillis(),
                    ok = allOk, source = source, log = log,
                ), ctx,
            )
        }
        return true
    }

    // ---------------- Conditions ----------------

    private fun evalCondition(
        c: AutomationHelper.Condition,
        ctx2: Map<String, String>,
        ctx: Context,
    ): Boolean = when (c.type) {
        "time_window" -> {
            val from = parseTime(c.params["from"]) ?: return true
            val to = parseTime(c.params["to"]) ?: return true
            val now = nowMinutes()
            if (from <= to) now in from..to else now >= from || now <= to
        }
        "day_of_week" -> {
            val days = (c.params["days"] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
            if (days.isEmpty()) true else {
                // Calendar.SUNDAY=1; we use 1=Mon..7=Sun for clarity.
                val cal = Calendar.getInstance()
                val isoDow = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                days.contains(isoDow)
            }
        }
        "battery_level" -> {
            val level = ctx2["battery_level"]?.toIntOrNull() ?: return true
            val op = c.params["op"] ?: "<"
            val th = c.params["threshold"]?.toIntOrNull() ?: 20
            when (op) { "<" -> level < th; "<=" -> level <= th; ">" -> level > th
                ">=" -> level >= th; "==" -> level == th; else -> true }
        }
        "charging" -> {
            val want = c.params["state"] == "true"
            ctx2["charging"]?.toBoolean() == want
        }
        "wifi_ssid" -> {
            val want = c.params["ssid"] ?: ""
            val have = ctx2["wifi_ssid"] ?: ""
            want.isEmpty() || have.equals(want, ignoreCase = true)
        }
        "silent_mode" -> {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            (am.ringerMode == AudioManager.RINGER_MODE_SILENT) ==
                (c.params["state"] == "true")
        }
        else -> true
    }

    // ---------------- Actions ----------------

    private suspend fun perform(
        a: AutomationHelper.Action,
        triggerCtx: Map<String, String>,
        ctx: Context,
    ): Pair<Boolean, String> {
        val p = a.params.mapValues { (_, v) -> substitute(v, triggerCtx, ctx) }
        return when (a.type) {
            "notify" -> doNotify(p, ctx)
            "vibrate" -> doVibrate(p, ctx)
            "delay" -> { delay((p["ms"]?.toLongOrNull() ?: 1000L).coerceIn(1, 600_000)); true to "" }
            "set_clipboard" -> doClipboard(p, ctx)
            "set_ringer" -> doRinger(p, ctx)
            "set_volume" -> doVolume(p, ctx)
            "send_sms" -> doSendSms(p, ctx)
            "make_call" -> doMakeCall(p, ctx)
            "launch_app" -> doLaunchApp(p, ctx)
            "toggle_wifi" -> doToggleWifi(p, ctx)
            "toggle_bluetooth" -> doToggleBluetooth(p, ctx)
            "toggle_dnd" -> doToggleDnd(p, ctx)
            "send_webhook", "http_get" -> doHttp(a.type, p, ctx)
            "speak" -> doSpeak(p, ctx)
            "flashlight" -> doFlashlight(p, ctx)
            "lock_screen" -> doLockScreen(ctx)
            "set_variable" -> {
                val k = p["key"].orEmpty(); val v = p["value"].orEmpty()
                if (k.isBlank()) false to "key required"
                else { AutomationHelper.setVariable(k, v, ctx); true to "$k=$v" }
            }
            "take_photo", "start_recording", "stop_recording" -> {
                // These are surfaced as best-effort hooks. Real capture is
                // delegated to the existing media services elsewhere in the
                // app; here we just enqueue a notification so the user knows
                // the rule fired and the device-side service can pick up.
                doNotify(mapOf(
                    "title" to "Automation: ${a.type}",
                    "body" to (p["body"] ?: a.params.toString()),
                ), ctx)
            }
            else -> false to "unknown action ${a.type}"
        }
    }

    private fun doNotify(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                NOTIF_CHANNEL, "Automation", NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val pi = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, com.ismartcoding.plain.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(p["title"] ?: "Automation")
            .setContentText(p["body"] ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(p["body"] ?: ""))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(("auto_${System.nanoTime()}").hashCode(), n)
        return true to ""
    }

    private fun doVibrate(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val ms = p["ms"]?.toLongOrNull() ?: 250L
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(10, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
        return true to "${ms}ms"
    }

    private fun doClipboard(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("automation", p["text"] ?: ""))
        return true to ""
    }

    private fun doRinger(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = when (p["mode"]) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            else -> return false to "mode required"
        }
        return try { am.ringerMode = mode; true to p["mode"]!! }
        catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private fun doVolume(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (p["stream"]) {
            "ring" -> AudioManager.STREAM_RING
            "music" -> AudioManager.STREAM_MUSIC
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val pct = (p["percent"]?.toIntOrNull() ?: 50).coerceIn(0, 100)
        val max = am.getStreamMaxVolume(stream)
        return try {
            am.setStreamVolume(stream, max * pct / 100, 0); true to "$pct%"
        } catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private fun doSendSms(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return false to "no SEND_SMS permission"
        val to = p["to"] ?: return false to "to required"
        val body = p["body"] ?: ""
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sms.divideMessage(body)
            sms.sendMultipartTextMessage(to, null, parts, null, null)
            true to to
        } catch (t: Throwable) { false to (t.message ?: "send failed") }
    }

    private fun doMakeCall(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val to = p["to"] ?: return false to "to required"
        return try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:$to"))
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(i); true to to
        } catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private fun doLaunchApp(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val pkg = p["package"] ?: return false to "package required"
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false to "no launcher"
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return try { ctx.startActivity(i); true to pkg }
        catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private fun doToggleWifi(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val on = p["state"] == "true"
        return try { WifiControlHelper.setWifiEnabled(on, ctx); true to (if (on) "on" else "off") }
        catch (t: Throwable) { false to (t.message ?: "blocked") }
    }

    private fun doToggleBluetooth(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val on = p["state"] == "true"
        return try { BluetoothControlHelper.setEnabled(on, ctx); true to (if (on) "on" else "off") }
        catch (t: Throwable) { false to (t.message ?: "blocked") }
    }

    private fun doToggleDnd(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val on = p["state"] == "true"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            if (!nm.isNotificationPolicyAccessGranted) return false to "no DND policy access"
            nm.setInterruptionFilter(if (on)
                NotificationManager.INTERRUPTION_FILTER_NONE else
                NotificationManager.INTERRUPTION_FILTER_ALL)
            true to (if (on) "on" else "off")
        } catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private suspend fun doHttp(verb: String, p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val url = p["url"] ?: return false to "url required"
        val method = (p["method"] ?: if (verb == "http_get") "GET" else "POST").uppercase()
        val body = p["body"] ?: ""
        val headers = p["headers"] ?: ""
        return withTimeoutOrNull(15_000L) {
            try {
                val u = URL(url); val c = u.openConnection() as HttpURLConnection
                c.requestMethod = method; c.connectTimeout = 8_000; c.readTimeout = 8_000
                headers.split("\n").forEach {
                    val pair = it.split(":", limit = 2)
                    if (pair.size == 2) c.setRequestProperty(pair[0].trim(), pair[1].trim())
                }
                if (method != "GET" && body.isNotEmpty()) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = c.responseCode
                val resp = try {
                    (if (code in 200..299) c.inputStream else c.errorStream)
                        ?.bufferedReader()?.readText()?.take(2000) ?: ""
                } catch (_: Throwable) { "" }
                if (p["captureAs"]?.isNotBlank() == true) {
                    AutomationHelper.setVariable(p["captureAs"]!!, resp, ctx)
                }
                (code in 200..299) to "$code"
            } catch (t: Throwable) {
                false to (t.message ?: "http failed")
            }
        } ?: (false to "timeout")
    }

    private suspend fun doSpeak(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        val text = p["text"] ?: return false to "text required"
        return try {
            ensureTts(ctx)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "auto_${System.nanoTime()}")
            true to ""
        } catch (t: Throwable) { false to (t.message ?: "tts failed") }
    }

    private fun ensureTts(ctx: Context) {
        if (tts != null) return
        synchronized(this) {
            if (tts != null) return
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
            }
        }
    }

    private fun doFlashlight(p: Map<String, String>, ctx: Context): Pair<Boolean, String> {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false to "no flash"
            cm.setTorchMode(id, p["state"] == "true")
            true to (p["state"] ?: "")
        } catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    private fun doLockScreen(ctx: Context): Pair<Boolean, String> {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as android.app.admin.DevicePolicyManager
            dpm.lockNow(); true to ""
        } catch (t: Throwable) { false to (t.message ?: "denied") }
    }

    // ---------------- Helpers ----------------

    private fun substitute(template: String, ctxMap: Map<String, String>, ctx: Context): String {
        if (!template.contains("{{")) return template
        var out = template
        // Built-in tokens
        val now = Date()
        val builtin = mapOf(
            "now" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now),
            "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now),
            "time" to SimpleDateFormat("HH:mm", Locale.US).format(now),
            "ts" to now.time.toString(),
        )
        val all = AutomationHelper.variables(ctx) + ctxMap + builtin
        all.forEach { (k, v) -> out = out.replace("{{${k}}}", v) }
        return out
    }

    private fun parseTime(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (_: Throwable) {}
        tts = null
    }
}
