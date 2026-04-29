package com.ismartcoding.plain.helpers

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import com.ismartcoding.plain.MainApp
import java.util.Calendar

/**
 * Per-app network usage via NetworkStatsManager.
 *
 * Note: requires PACKAGE_USAGE_STATS to be granted via Settings -> Apps -> Special access.
 * Without it, queries return 0 bytes. We expose `usageAccessGranted` so the UI can
 * surface a clear hint. We do NOT have access to remote IPs at the OS layer; that
 * level of detail requires the VPN-based packet capture (see PacketCaptureHelper).
 */
object NetworkUsageHelper {

    data class AppUsage(
        val packageName: String,
        val label: String,
        val rxBytes: Long,
        val txBytes: Long,
        val rxBytesWifi: Long,
        val txBytesWifi: Long,
        val rxBytesMobile: Long,
        val txBytesMobile: Long,
    )

    data class UsageWindow(
        val sinceMs: Long,
        val untilMs: Long,
        val totalRx: Long,
        val totalTx: Long,
        val apps: List<AppUsage>,
        val usageAccessGranted: Boolean,
    )

    fun usageAccessGranted(ctx: Context = MainApp.instance): Boolean {
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName,
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) { false }
    }

    /**
     * @param windowDays days back from now (1, 7, 30…)
     */
    fun query(windowDays: Int, ctx: Context = MainApp.instance): UsageWindow {
        val until = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = until
        cal.add(Calendar.DAY_OF_YEAR, -windowDays)
        val since = cal.timeInMillis
        val granted = usageAccessGranted(ctx)
        if (!granted) {
            return UsageWindow(since, until, 0L, 0L, emptyList(), false)
        }
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = ctx.packageManager
        val perUid = HashMap<Int, LongArray>(64) // [rxWifi, txWifi, rxMobile, txMobile]

        fun bucketize(type: Int, isMobile: Boolean) {
            try {
                val stats: NetworkStats = nsm.querySummary(type, null, since, until)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    val arr = perUid.getOrPut(uid) { LongArray(4) }
                    if (isMobile) { arr[2] += bucket.rxBytes; arr[3] += bucket.txBytes }
                    else { arr[0] += bucket.rxBytes; arr[1] += bucket.txBytes }
                }
                stats.close()
            } catch (_: Throwable) { /* ignore — some OEMs throw */ }
        }
        bucketize(ConnectivityManager.TYPE_WIFI, false)
        bucketize(ConnectivityManager.TYPE_MOBILE, true)

        val out = ArrayList<AppUsage>(perUid.size)
        var totalRx = 0L
        var totalTx = 0L
        for ((uid, arr) in perUid) {
            val rxW = arr[0]; val txW = arr[1]; val rxM = arr[2]; val txM = arr[3]
            val rx = rxW + rxM; val tx = txW + txM
            if (rx == 0L && tx == 0L) continue
            val pkgs = pm.getPackagesForUid(uid)
            val pkg = when {
                pkgs.isNullOrEmpty() && uid == 0 -> "system"
                pkgs.isNullOrEmpty() -> "uid:$uid"
                else -> pkgs[0]
            }
            val label = labelOf(pm, pkg)
            out.add(AppUsage(pkg, label, rx, tx, rxW, txW, rxM, txM))
            totalRx += rx; totalTx += tx
        }
        out.sortByDescending { it.rxBytes + it.txBytes }
        return UsageWindow(since, until, totalRx, totalTx, out, true)
    }

    private fun labelOf(pm: PackageManager, pkg: String): String {
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            ai.loadLabel(pm).toString().ifEmpty { pkg }
        } catch (_: PackageManager.NameNotFoundException) { pkg }
        catch (_: Throwable) { pkg }
    }

    fun activeNetworkType(ctx: Context = MainApp.instance): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return "none"
            val caps = cm.getNetworkCapabilities(n) ?: return "unknown"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }
        } catch (_: Throwable) { "unknown" }
    }
}
