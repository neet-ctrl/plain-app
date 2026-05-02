package com.ismartcoding.plain.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.ismartcoding.plain.MainApp
import kotlinx.serialization.Serializable
import java.net.NetworkInterface

@Serializable
data class VpnStatusInfo(
    val isConnected: Boolean,
    val vpnName: String,
    val vpnPackage: String,
    val vpnIp: String,
    val vpnType: String,
    val mtu: Int,
    val interfaces: List<VpnInterfaceInfo>,
)

@Serializable
data class VpnInterfaceInfo(val name: String, val address: String)

object VpnStatusHelper {

    fun getVpnStatus(): VpnStatusInfo {
        val ctx = MainApp.instance
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var isConnected = false
        var vpnType = "Unknown"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (network in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    isConnected = true
                    val ifaceName = cm.getLinkProperties(network)?.interfaceName ?: ""
                    vpnType = when {
                        ifaceName.startsWith("tun") -> "TUN"
                        ifaceName.startsWith("ppp") -> "PPP"
                        ifaceName.startsWith("ipsec") -> "IPSec"
                        ifaceName.startsWith("wg") -> "WireGuard"
                        else -> "VPN"
                    }
                    break
                }
            }
        } else {
            @Suppress("DEPRECATION")
            isConnected = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN)?.isConnected == true
        }

        val vpnInterfaces = mutableListOf<VpnInterfaceInfo>()
        var primaryVpnIp = ""

        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
                for (iface in ifaces.iterator()) {
                    val name = iface.name ?: continue
                    val isVpnIface = name.startsWith("tun") || name.startsWith("ppp") ||
                            name.startsWith("ipsec") || name.startsWith("wg") || name.startsWith("vpn")
                    if (isVpnIface && iface.isUp) {
                        isConnected = true
                        for (addr in iface.inetAddresses) {
                            if (!addr.isLoopbackAddress) {
                                val hostAddr = addr.hostAddress ?: continue
                                vpnInterfaces.add(VpnInterfaceInfo(name, hostAddr))
                                if (primaryVpnIp.isEmpty()) primaryVpnIp = hostAddr
                                if (vpnType == "Unknown" || vpnType == "VPN") {
                                    vpnType = when {
                                        name.startsWith("tun") -> "TUN"
                                        name.startsWith("ppp") -> "PPP"
                                        name.startsWith("wg") -> "WireGuard"
                                        else -> "VPN"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (!isConnected) return VpnStatusInfo(false, "", "", "", "", 0, emptyList())

        val mtu = try {
            val ifaceName = vpnInterfaces.firstOrNull()?.name ?: ""
            if (ifaceName.isNotEmpty()) NetworkInterface.getByName(ifaceName)?.mtu ?: 0 else 0
        } catch (_: Exception) { 0 }

        return VpnStatusInfo(
            isConnected = true,
            vpnName = "",
            vpnPackage = "",
            vpnIp = primaryVpnIp,
            vpnType = vpnType,
            mtu = mtu,
            interfaces = vpnInterfaces,
        )
    }
}
