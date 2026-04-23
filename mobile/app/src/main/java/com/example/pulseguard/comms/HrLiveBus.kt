package com.example.pulseguard.comms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HrUiState(
    // Heart rate fields
    val timestamp: Long = 0L,
    val rawBpm: Int = 0,
    val filteredBpm: Int = 0,
    val eventCode: Int = 0,
    // Connection field
    val connected: Boolean = false,

    // Alert fields
    val alertActive: Boolean = false,
    val alertMessage: String = "",
    val escalationRequested: Boolean = false,
    val escalationHandled: Boolean = false
)

object HrLiveBus {
    private val _state = MutableStateFlow(HrUiState())
    val state = _state.asStateFlow()

    fun post(state:HrUiState) {
        _state.value = state
    }

    fun clearAlert() {
        _state.value = HrUiState()
    }
}