package com.example.pulseguard.comms

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class HrListenerService: WearableListenerService() {
    companion object{
        private const val TAG = "HrListenerService"
        private const val PATH_HR = "/hr_data"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != PATH_HR) return

        val json = String(messageEvent.data, Charsets.UTF_8)
        Log.d(TAG, "HR RECEIVED: $json")
    }
}