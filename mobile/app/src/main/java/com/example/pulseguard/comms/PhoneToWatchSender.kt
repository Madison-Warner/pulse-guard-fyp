package com.example.pulseguard.comms

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext


class PhoneToWatchSender(private val context: Context) {
    companion object {
        private const val TAG = "PhoneToWatchSender"
        private const val PATH_HR = "/hr_data"
    }

    suspend fun send(payload: String) = withContext(Dispatchers.IO) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            if (nodes.isEmpty()) {
                Log.w(TAG,"No connected watch nodes")
                return@withContext
            }

            val bytes = payload.toByteArray(Charsets.UTF_8)

            for (node in nodes) {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, PATH_HR, bytes)
                    .await()
            }

            Log.d(TAG, "Send payload to ${nodes.size} watch node(s)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send cancel to watch", t)
        }
    }
}