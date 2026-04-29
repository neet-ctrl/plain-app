package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Records battery samples (level + plugged state) into a SharedPreferences-backed
 * ring buffer. A long-running BatterySamplerService writes one entry per
 * SAMPLE_INTERVAL_MS plus on every charger-state change, giving a reasonably
 * accurate charge/discharge timeline without needing a privileged BatteryStats
 * dump.
 */
object BatteryHistoryHelper {

    private const val PREFS = "plain_battery_history"
    private const val K_SAMPLES = "samples"
    private const val K_ENABLED = "enabled"
    const val SAMPLE_INTERVAL_MS = 5L * 60L * 1000L  // 5 minutes
    const val MAX_SAMPLES = 8640                       // ~30 days @ 5-min cadence

    data class Sample(
        val ts: Long,
        val level: Int,        // 0..100
        val plugged: Int,      // 0=none, 1=AC, 2=USB, 4=wireless
        val temperatureC: Float,
        val voltageMv: Int,
        val status: Int,       // BatteryManager.BATTERY_STATUS_*
    )

    data class Window(
        val sinceMs: Long,
        val untilMs: Long,
        val samples: List<Sample>,
        val charging: Boolean,
        val currentLevel: Int,
        val plugged: String,
    )

    private fun prefs(ctx: Context = MainApp.instance) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, true) // default on — passive sampling

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    @Synchronized
    fun appendSample(s: Sample, ctx: Context = MainApp.instance) {
        val raw = prefs(ctx).getString(K_SAMPLES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        // Coalesce: if last sample was within 60s with the same level + plugged, replace it.
        val last = if (arr.length() > 0) arr.optJSONObject(arr.length() - 1) else null
        val sameAsLast = last != null &&
            (s.ts - last.optLong("ts")) < 60_000L &&
            last.optInt("level") == s.level &&
            last.optInt("plugged") == s.plugged
        val obj = JSONObject().apply {
            put("ts", s.ts)
            put("level", s.level)
            put("plugged", s.plugged)
            put("temperatureC", s.temperatureC.toDouble())
            put("voltageMv", s.voltageMv)
            put("status", s.status)
        }
        if (sameAsLast) {
            arr.put(arr.length() - 1, obj)
        } else {
            arr.put(obj)
        }
        // Trim from the front if past cap.
        if (arr.length() > MAX_SAMPLES) {
            val excess = arr.length() - MAX_SAMPLES
            val trimmed = JSONArray()
            for (i in excess until arr.length()) trimmed.put(arr.getJSONObject(i))
            prefs(ctx).edit().putString(K_SAMPLES, trimmed.toString()).apply()
        } else {
            prefs(ctx).edit().putString(K_SAMPLES, arr.toString()).apply()
        }
    }

    fun window(days: Int, ctx: Context = MainApp.instance): Window {
        val until = System.currentTimeMillis()
        val since = until - days.coerceIn(1, 60) * 24L * 60L * 60L * 1000L
        val raw = prefs(ctx).getString(K_SAMPLES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val out = ArrayList<Sample>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ts = o.optLong("ts")
            if (ts < since) continue
            out.add(
                Sample(
                    ts = ts,
                    level = o.optInt("level"),
                    plugged = o.optInt("plugged"),
                    temperatureC = o.optDouble("temperatureC", 0.0).toFloat(),
                    voltageMv = o.optInt("voltageMv"),
                    status = o.optInt("status"),
                )
            )
        }

        // Get the live "now" sample so the chart line ends at the present.
        val live = sampleNow(ctx)
        if (live != null) out.add(live)

        val plugged = pluggedString(live?.plugged ?: 0)
        val charging = (live?.status == BatteryManager.BATTERY_STATUS_CHARGING) || (live?.plugged ?: 0) > 0
        return Window(since, until, out, charging, live?.level ?: -1, plugged)
    }

    fun clear(ctx: Context = MainApp.instance) {
        prefs(ctx).edit().remove(K_SAMPLES).apply()
    }

    fun sampleNow(ctx: Context = MainApp.instance): Sample? {
        val intent: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) (level * 100) / scale else -1
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return Sample(System.currentTimeMillis(), pct, plugged, temp, volt, status)
    }

    private fun pluggedString(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "ac"
        BatteryManager.BATTERY_PLUGGED_USB -> "usb"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
        0 -> "unplugged"
        else -> "other"
    }
}
