package com.ismartcoding.plain.helpers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.ProxyInfo
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver

object DeviceOwnerHelper {

    private fun dpm(ctx: Context = MainApp.instance): DevicePolicyManager? =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun admin(ctx: Context = MainApp.instance): ComponentName =
        ComponentName(ctx, PlainDeviceAdminReceiver::class.java)

    fun isDeviceOwner(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.isDeviceOwnerApp(ctx.packageName) == true
    } catch (_: Exception) { false }

    fun isDeviceAdmin(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.isAdminActive(admin(ctx)) == true
    } catch (_: Exception) { false }

    // ── 1. Grant all protected permissions to itself ──────────────────────────
    val AUTO_GRANTABLE_PERMISSIONS = listOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.TETHER_PRIVILEGED",
        "android.permission.READ_CLIPBOARD_IN_BACKGROUND",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_PHONE_NUMBERS",
    )

    data class GrantResult(
        val granted: List<String>,
        val failed: List<String>,
        val skipped: List<String>,
    )

    fun grantAllPermissionsToSelf(ctx: Context = MainApp.instance): GrantResult {
        val d = dpm(ctx) ?: return GrantResult(emptyList(), AUTO_GRANTABLE_PERMISSIONS, emptyList())
        val pkg = ctx.packageName
        val a = admin(ctx)
        val granted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (perm in AUTO_GRANTABLE_PERMISSIONS) {
            try {
                val short = perm.substringAfterLast(".")
                // Skip Android 10+ storage perms if MANAGE_EXTERNAL_STORAGE is better
                if (short == "READ_CLIPBOARD_IN_BACKGROUND" && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    skipped.add(perm); continue
                }
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    d.setPermissionGrantState(
                        a, pkg, perm,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                } else false
                if (result) granted.add(perm) else failed.add(perm)
            } catch (e: Exception) {
                LogCat.e("DeviceOwnerHelper grantPerm $perm: ${e.message}")
                failed.add(perm)
            }
        }
        return GrantResult(granted, failed, skipped)
    }

    // ── 2. Block / unblock self-uninstall ─────────────────────────────────────
    fun setUninstallBlocked(blocked: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setUninstallBlocked(admin(ctx), ctx.packageName, blocked)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setUninstallBlocked: ${e.message}"); false }

    fun isUninstallBlocked(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.isUninstallBlocked(admin(ctx), ctx.packageName) == true
    } catch (_: Exception) { false }

    // ── 3. Kiosk / Lock-Task mode ─────────────────────────────────────────────
    fun enableKioskMode(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setLockTaskPackages(admin(ctx), arrayOf(ctx.packageName))
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper kioskEnable: ${e.message}"); false }

    fun disableKioskMode(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setLockTaskPackages(admin(ctx), emptyArray())
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper kioskDisable: ${e.message}"); false }

    fun isKioskEnabled(ctx: Context = MainApp.instance): Boolean = try {
        val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dpm(ctx)?.getLockTaskPackages(admin(ctx)) ?: emptyArray()
        } else emptyArray()
        pkgs.contains(ctx.packageName)
    } catch (_: Exception) { false }

    // ── 4. Disable / enable camera device-wide ────────────────────────────────
    fun setCameraDisabled(disabled: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setCameraDisabled(admin(ctx), disabled)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setCameraDisabled: ${e.message}"); false }

    fun isCameraDisabled(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.getCameraDisabled(admin(ctx)) == true
    } catch (_: Exception) { false }

    // ── 5. Disable / enable Bluetooth device-wide ─────────────────────────────
    fun setBluetoothDisabled(disabled: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dpm(ctx)?.setBluetoothDisabled(admin(ctx), disabled)
            true
        } else false
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setBluetoothDisabled: ${e.message}"); false }

    fun isBluetoothDisabled(ctx: Context = MainApp.instance): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            dpm(ctx)?.isBluetoothDisabled(admin(ctx)) == true
        } else false
    } catch (_: Exception) { false }

    // ── 6. USB debugging toggle ───────────────────────────────────────────────
    fun setUsbDebuggingDisabled(disabled: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setSecureSetting(admin(ctx), Settings.Global.ADB_ENABLED, if (disabled) "0" else "1")
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper usbDebugging: ${e.message}"); false }

    fun isUsbDebuggingEnabled(ctx: Context = MainApp.instance): Boolean = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (_: Exception) { false }

    // ── 7. Global network proxy ───────────────────────────────────────────────
    fun setGlobalProxy(host: String, port: Int, ctx: Context = MainApp.instance): Boolean = try {
        val proxy = ProxyInfo.buildDirectProxy(host, port)
        dpm(ctx)?.setRecommendedGlobalProxy(admin(ctx), proxy)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setProxy: ${e.message}"); false }

    fun clearGlobalProxy(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setRecommendedGlobalProxy(admin(ctx), null)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper clearProxy: ${e.message}"); false }

    fun getGlobalProxy(ctx: Context = MainApp.instance): String = try {
        val proxy = dpm(ctx)?.getRecommendedGlobalProxy(admin(ctx))
        if (proxy != null) "${proxy.host}:${proxy.port}" else ""
    } catch (_: Exception) { "" }

    // ── 8. Screen lock policy ─────────────────────────────────────────────────
    fun setMaxFailedPasswordsForWipe(count: Int, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setMaximumFailedPasswordsForWipe(admin(ctx), count)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setMaxFailed: ${e.message}"); false }

    fun getMaxFailedPasswordsForWipe(ctx: Context = MainApp.instance): Int = try {
        dpm(ctx)?.getMaximumFailedPasswordsForWipe(admin(ctx)) ?: 0
    } catch (_: Exception) { 0 }

    fun setPasswordMinimumLength(length: Int, ctx: Context = MainApp.instance): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            dpm(ctx)?.setPasswordMinimumLength(admin(ctx), length)
            true
        } else false
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setPasswordMinLength: ${e.message}"); false }

    // ── 9. Hide / show another app from launcher ──────────────────────────────
    fun setAppHidden(packageName: String, hidden: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.setApplicationHidden(admin(ctx), packageName, hidden) == true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper setAppHidden: ${e.message}"); false }

    fun isAppHidden(packageName: String, ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.isApplicationHidden(admin(ctx), packageName) == true
    } catch (_: Exception) { false }

    // ── 10. Factory reset restriction ─────────────────────────────────────────
    fun setFactoryResetRestricted(restricted: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        val bundle = PersistableBundle()
        if (restricted) {
            bundle.putBoolean("no_factory_reset", true)
        }
        dpm(ctx)?.setApplicationRestrictions(admin(ctx), ctx.packageName, bundle)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper factoryReset: ${e.message}"); false }

    // ── 11. Remote enterprise wipe ────────────────────────────────────────────
    fun wipeDevice(wipeExternalStorage: Boolean, wipeResetProtection: Boolean, ctx: Context = MainApp.instance): Boolean = try {
        var flags = 0
        if (wipeExternalStorage) flags = flags or DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (wipeResetProtection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            flags = flags or DevicePolicyManager.WIPE_RESET_PROTECTION_DATA
        }
        dpm(ctx)?.wipeData(flags)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper wipeDevice: ${e.message}"); false }

    // ── 12. Clear device owner (self-removal) ─────────────────────────────────
    fun clearDeviceOwner(ctx: Context = MainApp.instance): Boolean = try {
        dpm(ctx)?.clearDeviceOwnerApp(ctx.packageName)
        true
    } catch (e: Exception) { LogCat.e("DeviceOwnerHelper clearOwner: ${e.message}"); false }

    // ── Status summary ─────────────────────────────────────────────────────────
    data class DeviceOwnerStatus(
        val isDeviceOwner: Boolean,
        val isDeviceAdmin: Boolean,
        val uninstallBlocked: Boolean,
        val kioskEnabled: Boolean,
        val cameraDisabled: Boolean,
        val bluetoothDisabled: Boolean,
        val usbDebuggingEnabled: Boolean,
        val globalProxy: String,
        val maxFailedPasswordsForWipe: Int,
        val grantablePermissionsCount: Int,
        val alreadyGrantedCount: Int,
    )

    fun getStatus(ctx: Context = MainApp.instance): DeviceOwnerStatus {
        val isOwner = isDeviceOwner(ctx)
        var alreadyGranted = 0
        if (isOwner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alreadyGranted = AUTO_GRANTABLE_PERMISSIONS.count { perm ->
                ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        return DeviceOwnerStatus(
            isDeviceOwner = isOwner,
            isDeviceAdmin = isDeviceAdmin(ctx),
            uninstallBlocked = if (isOwner) isUninstallBlocked(ctx) else false,
            kioskEnabled = if (isOwner) isKioskEnabled(ctx) else false,
            cameraDisabled = if (isOwner) isCameraDisabled(ctx) else false,
            bluetoothDisabled = if (isOwner) isBluetoothDisabled(ctx) else false,
            usbDebuggingEnabled = isUsbDebuggingEnabled(ctx),
            globalProxy = if (isOwner) getGlobalProxy(ctx) else "",
            maxFailedPasswordsForWipe = if (isOwner) getMaxFailedPasswordsForWipe(ctx) else 0,
            grantablePermissionsCount = AUTO_GRANTABLE_PERMISSIONS.size,
            alreadyGrantedCount = alreadyGranted,
        )
    }
}
