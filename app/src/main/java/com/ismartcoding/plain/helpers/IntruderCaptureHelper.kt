package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object IntruderCaptureHelper {

    private const val PREFS = "plain_intruder_captures"
    private const val K_ENTRIES = "entries"
    private const val MAX_ENTRIES = 500

    object Trigger {
        const val PER_APP_LOCK = "per_app_lock"
        const val APP_PIN = "app_pin"
        const val SECURITY_QA = "security_qa"
        const val TELEGRAM_BOT = "telegram_bot"
    }

    data class Capture(
        val id: String,
        val timestamp: Long,
        val trigger: String,
        val triggerDetail: String,
        val absPath: String,
        val lat: Double,
        val lng: Double,
        val hasLocation: Boolean,
    )

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun capturesDir(ctx: Context = MainApp.instance): File {
        val root = File(ctx.filesDir, ".PlainPrivate").apply { if (!exists()) mkdirs() }
        try { File(root, ".nomedia").createNewFile() } catch (_: Throwable) {}
        val dir = File(root, "IntruderCaptures").apply { if (!exists()) mkdirs() }
        try { File(dir, ".nomedia").createNewFile() } catch (_: Throwable) {}
        return dir
    }

    @Synchronized
    fun save(
        trigger: String,
        triggerDetail: String,
        photoFile: File?,
        lat: Double = 0.0,
        lng: Double = 0.0,
        hasLocation: Boolean = false,
        ctx: Context = MainApp.instance,
    ): Capture? {
        return try {
            val id = UUID.randomUUID().toString()
            val ts = System.currentTimeMillis()
            val dir = capturesDir(ctx)
            var finalPath = ""
            if (photoFile != null && photoFile.exists() && photoFile.length() > 0) {
                val dest = File(dir, "intruder_${ts}_${id.substring(0, 8)}.jpg")
                if (!photoFile.renameTo(dest)) {
                    photoFile.copyTo(dest, overwrite = true)
                    photoFile.delete()
                }
                finalPath = dest.absolutePath
            }
            val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
            val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
            val obj = JSONObject().apply {
                put("id", id)
                put("ts", ts)
                put("trigger", trigger)
                put("detail", triggerDetail)
                put("path", finalPath)
                put("lat", lat)
                put("lng", lng)
                put("hasLoc", hasLocation)
            }
            val merged = JSONArray()
            merged.put(obj)
            for (i in 0 until arr.length()) merged.put(arr.get(i))
            if (merged.length() > MAX_ENTRIES) {
                for (i in MAX_ENTRIES until merged.length()) {
                    try {
                        val p = merged.getJSONObject(i).optString("path")
                        if (p.isNotEmpty()) File(p).delete()
                    } catch (_: Throwable) {}
                }
                val pruned = JSONArray()
                for (i in 0 until MAX_ENTRIES) pruned.put(merged.get(i))
                prefs(ctx).edit().putString(K_ENTRIES, pruned.toString()).apply()
            } else {
                prefs(ctx).edit().putString(K_ENTRIES, merged.toString()).apply()
            }
            Capture(id, ts, trigger, triggerDetail, finalPath, lat, lng, hasLocation)
        } catch (_: Throwable) { null }
    }

    fun list(ctx: Context = MainApp.instance): List<Capture> {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val result = mutableListOf<Capture>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                result.add(Capture(
                    id = o.optString("id"),
                    timestamp = o.optLong("ts"),
                    trigger = o.optString("trigger"),
                    triggerDetail = o.optString("detail"),
                    absPath = o.optString("path"),
                    lat = o.optDouble("lat", 0.0),
                    lng = o.optDouble("lng", 0.0),
                    hasLocation = o.optBoolean("hasLoc", false),
                ))
            } catch (_: Throwable) {}
        }
        return result
    }

    fun count(ctx: Context = MainApp.instance): Int {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        return try { JSONArray(raw).length() } catch (_: Throwable) { 0 }
    }

    @Synchronized
    fun deleteByIds(ids: List<String>, ctx: Context = MainApp.instance): Int {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val idSet = ids.toSet()
        val newArr = JSONArray()
        var removed = 0
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                if (idSet.contains(o.optString("id"))) {
                    removed++
                    val p = o.optString("path")
                    if (p.isNotEmpty()) try { File(p).delete() } catch (_: Throwable) {}
                } else {
                    newArr.put(o)
                }
            } catch (_: Throwable) {}
        }
        prefs(ctx).edit().putString(K_ENTRIES, newArr.toString()).apply()
        return removed
    }

    @Synchronized
    fun clearAll(ctx: Context = MainApp.instance): Int {
        val cnt = count(ctx)
        try {
            capturesDir(ctx).listFiles()?.forEach { f ->
                if (f.name != ".nomedia") f.delete()
            }
        } catch (_: Throwable) {}
        prefs(ctx).edit().putString(K_ENTRIES, "[]").apply()
        return cnt
    }
}
