package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for the live GPS location stream history.
 * Each point has timestamp, lat, lng, accuracy, speed, altitude, bearing,
 * battery level (%), charging, provider.
 * Storage is a single SharedPreferences key holding a rolling JSON array
 * (most recent first), capped at MAX_POINTS to keep memory bounded.
 */
object LocationTrackingHelper {
    private const val PREFS = "plain_loc_tracking"
    private const val K_POINTS = "points"
    private const val K_ENABLED = "enabled"
    private const val K_INTERVAL = "interval_sec"
    private const val K_MIN_DISPLACEMENT = "min_displacement_m"
    const val MAX_POINTS = 5000
    const val DEFAULT_INTERVAL_SEC = 15
    const val DEFAULT_MIN_DISPLACEMENT_M = 0

    data class Point(
        val ts: Long,
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val speed: Float,
        val altitude: Double,
        val bearing: Float,
        val batteryLevel: Int,
        val charging: Boolean,
        val provider: String,
    )

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, false)

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    fun getIntervalSec(ctx: Context = MainApp.instance): Int =
        prefs(ctx).getInt(K_INTERVAL, DEFAULT_INTERVAL_SEC).coerceAtLeast(2)

    fun setIntervalSec(seconds: Int, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putInt(K_INTERVAL, seconds.coerceIn(2, 3600)).apply()
    }

    fun getMinDisplacement(ctx: Context = MainApp.instance): Int =
        prefs(ctx).getInt(K_MIN_DISPLACEMENT, DEFAULT_MIN_DISPLACEMENT_M)

    fun setMinDisplacement(meters: Int, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putInt(K_MIN_DISPLACEMENT, meters.coerceIn(0, 5000)).apply()
    }

    fun appendPoint(p: Point, ctx: Context = MainApp.instance) {
        try {
            val arr = readArray(ctx)
            // newest first
            val obj = JSONObject().apply {
                put("ts", p.ts)
                put("lat", p.lat)
                put("lng", p.lng)
                put("accuracy", p.accuracy.toDouble())
                put("speed", p.speed.toDouble())
                put("altitude", p.altitude)
                put("bearing", p.bearing.toDouble())
                put("battery", p.batteryLevel)
                put("charging", p.charging)
                put("provider", p.provider)
            }
            // prepend
            val newArr = JSONArray()
            newArr.put(obj)
            val limit = MAX_POINTS - 1
            val keep = minOf(arr.length(), limit)
            for (i in 0 until keep) newArr.put(arr.getJSONObject(i))
            prefs(ctx).edit().putString(K_POINTS, newArr.toString()).apply()
        } catch (e: Throwable) {
            LogCat.e("LocationTrackingHelper.appendPoint failed: ${e.message}", e)
        }
    }

    fun listPoints(
        offset: Int = 0,
        limit: Int = 1000,
        fromTs: Long = 0L,
        toTs: Long = 0L,
        ctx: Context = MainApp.instance,
    ): List<Point> {
        val arr = readArray(ctx)
        val out = mutableListOf<Point>()
        var skipped = 0
        var i = 0
        while (i < arr.length() && out.size < limit) {
            try {
                val o = arr.getJSONObject(i)
                val ts = o.optLong("ts")
                val passFrom = fromTs <= 0L || ts >= fromTs
                val passTo = toTs <= 0L || ts <= toTs
                if (passFrom && passTo) {
                    if (skipped < offset) {
                        skipped++
                    } else {
                        out.add(
                            Point(
                                ts = ts,
                                lat = o.optDouble("lat"),
                                lng = o.optDouble("lng"),
                                accuracy = o.optDouble("accuracy", 0.0).toFloat(),
                                speed = o.optDouble("speed", 0.0).toFloat(),
                                altitude = o.optDouble("altitude", 0.0),
                                bearing = o.optDouble("bearing", 0.0).toFloat(),
                                batteryLevel = o.optInt("battery", -1),
                                charging = o.optBoolean("charging", false),
                                provider = o.optString("provider", ""),
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
            }
            i++
        }
        return out
    }

    fun countPointsInRange(fromTs: Long, toTs: Long, ctx: Context = MainApp.instance): Int {
        val arr = readArray(ctx)
        var count = 0
        for (i in 0 until arr.length()) {
            try {
                val ts = arr.getJSONObject(i).optLong("ts")
                if ((fromTs <= 0L || ts >= fromTs) && (toTs <= 0L || ts <= toTs)) count++
            } catch (_: Throwable) {}
        }
        return count
    }

    fun countPoints(ctx: Context = MainApp.instance): Int = readArray(ctx).length()

    fun latestPoint(ctx: Context = MainApp.instance): Point? = listPoints(0, 1, ctx).firstOrNull()

    fun clear(ctx: Context = MainApp.instance) {
        prefs(ctx).edit().remove(K_POINTS).apply()
    }

    private fun readArray(ctx: Context): JSONArray {
        val raw = prefs(ctx).getString(K_POINTS, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
    }

    fun pointFromLocation(loc: Location): Point {
        val (level, charging) = readBattery()
        return Point(
            ts = loc.time,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = loc.accuracy,
            speed = loc.speed,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            batteryLevel = level,
            charging = charging,
            provider = loc.provider ?: "",
        )
    }

    private fun readBattery(): Pair<Int, Boolean> {
        return try {
            val ctx = MainApp.instance
            val intent = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL
                pct to charging
            } else -1 to false
        } catch (_: Throwable) { -1 to false }
    }
}
