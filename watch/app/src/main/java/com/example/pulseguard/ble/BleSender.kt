package com.example.pulseguard.ble

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BleSender(private val context: Context){

    companion object{
        private const val TAG = "BleSender"
        private const val PATH_HR = "S"
    }

    suspend fun send(payload: String) = withContext(Dispatchers.IO) {
        try{
            val nodeClient = Wearable.getNodeClient(context)
            val messageClient = Wearable.getMessageClient(context)

            val nodes = nodeClient.connectedNodes.await()

            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone nodes")
                return@withContext
            }

            val data = payload.toByteArray()

            for (node in nodes) {
                try {
                    messageClient.sendMessage(
                        node.id,
                        PATH_HR,
                        data
                    ).await()

                    Log.d(TAG, "Send HR packet to ${node.displayName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Send failed for node ${node.displayName}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "BleSender failure", e)
        }
    }
}