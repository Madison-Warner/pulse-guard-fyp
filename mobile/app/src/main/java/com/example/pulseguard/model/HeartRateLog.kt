package com.example.pulseguard.model

class HeartRateLog (
    val timestamp: Long = 0,
    val bpm: Int = 0,
    val source: String = "watch"
)