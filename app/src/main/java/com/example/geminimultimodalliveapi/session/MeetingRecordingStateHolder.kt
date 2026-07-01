package com.example.geminimultimodalliveapi.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.geminimultimodalliveapi.data.TranscriptSegment

object MeetingRecordingStateHolder {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _secondsElapsed = MutableStateFlow(0L)
    val secondsElapsed: StateFlow<Long> = _secondsElapsed

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude

    private val _liveTranscript = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val liveTranscript: StateFlow<List<TranscriptSegment>> = _liveTranscript

    private val _isLiveTranscript = MutableStateFlow(false)
    val isLiveTranscript: StateFlow<Boolean> = _isLiveTranscript

    fun updateRecordingState(recording: Boolean) {
        _isRecording.value = recording
        if (!recording) {
            _secondsElapsed.value = 0L
            _amplitude.value = 0
            _liveTranscript.value = emptyList()
            _isLiveTranscript.value = false
        }
    }

    fun updateSeconds(seconds: Long) {
        _secondsElapsed.value = seconds
    }

    fun updateAmplitude(amp: Int) {
        _amplitude.value = amp
    }

    fun updateLiveTranscript(transcript: List<TranscriptSegment>) {
        _liveTranscript.value = transcript
    }

    fun updateLiveTranscriptState(isLive: Boolean) {
        _isLiveTranscript.value = isLive
    }
}
