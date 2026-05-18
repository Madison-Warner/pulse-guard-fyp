package com.example.pulseguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pulseguard.alert.AlertController
import com.example.pulseguard.ble.BleSender
import com.example.pulseguard.data.HeartRateRepository
import com.example.pulseguard.sensor.AndroidHeartRateSensor
import com.example.pulseguard.processing.AnomalyProcessor
import kotlinx.coroutines.*

class HrForegroundService : Service(), SensorEventListener {
    companion object {
        private const val TAG = "HrForegroundService"
        private const val CHANNEL_ID = "pulseguard_monitoring"
        private const val CHANNEL_NAME = "PulseGuard Monitoring"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.pulseguard.ACTION_STOP"
        const val ACTION_HR_UPDATE = "com.example.pulseguard.ACTION_HR_UPDATE"
        const val EXTRA_BPM = "extra_bpm"
        const val EXTRA_RUNNING = "extra_running"
        const val ACTION_ALERT_UPDATE = "com.example.pulseguard.ACTION_ALERT_UPDATE"
        const val EXTRA_ALERT_ACTIVE = "extra_alert_active"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val ACTION_CANCEL_ALERT = "com.example.pulseguard.ACTION_CANCEL_ALERT"
        const val ACTION_SEND_HELP_NOW = "com.example.pulseguard.ACTION_SEND_HELP_NOW"
        const val ACTION_UPDATE_THRESHOLDS = "com.example.pulseguard.ACTION_UPDATE_THRESHOLDS"

    }

    // Failed Samsung Sensor Manger
    // private lateinit var hrManager: com.example.pulseguard.sensor.HeartRateSensorManager

    // Android SensorManager
    private var androidHRSensor: AndroidHeartRateSensor? = null
    private lateinit var repo: HeartRateRepository
    private var sensorStarted = false

    // Edge Processor
    private lateinit var edge: AnomalyProcessor
    private var lastEvent: Int = AnomalyProcessor.EVENT_NONE

    // BLE
    private lateinit var bleSender: BleSender
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Alert Controller
    private lateinit var alertController: AlertController
    private var alertActive = false
    private var countdownSecondsRemaining: Int = 0
    private var alertsMutedUntil = 0L
    private var awaitingRecovery = false

    // Algorithm + Activity Variables
    private var tachyThreshold = 120
    private var bradyThreshold = 45
    private var currentStepsPerMinute = 0
    private var currentTachyThreshold = 120
    private var lastStepCount = -1f
    private var lastStepTimestamp = 0L
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        repo = HeartRateRepository(this)
        edge = AnomalyProcessor()
        bleSender = BleSender(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        stepSensor?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

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

        alertController = AlertController(
            onTick = { seconds ->
                countdownSecondsRemaining = seconds
                Log.d(TAG, "Alert countdown: $seconds")
                broadcastAlertState(active = true, countdown = seconds)
            },
            onEscalate = {
                broadcastAlertState(active = false, countdown = 0)
                sendAlertEscalateToPhone()
                alertActive = false
                awaitingRecovery = true
            }
        )

        // Android SensorManager
        androidHRSensor = AndroidHeartRateSensor(
            context = this,
            onBpm = { bpm, ts ->
                // Run edge processing (filter + anomaly detection)
                val event = edge.processSample(bpm, ts)
                val filtered = edge.lastFilteredBpm()

                // BLE
                val json = """{"ts":$ts,"raw":$bpm,"filtered":$filtered,"event":$event}"""
                serviceScope.launch {
                    try {
                        bleSender.send(json)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to send HR packet", t)
                    }
                }

                // Log + store
                Log.d(TAG, "raw=$bpm filtered=$filtered event=$event ts=$ts")

                // For now keep raw log
                repo.append(ts, bpm, filtered, event)

                // Update UI broadcast (raw BPM still ok for now)
                broadcastStatus(bpm = bpm, running = true)

                if(event != 0 && !alertActive && !awaitingRecovery && !alertsMuted()) {
                    Log.d(TAG, "ALERT TRIGGERED: $event")
                    alertActive = true

                    // Tell phone to show alert screen immediately
                    sendAlertStartedToPhone()

                    broadcastAlertState(active = true, countdown = countdownSecondsRemaining)
                    alertController.startCountdown()
                }

                if (event == 0 && alertActive && !alertController.isActive()) {
                    alertActive = false
                }

                if (event == 0 && awaitingRecovery) {
                    Log.d(TAG, "Condition recovered - rearming alerts")
                    awaitingRecovery = false
                }

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

        if (intent?.action == ACTION_CANCEL_ALERT) {
            Log.d(TAG, "ACTION_CANCEL_ALERT received")

            alertController.cancel()
            alertActive = false

            // Mute alerts for 60 seconds after user says they are OK
            alertsMutedUntil = System.currentTimeMillis() + 300_000L

            broadcastAlertState(active = false, countdown = 0)

            sendAlertCancelledToPhone()

            return START_STICKY
        }

        if (intent?.action == ACTION_SEND_HELP_NOW) {
            Log.d(TAG, "ACTION_SEND_HELP_NOW received")

            alertController.cancel()
            alertActive = false
            awaitingRecovery = true

            broadcastAlertState(active = false, countdown = 0)

            sendAlertEscalateToPhone()

            return START_STICKY
        }

        if (intent?.action == ACTION_UPDATE_THRESHOLDS) {

            tachyThreshold =
                intent.getIntExtra("tachy", 120)

            bradyThreshold =
                intent.getIntExtra("brady", 45)

            Log.d(
                TAG,
                "Adaptive thresholds received " +
                        "tachy=$tachyThreshold brady=$bradyThreshold"
            )

            // PUSH INTO NATIVE EDGE PROCESSOR
            edge.nativeUpdateThresholds(
                tachyThreshold,
                bradyThreshold
            )

            Log.d(TAG, "Native thresholds updated successfully")
            return START_STICKY
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
        alertController.cancel()
        alertActive = false
        broadcastAlertState(active = false, countdown = 0)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        try{
            androidHRSensor?.stop()
            sensorStarted = false
        } catch (_: Throwable) {}
        serviceScope.cancel()
        alertController.cancel()
        sensorManager.unregisterListener(this)
        alertActive = false
        broadcastAlertState(active = false, countdown = 0)
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

    private fun sendEmergencyToPhone() {
        val json = """
            {
            "type":"alert",
            "ts":${System.currentTimeMillis()},
            "countdownExpired":true
            }
        """.trimIndent()

        serviceScope.launch {
            try {
                bleSender.send(json)
                Log.d(TAG, "Emergency alert sent to phone")
                alertActive = false
            } catch (t: Throwable) {
                Log.e(TAG, " Failed to send emergency alert", t)
            }
        }
    }

    private fun broadcastAlertState(active: Boolean, countdown: Int) {
        val intent = Intent(ACTION_ALERT_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_ALERT_ACTIVE, active)
            putExtra(EXTRA_COUNTDOWN, countdown)
        }
        sendBroadcast(intent)
    }

    private fun alertsMuted(): Boolean {
        return System.currentTimeMillis() < alertsMutedUntil
    }

    private fun sendAlertCancelledToPhone() {
        val payload = """
            {
                "type": "alert_cancelled",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        serviceScope.launch {
            try {
                bleSender.send(payload)
                Log.d(TAG, "Emergency alert cancelled on phone")
                alertActive = false
            } catch (t: Throwable) {
                Log.e(TAG, " Failed to cancel emergency alert on phone", t)
            }
        }
    }

    private fun sendAlertStartedToPhone() {
        val payload = """
        {
            "type":"alert_started",
            "ts":${System.currentTimeMillis()},
            "event":$lastEvent
        }
    """.trimIndent()

        serviceScope.launch {
            try {
                bleSender.send(payload)
                Log.d(TAG, "Alert started sent to phone")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send alert_started", t)
            }
        }
    }

    private fun sendAlertEscalateToPhone() {
        val payload = """
        {
            "type":"alert_escalate",
            "ts":${System.currentTimeMillis()},
            "event":$lastEvent
        }
    """.trimIndent()

        serviceScope.launch {
            try {
                bleSender.send(payload)
                Log.d(TAG, "Alert escalation sent to phone")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send alert_escalate", t)
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) {
            return
        }

        val totalSteps = event.values[0]
        val now = System.currentTimeMillis()

        if (lastStepCount < 0f) {
            lastStepCount = totalSteps
            lastStepTimestamp = now
            return
        }

        val stepDiff = totalSteps - lastStepCount
        val timeDiffMinutes =
            (now - lastStepTimestamp) / 60000f

        if (timeDiffMinutes > 0f) {

            currentStepsPerMinute =
                (stepDiff / timeDiffMinutes).toInt()

            updateActivityAdjustedThreshold()

            Log.d(
                TAG,
                "Steps/min=$currentStepsPerMinute " +
                        "tachy=$currentTachyThreshold"
            )
        }

        lastStepCount = totalSteps
        lastStepTimestamp = now
    }

    private fun updateActivityAdjustedThreshold() {

        currentTachyThreshold =
            when {

                currentStepsPerMinute >= 120 ->
                    tachyThreshold + 30

                currentStepsPerMinute >= 80 ->
                    tachyThreshold + 20

                currentStepsPerMinute >= 40 ->
                    tachyThreshold + 10

                else ->
                    tachyThreshold
            }

        edge.nativeUpdateThresholds(
            currentTachyThreshold,
            bradyThreshold
        )
    }
}
