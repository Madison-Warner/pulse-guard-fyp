package com.example.pulseguard.sensor

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class HeartRateSensorManager(
    private val context: Context,
    private val onBpm: (bpm: Int, timestamp: Long) -> Unit,
){
    companion object{
        private const val TAG = "HeartRateSensorManager"
    }

    private var healthTrackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d(TAG, "Health Platform connected")

            val service = healthTrackingService ?: return
            val supported = service.trackingCapability.supportHealthTrackerTypes

            if (!supported.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                Log.e(TAG, "HEART_RATE_CONTINUOUS not supported on this device")
                return
            }

            heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
            heartRateTracker?.setEventListener(heartRateListener)

            Log.d(TAG, "Heart rate listener set")
        }

        override fun onConnectionEnded() {
            Log.d(TAG, "Health Platform disconnected")
        }

        override fun onConnectionFailed(e: HealthTrackerException?) {
            Log.e(TAG, "Connection failed: ${e?.errorCode}", e)
        }
    }

    private val heartRateListener =  object : HealthTracker.TrackerEventListener{
        override fun onDataReceived(dataPoints: List<DataPoint?>) {
            for (dp in dataPoints) {
                if (dp == null) continue

                // BPM value for the datapoint
                val bpm = dp.getValue(ValueKey.HeartRateSet.HEART_RATE)
                val ts = dp.timestamp

                Log.d(TAG, "HR callback bpm=$bpm ts=$ts")
                onBpm(bpm, ts)
            }
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "HR flush completed")
        }

        override fun onError(error: HealthTracker.TrackerError?) {
            Log.e(TAG, "HR tracker error: $error")
        }
    }

    fun start() {
        if (healthTrackingService != null) return

        healthTrackingService = HealthTrackingService(connectionListener, context)
        healthTrackingService?.connectService()

        Log.d(TAG, "Connecting to Health Platform...")
    }

    fun stop() {
        heartRateTracker?.unsetEventListener()
        heartRateTracker = null

        healthTrackingService?.disconnectService()
        healthTrackingService = null

        Log.d(TAG, "Stopped HR tracking")
    }
}