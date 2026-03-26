package com.example.pulseguard.comms

import android.util.Log
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

        Log.d(TAG, "onMessageReceived path=${messageEvent.path}")

        if (messageEvent.path != PATH_HR) return

        val json = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "HR RECEIVED: $json")

        try {
            val obj = JSONObject(json)

            val state = HrUiState(
                timestamp = obj.optLong("ts", 0L),
                rawBpm = obj.optInt("raw", 0),
                filteredBpm = obj.optInt("filtered", 0),
                eventCode = obj.optInt("event", 0),
                connected = true
            )

            HrLiveBus.post(state)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse HR JSON", t)
        }
    }
}