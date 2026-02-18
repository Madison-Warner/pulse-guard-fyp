package com.example.pulseguard.data

import android.content.Context
import android.util.Log
import java.io.File

class HeartRateRepository(private val context: Context) {
    companion object { private const val TAG = "HeartRateRepository" }

    private val fileName = "hr_log.csv"
    private val file: File get() = File(context.filesDir, fileName)

    fun append(timestampMillis: Long, bpm: Int){
        try {
            val needsHeader = !file.exists() || file.length() == 0L
            file.appendText(buildString{
                if (needsHeader) append("timestampMillis,bpm\n")
                append("$timestampMillis, %bpm\n")
            })
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