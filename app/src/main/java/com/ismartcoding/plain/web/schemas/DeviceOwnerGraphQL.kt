package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.DeviceOwnerHelper

data class DeviceOwnerStatusModel(
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

data class GrantPermissionsResultModel(
    val granted: List<String>,
    val failed: List<String>,
    val skipped: List<String>,
    val success: Boolean,
)

fun SchemaBuilder.addDeviceOwnerSchema() {
    type<DeviceOwnerStatusModel>()
    type<GrantPermissionsResultModel>()

    query("deviceOwnerStatus") {
        resolver { ->
            val s = DeviceOwnerHelper.getStatus(MainApp.instance)
            DeviceOwnerStatusModel(
                isDeviceOwner = s.isDeviceOwner,
                isDeviceAdmin = s.isDeviceAdmin,
                uninstallBlocked = s.uninstallBlocked,
                kioskEnabled = s.kioskEnabled,
                cameraDisabled = s.cameraDisabled,
                bluetoothDisabled = s.bluetoothDisabled,
                usbDebuggingEnabled = s.usbDebuggingEnabled,
                globalProxy = s.globalProxy,
                maxFailedPasswordsForWipe = s.maxFailedPasswordsForWipe,
                grantablePermissionsCount = s.grantablePermissionsCount,
                alreadyGrantedCount = s.alreadyGrantedCount,
            )
        }
    }

    mutation("grantAllPermissionsToSelf") {
        resolver { ->
            val r = DeviceOwnerHelper.grantAllPermissionsToSelf(MainApp.instance)
            GrantPermissionsResultModel(
                granted = r.granted,
                failed = r.failed,
                skipped = r.skipped,
                success = r.failed.isEmpty(),
            )
        }
    }

    mutation("setUninstallBlocked") {
        resolver { blocked: Boolean ->
            DeviceOwnerHelper.setUninstallBlocked(blocked, MainApp.instance)
        }
    }

    mutation("setKioskMode") {
        resolver { enabled: Boolean ->
            if (enabled) DeviceOwnerHelper.enableKioskMode(MainApp.instance)
            else DeviceOwnerHelper.disableKioskMode(MainApp.instance)
        }
    }

    mutation("setCameraDisabledDpm") {
        resolver { disabled: Boolean ->
            DeviceOwnerHelper.setCameraDisabled(disabled, MainApp.instance)
        }
    }

    mutation("setBluetoothDisabledDpm") {
        resolver { disabled: Boolean ->
            DeviceOwnerHelper.setBluetoothDisabled(disabled, MainApp.instance)
        }
    }

    mutation("setUsbDebuggingDisabled") {
        resolver { disabled: Boolean ->
            DeviceOwnerHelper.setUsbDebuggingDisabled(disabled, MainApp.instance)
        }
    }

    mutation("setGlobalProxy") {
        resolver { host: String, port: Int ->
            DeviceOwnerHelper.setGlobalProxy(host, port, MainApp.instance)
        }
    }

    mutation("clearGlobalProxy") {
        resolver { ->
            DeviceOwnerHelper.clearGlobalProxy(MainApp.instance)
        }
    }

    mutation("setMaxFailedPasswordsForWipe") {
        resolver { count: Int ->
            DeviceOwnerHelper.setMaxFailedPasswordsForWipe(count, MainApp.instance)
        }
    }

    mutation("setAppHiddenDpm") {
        resolver { packageName: String, hidden: Boolean ->
            DeviceOwnerHelper.setAppHidden(packageName, hidden, MainApp.instance)
        }
    }

    mutation("wipeDeviceDpm") {
        resolver { wipeExternalStorage: Boolean, wipeResetProtection: Boolean ->
            DeviceOwnerHelper.wipeDevice(wipeExternalStorage, wipeResetProtection, MainApp.instance)
        }
    }

    mutation("clearDeviceOwner") {
        resolver { ->
            DeviceOwnerHelper.clearDeviceOwner(MainApp.instance)
        }
    }
}
