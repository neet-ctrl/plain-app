package com.ismartcoding.plain.helpers

import android.content.Context
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.preferences.AppInfoGuardEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference

/**
 * Guards ALL "App info" / App permissions pages behind the PlainApp PIN.
 *
 * PlainAccessibilityService detects when the system Settings shows any
 * app-details page and consults this guard before allowing it to remain
 * visible.
 *
 * Threading rules (critical for accessibility service health):
 *  - [isActiveCached] reads only volatile memory — SAFE to call on the
 *    accessibility service thread (binder callback). Never does I/O.
 *  - [refreshCache] is a suspend function — call it from ioScope/coIO only.
 *    The enforcementRunnable in PlainAccessibilityService calls it every 5 s
 *    so the cache is always warm.
 *  - [isActive] (blocking via runBlocking) must NEVER be called from the
 *    accessibility service thread. It is kept for non-service callers only.
 */
object AppInfoGuard {
    private const val VALID_WINDOW_MS = 30_000L
    private const val CACHE_TTL_MS = 5_000L

    @Volatile private var verifiedAt: Long = 0L
    @Volatile private var cachedActive: Boolean = false
    @Volatile private var cachedAt: Long = 0L

    fun markVerified() {
        verifiedAt = System.currentTimeMillis()
    }

    fun clear() {
        verifiedAt = 0L
    }

    fun isRecentlyVerified(): Boolean {
        val ts = verifiedAt
        if (ts <= 0L) return false
        return System.currentTimeMillis() - ts in 0..VALID_WINDOW_MS
    }

    fun invalidateCache() {
        cachedAt = 0L
    }

    /**
     * Pure in-memory read — ZERO I/O. Safe to call on the accessibility service
     * binder thread inside [onAccessibilityEvent]. Returns the last value written
     * by [refreshCache]. Defaults to false until the first [refreshCache] call.
     */
    fun isActiveCached(): Boolean = cachedActive

    /**
     * Suspend version — safe to call from ioScope / coIO. Updates the in-memory
     * cache so subsequent [isActiveCached] calls see fresh values.
     */
    suspend fun refreshCache(context: Context = MainApp.instance) {
        val v = try {
            AppInfoGuardEnabledPreference.getAsync(context) &&
                AppLockPinPreference.getAsync(context).isNotEmpty()
        } catch (_: Throwable) { false }
        cachedActive = v
        cachedAt = System.currentTimeMillis()
    }

    /**
     * Blocking version — kept for non-service callers (e.g. GraphQL mutations).
     * MUST NOT be called from the accessibility service binder thread.
     */
    fun isActive(context: Context = MainApp.instance): Boolean {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_TTL_MS) return cachedActive
        val v = try {
            kotlinx.coroutines.runBlocking {
                AppInfoGuardEnabledPreference.getAsync(context) &&
                    AppLockPinPreference.getAsync(context).isNotEmpty()
            }
        } catch (_: Throwable) { false }
        cachedActive = v
        cachedAt = now
        return v
    }

    fun looksLikeAppInfoScreen(packageName: String?, className: String?): Boolean {
        if (packageName == null || className == null) return false
        if (!isSettingsLikePackage(packageName)) return false
        val cn = className.lowercase()
        return cn.contains("installedappdetails") ||
            cn.contains("appinfodashboard") ||
            cn.contains("applicationinfo") ||
            cn.contains("appinfoactivity") ||
            cn.contains("appdetailsactivity") ||
            cn.contains("appinfo\$") ||
            cn.contains("applicationdetails") ||
            cn.contains("applicationsdetails") ||
            cn.contains("apppermission") ||
            cn.contains("appmanager") ||
            cn.contains("appsdetail") ||
            cn.contains("appdetail") ||
            cn.contains("permissiondetails") ||
            cn.contains("manageapplication")
    }

    private fun isSettingsLikePackage(pkg: String): Boolean {
        return pkg == "com.android.settings" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.miui.appmanager" ||
            pkg == "com.samsung.android.settings" ||
            pkg == "com.coloros.safecenter" ||
            pkg == "com.vivo.permissionmanager" ||
            pkg == "com.oppo.safe" ||
            pkg == "com.huawei.systemmanager" ||
            pkg == "com.honor.systemmanager" ||
            pkg.endsWith(".settings") ||
            pkg.contains(".settings.") ||
            pkg.contains(".securitycenter") ||
            pkg.contains(".appmanager")
    }
}
