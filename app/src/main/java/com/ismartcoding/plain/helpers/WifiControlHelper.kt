package com.ismartcoding.plain.helpers

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.ismartcoding.plain.MainApp

/**
 * Wi-Fi inspection. Scanning currently visible networks and reading the connection
 * state are supported. Reading "saved networks" was restricted starting in Android 8;
 * `getConfiguredNetworks()` returns an empty list for non-system apps. Hotspot toggle
 * also requires system-app status, so we only expose tethering *state* here.
 */
object WifiControlHelper {

    data class Network(
        val ssid: String,
        val bssid: String,
        val capabilities: String,
        val frequencyMhz: Int,
        val rssi: Int,
        val channelWidth: Int,
        val seenMs: Long,
        val isCurrent: Boolean,
    )

    data class State(
        val enabled: Boolean,
        val connectedSsid: String,
        val connectedBssid: String,
        val rssi: Int,
        val linkSpeedMbps: Int,
        val frequencyMhz: Int,
        val ipv4: String,
        val hotspotState: String, // "unknown" | "on" | "off"
        val savedListAccessible: Boolean,
        val canScan: Boolean,
    )

    private fun wm(ctx: Context = MainApp.instance): WifiManager =
        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun state(ctx: Context = MainApp.instance): State {
        val w = wm(ctx)
        val info = try { w.connectionInfo } catch (_: Throwable) { null }
        val ssid = info?.ssid?.removeSurrounding("\"") ?: ""
        val bssid = info?.bssid ?: ""
        val rssi = info?.rssi ?: -127
        val link = info?.linkSpeed ?: 0
        val freq = info?.frequency ?: 0
        val ip = ipv4String(info?.ipAddress ?: 0)
        return State(
            enabled = w.isWifiEnabled,
            connectedSsid = ssid,
            connectedBssid = bssid,
            rssi = rssi,
            linkSpeedMbps = link,
            frequencyMhz = freq,
            ipv4 = ip,
            hotspotState = hotspotState(w),
            savedListAccessible = false,
            canScan = true,
        )
    }

    fun scan(ctx: Context = MainApp.instance): List<Network> {
        val w = wm(ctx)
        val info = try { w.connectionInfo } catch (_: Throwable) { null }
        val currentBssid = info?.bssid
        // Trigger a scan; results are still cached even if startScan() returns false.
        try { @Suppress("DEPRECATION") w.startScan() } catch (_: Throwable) {}
        val list = try { w.scanResults } catch (_: Throwable) { return emptyList() }
        return list.map { it.toModel(currentBssid) }
            .sortedByDescending { it.rssi }
    }

    private fun ScanResult.toModel(currentBssid: String?): Network = Network(
        ssid = SSID ?: "",
        bssid = BSSID ?: "",
        capabilities = capabilities ?: "",
        frequencyMhz = frequency,
        rssi = level,
        channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) channelWidth else 0,
        seenMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) timestamp / 1000 else 0L,
        isCurrent = (BSSID != null && BSSID.equals(currentBssid, ignoreCase = true)),
    )

    private fun ipv4String(ip: Int): String {
        if (ip == 0) return ""
        return "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
    }

    /** Best-effort tethering state via reflection. Returns "unknown" if not accessible. */
    private fun hotspotState(w: WifiManager): String {
        return try {
            val m = w.javaClass.getDeclaredMethod("getWifiApState")
            m.isAccessible = true
            when (m.invoke(w) as? Int) {
                13 -> "on"   // WIFI_AP_STATE_ENABLED
                11 -> "off"  // WIFI_AP_STATE_DISABLED
                else -> "unknown"
            }
        } catch (_: Throwable) { "unknown" }
    }

    /** Toggle Wi-Fi if the OS still permits it (pre-Android 10) — best-effort. */
    fun setWifiEnabled(enabled: Boolean, ctx: Context = MainApp.instance): Boolean {
        return try {
            @Suppress("DEPRECATION")
            wm(ctx).isWifiEnabled = enabled
            true
        } catch (_: Throwable) { false }
    }
}
