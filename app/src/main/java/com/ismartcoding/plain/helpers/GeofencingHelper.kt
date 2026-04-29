package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.ismartcoding.lib.helpers.StringHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent registry of user-defined geofences plus the event/action log.
 *
 * Each fence is a circular region (lat, lng, radius in meters) with:
 *  - id, name, color, enabled
 *  - triggers: enter / exit (booleans)
 *  - actions: recordAudio (sec), notifyWeb, lockApps (list of pkg ids), customNote
 *  - lockApps duration seconds (0 = until manually released)
 *  - currentlyInside (transient state, recomputed at runtime)
 *
 * Stored in a single SharedPreferences file as JSON.
 */
object GeofencingHelper {
    private const val PREFS = "plain_geofences"
    private const val K_FENCES = "fences"
    private const val K_EVENTS = "events"
    private const val K_INSIDE = "inside_state"
    private const val K_AUDIOS = "audios"
    private const val MAX_EVENTS = 1000
    private const val MAX_AUDIOS = 500

    data class Geofence(
        val id: String,
        var name: String,
        var lat: Double,
        var lng: Double,
        var radius: Double,
        var color: String = "#6366f1",
        var enabled: Boolean = true,
        var triggerEnter: Boolean = true,
        var triggerExit: Boolean = true,
        var actionRecordAudio: Boolean = false,
        var recordAudioSec: Int = 30,
        var actionNotifyWeb: Boolean = true,
        var actionLockApps: Boolean = false,
        var lockedAppIds: List<String> = emptyList(),
        var lockAppsDurationSec: Int = 0,
        var customNote: String = "",
        var createdAt: Long = System.currentTimeMillis(),
    )

    data class GeofenceEvent(
        val id: String,
        val geofenceId: String,
        val geofenceName: String,
        val type: String,             // "enter" | "exit"
        val ts: Long,
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val batteryLevel: Int,
        val recordingFile: String,    // empty if no recording
        val recordingDurationMs: Long,
        val notifiedWeb: Boolean,
        val lockedApps: List<String>,
    )

    data class GeofenceAudio(
        val id: String,
        val geofenceId: String,
        val geofenceName: String,
        val eventId: String,
        val ts: Long,
        val durationMs: Long,
        val sizeBytes: Long,
        val absPath: String,
    )

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Fences ----------

    fun listFences(ctx: Context = MainApp.instance): List<Geofence> {
        val raw = prefs(ctx).getString(K_FENCES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val out = mutableListOf<Geofence>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(parseFence(o))
            } catch (_: Throwable) {}
        }
        return out
    }

    fun getFence(id: String, ctx: Context = MainApp.instance): Geofence? =
        listFences(ctx).firstOrNull { it.id == id }

    fun saveFence(g: Geofence, ctx: Context = MainApp.instance): Geofence {
        val list = listFences(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == g.id }
        if (idx >= 0) list[idx] = g else list.add(g)
        writeFences(list, ctx)
        return g
    }

    fun newFence(
        name: String,
        lat: Double,
        lng: Double,
        radius: Double,
        ctx: Context = MainApp.instance
    ): Geofence {
        val g = Geofence(
            id = StringHelper.shortUUID(),
            name = name,
            lat = lat,
            lng = lng,
            radius = radius,
        )
        return saveFence(g, ctx)
    }

    fun deleteFence(id: String, ctx: Context = MainApp.instance) {
        val list = listFences(ctx).filterNot { it.id == id }
        writeFences(list, ctx)
        // also wipe its inside state and (optionally) keep events for history
        val ins = readInside(ctx).toMutableMap()
        ins.remove(id)
        writeInside(ins, ctx)
    }

    private fun writeFences(list: List<Geofence>, ctx: Context) {
        val arr = JSONArray()
        list.forEach { arr.put(serializeFence(it)) }
        prefs(ctx).edit().putString(K_FENCES, arr.toString()).apply()
    }

    private fun serializeFence(g: Geofence): JSONObject = JSONObject().apply {
        put("id", g.id)
        put("name", g.name)
        put("lat", g.lat)
        put("lng", g.lng)
        put("radius", g.radius)
        put("color", g.color)
        put("enabled", g.enabled)
        put("triggerEnter", g.triggerEnter)
        put("triggerExit", g.triggerExit)
        put("actionRecordAudio", g.actionRecordAudio)
        put("recordAudioSec", g.recordAudioSec)
        put("actionNotifyWeb", g.actionNotifyWeb)
        put("actionLockApps", g.actionLockApps)
        put("lockedAppIds", JSONArray(g.lockedAppIds))
        put("lockAppsDurationSec", g.lockAppsDurationSec)
        put("customNote", g.customNote)
        put("createdAt", g.createdAt)
    }

    private fun parseFence(o: JSONObject): Geofence {
        val locked = mutableListOf<String>()
        val arr = o.optJSONArray("lockedAppIds")
        if (arr != null) for (i in 0 until arr.length()) locked.add(arr.optString(i, ""))
        return Geofence(
            id = o.optString("id"),
            name = o.optString("name", "Geofence"),
            lat = o.optDouble("lat"),
            lng = o.optDouble("lng"),
            radius = o.optDouble("radius", 100.0),
            color = o.optString("color", "#6366f1"),
            enabled = o.optBoolean("enabled", true),
            triggerEnter = o.optBoolean("triggerEnter", true),
            triggerExit = o.optBoolean("triggerExit", true),
            actionRecordAudio = o.optBoolean("actionRecordAudio", false),
            recordAudioSec = o.optInt("recordAudioSec", 30),
            actionNotifyWeb = o.optBoolean("actionNotifyWeb", true),
            actionLockApps = o.optBoolean("actionLockApps", false),
            lockedAppIds = locked.filter { it.isNotEmpty() },
            lockAppsDurationSec = o.optInt("lockAppsDurationSec", 0),
            customNote = o.optString("customNote", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    // ---------- Inside state ----------

    fun readInside(ctx: Context = MainApp.instance): Map<String, Boolean> {
        val raw = prefs(ctx).getString(K_INSIDE, "{}") ?: "{}"
        val o = try { JSONObject(raw) } catch (_: Throwable) { JSONObject() }
        val out = mutableMapOf<String, Boolean>()
        o.keys().forEach { k -> out[k] = o.optBoolean(k, false) }
        return out
    }

    fun writeInside(map: Map<String, Boolean>, ctx: Context = MainApp.instance) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs(ctx).edit().putString(K_INSIDE, o.toString()).apply()
    }

    fun isInside(g: Geofence, lat: Double, lng: Double): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(g.lat, g.lng, lat, lng, results)
        return results[0] <= g.radius
    }

    // ---------- Events ----------

    fun appendEvent(e: GeofenceEvent, ctx: Context = MainApp.instance) {
        try {
            val raw = prefs(ctx).getString(K_EVENTS, "[]") ?: "[]"
            val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
            val obj = JSONObject().apply {
                put("id", e.id)
                put("geofenceId", e.geofenceId)
                put("geofenceName", e.geofenceName)
                put("type", e.type)
                put("ts", e.ts)
                put("lat", e.lat)
                put("lng", e.lng)
                put("accuracy", e.accuracy.toDouble())
                put("batteryLevel", e.batteryLevel)
                put("recordingFile", e.recordingFile)
                put("recordingDurationMs", e.recordingDurationMs)
                put("notifiedWeb", e.notifiedWeb)
                put("lockedApps", JSONArray(e.lockedApps))
            }
            val newArr = JSONArray()
            newArr.put(obj)
            val keep = minOf(arr.length(), MAX_EVENTS - 1)
            for (i in 0 until keep) newArr.put(arr.getJSONObject(i))
            prefs(ctx).edit().putString(K_EVENTS, newArr.toString()).apply()
        } catch (e2: Throwable) {
            LogCat.e("GeofencingHelper.appendEvent failed: ${e2.message}", e2)
        }
    }

    fun listEvents(offset: Int = 0, limit: Int = 200, geofenceId: String = "", ctx: Context = MainApp.instance): List<GeofenceEvent> {
        val raw = prefs(ctx).getString(K_EVENTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val out = mutableListOf<GeofenceEvent>()
        var i = 0
        var skipped = 0
        while (i < arr.length() && out.size < limit) {
            try {
                val o = arr.getJSONObject(i)
                if (geofenceId.isEmpty() || o.optString("geofenceId") == geofenceId) {
                    if (skipped < offset) skipped++
                    else {
                        val locked = mutableListOf<String>()
                        val la = o.optJSONArray("lockedApps")
                        if (la != null) for (k in 0 until la.length()) locked.add(la.optString(k, ""))
                        out.add(
                            GeofenceEvent(
                                id = o.optString("id"),
                                geofenceId = o.optString("geofenceId"),
                                geofenceName = o.optString("geofenceName"),
                                type = o.optString("type"),
                                ts = o.optLong("ts"),
                                lat = o.optDouble("lat"),
                                lng = o.optDouble("lng"),
                                accuracy = o.optDouble("accuracy", 0.0).toFloat(),
                                batteryLevel = o.optInt("batteryLevel", -1),
                                recordingFile = o.optString("recordingFile"),
                                recordingDurationMs = o.optLong("recordingDurationMs", 0),
                                notifiedWeb = o.optBoolean("notifiedWeb", false),
                                lockedApps = locked,
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
            i++
        }
        return out
    }

    // ---------- Audio recordings ----------

    fun appendAudio(a: GeofenceAudio, ctx: Context = MainApp.instance) {
        try {
            val raw = prefs(ctx).getString(K_AUDIOS, "[]") ?: "[]"
            val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
            val obj = JSONObject().apply {
                put("id", a.id)
                put("geofenceId", a.geofenceId)
                put("geofenceName", a.geofenceName)
                put("eventId", a.eventId)
                put("ts", a.ts)
                put("durationMs", a.durationMs)
                put("sizeBytes", a.sizeBytes)
                put("absPath", a.absPath)
            }
            val newArr = JSONArray()
            newArr.put(obj)
            val keep = minOf(arr.length(), MAX_AUDIOS - 1)
            for (i in 0 until keep) newArr.put(arr.getJSONObject(i))
            prefs(ctx).edit().putString(K_AUDIOS, newArr.toString()).apply()
        } catch (e: Throwable) {
            LogCat.e("GeofencingHelper.appendAudio failed: ${e.message}", e)
        }
    }

    fun listAudios(offset: Int = 0, limit: Int = 200, geofenceId: String = "", ctx: Context = MainApp.instance): List<GeofenceAudio> {
        val raw = prefs(ctx).getString(K_AUDIOS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val out = mutableListOf<GeofenceAudio>()
        var skipped = 0
        var i = 0
        while (i < arr.length() && out.size < limit) {
            try {
                val o = arr.getJSONObject(i)
                if (geofenceId.isEmpty() || o.optString("geofenceId") == geofenceId) {
                    if (skipped < offset) skipped++
                    else {
                        out.add(
                            GeofenceAudio(
                                id = o.optString("id"),
                                geofenceId = o.optString("geofenceId"),
                                geofenceName = o.optString("geofenceName"),
                                eventId = o.optString("eventId"),
                                ts = o.optLong("ts"),
                                durationMs = o.optLong("durationMs"),
                                sizeBytes = o.optLong("sizeBytes"),
                                absPath = o.optString("absPath"),
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
            i++
        }
        return out
    }

    fun getAudio(id: String, ctx: Context = MainApp.instance): GeofenceAudio? {
        val raw = prefs(ctx).getString(K_AUDIOS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    return GeofenceAudio(
                        id = o.optString("id"),
                        geofenceId = o.optString("geofenceId"),
                        geofenceName = o.optString("geofenceName"),
                        eventId = o.optString("eventId"),
                        ts = o.optLong("ts"),
                        durationMs = o.optLong("durationMs"),
                        sizeBytes = o.optLong("sizeBytes"),
                        absPath = o.optString("absPath"),
                    )
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun deleteAudio(id: String, ctx: Context = MainApp.instance): Boolean {
        val raw = prefs(ctx).getString(K_AUDIOS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val newArr = JSONArray()
        var deleted = false
        var deletedPath = ""
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                deleted = true
                deletedPath = o.optString("absPath")
            } else newArr.put(o)
        }
        if (deleted) {
            prefs(ctx).edit().putString(K_AUDIOS, newArr.toString()).apply()
            try { if (deletedPath.isNotEmpty()) java.io.File(deletedPath).delete() } catch (_: Throwable) {}
        }
        return deleted
    }

    fun audiosDir(ctx: Context = MainApp.instance): java.io.File {
        val d = java.io.File(ctx.filesDir, ".PlainPrivate/GeofenceAudio")
        if (!d.exists()) d.mkdirs()
        try { java.io.File(d, ".nomedia").createNewFile() } catch (_: Throwable) {}
        return d
    }
}
