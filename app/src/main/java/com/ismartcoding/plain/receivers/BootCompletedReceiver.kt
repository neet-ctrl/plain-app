package com.ismartcoding.plain.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.preferences.CloudflareTunnelAutoStartPreference
import com.ismartcoding.plain.preferences.CloudflareTunnelEnabledPreference
import com.ismartcoding.plain.preferences.KeepAliveVpnEnabledPreference
import com.ismartcoding.plain.preferences.KeepAliveWatchdogEnabledPreference
import com.ismartcoding.plain.preferences.WebPreference
import com.ismartcoding.plain.services.CloudflareTunnelManager
import com.ismartcoding.plain.services.HttpServerService
import com.ismartcoding.plain.services.KeepAliveVpnService
import com.ismartcoding.plain.services.LocationTrackingService
import com.ismartcoding.plain.helpers.LocationTrackingHelper

/**
 * Re-arm everything after device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        LogCat.d("BootCompletedReceiver action=$action")
        val app = context.applicationContext
        val pending = goAsync()
        coIO {
            try {
                if (CloudflareTunnelEnabledPreference.getAsync(app) &&
                    CloudflareTunnelAutoStartPreference.getAsync(app)
                ) {
                    try { CloudflareTunnelManager.start(app) } catch (_: Throwable) {}
                }
                if (WebPreference.getAsync(app)) {
                    try {
                        ContextCompat.startForegroundService(
                            app, Intent(app, HttpServerService::class.java),
                        )
                    } catch (_: Throwable) {}
                }
                if (KeepAliveVpnEnabledPreference.getAsync(app) && VpnService.prepare(app) == null) {
                    try {
                        ContextCompat.startForegroundService(
                            app, Intent(app, KeepAliveVpnService::class.java),
                        )
                    } catch (_: Throwable) {}
                }
                if (KeepAliveWatchdogEnabledPreference.getAsync(app)) {
                    KeepAliveWatchdogReceiver.schedule(app)
                }
                if (LocationTrackingHelper.isEnabled(app)) {
                    try { LocationTrackingService.start(app) } catch (_: Throwable) {}
                }
                // Re-arm scheduled automation rules (time-of-day, scheduled_once).
                try {
                    com.ismartcoding.plain.helpers.AutomationScheduler.scheduleAll(app)
                } catch (_: Throwable) {}
                // Fire any "boot_completed" automation rules.
                try {
                    com.ismartcoding.plain.helpers.AutomationHelper.list(app)
                        .filter { it.enabled && it.trigger.type == "boot_completed" }
                        .forEach {
                            com.ismartcoding.plain.helpers.AutomationActionRunner.trigger(
                                ruleId = it.id, source = "boot", ctx = app,
                            )
                        }
                } catch (_: Throwable) {}
            } finally {
                pending.finish()
            }
        }
    }
}
