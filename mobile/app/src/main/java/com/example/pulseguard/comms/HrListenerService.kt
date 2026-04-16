package com.example.pulseguard.comms

import android.util.Log
import com.example.pulseguard.R
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class HrListenerService: WearableListenerService() {
    companion object {
        private const val TAG = "HrListenerService"
        private const val PATH_HR = "/hr_data"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HrListenerService created")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path != PATH_HR) return

        val json = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Received:$json")

        try {
            val obj = JSONObject(json)
            val type = obj.optString("type", "hr_data")
            when (type) {
                "hr_data" -> {
                    val current = HrLiveBus.state.value

                    HrLiveBus.post(
                        current.copy(
                            timestamp = obj.optLong("ts", 0L),
                            rawBpm = obj.optInt("raw", 0),
                            filteredBpm = obj.optInt("filtered", 0),
                            eventCode = obj.optInt("event", 0),
                            connected = true
                        )
                    )
                }

                "alert_started" -> {
                    val current = HrLiveBus.state.value
                    HrLiveBus.post(
                        current.copy(
                            alertActive = true,
                            alertMessage = "Abnormal heart rate detected"
                        )
                    )
                    Log.d(TAG, "Phone alert started")
                }

                "alert_cancelled" -> {
                    val current = HrLiveBus.state.value
                    HrLiveBus.post(
                        current.copy(
                            alertActive = false,
                            alertMessage = ""
                        )
                    )
                    Log.d(TAG, "Phone alert cancelled")
                }

                "alert_escalate" -> {
                    val current = HrLiveBus.state.value
                    HrLiveBus.post(
                        current.copy(
                            alertActive = true,
                            alertMessage = "Emergency escalation triggered"
                        )
                    )

                    // later: notification + SMS
                    Log.d(TAG, "Phone alert escalated")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse incoming message", t)
        }
    }

    private fun showEmergencyNotification() {
        val channelId = "pulseguard_alerts"
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "PulseGuard Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("PulseGuard Emergency Alert")
            .setContentText("Abnormal heart rate detected. Escalation required.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(2001, notification)
    }
}