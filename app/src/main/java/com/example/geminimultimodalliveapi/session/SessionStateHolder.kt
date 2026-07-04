package com.example.geminimultimodalliveapi.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.example.geminimultimodalliveapi.error.AppError
import java.util.Collections

data class SituationalDiagnostics(
    val attentionState: String = "IDLE",
    val activeTopic: String = "None",
    val lastEvent: String = "None",
    val motionState: String = "STILL",
    val locationState: String = "home"
)

object SessionStateHolder {

    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state

    private val _diagnostics = MutableStateFlow(SituationalDiagnostics())
    val diagnostics: StateFlow<SituationalDiagnostics> = _diagnostics

    fun updateDiagnostics(updater: (SituationalDiagnostics) -> SituationalDiagnostics) {
        _diagnostics.value = updater(_diagnostics.value)
    }

    private val _dateInsights = MutableStateFlow(com.example.geminimultimodalliveapi.data.DateInsight())
    val dateInsights: StateFlow<com.example.geminimultimodalliveapi.data.DateInsight> = _dateInsights

    fun updateDateInsights(updater: (com.example.geminimultimodalliveapi.data.DateInsight) -> com.example.geminimultimodalliveapi.data.DateInsight) {
        _dateInsights.value = updater(_dateInsights.value)
    }

    private val _isDateAssistantModeActive = MutableStateFlow(false)
    val isDateAssistantModeActiveFlow: StateFlow<Boolean> = _isDateAssistantModeActive

    var isDateAssistantModeActive: Boolean
        get() = _isDateAssistantModeActive.value
        set(value) {
            _isDateAssistantModeActive.value = value
        }

    @Volatile
    var activeProfileName: String = ""

    @Volatile
    var activeSkillId: String = ""

    private val _chatLogs = MutableStateFlow<String>("")
    val chatLogs: StateFlow<String> = _chatLogs

    private val _appErrors = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val appErrors: SharedFlow<String> = _appErrors

    private val _errorFlow = MutableSharedFlow<AppError>(extraBufferCapacity = 64)
    val errorFlow: SharedFlow<AppError> = _errorFlow


    fun updateState(newState: SessionState) {
        _state.value = newState
    }

    fun appendChatLog(message: String) {
        _chatLogs.update { current ->
            val lines = if (current.isEmpty()) mutableListOf() else current.split("\n").toMutableList()
            lines.add(message)
            if (lines.size > 50) {
                lines.removeAt(0)
            }
            lines.joinToString("\n")
        }
    }

    fun updateChatLogs(newLogs: String) {
        _chatLogs.value = newLogs
    }

    fun clearChatLogs() {
        _chatLogs.value = ""
    }


    fun postError(errorMessage: String) {
        _appErrors.tryEmit(errorMessage)
    }

    fun postError(error: AppError) {
        _errorFlow.tryEmit(error)
        
        // Also post string message to _appErrors for backward compatibility/logging if needed
        val msg = when (error) {
            is AppError.Network -> "Network error: ${error.message}"
            is AppError.Permission -> "Permission error: ${error.type}"
            is AppError.AuthExpired -> "Auth expired"
            is AppError.Api -> "Api error (${error.code}): ${error.message}"
            is AppError.Tool -> "Tool error (${error.name}): ${error.message}"
        }
        _appErrors.tryEmit(msg)
    }
}
