package com.ismartcoding.plain.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.location.LocationManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object IntruderFrontCamera {

    @SuppressLint("MissingPermission")
    suspend fun capture(ctx: Context = MainApp.instance): File? {
        return suspendCancellableCoroutine { cont ->
            val handlerThread = HandlerThread("IntruderCamCapture").apply { start() }
            val handler = Handler(handlerThread.looper)
            var cameraDevice: CameraDevice? = null
            var imageReader: ImageReader? = null

            fun cleanup() {
                try { cameraDevice?.close() } catch (_: Throwable) {}
                try { imageReader?.close() } catch (_: Throwable) {}
                try { handlerThread.quitSafely() } catch (_: Throwable) {}
            }

            cont.invokeOnCancellation { cleanup() }

            try {
                val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                } ?: cameraManager.cameraIdList.firstOrNull() ?: run {
                    cleanup()
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val file = File(ctx.cacheDir, "intruder_front_${System.currentTimeMillis()}.jpg")
                imageReader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)
                imageReader!!.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            file.writeBytes(bytes)
                            cleanup()
                            if (cont.isActive) cont.resume(if (file.length() > 0) file else null)
                        } catch (_: Exception) {
                            image.close()
                            cleanup()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }, handler)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            camera.createCaptureSession(
                                listOf(imageReader!!.surface),
                                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                        try {
                                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                addTarget(imageReader!!.surface)
                                                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                                            }
                                            session.capture(req.build(), null, handler)
                                        } catch (_: Exception) {
                                            cleanup()
                                            if (cont.isActive) cont.resume(null)
                                        }
                                    }
                                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                        cleanup()
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }, handler
                            )
                        } catch (_: Exception) {
                            cleanup()
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        cleanup()
                        if (cont.isActive && !cont.isCompleted) cont.resume(null)
                    }
                }, handler)
            } catch (e: Exception) {
                LogCat.e("IntruderFrontCamera.capture failed: ${e.message}")
                cleanup()
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(ctx: Context = MainApp.instance): Triple<Double, Double, Boolean> {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            for (provider in providers) {
                try {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (best == null || loc.accuracy < best.accuracy) best = loc
                } catch (_: Throwable) {}
            }
            if (best != null) Triple(best.latitude, best.longitude, true)
            else Triple(0.0, 0.0, false)
        } catch (_: Throwable) {
            Triple(0.0, 0.0, false)
        }
    }

    fun fireAndForget(
        trigger: String,
        triggerDetail: String,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val photo = capture()
                val (lat, lng, hasLoc) = getLastLocation()
                IntruderCaptureHelper.save(
                    trigger = trigger,
                    triggerDetail = triggerDetail,
                    photoFile = photo,
                    lat = lat,
                    lng = lng,
                    hasLocation = hasLoc,
                )
            } catch (e: Throwable) {
                LogCat.e("IntruderFrontCamera.fireAndForget failed: ${e.message}")
            }
        }
    }
}
