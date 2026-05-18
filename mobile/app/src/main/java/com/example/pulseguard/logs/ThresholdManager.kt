package com.example.pulseguard.logs

import com.example.pulseguard.model.HrLogBucket
import com.example.pulseguard.model.ThresholdConfig

class ThresholdManager {
    fun buildThresholdConfig(
        logs: List<HrLogBucket>
    ): ThresholdConfig {

        // FALLBACK STATIC DEFAULTS
        val staticTachy = 120
        val staticBrady = 45

        if (logs.isEmpty()) {
            return ThresholdConfig(
                baselineBpm = 70,
                tachyThreshold = staticTachy,
                bradyThreshold = staticBrady,
                updatedAt = System.currentTimeMillis()
            )
        }

        val baseline = logs.map { it.avgBpm }.average().toInt()
        val adaptiveTachy = maxOf(staticTachy, baseline + 35)
        val adaptiveBrady = minOf(staticBrady, baseline - 25)

        return ThresholdConfig(
            baselineBpm = baseline,
            tachyThreshold = adaptiveTachy,
            bradyThreshold = adaptiveBrady,
            updatedAt = System.currentTimeMillis()
        )
    }
}