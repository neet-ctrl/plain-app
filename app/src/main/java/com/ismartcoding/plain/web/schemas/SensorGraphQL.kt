package com.ismartcoding.plain.web.schemas

import android.hardware.Sensor
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.Permissions
import com.ismartcoding.plain.helpers.BarometerData
import com.ismartcoding.plain.helpers.GyroscopeData
import com.ismartcoding.plain.helpers.MagnetometerData
import com.ismartcoding.plain.helpers.SensorHelper
import kotlinx.serialization.Serializable

@Serializable
data class AccelerometerModel(
    val gravityX: Float, val gravityY: Float, val gravityZ: Float,
    val motionX: Float, val motionY: Float, val motionZ: Float,
    val angle: Float, val vibrationMagnitude: Float,
)

@Serializable
data class GyroscopeModel(
    val x: Float, val y: Float, val z: Float, val magnitude: Float,
)

@Serializable
data class MagnetometerModel(
    val x: Float, val y: Float, val z: Float,
    val heading: Float, val cardinalDir: String,
)

@Serializable
data class BarometerModel(
    val pressureHpa: Float, val altitudeMeters: Float,
)

@Serializable
data class ProximityModel(
    val near: Boolean, val distanceCm: Float, val maxRangeCm: Float, val supported: Boolean,
)

fun SchemaBuilder.addSensorSchema() {

    type<AccelerometerModel>()
    type<GyroscopeModel>()
    type<MagnetometerModel>()
    type<BarometerModel>()
    type<ProximityModel>()

    // ------------- Accelerometer / Vibration -------------
    query("accelerometerData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            val d = SensorHelper.latestAccel
            AccelerometerModel(d.gravityX, d.gravityY, d.gravityZ, d.motionX, d.motionY, d.motionZ, d.angle, d.vibrationMagnitude)
        }
    }

    // ------------- Ambient Light -------------
    query("ambientLight") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            SensorHelper.ambientLightLux
        }
    }

    // ------------- Gyroscope -------------
    query("gyroscopeData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            val d = SensorHelper.latestGyro
            GyroscopeModel(d.x, d.y, d.z, d.magnitude)
        }
    }

    query("gyroscopeSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_GYROSCOPE) }
    }

    // ------------- Magnetometer / Compass -------------
    query("magnetometerData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            val d = SensorHelper.latestMag
            MagnetometerModel(d.x, d.y, d.z, d.heading, d.cardinalDir)
        }
    }

    query("magnetometerSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    }

    // ------------- Barometer -------------
    query("barometerData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            val d = SensorHelper.latestBarometer
            BarometerModel(d.pressureHpa, d.altitudeMeters)
        }
    }

    query("barometerSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_PRESSURE) }
    }

    // ------------- Step Counter -------------
    query("stepCount") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            SensorHelper.stepCount
        }
    }

    query("pedometerSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_STEP_COUNTER) }
    }

    // ------------- Proximity -------------
    query("proximityData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            ProximityModel(
                near = SensorHelper.proximityNear,
                distanceCm = SensorHelper.proximityDistance,
                maxRangeCm = SensorHelper.proximityMaxRange,
                supported = SensorHelper.hasSensor(Sensor.TYPE_PROXIMITY),
            )
        }
    }

    // ------------- Ambient Temperature -------------
    query("ambientTemperature") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            SensorHelper.ambientTemperature
        }
    }

    query("temperatureSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) }
    }

    // ------------- Heart Rate -------------
    query("heartRate") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            SensorHelper.heartRate
        }
    }

    query("heartRateSupported") {
        resolver { -> SensorHelper.hasSensor(Sensor.TYPE_HEART_RATE) }
    }

    // ------------- Sound Meter -------------
    query("soundLevel") {
        resolver { -> SensorHelper.soundLevelDb }
    }

    query("soundMeterRunning") {
        resolver { -> SensorHelper.isSoundMeterRunning }
    }

    mutation("startSoundMeter") {
        resolver { ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.RECORD_AUDIO))
            SensorHelper.startSoundMeter()
            true
        }
    }

    mutation("stopSoundMeter") {
        resolver { ->
            SensorHelper.stopSoundMeter()
            true
        }
    }
}
