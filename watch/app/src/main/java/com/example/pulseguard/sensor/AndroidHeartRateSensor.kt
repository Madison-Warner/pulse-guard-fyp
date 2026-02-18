package com.example.pulseguard.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log


class AndroidHeartRateSensor (
    context: Context,
    private val onBpm: (bpm: Int, timestampMillis: Long) -> Unit
) : SensorEventListener {
    companion object { private const val TAG = "AndroidHrSensor" }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val hrSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    fun start(): Boolean {
        if (hrSensor == null) {
            Log.e(TAG, "TYPE_HEART_RATE sensor not available on this devices")
            return false
        }
        val ok = sensorManager.registerListener(
            this,
            hrSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.d(TAG, "registeredListener ok=$ok")
        return ok
    }

    fun stop(){
        sensorManager.unregisterListener(this)
        Log.d(TAG, "unregistered listener")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
        val bpm = event.values.firstOrNull()?.toInt() ?: return
        val ts = System.currentTimeMillis()

        if (bpm in 1..240){
            Log.d(TAG, "bpm=$bpm")
            onBpm(bpm, ts)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuracy changed: \${sensor?.name} accuracy=\$accuracy")
    }
}