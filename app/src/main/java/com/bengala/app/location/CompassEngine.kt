package com.bengala.app.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Azimut del dispositivo (grados desde el norte) usando el sensor de rotación.
 * Alimenta el radar para que "arriba" sea hacia donde apuntas el teléfono.
 */
class CompassEngine(
    context: Context,
    private val onAzimuth: (Float) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var smoothed = Float.NaN

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f

        // Suavizado circular para que el radar no tiemble.
        smoothed = if (smoothed.isNaN()) azimuth else {
            var delta = azimuth - smoothed
            if (delta > 180) delta -= 360
            if (delta < -180) delta += 360
            (smoothed + delta * 0.15f + 360f) % 360f
        }
        onAzimuth(smoothed)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
