package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.Permissions
import com.ismartcoding.plain.helpers.UtilitiesHelper
import com.ismartcoding.plain.features.PackageHelper
import com.ismartcoding.plain.services.AppBlockHelper
import com.ismartcoding.plain.services.LiveCallTracker
import com.ismartcoding.plain.services.LiveCallStateData
import com.ismartcoding.plain.services.LocateRingService
import com.ismartcoding.plain.services.MessageOverlayService
import com.ismartcoding.plain.services.NotificationLogHelper
import com.ismartcoding.plain.services.PlainAccessibilityService
import com.ismartcoding.plain.services.TimelineEntryData
import com.ismartcoding.plain.services.TimelineHelper
import com.ismartcoding.plain.web.models.toModel

data class DeviceLocationModel(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val plusCode: String,
    val provider: String,
    val timestamp: Long,
    val googleMapsUrl: String,
)

data class VolumeLevel(val stream: String, val percent: Int)

data class TimeLimitModel(val packageId: String, val appName: String, val dailyMs: Long, val usedMs: Long)

data class BedtimeModel(
    val enabled: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val packages: List<String>,
)

data class LaunchHistoryModel(val packageId: String, val appName: String, val timestamp: Long)

data class BlockedAppsState(
    val blocked: List<String>,
    val timeLimits: List<TimeLimitModel>,
    val bedtime: BedtimeModel,
    val accessibilityServiceEnabled: Boolean,
)

fun SchemaBuilder.addUtilitiesSchema() {

    // ---- Models ----
    type<DeviceLocationModel>()
    type<VolumeLevel>()
    type<TimeLimitModel>()
    type<BedtimeModel>()
    type<LaunchHistoryModel>()
    type<BlockedAppsState>()
    type<TimelineEntryData>()
    type<LiveCallStateData>()

    // ---- Mutations: simple commands ----

    mutation("speakMessage") {
        resolver { text: String, locale: String? ->
            UtilitiesHelper.speak(text, locale)
            true
        }
    }

    mutation("stopSpeaking") {
        resolver { ->
            UtilitiesHelper.stopSpeaking(); true
        }
    }

    mutation("showMessage") {
        resolver { title: String, message: String, durationMs: Int, blocking: Boolean ->
            if (!android.provider.Settings.canDrawOverlays(MainApp.instance)) {
                throw GraphQLError("SYSTEM_ALERT_WINDOW permission not granted")
            }
            MessageOverlayService.show(
                title = title.ifBlank { "PlainApp" },
                message = message,
                durationMs = durationMs.toLong().coerceIn(500L, 60_000L),
                blocking = blocking,
            )
            true
        }
    }

    mutation("vibrate") {
        resolver { durationMs: Int ->
            UtilitiesHelper.vibrate(durationMs.toLong())
            true
        }
    }

    mutation("locatePhone") {
        resolver { start: Boolean ->
            if (start) LocateRingService.start() else LocateRingService.stop()
            true
        }
    }

    query("locateRunning") {
        resolver { -> LocateRingService.running }
    }

    mutation("wakeScreen") {
        resolver { durationMs: Int ->
            UtilitiesHelper.wakeScreen(durationMs.toLong())
            true
        }
    }

    mutation("setTorch") {
        resolver { on: Boolean ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.CAMERA))
            UtilitiesHelper.setTorch(on)
        }
    }

    query("torchOn") {
        resolver { -> UtilitiesHelper.isTorchOn() }
    }

    query("torchPattern") {
        resolver { -> UtilitiesHelper.getTorchPatternType() }
    }

    mutation("startTorchPattern") {
        resolver { type: String, onMs: Int, offMs: Int ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.CAMERA))
            UtilitiesHelper.startTorchPattern(type, onMs.toLong(), offMs.toLong())
            true
        }
    }

    mutation("stopTorchPattern") {
        resolver { ->
            UtilitiesHelper.stopTorchPattern()
            true
        }
    }

    // ---- Volume ----

    query("volumes") {
        resolver { ->
            UtilitiesHelper.getVolumes().map { (k, v) -> VolumeLevel(k, v) }
        }
    }

    mutation("setVolume") {
        resolver { stream: String, percent: Int ->
            UtilitiesHelper.setVolume(stream, percent)
        }
    }

    // ---- Brightness ----

    query("brightness") {
        resolver { -> UtilitiesHelper.getBrightness() }
    }

    mutation("setBrightness") {
        resolver { percent: Int ->
            if (!UtilitiesHelper.setBrightness(percent)) {
                throw GraphQLError("WRITE_SETTINGS permission not granted")
            }
            true
        }
    }

    // ---- Location ----

    query("deviceLocation") {
        resolver { ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.ACCESS_FINE_LOCATION))
            val loc = UtilitiesHelper.getLocation()
                ?: throw GraphQLError("Could not get device location. Make sure GPS is on.")
            DeviceLocationModel(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = loc.accuracyMeters,
                plusCode = loc.plusCode,
                provider = loc.provider,
                timestamp = loc.timestamp,
                googleMapsUrl = "https://www.google.com/maps?q=${loc.latitude},${loc.longitude}",
            )
        }
    }

    // ---- Mobile data deep-link ----

    mutation("openDataSettings") {
        resolver { -> UtilitiesHelper.openDataSettings() }
    }

    mutation("playAudioBase64") {
        resolver { data: String, mime: String -> UtilitiesHelper.playAudioBase64(data, mime) }
    }

    // ---- App block / parental controls ----

    query("blockedAppsState") {
        resolver { ->
            val blocked = AppBlockHelper.getBlockedSet().toList()
            val limits = AppBlockHelper.getTimeLimits()
            val usage = AppBlockHelper.getUsageTodayWithLive(
                PlainAccessibilityService.currentForegroundPackage,
                PlainAccessibilityService.currentForegroundEnteredAt,
            )
            val timeLimits = limits.map { (k, v) ->
                val name = try { PackageHelper.getLabel(k).ifEmpty { k } } catch (_: Throwable) { k }
                TimeLimitModel(k, name, v, usage[k] ?: 0L)
            }
            val b = AppBlockHelper.getBedtime()
            BlockedAppsState(
                blocked = blocked,
                timeLimits = timeLimits,
                bedtime = BedtimeModel(b.enabled, b.startMinutes, b.endMinutes, b.packages),
                accessibilityServiceEnabled = PlainAccessibilityService.isEnabled(MainApp.instance),
            )
        }
    }

    mutation("setAppBlocked") {
        resolver { packageId: String, blocked: Boolean ->
            AppBlockHelper.setBlocked(packageId, blocked); true
        }
    }

    mutation("setAppTimeLimit") {
        resolver { packageId: String, dailyMs: Int ->
            AppBlockHelper.setTimeLimit(packageId, dailyMs.toLong()); true
        }
    }

    mutation("removeAppTimeLimit") {
        resolver { packageId: String ->
            AppBlockHelper.removeTimeLimit(packageId); true
        }
    }

    mutation("setBedtime") {
        resolver { enabled: Boolean, startMinutes: Int, endMinutes: Int, packages: List<String> ->
            AppBlockHelper.setBedtime(
                AppBlockHelper.Bedtime(enabled, startMinutes, endMinutes, packages)
            ); true
        }
    }

    query("launchHistory") {
        resolver { limit: Int ->
            val list = AppBlockHelper.getHistory().asReversed()
            val cap = if (limit <= 0) 200 else limit
            list.take(cap).map {
                val name = try { PackageHelper.getLabel(it.pkg).ifEmpty { it.pkg } } catch (_: Throwable) { it.pkg }
                LaunchHistoryModel(it.pkg, name, it.ts)
            }
        }
    }

    mutation("clearLaunchHistory") {
        resolver { -> AppBlockHelper.clearHistory(); true }
    }

    // ---- Notification log (persistent on server) ----
    query("notificationLog") {
        resolver { ->
            NotificationLogHelper.all().map { it.toModel() }
        }
    }
    mutation("clearNotificationLog") {
        resolver { -> NotificationLogHelper.clear(); true }
    }

    // ---- Timeline (persistent on server) ----
    query("timelineEntries") {
        resolver { limit: Int ->
            val cap = if (limit <= 0) 500 else limit
            TimelineHelper.all(cap)
        }
    }
    query("timelineStartedAt") {
        resolver { -> TimelineHelper.serverStartedAt }
    }

    // ---- Live call ----
    query("liveCallState") {
        resolver { -> LiveCallTracker.snapshot() }
    }
    mutation("acceptLiveCall") {
        resolver { -> LiveCallTracker.acceptFromPanel(); true }
    }
    mutation("endLiveCall") {
        resolver { -> LiveCallTracker.end(); true }
    }
    mutation("setLiveCallMuted") {
        resolver { muted: Boolean -> LiveCallTracker.setMuted(muted); true }
    }
    mutation("ensureLiveCallListening") {
        // Suspending: kicks off LiveMicService and waits up to 4 s for it to
        // finish initialising. Returning before the service is ready was the
        // root cause of the "Connecting…" hang — the browser would send its
        // `ready` signaling immediately on resolve, but LiveMicService.instance
        // was still null so the message was silently dropped and no offer
        // ever came back.
        resolver { ->
            LiveCallTracker.ensureListening()
            LiveCallTracker.awaitListenerReady(4000L)
        }
    }
}
