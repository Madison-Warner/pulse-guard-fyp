package com.example.pulseguard.comms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HrUiState(
    val timestamp: Long = 0L,
    val rawBpm: Int = 0,
    val filteredBpm: Int = 0,
    val eventCode: Int = 0,
    val connected: Boolean = false
)

object HrLiveBus {
    private val _state = MutableStateFlow(HrUiState())
    val state = _state.asStateFlow()

    fun post(state:HrUiState) {
        _state.value = state
    }
}