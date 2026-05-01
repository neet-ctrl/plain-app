package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.Permissions
import com.ismartcoding.plain.helpers.SensorHelper
import kotlinx.serialization.Serializable

@Serializable
data class AccelerometerModel(
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val motionX: Float,
    val motionY: Float,
    val motionZ: Float,
    val angle: Float,
    val vibrationMagnitude: Float,
)

fun SchemaBuilder.addSensorSchema() {

    type<AccelerometerModel>()

    // ------------- Accelerometer / Vibration -------------

    query("accelerometerData") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            val d = SensorHelper.latestAccel
            AccelerometerModel(
                gravityX = d.gravityX,
                gravityY = d.gravityY,
                gravityZ = d.gravityZ,
                motionX = d.motionX,
                motionY = d.motionY,
                motionZ = d.motionZ,
                angle = d.angle,
                vibrationMagnitude = d.vibrationMagnitude,
            )
        }
    }

    // ------------- Ambient Light -------------

    query("ambientLight") {
        resolver { ->
            SensorHelper.ensureSensorsStarted()
            SensorHelper.ambientLightLux
        }
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
