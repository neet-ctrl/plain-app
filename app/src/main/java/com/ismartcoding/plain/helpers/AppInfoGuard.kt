package com.ismartcoding.plain.helpers

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.preferences.AppInfoGuardEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference

/**
 * Tracks whether the user has just passed the in-app PIN check that protects
 * PlainApp's own "App info" / application details screen.
 *
 * PlainAccessibilityService detects when the system Settings shows an
 * app-details page and consults this guard before allowing it to remain
 * visible — but ONLY when the page being shown is for our own package
 * (com.ismartcoding.plain).  Other apps' App Info pages are never blocked.
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

    private const val OWN_PACKAGE = "com.ismartcoding.plain"

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

    /**
     * Fast Layer-1 check: inspects already-delivered event text strings (no IPC).
     * Safe on the accessibility service binder thread.
     * [texts] should be pre-extracted from [AccessibilityEvent.getText] as plain
     * String copies before the event is recycled.
     */
    fun isOwnAppInfoPageFast(texts: List<String>): Boolean {
        for (text in texts) {
            if (text.contains(OWN_PACKAGE)) return true
        }
        return false
    }

    /**
     * Full check: Layer-1 (fast text scan) + Layer-2 (accessibility node tree IPC).
     * Must be called from ioScope / background thread — NOT from the service binder
     * thread — because [findAccessibilityNodeInfosByText] is a synchronous IPC call.
     * [source] is the [AccessibilityNodeInfo] captured from [AccessibilityEvent.source]
     * on the service thread; this function recycles it when done.
     */
    fun isOwnAppInfoPageFull(source: AccessibilityNodeInfo?, texts: List<String>): Boolean {
        // Layer 1: fast text scan on already-delivered strings
        for (text in texts) {
            if (text.contains(OWN_PACKAGE)) return true
        }
        // Layer 2: node tree IPC (safe here — we are on a background thread)
        if (source == null) return false
        return try {
            val nodes = source.findAccessibilityNodeInfosByText(OWN_PACKAGE)
            val found = nodes.isNotEmpty()
            nodes.forEach { n -> try { n.recycle() } catch (_: Throwable) {} }
            found
        } catch (_: Throwable) {
            false
        } finally {
            try { source.recycle() } catch (_: Throwable) {}
        }
    }

    /**
     * Legacy full check operating directly on an [AccessibilityEvent] —
     * kept for callers that already hold the event. Recycles the source node.
     * Must NOT be called on the accessibility service binder thread.
     */
    fun isOwnAppInfoPage(event: AccessibilityEvent): Boolean {
        val texts = try { event.text.map { it?.toString().orEmpty() } } catch (_: Throwable) { emptyList() }
        val source = try { event.source } catch (_: Throwable) { null }
        return isOwnAppInfoPageFull(source, texts)
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
            cn.contains("appinfo\$")
    }

    private fun isSettingsLikePackage(pkg: String): Boolean {
        return pkg == "com.android.settings" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.samsung.android.settings" ||
            pkg.endsWith(".settings") ||
            pkg.contains(".settings.")
    }
}
