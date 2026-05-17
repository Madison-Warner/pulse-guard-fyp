package com.example.pulseguard.model

data class HrLogBucket(
    val bucketStart: Long = 0L,
    val bucketEnd: Long = 0L,
    val avgBpm: Int = 0,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val sampleCount: Int = 0,
    val createdAt: Long = 0L
)
