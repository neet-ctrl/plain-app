package com.neet.tracker.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {
    private const val PREFS         = "neet_cache_prefs"
    private const val KEY_AUTO      = "auto_cache_clear_enabled"
    private const val KEY_DAYS      = "auto_cache_clear_days"
    private const val KEY_LAST      = "last_cache_clear_time"

    fun isAutoClearEnabled(ctx: Context) =
        prefs(ctx).getBoolean(KEY_AUTO, false)

    fun setAutoClearEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AUTO, v).apply()

    fun getAutoClearDays(ctx: Context) =
        prefs(ctx).getInt(KEY_DAYS, 7)

    fun setAutoClearDays(ctx: Context, days: Int) =
        prefs(ctx).edit().putInt(KEY_DAYS, days).apply()

    fun getLastClearTime(ctx: Context) =
        prefs(ctx).getLong(KEY_LAST, 0L)

    fun shouldAutoClean(ctx: Context): Boolean {
        if (!isAutoClearEnabled(ctx)) return false
        val ms = getAutoClearDays(ctx) * 86_400_000L
        return System.currentTimeMillis() - getLastClearTime(ctx) > ms
    }

    suspend fun getCacheSize(ctx: Context): Long = withContext(Dispatchers.IO) {
        dirSize(ctx.cacheDir) + dirSize(File(ctx.filesDir, "uploads"))
    }

    suspend fun clearCache(ctx: Context) = withContext(Dispatchers.IO) {
        deleteDir(ctx.cacheDir)
        prefs(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().sumOf { it.length() }
    }

    private fun deleteDir(dir: File) {
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { if (it != dir) it.delete() }
    }

    fun formatSize(bytes: Long) = when {
        bytes < 1_024L               -> "$bytes B"
        bytes < 1_048_576L           -> "%.1f KB".format(bytes / 1_024f)
        bytes < 1_073_741_824L       -> "%.1f MB".format(bytes / 1_048_576f)
        else                         -> "%.2f GB".format(bytes / 1_073_741_824f)
    }

    fun formatLastClear(time: Long): String {
        if (time == 0L) return "Never"
        val diff  = System.currentTimeMillis() - time
        val mins  = diff / 60_000
        val hours = diff / 3_600_000
        val days  = diff / 86_400_000
        return when {
            mins  < 1  -> "Just now"
            mins  < 60 -> "$mins min ago"
            hours < 24 -> "$hours hr ago"
            days  == 1L -> "Yesterday"
            else       -> "$days days ago"
        }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
