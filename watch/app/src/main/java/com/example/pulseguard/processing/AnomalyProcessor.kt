package com.example.pulseguard.processing

class AnomalyProcessor {

    companion object{
        // Event codes returned from native
        const val EVENT_NONE = 0
        const val EVENT_TACHY = 1
        const val EVENT_BRADY = 2
    }

    init {
        System.loadLibrary("pulseguard-lib")
    }

    external fun nativeProcessSample(bpm: Int, timestampMillis: Long): Int
    external fun nativeGetLastFilteredBpm(): Int

    fun processSample(bpm: Int, timestampMillis:Long): Int {
        return nativeProcessSample(bpm, timestampMillis)
    }

    fun lastFilteredBpm(): Int = nativeGetLastFilteredBpm()
}