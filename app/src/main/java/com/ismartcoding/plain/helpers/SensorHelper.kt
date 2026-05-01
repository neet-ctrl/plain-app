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
import kotlin.math.pow
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

data class GyroscopeData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
)

data class MagnetometerData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val heading: Float = 0f,         // compass degrees 0-360
    val cardinalDir: String = "N",
)

data class BarometerData(
    val pressureHpa: Float = 0f,
    val altitudeMeters: Float = 0f,
)

object SensorHelper : SensorEventListener {

    @Volatile var latestAccel = AccelerometerData()
        private set
    @Volatile var latestGyro = GyroscopeData()
        private set
    @Volatile var latestMag = MagnetometerData()
        private set
    @Volatile var latestBarometer = BarometerData()
        private set
    @Volatile var ambientLightLux: Float = -1f
        private set
    @Volatile var stepCount: Long = -1L
        private set
    @Volatile var proximityNear: Boolean = false
        private set
    @Volatile var proximityDistance: Float = -1f
        private set
    @Volatile var proximityMaxRange: Float = 1f
        private set
    @Volatile var ambientTemperature: Float = Float.MIN_VALUE
        private set
    @Volatile var heartRate: Float = -1f
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

    // --------------- All Sensors ---------------

    fun ensureSensorsStarted() {
        if (sensorsStarted) return
        sensorsStarted = true
        mainHandler.post {
            val sm = MainApp.instance.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager = sm
            fun reg(type: Int) {
                sm.getDefaultSensor(type)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, mainHandler) }
            }
            reg(Sensor.TYPE_GRAVITY)
            reg(Sensor.TYPE_LINEAR_ACCELERATION)
            reg(Sensor.TYPE_LIGHT)
            reg(Sensor.TYPE_GYROSCOPE)
            reg(Sensor.TYPE_MAGNETIC_FIELD)
            reg(Sensor.TYPE_PRESSURE)
            reg(Sensor.TYPE_STEP_COUNTER)
            reg(Sensor.TYPE_PROXIMITY)
            reg(Sensor.TYPE_AMBIENT_TEMPERATURE)
            reg(Sensor.TYPE_HEART_RATE)
        }
    }

    fun hasSensor(type: Int): Boolean {
        val sm = sensorManager
            ?: (MainApp.instance.getSystemService(Context.SENSOR_SERVICE) as SensorManager).also { sensorManager = it }
        return sm.getDefaultSensor(type) != null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val angleDeg = (atan2(v[1].toDouble(), v[0].toDouble()) * (180.0 / Math.PI)).toFloat()
                val cur = latestAccel
                latestAccel = cur.copy(
                    gravityX = round2(v[0]),
                    gravityY = round2(v[1]),
                    gravityZ = round2(v[2]),
                    angle = angleDeg.toInt().toFloat(),
                )
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                val cur = latestAccel
                latestAccel = cur.copy(
                    motionX = round2(v[0]),
                    motionY = round2(v[1]),
                    motionZ = round2(v[2]),
                    vibrationMagnitude = round2(mag),
                )
            }
            Sensor.TYPE_LIGHT -> {
                ambientLightLux = v[0]
            }
            Sensor.TYPE_GYROSCOPE -> {
                val mag = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                latestGyro = GyroscopeData(round3(v[0]), round3(v[1]), round3(v[2]), round3(mag))
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val heading = computeHeading(v[0], v[1])
                latestMag = MagnetometerData(round2(v[0]), round2(v[1]), round2(v[2]), round1(heading), bearingToCardinal(heading))
            }
            Sensor.TYPE_PRESSURE -> {
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, v[0])
                latestBarometer = BarometerData(round2(v[0]), round1(alt))
            }
            Sensor.TYPE_STEP_COUNTER -> {
                stepCount = v[0].toLong()
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityMaxRange = event.sensor.maximumRange
                proximityDistance = v[0]
                proximityNear = v[0] < proximityMaxRange
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                ambientTemperature = round1(v[0])
            }
            Sensor.TYPE_HEART_RATE -> {
                if (v[0] > 0) heartRate = round1(v[0])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun computeHeading(mx: Float, my: Float): Float {
        var heading = (Math.toDegrees(atan2(my.toDouble(), mx.toDouble()))).toFloat()
        if (heading < 0) heading += 360f
        return heading
    }

    private fun bearingToCardinal(deg: Float): String {
        val dirs = arrayOf("N","NE","E","SE","S","SW","W","NW")
        return dirs[(((deg + 22.5f) / 45f).toInt() % 8)]
    }

    private fun round1(f: Float): Float = (Math.round(f * 10) / 10f)
    private fun round2(f: Float): Float = (Math.round(f * 100) / 100f)
    private fun round3(f: Float): Float = (Math.round(f * 1000) / 1000f)

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
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2,
            )
            audioRecord = ar
            ar.startRecording()
            soundThread = Thread {
                val buf = ShortArray(bufSize)
                while (isSoundMeterRunning && !Thread.currentThread().isInterrupted) {
                    val read = ar.read(buf, 0, bufSize)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) { val s = buf[i] / 32768.0; sumSq += s * s }
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
