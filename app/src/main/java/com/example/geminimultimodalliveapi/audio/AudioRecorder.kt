package com.example.geminimultimodalliveapi.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class AudioRecorder(
    private val context: Context,
    private val sampleRate: Int = AudioConfig.INPUT_SAMPLE_RATE,
    private val onAudioChunkRecorded: (ByteArray, Int, Float) -> Unit
) {
    private var runningAgcGain = 2.0f
    private val channelConfig = AudioConfig.CHANNEL_IN
    private val audioEncoding = AudioConfig.ENCODING
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
    private val sendChunkSize = 640 // 40ms of audio at 16kHz

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    @Volatile
    var isRecording = false
        private set

    // Thread isolation - dedicated high-priority single thread
    private val recordExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gemini-record-thread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val recordDispatcher = recordExecutor.asCoroutineDispatcher()

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording) return
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioEncoding,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecorder", "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start audio recording", e)
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            return
        }

        val audioSessionId = audioRecord?.audioSessionId ?: 0
        if (audioSessionId != 0) {
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    aec = AcousticEchoCanceler.create(audioSessionId).apply {
                        enabled = true
                    }
                    Log.i("AudioRecorder", "AcousticEchoCanceler enabled successfully")
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Failed to enable AcousticEchoCanceler", e)
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                try {
                    ns = NoiseSuppressor.create(audioSessionId).apply {
                        enabled = true
                    }
                    Log.i("AudioRecorder", "NoiseSuppressor enabled successfully")
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Failed to enable NoiseSuppressor", e)
                }
            }
        }

        recordingJob = scope.launch(recordDispatcher) {
            val buffer = ShortArray(sendChunkSize)
            val byteBuffer = ByteBuffer.allocate(sendChunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, sendChunkSize) ?: 0
                if (readSize > 0) {
                    var rawMaxAmp = 0
                    for (i in 0 until readSize) {
                        val absVal = java.lang.Math.abs(buffer[i].toInt())
                        if (absVal > rawMaxAmp) {
                            rawMaxAmp = absVal
                        }
                    }

                    val appPrefs = com.example.geminimultimodalliveapi.data.AppPreferences.getInstance(context)
                    val isAgc = appPrefs.isMicAgcEnabled
                    val manualGain = appPrefs.micGainValue

                    if (isAgc && rawMaxAmp > 300) {
                        // Dynamic AGC target peak amplitude is ~12000
                        val targetAmp = 12000f
                        val targetGain = targetAmp / rawMaxAmp.toFloat()
                        if (targetGain < runningAgcGain) {
                            // Fast attack to prevent clipping
                            runningAgcGain += (targetGain - runningAgcGain) * 0.2f
                        } else {
                            // Slow decay to maintain sensitivity
                            runningAgcGain += (targetGain - runningAgcGain) * 0.05f
                        }
                        runningAgcGain = runningAgcGain.coerceIn(0.5f, 5.0f)
                    }

                    val baseGain = if (isAgc) runningAgcGain else manualGain
                    val isAiSpeaking = com.example.geminimultimodalliveapi.FloatingWidgetService.instance?.isAudioPlaying() == true
                    val softwareGain = if (isAiSpeaking) baseGain * 0.1f else baseGain

                    var maxAmp = 0
                    var crossings = 0
                    for (i in 0 until readSize) {
                        val boostedValue = (buffer[i] * softwareGain).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt()
                        buffer[i] = boostedValue.toShort()
                        val absVal = java.lang.Math.abs(boostedValue)
                        if (absVal > maxAmp) {
                            maxAmp = absVal
                        }
                        if (i > 0) {
                            val prev = buffer[i - 1]
                            val curr = buffer[i]
                            if ((prev > 0 && curr <= 0) || (prev < 0 && curr >= 0)) {
                                crossings++
                            }
                        }
                    }
                    val zcr = if (readSize > 1) crossings.toFloat() / (readSize - 1) else 0.0f
                    
                    byteBuffer.clear()
                    for (i in 0 until readSize) {
                        byteBuffer.putShort(buffer[i])
                    }
                    val byteArray = ByteArray(readSize * 2)
                    System.arraycopy(byteBuffer.array(), 0, byteArray, 0, readSize * 2)
                    
                    Log.d("AudioRecorder", "Recorded chunk: size=${byteArray.size}, maxAmp=$maxAmp, zcr=$zcr")
                    onAudioChunkRecorded(byteArray, maxAmp, zcr)
                } else if (readSize < 0) {
                    Log.w("AudioRecorder", "Error reading audio data: $readSize")
                    delay(100)
                } else {
                    yield()
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob?.join()
        recordingJob = null
        try {
            audioRecord?.let { record ->
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            aec?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing AEC", e)
        }
        aec = null

        try {
            ns?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing NS", e)
        }
        ns = null
    }

    fun release() {
        stop()
        try {
            recordExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error shutting down record executor", e)
        }
    }
}
