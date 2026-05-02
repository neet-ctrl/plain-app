package com.ismartcoding.plain.web.schemas

import android.app.NotificationManager
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.helpers.QrCodeGenerateHelper
import com.ismartcoding.plain.helpers.SimInfoHelper
import com.ismartcoding.plain.helpers.SystemControlHelper
import com.ismartcoding.plain.helpers.VpnStatusHelper
import kotlinx.serialization.Serializable

@Serializable
data class StorageBreakdownModel(
    val totalBytes: Long, val usedBytes: Long, val freeBytes: Long,
    val appsBytes: Long, val imagesBytes: Long, val videosBytes: Long,
    val audioBytes: Long, val documentsBytes: Long, val otherBytes: Long, val cacheBytes: Long,
)

@Serializable
data class RunningProcessModel(
    val pid: Int, val processName: String, val appLabel: String,
    val packageName: String, val importance: Int, val importanceLabel: String, val rssKb: Long,
)

@Serializable
data class NetworkInterfaceModel(
    val name: String, val addresses: List<String>, val isUp: Boolean, val isLoopback: Boolean,
)

@Serializable
data class DndStatusModel(val mode: Int, val modeLabel: String, val policyGranted: Boolean)

@Serializable
data class HotspotStatusModel(val enabled: Boolean)

@Serializable
data class VpnInterfaceModel(val name: String, val address: String)

@Serializable
data class VpnStatusModel(
    val isConnected: Boolean,
    val vpnName: String,
    val vpnPackage: String,
    val vpnIp: String,
    val vpnType: String,
    val mtu: Int,
    val interfaces: List<VpnInterfaceModel>,
)

@Serializable
data class SimInfoModel(
    val slotIndex: Int,
    val carrierName: String,
    val operatorName: String,
    val phoneNumber: String,
    val networkTypeName: String,
    val mcc: String,
    val mnc: String,
    val isRoaming: Boolean,
    val isDataActive: Boolean,
    val signalBars: Int,
    val simState: String,
    val iccid: String,
)

fun SchemaBuilder.addSystemControlSchema() {

    type<StorageBreakdownModel>()
    type<RunningProcessModel>()
    type<NetworkInterfaceModel>()
    type<DndStatusModel>()
    type<HotspotStatusModel>()
    type<VpnInterfaceModel>()
    type<VpnStatusModel>()
    type<SimInfoModel>()

    // ---------- DND ----------
    query("dndStatus") {
        resolver { ->
            val mode = SystemControlHelper.getDndMode()
            val label = when (mode) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
                NotificationManager.INTERRUPTION_FILTER_ALL -> "All"
                else -> "Unknown"
            }
            val nm = android.app.Application().let {
                com.ismartcoding.plain.MainApp.instance.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            }
            DndStatusModel(mode, label, nm.isNotificationPolicyAccessGranted)
        }
    }

    mutation("setDnd") {
        resolver { enabled: Boolean ->
            val mode = if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                       else NotificationManager.INTERRUPTION_FILTER_ALL
            SystemControlHelper.setDndMode(mode)
        }
    }

    mutation("setDndMode") {
        resolver { mode: Int ->
            SystemControlHelper.setDndMode(mode)
        }
    }

    // ---------- Airplane Mode ----------
    query("airplaneMode") {
        resolver { -> SystemControlHelper.getAirplaneMode() }
    }

    mutation("setAirplaneMode") {
        resolver { enabled: Boolean ->
            SystemControlHelper.setAirplaneMode(enabled)
        }
    }

    // ---------- Hotspot ----------
    query("hotspotStatus") {
        resolver { ->
            HotspotStatusModel(SystemControlHelper.getHotspotEnabled())
        }
    }

    mutation("setHotspot") {
        resolver { enabled: Boolean ->
            SystemControlHelper.setHotspotEnabled(enabled)
        }
    }

    // ---------- VPN Status ----------
    query("vpnStatus") {
        resolver { ->
            val info = VpnStatusHelper.getVpnStatus()
            VpnStatusModel(
                isConnected = info.isConnected,
                vpnName = info.vpnName,
                vpnPackage = info.vpnPackage,
                vpnIp = info.vpnIp,
                vpnType = info.vpnType,
                mtu = info.mtu,
                interfaces = info.interfaces.map { VpnInterfaceModel(it.name, it.address) },
            )
        }
    }

    // ---------- SIM Info ----------
    query("simInfo") {
        resolver { ->
            SimInfoHelper.getAll().map { s ->
                SimInfoModel(
                    slotIndex = s.slotIndex,
                    carrierName = s.carrierName,
                    operatorName = s.operatorName,
                    phoneNumber = s.phoneNumber,
                    networkTypeName = s.networkTypeName,
                    mcc = s.mcc,
                    mnc = s.mnc,
                    isRoaming = s.isRoaming,
                    isDataActive = s.isDataActive,
                    signalBars = s.signalBars,
                    simState = s.simState,
                    iccid = s.iccid,
                )
            }
        }
    }

    // ---------- Lock Screen / Reboot ----------
    mutation("lockScreen") {
        resolver { ->
            SystemControlHelper.lockScreen()
            true
        }
    }

    mutation("rebootDevice") {
        resolver { ->
            SystemControlHelper.reboot()
            true
        }
    }

    // ---------- Clipboard ----------
    query("clipboardText") {
        resolver { -> SystemControlHelper.getClipboardText() }
    }

    mutation("setClipboardText") {
        resolver { text: String ->
            SystemControlHelper.setClipboardText(text)
            true
        }
    }

    mutation("clearClipboard") {
        resolver { ->
            SystemControlHelper.clearClipboard()
            true
        }
    }

    // ---------- Storage Analyzer ----------
    query("storageBreakdown") {
        resolver { ->
            val d = SystemControlHelper.getStorageBreakdown()
            StorageBreakdownModel(d.totalBytes, d.usedBytes, d.freeBytes, d.appsBytes,
                d.imagesBytes, d.videosBytes, d.audioBytes, d.documentsBytes, d.otherBytes, d.cacheBytes)
        }
    }

    // ---------- Process Manager ----------
    query("runningProcesses") {
        resolver { ->
            SystemControlHelper.getRunningProcesses().map {
                RunningProcessModel(it.pid, it.processName, it.appLabel, it.packageName,
                    it.importance, it.importanceLabel, it.rssKb)
            }
        }
    }

    mutation("killProcess") {
        resolver { pid: Int ->
            SystemControlHelper.killProcess(pid)
        }
    }

    // ---------- Network Interfaces ----------
    query("networkInterfaces") {
        resolver { ->
            SystemControlHelper.getNetworkInterfaces().map {
                NetworkInterfaceModel(it.name, it.addresses, it.isUp, it.isLoopback)
            }
        }
    }

    // ---------- QR Code ----------
    query("generateQrCode") {
        resolver { text: String ->
            try {
                val bmp = QrCodeGenerateHelper.generate(text, 512, 512)
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
