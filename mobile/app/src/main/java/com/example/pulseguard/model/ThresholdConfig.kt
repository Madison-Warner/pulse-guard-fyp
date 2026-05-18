package com.example.pulseguard.model

data class ThresholdConfig(
    val baselineBpm: Int = 70,
    val tachyThreshold: Int = 110,
    val bradyThreshold: Int = 50,
    val updatedAt: Long = 0L
)
