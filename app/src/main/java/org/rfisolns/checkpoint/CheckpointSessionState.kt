package org.rfisolns.checkpoint

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// In-process shared state between the service and the UI. Same process, so a
// plain StateFlow singleton is simpler than Binder/Messenger IPC.
object CheckpointSessionState {
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive

    private val _manualOverride = MutableStateFlow(false)
    val manualOverride: StateFlow<Boolean> = _manualOverride

    fun setSessionActive(active: Boolean) {
        _sessionActive.value = active
    }

    fun setManualOverride(active: Boolean) {
        _manualOverride.value = active
    }
}
