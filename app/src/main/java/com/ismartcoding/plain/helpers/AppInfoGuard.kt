package com.ismartcoding.plain.helpers

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.preferences.AppInfoGuardEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import kotlinx.coroutines.runBlocking

/**
 * Tracks whether the user has just passed the in-app PIN check that protects
 * PlainApp's own "App info" / application details screen.
 *
 * PlainAccessibilityService detects when the system Settings shows an
 * app-details page and consults this guard before allowing it to remain
 * visible — but ONLY when the page being shown is for our own package
 * (com.ismartcoding.plain).  Other apps' App Info pages are never blocked.
 *
 * The verified-window is short on purpose: it is only meant to let the user
 * land on the App info screen they intended to view, not to hand out a
 * blanket bypass for the rest of the session.
 */
object AppInfoGuard {
    private const val VALID_WINDOW_MS = 30_000L
    private const val CACHE_TTL_MS = 5_000L

    /** Package name we protect. */
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

    /**
     * Eagerly refresh the cached "active" flag. Call this from the GraphQL
     * mutation that toggles the guard so the accessibility-service hot-path
     * picks up the new value immediately.
     */
    fun invalidateCache() {
        cachedAt = 0L
    }

    /**
     * True only when the guard is enabled in preferences AND a PIN has been
     * configured. Without a PIN we cannot meaningfully challenge the user, so
     * we silently no-op rather than locking them out of system Settings.
     *
     * The accessibility service consults this on every window-state change,
     * so the result is cached for a few seconds to avoid hammering DataStore.
     */
    fun isActive(context: Context = MainApp.instance): Boolean {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_TTL_MS) return cachedActive
        val v = try {
            runBlocking {
                AppInfoGuardEnabledPreference.getAsync(context) &&
                    AppLockPinPreference.getAsync(context).isNotEmpty()
            }
        } catch (_: Throwable) {
            false
        }
        cachedActive = v
        cachedAt = now
        return v
    }

    /**
     * True when the given (foreground package, foreground class) pair looks
     * like a system "App info" / application details screen.
     *
     * We match on activity class name rather than on package, because OEMs
     * ship their own Settings packages (One UI, MIUI, ColorOS, EMUI, …) but
     * keep the AOSP class names for the app-details surface.
     */
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

    /**
     * Returns true when the App Info screen currently on screen is showing
     * details for OUR OWN package (com.ismartcoding.plain).
     *
     * Strategy (two layers for cross-device reliability):
     *  1. Check event.text list — some Android versions include visible text
     *     from the window, which can contain the package name string.
     *  2. Walk the accessibility node tree via findAccessibilityNodeInfosByText
     *     looking for our exact package name string.  On most Android versions
     *     (API 26+) the App Info page displays the package name in a "Version"
     *     or "App details" section.
     *
     * If neither layer finds our package name we assume it is someone else's
     * App Info page and DO NOT intercept.
     */
    fun isOwnAppInfoPage(event: AccessibilityEvent): Boolean {
        // Layer 1: event text list (fast, no Binder call)
        try {
            for (i in 0 until event.text.size) {
                if (event.text[i]?.contains(OWN_PACKAGE) == true) return true
            }
        } catch (_: Throwable) {}

        // Layer 2: accessibility node tree search
        val source = try { event.source } catch (_: Throwable) { null } ?: return false
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

    private fun isSettingsLikePackage(pkg: String): Boolean {
        return pkg == "com.android.settings" ||
            pkg == "com.miui.securitycenter" ||
            pkg == "com.samsung.android.settings" ||
            pkg.endsWith(".settings") ||
            pkg.contains(".settings.")
    }
}
