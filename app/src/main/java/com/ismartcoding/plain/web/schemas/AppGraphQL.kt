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
