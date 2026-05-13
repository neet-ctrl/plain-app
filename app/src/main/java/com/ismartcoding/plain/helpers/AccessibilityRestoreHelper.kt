package com.ismartcoding.plain.helpers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ismartcoding.plain.Constants
import com.ismartcoding.plain.R
import com.ismartcoding.plain.ui.MainActivity

/**
 * Detects when an APK update has silently disabled the accessibility service
 * and fires a high-priority notification prompting the user to re-enable it.
 *
 * Android forcibly disables all accessibility services after every APK update
 * (security policy, cannot be bypassed). Without this helper the user would
 * just notice that remote control / app-lock / stealth screenshots / the
 * app-info guard stopped working — with no indication of why.
 *
 * Usage:
 *   Call [markEnabled] from PlainAccessibilityService.onServiceConnected().
 *   Call [checkAndNotify] from MainApp.onCreate() (runs on the coIO thread).
 */
object AccessibilityRestoreHelper {

    private const val PREFS_NAME = "accessibility_restore"
    private const val KEY_WAS_ENABLED = "was_enabled"
    private const val NOTIF_ID = 0x4AC1

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Call from onServiceConnected — records that the service was active. */
    fun markEnabled(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_WAS_ENABLED, true).apply()
    }

    /** Call from onDestroy — keeps the flag set so we can detect update-disable. */
    fun markDisabled(ctx: Context) {
        // Intentionally leave the flag TRUE so if the service is disabled by an
        // update (rather than by the user) we can notify on next launch.
        // Only the user explicitly toggling it off in Settings should clear it;
        // we detect that by checking the system setting, not this flag.
    }

    /**
     * Returns true when the system currently shows PlainApp's accessibility
     * service as enabled in Settings → Accessibility.
     */
    fun isSystemEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = "${ctx.packageName}/.services.PlainAccessibilityService"
        return enabled.split(":").any { it.equals(component, ignoreCase = true) }
    }

    /**
     * Call once on app start (from a background / coIO thread).
     * If we recorded that the service was previously enabled but it is no longer
     * listed in system settings (which happens after every APK update), a
     * sticky high-priority notification is posted directing the user to re-enable.
     */
    fun checkAndNotify(ctx: Context) {
        try {
            val wasEnabled = prefs(ctx).getBoolean(KEY_WAS_ENABLED, false)
            if (!wasEnabled) return
            if (isSystemEnabled(ctx)) return

            // Service was active before but is now disabled — most likely an APK update.
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val deepLinkIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                ctx, 0, deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(ctx, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("PlainApp accessibility service disabled")
                .setContentText("Tap to re-enable — required for remote control, app lock & stealth screenshots.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "After an app update Android requires you to manually re-enable the " +
                    "PlainApp accessibility service.\n\nTap to open Accessibility Settings " +
                    "and turn it back on."
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIF_ID, notification)
        } catch (_: Throwable) {}
    }

    /**
     * Dismiss the restore notification and clear the flag.
     * Call when the service successfully connects ([onServiceConnected]).
     */
    fun dismiss(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (_: Throwable) {}
        prefs(ctx).edit().putBoolean(KEY_WAS_ENABLED, true).apply()
    }
}
