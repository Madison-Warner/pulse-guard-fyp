package com.example.pulseguard.processing

class AnomalyProcessor {
    init {
        System.loadLibrary("pulseguard-lib")
    }

    external fun nativeProcessSample(bpm: Int): Boolean

    fun processSample(bpm: Int): Boolean {
        return nativeProcessSample(bpm)
    }
}