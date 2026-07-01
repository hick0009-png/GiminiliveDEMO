package com.example.geminimultimodalliveapi.architecture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed class PerceptionEvent(val timestamp: Long = System.currentTimeMillis()) {
    data class SpeechDetected(val speaker: String, val confidence: Float) : PerceptionEvent()
    data class WakewordDetected(val command: String = "") : PerceptionEvent()
    data class ScreenStateChanged(val isScreenOn: Boolean) : PerceptionEvent()
    data class BluetoothStateChanged(val deviceName: String, val isConnected: Boolean) : PerceptionEvent()
    data class MotionStateChanged(val motion: MotionType) : PerceptionEvent()
    data class LocationChanged(val placeType: String, val locationName: String) : PerceptionEvent()
    data object TimeTick : PerceptionEvent()
}

enum class MotionType { STILL, WALKING, DRIVING, UNKNOWN }

class PerceptionEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val _events = MutableSharedFlow<PerceptionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PerceptionEvent> = _events

    // Debouncing variables for Motion state
    private var currentMotion = MotionType.STILL
    private var pendingMotion: MotionType? = null
    private var motionDebounceJob: Job? = null
    private val debounceDelayMs = 10000L // 10 seconds debounce to filter rapid fluctuations

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                when (action) {
                    Intent.ACTION_SCREEN_ON -> emitEvent(PerceptionEvent.ScreenStateChanged(true))
                    Intent.ACTION_SCREEN_OFF -> emitEvent(PerceptionEvent.ScreenStateChanged(false))
                }
            }
        }
    }

    init {
        registerReceivers()
    }

    private fun registerReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(screenReceiver, filter)
            Log.i("PerceptionEngine", "Registered Screen State BroadcastReceiver")
        } catch (e: Exception) {
            Log.e("PerceptionEngine", "Failed to register screen state receiver", e)
        }
    }

    fun emitEvent(event: PerceptionEvent) {
        coroutineScope.launch {
            _events.emit(event)
            Log.d("PerceptionEngine", "Emitted event: ${event.javaClass.simpleName} at ${event.timestamp}")
            
            // Log specifically to ChatLogs so the user can see it in real-time
            val msg = when (event) {
                is PerceptionEvent.SpeechDetected -> "[Perception] Speech: ${event.speaker} (${event.confidence})"
                is PerceptionEvent.WakewordDetected -> "[Perception] Wakeword Detected: ${event.command}"
                is PerceptionEvent.ScreenStateChanged -> "[Perception] Screen: ${if (event.isScreenOn) "ON" else "OFF"}"
                is PerceptionEvent.BluetoothStateChanged -> "[Perception] Bluetooth: ${event.deviceName} isConnected=${event.isConnected}"
                is PerceptionEvent.MotionStateChanged -> "[Perception] Motion: ${event.motion}"
                is PerceptionEvent.LocationChanged -> "[Perception] Location: ${event.placeType} (${event.locationName})"
                is PerceptionEvent.TimeTick -> null
            }
            if (msg != null) {
                com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(msg)
                
                // Update UI diagnostics panel info
                com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDiagnostics { current ->
                    val baseUpdated = current.copy(lastEvent = msg.substringAfter("[Perception] ").trim())
                    when (event) {
                        is PerceptionEvent.MotionStateChanged -> baseUpdated.copy(motionState = event.motion.name)
                        is PerceptionEvent.LocationChanged -> baseUpdated.copy(locationState = event.placeType)
                        else -> baseUpdated
                    }
                }
            }
        }
    }

    /**
     * Updates the motion state with a debounce filter.
     * Prevents rapid changes (like STILL to DRIVING and back immediately) from triggering spam events.
     */
    fun updateMotionStateWithDebounce(newMotion: MotionType) {
        if (newMotion == currentMotion) {
            motionDebounceJob?.cancel()
            pendingMotion = null
            return
        }

        if (newMotion == pendingMotion) {
            // Already pending this transition, continue waiting
            return
        }

        // Cancel previous pending transition, start new one
        motionDebounceJob?.cancel()
        pendingMotion = newMotion
        
        Log.d("PerceptionEngine", "Pending motion change: $currentMotion -> $newMotion (debouncing...)")
        motionDebounceJob = coroutineScope.launch {
            delay(debounceDelayMs)
            currentMotion = newMotion
            pendingMotion = null
            Log.i("PerceptionEngine", "Debounced motion committed: $newMotion")
            emitEvent(PerceptionEvent.MotionStateChanged(newMotion))
        }
    }

    fun forceEmitMotionState(newMotion: MotionType) {
        motionDebounceJob?.cancel()
        pendingMotion = null
        currentMotion = newMotion
        emitEvent(PerceptionEvent.MotionStateChanged(newMotion))
    }

    fun destroy() {
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Might not be registered
        }
        motionDebounceJob?.cancel()
    }
}
