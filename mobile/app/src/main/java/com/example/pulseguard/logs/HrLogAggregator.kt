package com.example.pulseguard.logs

import com.example.pulseguard.model.HrLogBucket

class HrLogAggregator(
    private val bucketDurationMillis: Long = 20 * 60 * 1000L // test value = 10_000L (10 seconds)
) {
    private var bucketStart: Long? = null
    private var sum = 0L
    private var count = 0
    private var min = Int.MAX_VALUE
    private var max = Int.MIN_VALUE

    fun addSample(timestamp: Long, bpm: Int): HrLogBucket? {
        if (bpm <= 0) return null

        if (bucketStart == null) {
            bucketStart = timestamp
        }

        val start = bucketStart ?: timestamp
        val diff = timestamp - start


        if (diff >= bucketDurationMillis && count > 0) {
            val completedBucket = HrLogBucket(
                bucketStart = start,
                bucketEnd = timestamp,
                avgBpm = (sum / count).toInt(),
                minBpm = min,
                maxBpm = max,
                sampleCount = count,
                createdAt = System.currentTimeMillis()
            )

            bucketStart = timestamp
            sum = bpm.toLong()
            count = 1
            min = bpm
            max = bpm

            return completedBucket
        }

        sum += bpm
        count++
        min = minOf(min, bpm)
        max = maxOf(max, bpm)

        return null
    }
}