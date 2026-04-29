package com.ismartcoding.plain.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.PacketCaptureHelper
import com.ismartcoding.plain.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * URL-logging VPN service.
 *
 * Strategy: we install ourselves as the system DNS server inside a tiny TUN
 * device and route ONLY our virtual DNS IP through the TUN. All other traffic
 * (HTTP / HTTPS bodies, etc.) flows over the normal network and is NOT
 * intercepted, so user apps keep working.
 *
 * For every DNS query we read from the TUN we:
 *   1. Parse the QNAME (hostname being looked up).
 *   2. Forward the raw UDP packet to a real upstream resolver via a
 *      protect()-ed socket so it does not loop back through the TUN.
 *   3. Write the upstream response back to the TUN so the app gets a normal
 *      DNS reply.
 *   4. Log the hostname into [PacketCaptureHelper] so the web panel shows it.
 *
 * Only DNS queries are captured and visible. DNS-over-HTTPS / DoT bypass this
 * (because they encrypt the hostname inside HTTPS), and we document that in
 * the UI.
 */
class PacketCaptureVpnService : VpnService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var readerJob: Job? = null
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var stopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (tun == null) {
            try {
                tun = Builder()
                    .setSession("PlainApp Packet Capture")
                    .addAddress(VPN_LOCAL_IP, 32)
                    .addDnsServer(VIRTUAL_DNS_IP)
                    // Route ONLY the virtual DNS server's address through us; everything
                    // else escapes the TUN and uses the real network normally.
                    .addRoute(VIRTUAL_DNS_IP, 32)
                    .addDisallowedApplication(packageName)
                    .setBlocking(true)
                    .setMtu(MTU)
                    .establish()
                if (tun == null) {
                    LogCat.e("PacketCaptureVpn establish() returned null")
                    PacketCaptureHelper.setRunning(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                PacketCaptureHelper.setRunning(true)
                LogCat.d("PacketCaptureVpn TUN up, fd=${tun?.fd}")
                startReader()
            } catch (t: Throwable) {
                LogCat.e("PacketCaptureVpn establish failed: ${t.message}")
                PacketCaptureHelper.setRunning(false)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Packet capture",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Packet capture active")
            .setContentText("Logging URL hostnames over VPN")
            .setSmallIcon(R.drawable.notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(FG_ID, n)
    }

    private fun startReader() {
        val pfd = tun ?: return
        readerJob = scope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buf = ByteArray(MTU)
            while (isActive && !stopping) {
                val n = try { input.read(buf) } catch (_: Throwable) { -1 }
                if (n <= 0) continue
                handlePacket(buf, n, output)
            }
            try { input.close() } catch (_: Throwable) {}
            try { output.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Handle a single IPv4 UDP packet (DNS) from the TUN.
     * Layout: [ IPv4 header (20+ B) | UDP header (8 B) | DNS payload ]
     */
    private fun handlePacket(buf: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 28) return
        val versionIhl = buf[0].toInt() and 0xff
        val version = versionIhl ushr 4
        if (version != 4) return
        val ihl = (versionIhl and 0x0f) * 4
        if (ihl < 20 || len < ihl + 8) return
        val protocol = buf[9].toInt() and 0xff
        if (protocol != 17 /* UDP */) return
        val dstIp = buf.copyOfRange(16, 20)
        val udpStart = ihl
        val srcPort = ((buf[udpStart].toInt() and 0xff) shl 8) or (buf[udpStart + 1].toInt() and 0xff)
        val dstPort = ((buf[udpStart + 2].toInt() and 0xff) shl 8) or (buf[udpStart + 3].toInt() and 0xff)
        if (dstPort != 53) return
        val payloadStart = udpStart + 8
        val payloadLen = len - payloadStart
        if (payloadLen <= 12) return

        // Parse the question section (we only need the first QNAME).
        val qname = parseDnsQname(buf, payloadStart) ?: return
        if (qname.isNotBlank()) {
            try {
                PacketCaptureHelper.append(
                    host = qname,
                    port = 0,
                    protocol = "dns",
                    appPackage = "",
                    appLabel = "",
                    sizeBytes = payloadLen,
                )
            } catch (_: Throwable) {}
        }

        // Forward to a real upstream and write the response back into the TUN.
        scope.launch {
            try {
                val payload = buf.copyOfRange(payloadStart, payloadStart + payloadLen)
                val sock = DatagramSocket()
                try {
                    protect(sock)
                    sock.soTimeout = 4000
                    val pkt = DatagramPacket(payload, payload.size, InetSocketAddress(UPSTREAM_DNS, 53))
                    sock.send(pkt)
                    val resp = ByteArray(2048)
                    val rp = DatagramPacket(resp, resp.size)
                    sock.receive(rp)
                    val respLen = rp.length
                    val outPkt = buildResponseIpUdp(buf, ihl, srcPort, dstPort, dstIp, resp, respLen)
                    if (outPkt != null) {
                        try { output.write(outPkt) } catch (_: Throwable) {}
                    }
                } finally {
                    try { sock.close() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                // Network down / upstream timeout — silently drop.
            }
        }
    }

    private fun parseDnsQname(buf: ByteArray, payloadStart: Int): String? {
        // Skip 12-byte DNS header.
        var i = payloadStart + 12
        val sb = StringBuilder()
        while (i < buf.size) {
            val len = buf[i].toInt() and 0xff
            if (len == 0) break
            if (len and 0xc0 == 0xc0) {
                // Compression pointer in question section is unusual; bail out.
                return sb.toString().ifEmpty { null }
            }
            i++
            if (i + len > buf.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until len) sb.append((buf[i + j].toInt() and 0xff).toChar())
            i += len
            if (sb.length > 253) return null
        }
        return sb.toString().ifEmpty { null }
    }

    /**
     * Wrap the upstream DNS response in a fresh IPv4 + UDP header so the OS
     * delivers it back to the calling app via the TUN.
     */
    private fun buildResponseIpUdp(
        reqBuf: ByteArray,
        reqIhl: Int,
        reqSrcPort: Int,
        reqDstPort: Int,
        reqDstIp: ByteArray,
        respPayload: ByteArray,
        respLen: Int,
    ): ByteArray? {
        if (respLen <= 0) return null
        val totalLen = 20 + 8 + respLen
        val out = ByteBuffer.allocate(totalLen).order(java.nio.ByteOrder.BIG_ENDIAN)
        // IPv4 header
        out.put(0x45.toByte())                  // version 4 + IHL 5
        out.put(0x00.toByte())                  // DSCP/ECN
        out.putShort(totalLen.toShort())        // total length
        out.putShort(0)                         // identification
        out.putShort(0x4000.toShort())          // DF
        out.put(64.toByte())                    // TTL
        out.put(17.toByte())                    // UDP
        out.putShort(0)                         // checksum (filled later)
        // src IP = the original request's dst IP (our virtual DNS IP)
        out.put(reqDstIp)
        // dst IP = original request's src IP (the caller)
        out.put(reqBuf, 12, 4)
        // Patch IP checksum
        val ipBuf = out.array()
        val ipCs = checksum(ipBuf, 0, 20)
        ipBuf[10] = (ipCs ushr 8 and 0xff).toByte()
        ipBuf[11] = (ipCs and 0xff).toByte()
        // UDP header at offset 20
        out.putShort(reqDstPort.toShort())   // src port = DNS:53 (the original dst)
        out.putShort(reqSrcPort.toShort())   // dst port = original src
        out.putShort((8 + respLen).toShort())
        out.putShort(0)                       // checksum 0 = unused for IPv4 UDP
        out.put(respPayload, 0, respLen)
        return out.array()
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xff) shl 8) or (buf[i + 1].toInt() and 0xff)
            i += 2
        }
        if (i < end) sum += (buf[i].toInt() and 0xff) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xffffL) + (sum ushr 16)
        return (sum.inv().toInt() and 0xffff)
    }

    private fun stop() {
        stopping = true
        readerJob?.cancel()
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
        PacketCaptureHelper.setRunning(false)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    override fun onRevoke() {
        LogCat.d("PacketCaptureVpn revoked")
        stop()
        super.onRevoke()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    companion object {
        private const val MTU = 1500
        private const val VPN_LOCAL_IP = "10.222.111.1"
        private const val VIRTUAL_DNS_IP = "10.222.111.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val CHANNEL_ID = "packet_capture"
        private const val FG_ID = 0xCAB1
        const val ACTION_STOP = "com.ismartcoding.plain.PACKET_CAPTURE_STOP"

        fun start(ctx: Context = MainApp.instance) {
            val i = Intent(ctx, PacketCaptureVpnService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (t: Throwable) { LogCat.e("PacketCaptureVpn start: ${t.message}") }
        }

        fun stop(ctx: Context = MainApp.instance) {
            try {
                val i = Intent(ctx, PacketCaptureVpnService::class.java).apply { action = ACTION_STOP }
                ctx.startService(i)
            } catch (_: Throwable) {}
        }
    }
}
