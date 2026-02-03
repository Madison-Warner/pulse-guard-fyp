package com.example.pulseguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os. PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class HrForegroundService : Service() {
    companion object {
        private const val TAG = "HrForegroundService"
        private const val CHANNEL_ID = "pulseguard_monitoring"
        private const val CHANNEL_NAME = "PulseGuard Monitoring"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.pulseguard.ACTION_STOP"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() - starting foreground")

        // Start foreground immediately
        startForeground(NOTIFICATION_ID, buildNotification())

        acquireCpuWakeLock()

        // Smoke-test loop: log every 5 seconds
        serviceScope.launch {
            while (isActive) {
                Log.d(TAG, "Service allive @ ${System.currentTimeMillis()}")
                delay(5000)
            }
        }

        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Keep running unless it is explicitly stopped
        return START_STICKY
    }

    private fun acquireCpuWakeLock() {
        if (wakeLock?.isHeld == true) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PulseGuard:HrForegroundService"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }

        Log.d(TAG, "CPU WakeLock acquired")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        serviceJob.cancel()
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

    private fun createNotificationChannel(){
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
}
