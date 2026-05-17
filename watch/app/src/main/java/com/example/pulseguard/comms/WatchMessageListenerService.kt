package com.example.pulseguard.comms

import android.content.Intent
import android.util.Log
import com.example.pulseguard.service.HrForegroundService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class WatchMessageListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WatchMsgListener"
        private const val PATH_HR = "/hr_data"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WatchMessageListenerService created")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path != PATH_HR) return

        val json = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "Received from phone: $json")

        try {
            val obj = JSONObject(json)
            val type = obj.optString("type", "")

            when (type) {
                "alert_cancelled" -> {
                    Log.d(TAG, "Phone requested alert cancellation")

                    val intent = Intent(this, HrForegroundService::class.java).apply {
                        action = HrForegroundService.ACTION_CANCEL_ALERT
                    }

                    startForegroundService(intent)
                }

                "alert_escalate" -> {
                    Log.d(TAG, "Phone requested emergency escalation")

                    val intent = Intent(this, HrForegroundService::class.java).apply {
                        action = HrForegroundService.ACTION_SEND_HELP_NOW
                    }

                    startForegroundService(intent)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse phone message", t)
        }
    }
}