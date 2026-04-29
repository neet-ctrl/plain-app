package com.ismartcoding.plain.helpers

import android.content.Context
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * URL log produced by the VPN-mode packet capture service.
 *
 * The VPN service intercepts outgoing TCP/UDP packets and parses out the
 * destination hostname (HTTP `Host:` header for cleartext, TLS Server Name
 * Indication extension for HTTPS), then drops a row in here. Bodies are never
 * stored — only timestamp, host, app uid, port, and protocol.
 *
 * This is a SharedPreferences-backed ring buffer to keep the implementation
 * dependency-free and resilient across process death.
 */
object PacketCaptureHelper {
    private const val PREFS = "plain_packet_log"
    private const val K_ENTRIES = "entries"
    private const val K_ENABLED = "enabled"
    private const val K_RUNNING = "running"
    const val MAX_ENTRIES = 5000

    data class Entry(
        val id: String,
        val ts: Long,
        val host: String,
        val port: Int,
        val protocol: String, // "https" | "http" | "udp" | "tcp"
        val appPackage: String,
        val appLabel: String,
        val sizeBytes: Int,
    )

    data class State(
        val supported: Boolean,
        val enabled: Boolean,
        val running: Boolean,
        val totalEntries: Int,
        val needsConsent: Boolean,
    )

    private fun prefs(ctx: Context = MainApp.instance) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean = prefs(ctx).getBoolean(K_ENABLED, false)
    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    fun isRunning(ctx: Context = MainApp.instance): Boolean = prefs(ctx).getBoolean(K_RUNNING, false)
    fun setRunning(running: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_RUNNING, running).apply()
    }

    @Synchronized
    fun append(host: String, port: Int, protocol: String, appPackage: String, appLabel: String, sizeBytes: Int, ctx: Context = MainApp.instance) {
        if (host.isBlank()) return
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("ts", System.currentTimeMillis())
            put("host", host)
            put("port", port)
            put("protocol", protocol)
            put("appPackage", appPackage)
            put("appLabel", appLabel)
            put("sizeBytes", sizeBytes)
        }
        // Coalesce identical (host, app, protocol) within 1s — high-volume hosts otherwise spam the log.
        val last = if (arr.length() > 0) arr.optJSONObject(0) else null
        val recent = last != null &&
            (System.currentTimeMillis() - last.optLong("ts")) < 1000L &&
            last.optString("host") == host &&
            last.optString("appPackage") == appPackage &&
            last.optString("protocol") == protocol
        val merged = JSONArray()
        merged.put(obj)
        var i = if (recent) 1 else 0
        while (i < arr.length() && merged.length() < MAX_ENTRIES) {
            merged.put(arr.getJSONObject(i)); i++
        }
        prefs(ctx).edit().putString(K_ENTRIES, merged.toString()).apply()
    }

    fun list(offset: Int, limit: Int, hostFilter: String, ctx: Context = MainApp.instance): List<Entry> {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
        val q = hostFilter.trim().lowercase()
        val out = ArrayList<Entry>(limit)
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val host = o.optString("host")
            if (q.isNotEmpty() && !host.lowercase().contains(q)) continue
            if (skipped < offset) { skipped++; continue }
            out.add(
                Entry(
                    id = o.optString("id"),
                    ts = o.optLong("ts"),
                    host = host,
                    port = o.optInt("port"),
                    protocol = o.optString("protocol"),
                    appPackage = o.optString("appPackage"),
                    appLabel = o.optString("appLabel"),
                    sizeBytes = o.optInt("sizeBytes"),
                )
            )
            if (out.size >= limit) break
        }
        return out
    }

    fun count(ctx: Context = MainApp.instance): Int {
        val raw = prefs(ctx).getString(K_ENTRIES, "[]") ?: "[]"
        return try { JSONArray(raw).length() } catch (_: Throwable) { 0 }
    }

    fun clear(ctx: Context = MainApp.instance) {
        prefs(ctx).edit().remove(K_ENTRIES).apply()
    }
}
