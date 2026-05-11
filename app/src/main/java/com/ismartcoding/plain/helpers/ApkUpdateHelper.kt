package com.ismartcoding.plain.helpers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.features.PackageHelper
import java.io.File

object ApkUpdateHelper {

    /**
     * Returns true if PlainApp is currently the Device Owner.
     * Device Owner mode allows completely silent APK installs (no user tap required).
     * To set Device Owner (one-time, requires USB/ADB):
     *   adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver
     */
    fun isDeviceOwner(ctx: Context): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? android.app.admin.DevicePolicyManager
            dpm?.isDeviceOwnerApp(ctx.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Install [apkFile] on the device.
     *
     * - If the app is the Device Owner AND running Android 12+:
     *   uses PackageInstaller with USER_ACTION_NOT_REQUIRED → completely silent, no tap needed.
     *   [onResult] is called with (true, "silent_ok") on success or (false, reason) on failure.
     *
     * - Otherwise:
     *   launches the system "Install" dialog via ACTION_VIEW. The user needs one tap.
     *   [onResult] is called immediately with (true, "dialog_shown").
     *
     * [onResult] may be called on any thread.
     */
    fun install(
        ctx: Context,
        apkFile: File,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        if (!apkFile.exists()) {
            onResult(false, "APK file not found: ${apkFile.name}")
            return
        }
        if (isDeviceOwner(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSilently(ctx, apkFile, onResult)
        } else {
            try {
                PackageHelper.install(ctx, apkFile)
                onResult(true, "dialog_shown")
            } catch (e: Exception) {
                LogCat.e("ApkUpdateHelper install (dialog): ${e.message}", e)
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }

    private fun installSilently(
        ctx: Context,
        apkFile: File,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Shouldn't reach here, but guard anyway
            PackageHelper.install(ctx, apkFile)
            onResult(true, "dialog_shown")
            return
        }
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                @Suppress("NewApi")
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                setAppPackageName(ctx.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("package", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }

                val actionName = "${ctx.packageName}.APK_INSTALL_RESULT.$sessionId"
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        context.unregisterReceiver(this)
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                        onResult(status == PackageInstaller.STATUS_SUCCESS, msg.ifBlank { "status=$status" })
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.registerReceiver(
                        receiver,
                        IntentFilter(actionName),
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    ctx.registerReceiver(receiver, IntentFilter(actionName))
                }

                val pi = PendingIntent.getBroadcast(
                    ctx,
                    sessionId,
                    Intent(actionName).setPackage(ctx.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            LogCat.e("ApkUpdateHelper installSilently failed: ${e.message}", e)
            // Fall back to system dialog
            try {
                PackageHelper.install(ctx, apkFile)
                onResult(true, "dialog_shown")
            } catch (e2: Exception) {
                onResult(false, e2.message ?: "Unknown error")
            }
        }
    }
}
