package com.example.pulseguard.model

data class HeartRateSample (
    val timestamp: Long,
    val bpm: Int,
    val isWorn: Boolean,
    val activityState: ActivityState
)

enum class ActivityState{
    REST,
    ACTIVE,
    UNKNOWN
}