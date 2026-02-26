package com.example.pulseguard.data

import android.content.Context
import android.util.Log
import java.io.File

class HeartRateRepository(private val context: Context) {
    companion object { private const val TAG = "HeartRateRepository" }

    private val fileName = "hr_log.csv"
    private val file: File get() = File(context.filesDir, fileName)

    fun append(ts: Long, raw: Int, filtered: Int, event: Int){
        try {
            val file = File(context.filesDir, "hr_log.csv")

            if (!file.exists()) {
                file.writeText("timestampMillis,rawBpm,filteredBpm,eventCode\n")
            }

            file.appendText("$ts,$raw,$filtered,$event\n")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to append HR log", t)
        }
    }

    fun clear(){
        try {
            if(file.exists()) file.delete()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to clear HR log", t)
        }
    }

    fun path(): String = file.absolutePath
}