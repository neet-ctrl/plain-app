package com.ismartcoding.plain.helpers

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import kotlinx.serialization.Serializable

@Serializable
data class StorageBreakdown(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val appsBytes: Long,
    val imagesBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val documentsBytes: Long,
    val otherBytes: Long,
    val cacheBytes: Long,
)

@Serializable
data class RunningProcess(
    val pid: Int,
    val processName: String,
    val appLabel: String,
    val packageName: String,
    val importance: Int,
    val importanceLabel: String,
    val rssKb: Long,
)

@Serializable
data class NetworkInterface(
    val name: String,
    val addresses: List<String>,
    val isUp: Boolean,
    val isLoopback: Boolean,
)

object SystemControlHelper {

    // ---------- DND ----------
    fun getDndMode(): Int {
        val nm = MainApp.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter
    }

    fun setDndMode(mode: Int): Boolean {
        val nm = MainApp.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(mode)
            true
        } else {
            false
        }
    }

    // ---------- Airplane Mode ----------
    fun getAirplaneMode(): Boolean {
        return Settings.Global.getInt(
            MainApp.instance.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    fun setAirplaneMode(enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                MainApp.instance.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", enabled)
            MainApp.instance.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            LogCat.e("setAirplaneMode: ${e.message}")
            false
        }
    }

    // ---------- Hotspot ----------
    fun getHotspotEnabled(): Boolean {
        return try {
            val wm = MainApp.instance.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wm.javaClass.getMethod("isWifiApEnabled")
            method.invoke(wm) as Boolean
        } catch (e: Exception) { false }
    }

    fun setHotspotEnabled(enabled: Boolean): Boolean {
        val ctx = MainApp.instance
        // Android 8+ — deprecated WifiManager reflection is removed; use ConnectivityManager tethering
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return try {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    // Android 10+: TetheringManager
                    val tmClass = Class.forName("android.net.TetheringManager")
                    val tm = ctx.getSystemService(tmClass)
                    if (enabled) {
                        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
                        val builder = builderClass.getConstructor(Int::class.java).newInstance(0)
                        val request = builderClass.getMethod("build").invoke(builder)
                        tmClass.getMethod(
                            "startTethering",
                            Class.forName("android.net.TetheringManager\$TetheringRequest"),
                            java.util.concurrent.Executor::class.java,
                            Class.forName("android.net.TetheringManager\$StartTetheringCallback"),
                        ).invoke(tm, request, java.util.concurrent.Executors.newSingleThreadExecutor(), null)
                    } else {
                        tmClass.getMethod("stopTethering", Int::class.java).invoke(tm, 0)
                    }
                } else {
                    // Android 8-9: ConnectivityManager hidden API
                    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    if (enabled) {
                        cm.javaClass.getMethod(
                            "startTethering", Int::class.java, Boolean::class.java,
                            Class.forName("android.net.ConnectivityManager\$OnStartTetheringCallback"),
                            android.os.Handler::class.java,
                        ).invoke(cm, 0, true, null, null)
                    } else {
                        cm.javaClass.getMethod("stopTethering", Int::class.java).invoke(cm, 0)
                    }
                }
                true
            } catch (e: Exception) {
                LogCat.e("setHotspot (8+): ${e.message}")
                false
            }
        }
        // Android 7 and below: deprecated reflection still works
        return try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val method = wm.javaClass.getMethod("setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java, Boolean::class.java)
            method.invoke(wm, null, enabled) as Boolean
        } catch (e: Exception) {
            LogCat.e("setHotspot (legacy): ${e.message}")
            false
        }
    }

    // ---------- Lock Screen ----------
    fun lockScreen() {
        try {
            val km = MainApp.instance.getSystemService(Context.KEYGUARD_SERVICE)
                as android.app.KeyguardManager
            @Suppress("DEPRECATION")
            if (!km.isKeyguardLocked) {
                val pm = MainApp.instance.getSystemService(Context.POWER_SERVICE)
                    as android.os.PowerManager
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK
                    or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "PlainApp:lockscreen")
                wl.acquire(100)
                wl.release()
            }
            val dpm = MainApp.instance.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as android.app.admin.DevicePolicyManager
            dpm.lockNow()
        } catch (e: Exception) {
            LogCat.e("lockScreen: ${e.message}")
            // fallback: send KEYCODE_POWER broadcast
        }
    }

    // ---------- Reboot ----------
    fun reboot() {
        try {
            val pm = MainApp.instance.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.reboot(null)
        } catch (e: Exception) {
            LogCat.e("reboot: ${e.message}")
            try { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")) } catch (_: Exception) {}
        }
    }

    // ---------- Clipboard ----------
    fun getClipboardText(): String {
        return try {
            val cm = MainApp.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(MainApp.instance)?.toString() ?: ""
        } catch (e: Exception) { "" }
    }

    fun setClipboardText(text: String) {
        val cm = MainApp.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PlainApp", text))
    }

    fun clearClipboard() {
        val cm = MainApp.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    // ---------- Storage Analyzer ----------
    fun getStorageBreakdown(): StorageBreakdown {
        val ctx = MainApp.instance
        val ext = Environment.getExternalStorageDirectory()
        val stat = StatFs(ext.path)
        val blockSize = stat.blockSizeLong
        val total = stat.blockCountLong * blockSize
        val free = stat.availableBlocksLong * blockSize

        // Category sizes via MediaStore
        fun querySize(uri: android.net.Uri, sizeCol: String): Long {
            var size = 0L
            try {
                ctx.contentResolver.query(uri, arrayOf(sizeCol), null, null, null)?.use { c ->
                    val idx = c.getColumnIndexOrThrow(sizeCol)
                    while (c.moveToNext()) size += c.getLong(idx)
                }
            } catch (_: Exception) {}
            return size
        }
        val images = querySize(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, android.provider.MediaStore.Images.Media.SIZE)
        val videos = querySize(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, android.provider.MediaStore.Video.Media.SIZE)
        val audio = querySize(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, android.provider.MediaStore.Audio.Media.SIZE)

        // Cache size
        var cacheBytes = ctx.cacheDir.walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
        ctx.externalCacheDir?.walkTopDown()?.forEach { if (it.isFile) cacheBytes += it.length() }

        // Apps: data/app usage approx
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val packs = ctx.packageManager.getInstalledPackages(0)
        var appsBytes = 0L
        for (pkg in packs) {
            try {
                val ai = pkg.applicationInfo ?: continue
                val src = java.io.File(ai.sourceDir)
                if (src.exists()) appsBytes += src.length()
            } catch (_: Exception) {}
        }

        val used = total - free
        val other = maxOf(0L, used - images - videos - audio - appsBytes - cacheBytes)
        return StorageBreakdown(total, used, free, appsBytes, images, videos, audio, 0L, other, cacheBytes)
    }

    // ---------- Process Manager ----------
    fun getRunningProcesses(): List<RunningProcess> {
        val ctx = MainApp.instance
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.packageManager
        val list = mutableListOf<RunningProcess>()
        try {
            val running = am.runningAppProcesses ?: return list
            for (proc in running) {
                val label = try {
                    val pkg = proc.pkgList?.firstOrNull() ?: proc.processName
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { proc.processName }
                val importanceLabel = when (proc.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
                    else -> "Background"
                }
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                val pids = intArrayOf(proc.pid)
                val mem = am.getProcessMemoryInfo(pids)
                val rss = mem?.firstOrNull()?.totalPss?.toLong() ?: 0L
                list.add(RunningProcess(
                    pid = proc.pid,
                    processName = proc.processName,
                    appLabel = label,
                    packageName = proc.pkgList?.firstOrNull() ?: proc.processName,
                    importance = proc.importance,
                    importanceLabel = importanceLabel,
                    rssKb = rss,
                ))
            }
        } catch (e: Exception) {
            LogCat.e("getRunningProcesses: ${e.message}")
        }
        return list.sortedBy { it.importance }
    }

    fun killProcess(pid: Int): Boolean {
        return try {
            android.os.Process.killProcess(pid)
            true
        } catch (e: Exception) {
            LogCat.e("killProcess: ${e.message}")
            false
        }
    }

    // ---------- Network Interfaces ----------
    fun getNetworkInterfaces(): List<NetworkInterface> {
        val list = mutableListOf<NetworkInterface>()
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return list
            for (iface in ifaces.iterator()) {
                val addrs = iface.inetAddresses.toList()
                    .filter { !it.isLoopbackAddress || iface.isLoopback }
                    .map { it.hostAddress ?: "" }
                    .filter { it.isNotEmpty() }
                list.add(NetworkInterface(iface.name, addrs, iface.isUp, iface.isLoopback))
            }
        } catch (e: Exception) { LogCat.e("getNetworkInterfaces: ${e.message}") }
        return list
    }

}
