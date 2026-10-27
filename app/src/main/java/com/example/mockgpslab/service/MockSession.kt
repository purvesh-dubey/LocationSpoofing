package com.example.mockgpslab.service

import com.example.mockgpslab.fused.FusedState
import com.example.mockgpslab.mock.EngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide mirror of what the service is doing, so the UI can observe it without binding to the service.
 * Activity and service live in the same process, so a plain shared object is enough.
 */
class MockSession {
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    private val _serviceRunning = MutableStateFlow(false)

    private val _fusedState = MutableStateFlow<FusedState>(FusedState.Off)

    val engineState: StateFlow<EngineState> = _engineState
    val fusedState: StateFlow<FusedState> = _fusedState
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    fun setEngineState(state: EngineState) {
        _engineState.value = state
    }

    fun setFusedState(state: FusedState) {
        _fusedState.value = state
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }
}
