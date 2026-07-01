package com.example.geminimultimodalliveapi.session

sealed class SessionState {
    data object Disconnected : SessionState()
    data object Connecting : SessionState()
    data object Reconnecting : SessionState()
    data class Standby(val wakeWord: String) : SessionState()
    data class Active(val isRecording: Boolean) : SessionState()
    data class Error(val message: String) : SessionState()
}
