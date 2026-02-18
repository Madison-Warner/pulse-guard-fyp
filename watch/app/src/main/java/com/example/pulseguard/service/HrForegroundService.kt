package com.example.pulseguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pulseguard.data.HeartRateRepository
import com.example.pulseguard.sensor.AndroidHeartRateSensor

class HrForegroundService : Service() {
    companion object {
        private const val TAG = "HrForegroundService"
        private const val CHANNEL_ID = "pulseguard_monitoring"
        private const val CHANNEL_NAME = "PulseGuard Monitoring"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.pulseguard.ACTION_STOP"
        const val ACTION_HR_UPDATE = "com.example.pulseguard.ACTION_HR_UPDATE"
        const val EXTRA_BPM = "extra_bpm"
        const val EXTRA_RUNNING = "extra_running"
    }

    // Failed Samsung Sensor Manger
    // private lateinit var hrManager: com.example.pulseguard.sensor.HeartRateSensorManager

    // Android SensorManager
    private var androidHRSensor: AndroidHeartRateSensor? = null
    private lateinit var repo: HeartRateRepository
    private var sensorStarted = false


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        repo = HeartRateRepository(this)
        createNotificationChannel()
        // Failed Samsung Sensor Manger
        /*
        hrManager = com.example.pulseguard.sensor.HeartRateSensorManager(
            context = this,
            onBpm = { bpm, ts ->
                Log.d(TAG, "HR callback bpm=$bpm ts=$ts")
            }
        )
        */
        // Android SensorManager
        androidHRSensor = AndroidHeartRateSensor(
            context = this,
            onBpm = { bpm, ts ->
                Log.d(TAG, " BPM=$bpm ts=$ts")
                repo.append(ts, bpm) // later: send to C++ / anomaly processor
                broadcastStatus(bpm = bpm, running = true)
            }
        )

        Log.d(TAG, "Logging HR to: ${repo.path()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() - starting foreground")


        // Handle stop action
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "ACTION_STOP received")
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        // Start foreground and broadcast immediately
        startForeground(NOTIFICATION_ID, buildNotification())
        broadcastStatus(bpm = null, running = true)
        Log.d(TAG, "FGS started")

        if (!sensorStarted) {
            try {
                val ok = androidHRSensor?.start() ?: false
                sensorStarted = ok
                Log.d(TAG, "Android HR sensor started? $ok")
            } catch (t: Throwable) {
                Log.e(TAG, "Starting Android HR sensor failed", t)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Keep running unless it is explicitly stopped
        return START_NOT_STICKY
    }

    private fun stopTrackingAndSelf(){
        try {
            androidHRSensor?.stop()
            sensorStarted = false
        } catch (t: Throwable){
            Log.e(TAG, "Error stopping HR sensor", t)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastStatus(bpm = null, running = false)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        try{
            androidHRSensor?.stop()
            sensorStarted = false
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this,CHANNEL_ID)
            .setContentTitle("PulseGuard running")
            .setContentText("Monitoring service active")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun broadcastStatus(bpm: Int?, running: Boolean) {
        val intent = Intent(ACTION_HR_UPDATE).apply {
            setPackage(packageName) // Keeps it inside the app
            putExtra(EXTRA_RUNNING, running)
            if (bpm != null) putExtra(EXTRA_BPM, bpm)
        }
        sendBroadcast(intent)
    }
}
