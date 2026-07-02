package com.example.geminimultimodalliveapi.architecture

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class AttentionState {
    IDLE,            // Waiting for wakeword or widget activation
    LISTENING,       // Local wakeword hit, listening for user prompt
    ACTIVE_SESSION,  // Actively streaming user audio / receiving replies
    BACKGROUND       // Suspended attention due to interruptions or stranger speech
}

class AttentionManager(
    private val scope: CoroutineScope,
    private val onAttentionChange: (Boolean) -> Unit
) {
    constructor(onAttentionChange: (Boolean) -> Unit) : this(
        scope = CoroutineScope(Dispatchers.Default),
        onAttentionChange = onAttentionChange
    )

    private val _state = MutableStateFlow(AttentionState.IDLE)
    val state: StateFlow<AttentionState> = _state

    private val _isAttentionActive = MutableStateFlow(false)
    val isAttentionActive: StateFlow<Boolean> = _isAttentionActive

    private var ignoreOtherSpeakers = false

    var listeningTimeoutMs: Long = 5 * 60 * 1000L
    var activeSessionTimeoutMs: Long = 30 * 60 * 1000L
    private var timeoutJob: Job? = null

    fun setIgnoreOtherSpeakers(ignore: Boolean) {
        ignoreOtherSpeakers = ignore
        Log.i("AttentionManager", "Updated ignoreOtherSpeakers rule: $ignore")
    }

    fun onNewEvent(event: PerceptionEvent) {
        val previousState = _state.value
        val newState = when (_state.value) {
            AttentionState.IDLE -> when (event) {
                is PerceptionEvent.WakewordDetected -> AttentionState.LISTENING
                is PerceptionEvent.SpeechDetected -> {
                    // Quick wake word match from Deepgram streaming
                    if (event.speaker == "owner" && event.confidence > 0.90) {
                        AttentionState.ACTIVE_SESSION
                    } else AttentionState.IDLE
                }
                else -> AttentionState.IDLE
            }
            AttentionState.LISTENING -> when (event) {
                is PerceptionEvent.SpeechDetected -> {
                    if (event.speaker == "owner") {
                        AttentionState.ACTIVE_SESSION
                    } else {
                        // Stranger speaking
                        if (ignoreOtherSpeakers) AttentionState.LISTENING else AttentionState.BACKGROUND
                    }
                }
                is PerceptionEvent.ScreenStateChanged -> {
                    if (!event.isScreenOn) AttentionState.IDLE else AttentionState.LISTENING
                }
                else -> AttentionState.LISTENING
            }
            AttentionState.ACTIVE_SESSION -> when (event) {
                is PerceptionEvent.SpeechDetected -> {
                    if (event.speaker == "owner") {
                        AttentionState.ACTIVE_SESSION
                    } else {
                        // Stranger interrupting
                        if (ignoreOtherSpeakers) AttentionState.ACTIVE_SESSION else AttentionState.BACKGROUND
                    }
                }
                is PerceptionEvent.ScreenStateChanged -> {
                    if (!event.isScreenOn) AttentionState.BACKGROUND else AttentionState.ACTIVE_SESSION
                }
                else -> AttentionState.ACTIVE_SESSION
            }
            AttentionState.BACKGROUND -> when (event) {
                is PerceptionEvent.WakewordDetected -> AttentionState.ACTIVE_SESSION
                is PerceptionEvent.SpeechDetected -> {
                    if (event.speaker == "owner" && event.confidence > 0.90) {
                        AttentionState.ACTIVE_SESSION
                    } else AttentionState.BACKGROUND
                }
                is PerceptionEvent.ScreenStateChanged -> {
                    if (event.isScreenOn) AttentionState.ACTIVE_SESSION else AttentionState.BACKGROUND
                }
                else -> AttentionState.BACKGROUND
            }
        }

        if (newState != previousState) {
            _state.value = newState
            val active = newState == AttentionState.LISTENING || newState == AttentionState.ACTIVE_SESSION
            _isAttentionActive.value = active
            
            Log.i("AttentionManager", "State transition: $previousState -> $newState (Active: $active)")
            
            // Print log for user visibility
            val msg = "[Attention] State: $newState"
            com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(msg)
            
            // Update diagnostics UI
            com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDiagnostics { current ->
                current.copy(attentionState = newState.name)
            }

            // Manage timeout job
            timeoutJob?.cancel()
            timeoutJob = null
            if (newState == AttentionState.LISTENING) {
                timeoutJob = scope.launch {
                    delay(listeningTimeoutMs)
                    Log.i("AttentionManager", "Idle timeout triggered for LISTENING state, resetting to IDLE")
                    forceState(AttentionState.IDLE)
                }
            } else if (newState == AttentionState.ACTIVE_SESSION) {
                timeoutJob = scope.launch {
                    delay(activeSessionTimeoutMs)
                    Log.i("AttentionManager", "Idle timeout triggered for ACTIVE_SESSION state, resetting to IDLE")
                    forceState(AttentionState.IDLE)
                }
            }

            onAttentionChange(active)
        }
    }

    fun forceState(newState: AttentionState) {
        val previousState = _state.value
        if (newState != previousState) {
            _state.value = newState
            val active = newState == AttentionState.LISTENING || newState == AttentionState.ACTIVE_SESSION
            _isAttentionActive.value = active
            Log.i("AttentionManager", "Forced state: $previousState -> $newState (Active: $active)")
            
            val msg = "[Attention] State: $newState"
            com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(msg)
            
            com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDiagnostics { current ->
                current.copy(attentionState = newState.name)
            }

            // Manage timeout job
            timeoutJob?.cancel()
            timeoutJob = null
            if (newState == AttentionState.LISTENING) {
                timeoutJob = scope.launch {
                    delay(listeningTimeoutMs)
                    Log.i("AttentionManager", "Idle timeout triggered for LISTENING state, resetting to IDLE")
                    forceState(AttentionState.IDLE)
                }
            } else if (newState == AttentionState.ACTIVE_SESSION) {
                timeoutJob = scope.launch {
                    delay(activeSessionTimeoutMs)
                    Log.i("AttentionManager", "Idle timeout triggered for ACTIVE_SESSION state, resetting to IDLE")
                    forceState(AttentionState.IDLE)
                }
            }
            
            onAttentionChange(active)
        }
    }
}
