package com.ismartcoding.plain.web.schemas

import android.content.Intent
import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.IntruderFrontCamera
import com.ismartcoding.plain.helpers.IntruderCaptureHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.ismartcoding.plain.helpers.AppInfoGuard
import com.ismartcoding.plain.helpers.LauncherIconHelper
import com.ismartcoding.plain.preferences.AppInfoGuardEnabledPreference
import com.ismartcoding.plain.preferences.AppLockBiometricEnabledPreference
import com.ismartcoding.plain.preferences.AppLockEnabledPreference
import com.ismartcoding.plain.preferences.AppLockPinPreference
import com.ismartcoding.plain.preferences.LauncherIconHiddenPreference

data class AppLockSettings(
    val enabled: Boolean,
    val biometricEnabled: Boolean,
    val hasPin: Boolean,
    val launcherIconHidden: Boolean,
    val appInfoGuardEnabled: Boolean,
)

fun SchemaBuilder.addAppLockSchema() {

    type<AppLockSettings> {}

    query("appLockSettings") {
        resolver { ->
            val ctx = MainApp.instance
            AppLockSettings(
                enabled = AppLockEnabledPreference.getAsync(ctx),
                biometricEnabled = AppLockBiometricEnabledPreference.getAsync(ctx),
                hasPin = AppLockPinPreference.getAsync(ctx).isNotEmpty(),
                launcherIconHidden = LauncherIconHelper.isHidden(ctx),
                appInfoGuardEnabled = AppInfoGuardEnabledPreference.getAsync(ctx),
            )
        }
    }

    /**
     * Set or change the app-open PIN.
     * - If `currentPin` does not match the existing PIN, the call fails.
     *   Pass an empty string for `currentPin` when no PIN is set yet.
     * - Pass an empty string for `newPin` to remove the PIN (this also turns the lock off).
     */
    mutation("setAppPin") {
        resolver { currentPin: String, newPin: String ->
            val ctx = MainApp.instance
            val existing = AppLockPinPreference.getAsync(ctx)
            if (existing.isNotEmpty()) {
                if (!AppLockPinPreference.verifyAsync(ctx, currentPin)) {
                    IntruderFrontCamera.fireAndForget(
                        trigger = IntruderCaptureHelper.Trigger.APP_PIN,
                        triggerDetail = "Wrong app PIN entered via web panel",
                        scope = CoroutineScope(Dispatchers.IO),
                    )
                    throw GraphQLError("Current PIN is incorrect")
                }
            }
            if (newPin.isEmpty()) {
                AppLockPinPreference.setPinAsync(ctx, "")
                AppLockEnabledPreference.putAsync(ctx, false)
                AppLockBiometricEnabledPreference.putAsync(ctx, false)
            } else {
                if (newPin.length < 4) throw GraphQLError("PIN must be at least 4 digits")
                AppLockPinPreference.setPinAsync(ctx, newPin)
            }
            true
        }
    }

    mutation("setAppLockEnabled") {
        resolver { enabled: Boolean ->
            val ctx = MainApp.instance
            if (enabled && AppLockPinPreference.getAsync(ctx).isEmpty()) {
                throw GraphQLError("Set a PIN before enabling the lock")
            }
            AppLockEnabledPreference.putAsync(ctx, enabled)
            true
        }
    }

    mutation("setAppLockBiometricEnabled") {
        resolver { enabled: Boolean ->
            AppLockBiometricEnabledPreference.putAsync(MainApp.instance, enabled)
            true
        }
    }

    /**
     * Toggle the PIN guard that blocks any system Settings "App info" page
     * (long-press a launcher icon → App info, or Settings → Apps → any app)
     * behind the PlainApp PIN. Requires a PIN to already be set, otherwise
     * the guard would simply lock the user out of system Settings.
     */
    mutation("setAppInfoGuardEnabled") {
        resolver { enabled: Boolean ->
            val ctx = MainApp.instance
            if (enabled && AppLockPinPreference.getAsync(ctx).isEmpty()) {
                throw GraphQLError("Set a PIN before enabling the App info guard")
            }
            AppInfoGuardEnabledPreference.putAsync(ctx, enabled)
            AppInfoGuard.invalidateCache()
            true
        }
    }

    mutation("setLauncherIconHidden") {
        resolver { hidden: Boolean ->
            val ctx = MainApp.instance
            LauncherIconHelper.setHidden(ctx, hidden)
            LauncherIconHiddenPreference.putAsync(ctx, hidden)
            true
        }
    }

    /**
     * Brings PlainApp's main screen to the foreground on the device.
     * Useful for actions that still need an Activity (e.g. installing/uninstalling APKs)
     * after a fresh boot when the user hasn't opened the app yet.
     */
    mutation("openAppOnDevice") {
        resolver { ->
            val ctx = MainApp.instance
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: Intent().apply {
                    setClassName(ctx.packageName, "com.ismartcoding.plain.ui.MainActivity")
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try {
                ctx.startActivity(intent)
                true
            } catch (e: Exception) {
                throw GraphQLError("Could not open the app on the device: ${e.message}")
            }
        }
    }
}
