package com.ismartcoding.plain.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.sqrt

data class AccelerometerData(
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val gravityZ: Float = 0f,
    val motionX: Float = 0f,
    val motionY: Float = 0f,
    val motionZ: Float = 0f,
    val angle: Float = 0f,
    val vibrationMagnitude: Float = 0f,
)

object SensorHelper : SensorEventListener {

    @Volatile var latestAccel = AccelerometerData()
        private set

    @Volatile var soundLevelDb: Float = 0f
        private set

    @Volatile var isSoundMeterRunning = false
        private set

    @Volatile private var sensorsStarted = false
    private var sensorManager: SensorManager? = null
    private var audioRecord: AudioRecord? = null
    private var soundThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // --------------- Accelerometer / Vibration ---------------

    fun ensureSensorsStarted() {
        if (sensorsStarted) return
        sensorsStarted = true
        mainHandler.post {
            val sm = MainApp.instance.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager = sm
            sm.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler)
            }
            sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val cur = latestAccel
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val angleDeg = (atan2(v[1].toDouble(), v[0].toDouble()) * (180.0 / Math.PI)).toFloat()
                latestAccel = cur.copy(
                    gravityX = round1(v[0]),
                    gravityY = round1(v[1]),
                    gravityZ = round1(v[2]),
                    angle = angleDeg.toInt().toFloat(),
                )
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                latestAccel = cur.copy(
                    motionX = round1(v[0]),
                    motionY = round1(v[1]),
                    motionZ = round1(v[2]),
                    vibrationMagnitude = round1(mag),
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun round1(f: Float): Float = (Math.round(f * 10) / 10f)

    // --------------- Sound Meter ---------------

    fun startSoundMeter() {
        if (isSoundMeterRunning) return
        isSoundMeterRunning = true
        val sampleRate = 44100
        val bufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        try {
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2,
            )
            audioRecord = ar
            ar.startRecording()
            soundThread = Thread {
                val buf = ShortArray(bufSize)
                while (isSoundMeterRunning && !Thread.currentThread().isInterrupted) {
                    val read = ar.read(buf, 0, bufSize)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = buf[i] / 32768.0
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / read)
                        soundLevelDb = if (rms > 0) (20.0 * log10(rms) + 90.0).toFloat().coerceIn(0f, 120f) else 0f
                    }
                }
            }.also { it.isDaemon = true; it.start() }
        } catch (e: Exception) {
            LogCat.e("startSoundMeter failed: ${e.message}", e)
            isSoundMeterRunning = false
        }
    }

    fun stopSoundMeter() {
        isSoundMeterRunning = false
        soundThread?.interrupt()
        soundThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        soundLevelDb = 0f
    }
}
