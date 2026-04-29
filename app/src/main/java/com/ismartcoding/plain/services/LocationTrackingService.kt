package com.ismartcoding.plain.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.lib.helpers.StringHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import com.ismartcoding.plain.helpers.GeofenceAudioRecorderHelper
import com.ismartcoding.plain.helpers.GeofencingHelper
import com.ismartcoding.plain.helpers.LocationTrackingHelper
import com.ismartcoding.plain.services.AppBlockHelper
import kotlinx.serialization.Serializable

/**
 * Foreground service that:
 *  - Subscribes to GPS + NETWORK location updates at the user-configured interval.
 *  - Stores each fix in LocationTrackingHelper.
 *  - Streams the fix to the web panel via WebSocket (LOCATION_UPDATE).
 *  - Evaluates each fix against all enabled geofences and triggers actions
 *    (notify web, record audio for N sec, lock apps via AppBlockHelper).
 */
class LocationTrackingService : Service(), LocationListener {

    private var lm: LocationManager? = null
    private var listenerThread: HandlerThread? = null
    private var listenerHandler: Handler? = null
    private val pendingUnlocks = mutableMapOf<String, Long>() // pkg -> ts when to unlock

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequests()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_INTERVAL -> {
                stopRequests()
                startRequests()
            }
            else -> startRequests()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRequests() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            LogCat.e("LocationTrackingService: missing fine location permission")
            stopSelf()
            return
        }
        try {
            val mgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm = mgr
            val intervalMs = LocationTrackingHelper.getIntervalSec(this) * 1000L
            val minDist = LocationTrackingHelper.getMinDisplacement(this).toFloat()
            listenerThread = HandlerThread("LocationTrackingListener").also { it.start() }
            listenerHandler = Handler(listenerThread!!.looper)
            val providers = mutableListOf<String>()
            if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER)
            if (mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER)
            if (providers.isEmpty()) {
                LogCat.e("LocationTrackingService: no location providers enabled")
            }
            for (p in providers) {
                try {
                    mgr.requestLocationUpdates(p, intervalMs, minDist, this, listenerThread!!.looper)
                } catch (e: Throwable) {
                    LogCat.e("LocationTrackingService.requestLocationUpdates($p) failed: ${e.message}", e)
                }
            }
            // also push the last known location immediately so the UI updates fast
            for (p in providers) {
                try {
                    mgr.getLastKnownLocation(p)?.let { onLocationChanged(it) }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            LogCat.e("LocationTrackingService.startRequests failed: ${e.message}", e)
        }
    }

    private fun stopRequests() {
        try { lm?.removeUpdates(this) } catch (_: Throwable) {}
        lm = null
        try { listenerThread?.quitSafely() } catch (_: Throwable) {}
        listenerThread = null
        listenerHandler = null
    }

    override fun onDestroy() {
        instance = null
        stopRequests()
        super.onDestroy()
    }

    @Serializable
    data class LocationUpdatePayload(
        val ts: Long,
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val speed: Float,
        val altitude: Double,
        val bearing: Float,
        val battery: Int,
        val charging: Boolean,
        val provider: String,
        val totalPoints: Int,
    )

    @Serializable
    data class GeofenceEventPayload(
        val id: String,
        val geofenceId: String,
        val geofenceName: String,
        val type: String,
        val ts: Long,
        val lat: Double,
        val lng: Double,
        val accuracy: Float,
        val batteryLevel: Int,
        val recordingFile: String,
        val recordingDurationMs: Long,
        val notifiedWeb: Boolean,
        val lockedApps: List<String>,
    )

    override fun onLocationChanged(location: Location) {
        try {
            val point = LocationTrackingHelper.pointFromLocation(location)
            LocationTrackingHelper.appendPoint(point)
            // stream to web
            val totalPoints = LocationTrackingHelper.countPoints()
            val payload = LocationUpdatePayload(
                ts = point.ts,
                lat = point.lat,
                lng = point.lng,
                accuracy = point.accuracy,
                speed = point.speed,
                altitude = point.altitude,
                bearing = point.bearing,
                battery = point.batteryLevel,
                charging = point.charging,
                provider = point.provider,
                totalPoints = totalPoints,
            )
            sendEvent(WebSocketEvent(EventType.LOCATION_UPDATE, JsonHelper.jsonEncode(payload)))
            // evaluate geofences
            evaluateGeofences(point)
            // release any expired app-locks
            checkPendingUnlocks()
        } catch (e: Throwable) {
            LogCat.e("LocationTrackingService.onLocationChanged failed: ${e.message}", e)
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun evaluateGeofences(point: LocationTrackingHelper.Point) {
        val fences = GeofencingHelper.listFences().filter { it.enabled }
        if (fences.isEmpty()) return
        val insideMap = GeofencingHelper.readInside().toMutableMap()
        var changed = false
        for (g in fences) {
            val nowInside = GeofencingHelper.isInside(g, point.lat, point.lng)
            val prevInside = insideMap[g.id] ?: false
            if (nowInside != prevInside) {
                changed = true
                insideMap[g.id] = nowInside
                val type = if (nowInside) "enter" else "exit"
                if ((nowInside && g.triggerEnter) || (!nowInside && g.triggerExit)) {
                    fireGeofenceTrigger(g, type, point)
                }
            }
        }
        if (changed) GeofencingHelper.writeInside(insideMap)
    }

    private fun fireGeofenceTrigger(
        g: GeofencingHelper.Geofence,
        type: String,
        point: LocationTrackingHelper.Point,
    ) {
        val eventId = StringHelper.shortUUID()
        var recordingPath = ""
        var recordingMs = 0L
        if (g.actionRecordAudio && g.recordAudioSec > 0) {
            try {
                val started = GeofenceAudioRecorderHelper.start(g.id, g.name, eventId, this)
                if (started) {
                    val sec = g.recordAudioSec.coerceIn(5, 600)
                    listenerHandler?.postDelayed({
                        try {
                            val audio = GeofenceAudioRecorderHelper.stop()
                            if (audio != null) {
                                sendEvent(WebSocketEvent(EventType.GEOFENCE_AUDIO_CHANGED, ""))
                                LogCat.d("Geofence audio saved: ${audio.absPath} (${audio.durationMs}ms)")
                            }
                        } catch (_: Throwable) {}
                    }, sec * 1000L)
                    recordingMs = sec * 1000L
                    recordingPath = "(in_progress)"
                }
            } catch (e: Throwable) {
                LogCat.e("Geofence audio start failed: ${e.message}", e)
            }
        }
        val lockedApps = mutableListOf<String>()
        if (g.actionLockApps && g.lockedAppIds.isNotEmpty()) {
            for (pkg in g.lockedAppIds) {
                try {
                    AppBlockHelper.setBlocked(pkg, true, this)
                    lockedApps.add(pkg)
                    if (g.lockAppsDurationSec > 0) {
                        pendingUnlocks[pkg] = System.currentTimeMillis() + g.lockAppsDurationSec * 1000L
                    }
                } catch (_: Throwable) {}
            }
        }
        val event = GeofencingHelper.GeofenceEvent(
            id = eventId,
            geofenceId = g.id,
            geofenceName = g.name,
            type = type,
            ts = point.ts,
            lat = point.lat,
            lng = point.lng,
            accuracy = point.accuracy,
            batteryLevel = point.batteryLevel,
            recordingFile = recordingPath,
            recordingDurationMs = recordingMs,
            notifiedWeb = g.actionNotifyWeb,
            lockedApps = lockedApps,
        )
        GeofencingHelper.appendEvent(event)
        if (g.actionNotifyWeb) {
            try {
                val payload = GeofenceEventPayload(
                    id = event.id,
                    geofenceId = event.geofenceId,
                    geofenceName = event.geofenceName,
                    type = event.type,
                    ts = event.ts,
                    lat = event.lat,
                    lng = event.lng,
                    accuracy = event.accuracy,
                    batteryLevel = event.batteryLevel,
                    recordingFile = event.recordingFile,
                    recordingDurationMs = event.recordingDurationMs,
                    notifiedWeb = event.notifiedWeb,
                    lockedApps = event.lockedApps,
                )
                sendEvent(WebSocketEvent(EventType.GEOFENCE_EVENT, JsonHelper.jsonEncode(payload)))
            } catch (e: Throwable) {
                LogCat.e("Geofence event broadcast failed: ${e.message}", e)
            }
        }
    }

    private fun checkPendingUnlocks() {
        if (pendingUnlocks.isEmpty()) return
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        for ((pkg, dueAt) in pendingUnlocks) {
            if (now >= dueAt) {
                try { AppBlockHelper.setBlocked(pkg, false, this) } catch (_: Throwable) {}
                toRemove.add(pkg)
            }
        }
        for (p in toRemove) pendingUnlocks.remove(p)
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_SECRET
            nm.createNotificationChannel(ch)
        }
    }

    private fun startInForeground() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PlainApp")
            .setContentText("Location streaming active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
        } catch (e: Throwable) {
            LogCat.e("LocationTrackingService startForeground failed: ${e.message}", e)
        }
    }

    companion object {
        const val ACTION_STOP = "com.ismartcoding.plain.action.STOP_LOCATION_TRACKING"
        const val ACTION_REFRESH_INTERVAL = "com.ismartcoding.plain.action.REFRESH_LOCATION_INTERVAL"
        const val CHANNEL_ID = "plain_location_tracking_channel"
        const val NOTIF_ID = 0xFA00

        @Volatile var instance: LocationTrackingService? = null

        fun isRunning(): Boolean = instance != null

        fun start(ctx: Context = MainApp.instance) {
            try {
                val intent = Intent(ctx, LocationTrackingService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
            } catch (e: Throwable) { LogCat.e("LocationTrackingService.start failed: ${e.message}", e) }
        }

        fun stop(ctx: Context = MainApp.instance) {
            try {
                val intent = Intent(ctx, LocationTrackingService::class.java).apply { action = ACTION_STOP }
                ctx.startService(intent)
            } catch (e: Throwable) { LogCat.e("LocationTrackingService.stop failed: ${e.message}", e) }
        }

        fun refreshInterval(ctx: Context = MainApp.instance) {
            if (!isRunning()) return
            try {
                val intent = Intent(ctx, LocationTrackingService::class.java).apply { action = ACTION_REFRESH_INTERVAL }
                ctx.startService(intent)
            } catch (_: Throwable) {}
        }
    }
}
