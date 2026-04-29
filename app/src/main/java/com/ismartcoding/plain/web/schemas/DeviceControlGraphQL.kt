package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.AppLauncherHelper
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.helpers.BluetoothControlHelper
import com.ismartcoding.plain.helpers.NetworkUsageHelper
import com.ismartcoding.plain.helpers.PacketCaptureHelper
import com.ismartcoding.plain.helpers.WifiControlHelper
import com.ismartcoding.plain.services.PacketCaptureVpnService
import kotlinx.serialization.Serializable

// ---------- App launcher ----------

@Serializable
data class LaunchAppModel(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val launchable: Boolean,
)

// ---------- Network usage ----------

@Serializable
data class NetAppUsageModel(
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxBytesWifi: Long,
    val txBytesWifi: Long,
    val rxBytesMobile: Long,
    val txBytesMobile: Long,
)

@Serializable
data class NetUsageWindowModel(
    val sinceMs: Long,
    val untilMs: Long,
    val totalRx: Long,
    val totalTx: Long,
    val activeNetwork: String,
    val usageAccessGranted: Boolean,
    val apps: List<NetAppUsageModel>,
)

// ---------- Wi-Fi ----------

@Serializable
data class WifiNetworkModel(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequencyMhz: Int,
    val rssi: Int,
    val channelWidth: Int,
    val seenMs: Long,
    val isCurrent: Boolean,
)

@Serializable
data class WifiStateModel(
    val enabled: Boolean,
    val connectedSsid: String,
    val connectedBssid: String,
    val rssi: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val ipv4: String,
    val hotspotState: String,
    val savedListAccessible: Boolean,
    val canScan: Boolean,
)

// ---------- Bluetooth ----------

@Serializable
data class BtDeviceModel(
    val address: String,
    val name: String,
    val type: String,
    val bondState: String,
    val rssi: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val nearby: Boolean,
)

@Serializable
data class BtStateModel(
    val supported: Boolean,
    val enabled: Boolean,
    val scanning: Boolean,
    val hasScanPermission: Boolean,
    val hasConnectPermission: Boolean,
    val pairedCount: Int,
    val nearbyCount: Int,
)

// ---------- Battery ----------

@Serializable
data class BatterySampleModel(
    val ts: Long,
    val level: Int,
    val plugged: Int,
    val temperatureC: Double,
    val voltageMv: Int,
    val status: Int,
)

@Serializable
data class BatteryWindowModel(
    val sinceMs: Long,
    val untilMs: Long,
    val charging: Boolean,
    val currentLevel: Int,
    val plugged: String,
    val samples: List<BatterySampleModel>,
)

// ---------- Packet capture ----------

@Serializable
data class PacketEntryModel(
    val id: String,
    val ts: Long,
    val host: String,
    val port: Int,
    val protocol: String,
    val appPackage: String,
    val appLabel: String,
    val sizeBytes: Int,
    val resolvedIp: String,
)

@Serializable
data class PacketStateModel(
    val supported: Boolean,
    val enabled: Boolean,
    val running: Boolean,
    val totalEntries: Int,
    val needsConsent: Boolean,
)

// ---------- Schema registration ----------

fun SchemaBuilder.addDeviceControlSchema() {

    // ---- App launcher ----
    query("launchApps") {
        resolver { query: String ->
            AppLauncherHelper.list(MainApp.instance, query).map {
                LaunchAppModel(
                    packageName = it.packageName,
                    label = it.label,
                    versionName = it.versionName,
                    isSystem = it.isSystem,
                    installedAt = it.installedAt,
                    updatedAt = it.updatedAt,
                    launchable = it.launchable,
                )
            }
        }
    }
    mutation("launchApp") {
        resolver { packageName: String ->
            AppLauncherHelper.launch(packageName, MainApp.instance)
        }
    }

    // ---- Network usage ----
    query("networkUsage") {
        resolver { windowDays: Int ->
            val w = NetworkUsageHelper.query(windowDays.coerceIn(1, 90), MainApp.instance)
            NetUsageWindowModel(
                sinceMs = w.sinceMs,
                untilMs = w.untilMs,
                totalRx = w.totalRx,
                totalTx = w.totalTx,
                activeNetwork = NetworkUsageHelper.activeNetworkType(MainApp.instance),
                usageAccessGranted = w.usageAccessGranted,
                apps = w.apps.map {
                    NetAppUsageModel(
                        packageName = it.packageName, label = it.label,
                        rxBytes = it.rxBytes, txBytes = it.txBytes,
                        rxBytesWifi = it.rxBytesWifi, txBytesWifi = it.txBytesWifi,
                        rxBytesMobile = it.rxBytesMobile, txBytesMobile = it.txBytesMobile,
                    )
                },
            )
        }
    }

    // ---- Wi-Fi ----
    query("wifiState") {
        resolver { ->
            val s = WifiControlHelper.state(MainApp.instance)
            WifiStateModel(
                enabled = s.enabled,
                connectedSsid = s.connectedSsid, connectedBssid = s.connectedBssid,
                rssi = s.rssi, linkSpeedMbps = s.linkSpeedMbps, frequencyMhz = s.frequencyMhz,
                ipv4 = s.ipv4, hotspotState = s.hotspotState,
                savedListAccessible = s.savedListAccessible, canScan = s.canScan,
            )
        }
    }
    query("wifiScan") {
        resolver { ->
            WifiControlHelper.scan(MainApp.instance).map {
                WifiNetworkModel(
                    ssid = it.ssid, bssid = it.bssid, capabilities = it.capabilities,
                    frequencyMhz = it.frequencyMhz, rssi = it.rssi,
                    channelWidth = it.channelWidth, seenMs = it.seenMs,
                    isCurrent = it.isCurrent,
                )
            }
        }
    }
    mutation("setWifiEnabled") {
        resolver { enabled: Boolean ->
            WifiControlHelper.setWifiEnabled(enabled, MainApp.instance)
        }
    }

    // ---- Bluetooth ----
    query("bluetoothState") {
        resolver { ->
            val s = BluetoothControlHelper.state(MainApp.instance)
            BtStateModel(
                supported = s.supported, enabled = s.enabled,
                scanning = s.scanning,
                hasScanPermission = s.hasScanPermission,
                hasConnectPermission = s.hasConnectPermission,
                pairedCount = s.pairedCount, nearbyCount = s.nearbyCount,
            )
        }
    }
    query("bluetoothPaired") {
        resolver { ->
            BluetoothControlHelper.pairedList(MainApp.instance).map { it.toModel() }
        }
    }
    query("bluetoothNearby") {
        resolver { ->
            BluetoothControlHelper.nearbyList().map { it.toModel() }
        }
    }
    mutation("bluetoothStartScan") {
        resolver { -> BluetoothControlHelper.startScan(MainApp.instance) }
    }
    mutation("bluetoothStopScan") {
        resolver { -> BluetoothControlHelper.stopScan(MainApp.instance) }
    }
    mutation("bluetoothPair") {
        resolver { address: String -> BluetoothControlHelper.pair(address, MainApp.instance) }
    }
    mutation("bluetoothUnpair") {
        resolver { address: String -> BluetoothControlHelper.unpair(address, MainApp.instance) }
    }
    mutation("setBluetoothEnabled") {
        resolver { enabled: Boolean -> BluetoothControlHelper.setEnabled(enabled, MainApp.instance) }
    }

    // ---- Battery ----
    query("batteryHistory") {
        resolver { days: Int ->
            val w = BatteryHistoryHelper.window(days.coerceIn(1, 60), MainApp.instance)
            BatteryWindowModel(
                sinceMs = w.sinceMs, untilMs = w.untilMs,
                charging = w.charging, currentLevel = w.currentLevel,
                plugged = w.plugged,
                samples = w.samples.map {
                    BatterySampleModel(
                        ts = it.ts, level = it.level, plugged = it.plugged,
                        temperatureC = it.temperatureC.toDouble(),
                        voltageMv = it.voltageMv, status = it.status,
                    )
                },
            )
        }
    }
    mutation("clearBatteryHistory") {
        resolver { ->
            BatteryHistoryHelper.clear(MainApp.instance)
            true
        }
    }
    mutation("setBatteryHistoryEnabled") {
        resolver { enabled: Boolean ->
            BatteryHistoryHelper.setEnabled(enabled, MainApp.instance)
            true
        }
    }

    // ---- Packet capture ----
    query("packetCaptureState") {
        resolver { ->
            val running = PacketCaptureHelper.isRunning(MainApp.instance)
            val enabled = PacketCaptureHelper.isEnabled(MainApp.instance)
            PacketStateModel(
                supported = true,
                enabled = enabled,
                running = running,
                totalEntries = PacketCaptureHelper.count(MainApp.instance),
                // The web UI uses this to surface "tap to grant VPN consent" because
                // VpnService.prepare() must be triggered from an Activity on-device.
                needsConsent = enabled && !running,
            )
        }
    }
    query("packetEntries") {
        resolver { offset: Int, limit: Int, host: String ->
            PacketCaptureHelper.list(offset, limit.coerceIn(1, 1000), host, MainApp.instance).map {
                PacketEntryModel(
                    id = it.id, ts = it.ts, host = it.host, port = it.port,
                    protocol = it.protocol, appPackage = it.appPackage,
                    appLabel = it.appLabel, sizeBytes = it.sizeBytes,
                    resolvedIp = it.resolvedIp,
                )
            }
        }
    }
    mutation("setPacketCaptureEnabled") {
        resolver { enabled: Boolean ->
            PacketCaptureHelper.setEnabled(enabled, MainApp.instance)
            if (!enabled) {
                PacketCaptureVpnService.stop(MainApp.instance)
            } else {
                // Best-effort start; if VPN consent has not been granted yet the
                // service will fail to establish() and report needsConsent=true on
                // the next state query.
                PacketCaptureVpnService.start(MainApp.instance)
            }
            true
        }
    }
    mutation("clearPacketEntries") {
        resolver { ->
            PacketCaptureHelper.clear(MainApp.instance)
            true
        }
    }
}

// ---------- Local conversion helpers ----------

private fun BluetoothControlHelper.Device.toModel(): BtDeviceModel = BtDeviceModel(
    address = address, name = name, type = type, bondState = bondState,
    rssi = rssi, firstSeenMs = firstSeenMs, lastSeenMs = lastSeenMs,
    nearby = nearby,
)
