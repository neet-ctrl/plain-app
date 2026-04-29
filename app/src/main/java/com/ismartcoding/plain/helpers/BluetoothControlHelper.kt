package com.ismartcoding.plain.helpers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ismartcoding.plain.MainApp
import java.util.concurrent.ConcurrentHashMap

/**
 * Bluetooth scanner + paired device list + pair / unpair control.
 *
 * Uses BLE scanning under the hood for nearby device discovery (more accurate
 * RSSI, less intrusive than classic discovery). Classic-paired devices are
 * surfaced from the bonded set.
 */
object BluetoothControlHelper {

    data class Device(
        val address: String,
        val name: String,
        val type: String,        // classic | le | dual | unknown
        val bondState: String,   // none | bonding | bonded
        val rssi: Int,           // -127 if unknown
        val firstSeenMs: Long,
        val lastSeenMs: Long,
        val nearby: Boolean,
    )

    data class State(
        val supported: Boolean,
        val enabled: Boolean,
        val scanning: Boolean,
        val hasScanPermission: Boolean,
        val hasConnectPermission: Boolean,
        val pairedCount: Int,
        val nearbyCount: Int,
    )

    private val nearby = ConcurrentHashMap<String, Device>()
    @Volatile private var scanning: Boolean = false
    private var scanCallback: ScanCallback? = null
    private var scanStartedAt: Long = 0L

    private fun adapter(ctx: Context = MainApp.instance): BluetoothAdapter? {
        val mgr = ctx.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun hasPerm(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPerm(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPerm(ctx, Manifest.permission.BLUETOOTH_SCAN) else true

    private fun hasConnectPerm(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hasPerm(ctx, Manifest.permission.BLUETOOTH_CONNECT) else true

    fun state(ctx: Context = MainApp.instance): State {
        val a = adapter(ctx)
        val supported = a != null
        val enabled = a?.isEnabled == true
        val paired = if (enabled && hasConnectPerm(ctx)) {
            try { a?.bondedDevices?.size ?: 0 } catch (_: SecurityException) { 0 } catch (_: Throwable) { 0 }
        } else 0
        return State(
            supported = supported,
            enabled = enabled,
            scanning = scanning,
            hasScanPermission = hasScanPerm(ctx),
            hasConnectPermission = hasConnectPerm(ctx),
            pairedCount = paired,
            nearbyCount = nearby.size,
        )
    }

    @Synchronized
    fun startScan(ctx: Context = MainApp.instance): Boolean {
        val a = adapter(ctx) ?: return false
        if (!a.isEnabled) return false
        if (!hasScanPerm(ctx)) return false
        if (scanning) return true
        val scanner = try { a.bluetoothLeScanner } catch (_: SecurityException) { null } ?: return false
        nearby.clear()
        scanStartedAt = System.currentTimeMillis()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val addr = dev.address ?: return
                val now = System.currentTimeMillis()
                val name = safeName(ctx, dev)
                val existing = nearby[addr]
                val merged = Device(
                    address = addr,
                    name = name.ifEmpty { existing?.name ?: "" },
                    type = devTypeStr(dev.type),
                    bondState = bondStateStr(dev.bondState),
                    rssi = result.rssi,
                    firstSeenMs = existing?.firstSeenMs ?: now,
                    lastSeenMs = now,
                    nearby = true,
                )
                nearby[addr] = merged
            }
        }
        scanCallback = cb
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(emptyList(), settings, cb)
            scanning = true
            return true
        } catch (_: SecurityException) { return false }
        catch (_: Throwable) { return false }
    }

    @Synchronized
    fun stopScan(ctx: Context = MainApp.instance): Boolean {
        if (!scanning) return true
        val a = adapter(ctx) ?: return false
        val scanner = try { a.bluetoothLeScanner } catch (_: SecurityException) { null }
        val cb = scanCallback
        try { if (scanner != null && cb != null) scanner.stopScan(cb) } catch (_: Throwable) {}
        scanCallback = null
        scanning = false
        return true
    }

    fun nearbyList(): List<Device> = nearby.values.sortedByDescending { it.rssi }

    fun pairedList(ctx: Context = MainApp.instance): List<Device> {
        val a = adapter(ctx) ?: return emptyList()
        if (!hasConnectPerm(ctx)) return emptyList()
        return try {
            val bonded = a.bondedDevices ?: return emptyList()
            bonded.map { d ->
                Device(
                    address = d.address ?: "",
                    name = safeName(ctx, d),
                    type = devTypeStr(d.type),
                    bondState = bondStateStr(d.bondState),
                    rssi = nearby[d.address]?.rssi ?: -127,
                    firstSeenMs = 0L,
                    lastSeenMs = nearby[d.address]?.lastSeenMs ?: 0L,
                    nearby = nearby.containsKey(d.address),
                )
            }
        } catch (_: SecurityException) { emptyList() }
        catch (_: Throwable) { emptyList() }
    }

    fun pair(address: String, ctx: Context = MainApp.instance): Boolean {
        val a = adapter(ctx) ?: return false
        if (!hasConnectPerm(ctx)) return false
        return try {
            val dev = a.getRemoteDevice(address) ?: return false
            if (dev.bondState == BluetoothDevice.BOND_BONDED) return true
            dev.createBond()
        } catch (_: SecurityException) { false }
        catch (_: Throwable) { false }
    }

    fun unpair(address: String, ctx: Context = MainApp.instance): Boolean {
        val a = adapter(ctx) ?: return false
        if (!hasConnectPerm(ctx)) return false
        return try {
            val dev = a.getRemoteDevice(address) ?: return false
            // BluetoothDevice has no public removeBond() — use reflection.
            val m = dev.javaClass.getMethod("removeBond")
            (m.invoke(dev) as? Boolean) == true
        } catch (_: Throwable) { false }
    }

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance): Boolean {
        val a = adapter(ctx) ?: return false
        if (!hasConnectPerm(ctx)) return false
        return try {
            // Direct enable/disable was deprecated in API 33; the call still works on many
            // OEM builds but officially returns false. We try anyway and return the result.
            @Suppress("DEPRECATION")
            if (enabled) a.enable() else a.disable()
        } catch (_: SecurityException) { false }
        catch (_: Throwable) { false }
    }

    private fun safeName(ctx: Context, d: BluetoothDevice): String {
        return try {
            if (!hasConnectPerm(ctx)) ""
            else d.name ?: ""
        } catch (_: SecurityException) { "" }
        catch (_: Throwable) { "" }
    }

    private fun devTypeStr(t: Int): String = when (t) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "le"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
        else -> "unknown"
    }

    private fun bondStateStr(s: Int): String = when (s) {
        BluetoothDevice.BOND_BONDED -> "bonded"
        BluetoothDevice.BOND_BONDING -> "bonding"
        else -> "none"
    }
}
