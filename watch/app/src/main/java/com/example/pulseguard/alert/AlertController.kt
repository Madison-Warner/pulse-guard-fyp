package com.example.pulseguard.alert

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlertController(
    private val onTick: (Int) -> Unit,
    private val onEscalate: () -> Unit
) {
    private var job : Job? = null

    fun isActive(): Boolean = job?.isActive == true
    fun startCountdown(seconds: Int = 45) {
        if (isActive()) return

        job = CoroutineScope(Dispatchers.Main).launch {
            for (i in seconds downTo 1) {
                Log.d("Alert", "Countdown: $i")
                onTick(i)
                delay(1000)
            }
            onEscalate()
        }
    }

    fun cancel(){
        job?.cancel()
        job = null
        Log.d("Alert", "Cancelled by user")
    }
}