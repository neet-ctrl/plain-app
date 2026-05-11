package com.ismartcoding.plain.web.schemas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Base64
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.extensions.appDir
import com.ismartcoding.plain.BuildConfig
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.events.RestartAppEvent
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.file.FileSystemHelper
import com.ismartcoding.plain.helpers.DeviceInfoHelper
import com.ismartcoding.plain.helpers.PhoneHelper
import com.ismartcoding.plain.helpers.TempHelper
import com.ismartcoding.plain.preferences.ApiPermissionsPreference
import com.ismartcoding.plain.preferences.AudioPlayingPreference
import com.ismartcoding.plain.preferences.AudioPlaylistPreference
import com.ismartcoding.plain.preferences.DeveloperModePreference
import com.ismartcoding.plain.preferences.FavoriteFoldersPreference
import com.ismartcoding.plain.receivers.BatteryReceiver
import com.ismartcoding.plain.receivers.PlugInControlReceiver
import com.ismartcoding.plain.web.models.App
import com.ismartcoding.plain.web.models.TempValue
import com.ismartcoding.plain.web.models.toModel

data class PermissionStatusItem(
    val name: String,
    val label: String,
    val granted: Boolean,
    val enabled: Boolean,
    val category: String,
)

data class ProtectedPermissionItem(
    val name: String,
    val label: String,
    val description: String,
    val features: List<String>,
    val adbCommand: String,
    val grantType: String,
    val granted: Boolean,
    val settingsPath: String,
)

private fun categorize(name: String): String {
    return when {
        name.startsWith("READ_SMS") || name.startsWith("SEND_SMS") -> "messaging"
        name.contains("CONTACT") -> "contacts"
        name.contains("CALL") || name == "READ_PHONE_STATE" || name == "READ_PHONE_NUMBERS" -> "phone"
        name.contains("LOCATION") -> "location"
        name == "CAMERA" || name.startsWith("READ_MEDIA") || name == "WRITE_EXTERNAL_STORAGE" -> "media_storage"
        name.contains("BLUETOOTH") -> "connectivity"
        name == "RECORD_AUDIO" -> "audio"
        name == "POST_NOTIFICATIONS" || name == "NOTIFICATION_LISTENER" -> "notifications"
        name == "ACCESSIBILITY_SERVICE" || name == "PACKAGE_USAGE_STATS" || name == "SYSTEM_ALERT_WINDOW" || name == "WRITE_SETTINGS" || name == "QUERY_ALL_PACKAGES" || name == "SCHEDULE_EXACT_ALARM" -> "system"
        else -> "other"
    }
}

fun SchemaBuilder.addAppSchema() {
    query("deviceInfo") {
        resolver { ->
            val context = MainApp.instance
            val apiPermissions = ApiPermissionsPreference.getAsync(context)
            val readPhoneNumber = apiPermissions.contains(Permission.READ_PHONE_NUMBERS.toString())
            DeviceInfoHelper.getDeviceInfo(context, readPhoneNumber).toModel()
        }
    }
    query("battery") {
        resolver { ->
            BatteryReceiver.get(MainApp.instance).toModel()
        }
    }
    query("app") {
        resolver { ->
            val context = MainApp.instance
            val apiPermissions = ApiPermissionsPreference.getAsync(context)
            val grantedPermissions = Permission.entries.filter { apiPermissions.contains(it.name) && it.can(MainApp.instance) }.toMutableList()
            if (Permission.RECORD_AUDIO.can(context) && !grantedPermissions.contains(Permission.RECORD_AUDIO)) {
                grantedPermissions.add(Permission.RECORD_AUDIO)
            }
            App(
                usbConnected = PlugInControlReceiver.isUSBConnected(context),
                urlToken = Base64.encodeToString(TempData.urlToken, Base64.NO_WRAP),
                httpPort = TempData.httpPort,
                httpsPort = TempData.httpsPort,
                appDir = context.appDir(),
                deviceName = TempData.deviceName,
                PhoneHelper.getBatteryPercentage(context),
                BuildConfig.VERSION_CODE,
                Build.VERSION.SDK_INT,
                BuildConfig.CHANNEL,
                grantedPermissions,
                AudioPlaylistPreference.getValueAsync(context).map { it.toModel() },
                TempData.audioPlayMode,
                AudioPlayingPreference.getValueAsync(context),
                sdcardPath = FileSystemHelper.getSDCardPath(context),
                usbDiskPaths = FileSystemHelper.getUsbDiskPaths(),
                internalStoragePath = FileSystemHelper.getInternalStoragePath(),
                downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                developerMode = DeveloperModePreference.getAsync(context),
                favoriteFolders = FavoriteFoldersPreference.getValueAsync(context).map { it.toModel() },
            )
        }
    }
    type<ProtectedPermissionItem>()

    query("protectedPermissionsStatus") {
        resolver { ->
            val ctx = MainApp.instance
            val pkg = ctx.packageName
            fun pmCheck(perm: String): Boolean =
                ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED

            fun appOpsCheck(): Boolean = try {
                val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                else @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), pkg)
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) { false }

            fun dndGranted(): Boolean = try {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .isNotificationPolicyAccessGranted
            } catch (_: Exception) { false }

            fun deviceAdminGranted(): Boolean = try {
                val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val cn = android.content.ComponentName(ctx, com.ismartcoding.plain.receivers.PlainDeviceAdminReceiver::class.java)
                dpm.isAdminActive(cn)
            } catch (_: Exception) { false }

            fun notifListenerGranted(): Boolean = try {
                val cn = android.content.ComponentName(ctx, com.ismartcoding.plain.services.PNotificationListenerService::class.java)
                val enabled = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
                enabled.contains(cn.flattenToString())
            } catch (_: Exception) { false }

            listOf(
                ProtectedPermissionItem(
                    name = "WRITE_SECURE_SETTINGS",
                    label = "Modify Secure Settings",
                    description = "Required to toggle Airplane Mode programmatically on Android 4.2+.",
                    features = listOf("Airplane mode toggle"),
                    adbCommand = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
                    grantType = "pm_grant",
                    granted = pmCheck("android.permission.WRITE_SECURE_SETTINGS"),
                    settingsPath = "",
                ),
                ProtectedPermissionItem(
                    name = "TETHER_PRIVILEGED",
                    label = "Hotspot / Tethering Control",
                    description = "Required to toggle the mobile hotspot on Android 8+.",
                    features = listOf("Mobile hotspot toggle", "Wi-Fi tethering"),
                    adbCommand = "adb shell pm grant $pkg android.permission.TETHER_PRIVILEGED",
                    grantType = "pm_grant",
                    granted = pmCheck("android.permission.TETHER_PRIVILEGED"),
                    settingsPath = "",
                ),
                ProtectedPermissionItem(
                    name = "READ_CLIPBOARD_IN_BACKGROUND",
                    label = "Read Clipboard in Background",
                    description = "Allows reading clipboard content when the app is not in the foreground (Android 10+).",
                    features = listOf("Clipboard read from web panel", "Remote clipboard access"),
                    adbCommand = "adb shell pm grant $pkg android.permission.READ_CLIPBOARD_IN_BACKGROUND",
                    grantType = "pm_grant",
                    granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        pmCheck("android.permission.READ_CLIPBOARD_IN_BACKGROUND") else true,
                    settingsPath = "",
                ),
                ProtectedPermissionItem(
                    name = "PACKAGE_USAGE_STATS",
                    label = "Usage Access (Screen Time)",
                    description = "Allows reading app usage statistics for screen time and app launch history.",
                    features = listOf("Screen time stats", "App launch history", "/screentime command"),
                    adbCommand = "adb shell appops set $pkg GET_USAGE_STATS allow",
                    grantType = "appops",
                    granted = appOpsCheck(),
                    settingsPath = "Settings → Apps → Special Access → Usage Access",
                ),
                ProtectedPermissionItem(
                    name = "NOTIFICATION_POLICY_ACCESS",
                    label = "Do Not Disturb Access",
                    description = "Allows controlling Do Not Disturb mode and interruption filters.",
                    features = listOf("DND toggle", "Interruption filter", "/dnd command"),
                    adbCommand = "adb shell cmd notification allow_dnd $pkg",
                    grantType = "cmd",
                    granted = dndGranted(),
                    settingsPath = "Settings → Apps → Special Access → Do Not Disturb",
                ),
                ProtectedPermissionItem(
                    name = "NOTIFICATION_LISTENER",
                    label = "Notification Listener",
                    description = "Allows reading and forwarding incoming notifications to the web panel and Telegram.",
                    features = listOf("Notification forwarding", "Notification log", "/notifications command"),
                    adbCommand = "adb shell cmd notification allow_listener $pkg/.services.PNotificationListenerService",
                    grantType = "cmd",
                    granted = notifListenerGranted(),
                    settingsPath = "Settings → Apps → Special Access → Notification Access",
                ),
                ProtectedPermissionItem(
                    name = "WRITE_SETTINGS",
                    label = "Modify System Settings",
                    description = "Allows changing system settings like screen brightness and media volume.",
                    features = listOf("Screen brightness control", "Volume control", "/brightness command", "/volume command"),
                    adbCommand = "adb shell pm grant $pkg android.permission.WRITE_SETTINGS",
                    grantType = "settings_ui",
                    granted = android.provider.Settings.System.canWrite(ctx),
                    settingsPath = "Settings → Apps → Special Access → Modify System Settings",
                ),
                ProtectedPermissionItem(
                    name = "SYSTEM_ALERT_WINDOW",
                    label = "Display Over Other Apps",
                    description = "Allows showing overlays and banners on top of other apps.",
                    features = listOf("Toast/banner overlay", "/show command", "/findphone alarm overlay"),
                    adbCommand = "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow",
                    grantType = "settings_ui",
                    granted = android.provider.Settings.canDrawOverlays(ctx),
                    settingsPath = "Settings → Apps → Special Access → Display over other apps",
                ),
                ProtectedPermissionItem(
                    name = "ACCESSIBILITY_SERVICE",
                    label = "Accessibility Service",
                    description = "Required for keystroke logging and advanced app interaction monitoring.",
                    features = listOf("Keystroke capture", "App usage monitoring", "/keystrokes command", "/keytop command"),
                    adbCommand = "adb shell settings put secure enabled_accessibility_services $pkg/.services.PlainAccessibilityService",
                    grantType = "accessibility",
                    granted = com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(ctx),
                    settingsPath = "Settings → Accessibility → Installed Apps → PlainApp",
                ),
                ProtectedPermissionItem(
                    name = "DEVICE_ADMIN",
                    label = "Device Administrator",
                    description = "Allows remotely locking the device screen.",
                    features = listOf("Remote screen lock", "/lockscreen command"),
                    adbCommand = "adb shell dpm set-active-admin $pkg/.receivers.PlainDeviceAdminReceiver",
                    grantType = "dpm",
                    granted = deviceAdminGranted(),
                    settingsPath = "Settings → Security → Device Admin Apps → PlainApp",
                ),
                ProtectedPermissionItem(
                    name = "DEVICE_OWNER",
                    label = "Device Owner (Silent APK Install)",
                    description = "Makes PlainApp the Device Owner. Required for fully silent, zero-touch APK self-updates — the system install dialog never appears. Set once via ADB. Warning: cannot be removed without a factory reset (unless removed via ADB first).",
                    features = listOf("Silent APK self-update (no tap)", "/update bot command zero-touch", "Web panel APK installer zero-touch"),
                    adbCommand = "adb shell dpm set-device-owner $pkg/.receivers.PlainDeviceAdminReceiver",
                    grantType = "dpm_owner",
                    granted = try {
                        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                        dpm?.isDeviceOwnerApp(pkg) == true
                    } catch (_: Exception) { false },
                    settingsPath = "",
                ),
            )
        }
    }

    query("allPermissionsStatus") {
        resolver { ->
            val context = MainApp.instance
            val apiPermissions = ApiPermissionsPreference.getAsync(context)
            Permission.entries
                .filter { it != Permission.NONE }
                .map { p ->
                    val granted = try { p.can(context) } catch (_: Throwable) { false }
                    val enabled = apiPermissions.contains(p.name)
                    PermissionStatusItem(
                        name = p.name,
                        label = try { p.getText() } catch (_: Throwable) { p.name },
                        granted = granted,
                        enabled = enabled,
                        category = categorize(p.name),
                    )
                }
        }
    }
    mutation("setTempValue") {
        resolver { key: String, value: String ->
            TempHelper.setValue(key, value)
            TempValue(key, value)
        }
    }
    mutation("relaunchApp") {
        resolver { ->
            sendEvent(RestartAppEvent())
            true
        }
    }
    mutation("setClip") {
        resolver { text: String ->
            val clipboard = MainApp.instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
            true
        }
    }
}
