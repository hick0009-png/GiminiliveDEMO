package com.example.geminimultimodalliveapi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AudioPlayer(
    private val sampleRate: Int = AudioConfig.OUTPUT_SAMPLE_RATE,
    private val context: Context? = null
) {
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    var isPlaying = false
        private set
    private var currentSampleRate: Int = sampleRate
    private var audioTrack: AudioTrack? = null
    var onPlaybackActive: (() -> Unit)? = null

    // Audio Focus management
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusLock = Any()

    // Thread isolation - dedicated high-priority single thread
    private val playbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gemini-playback-thread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val playbackDispatcher = playbackExecutor.asCoroutineDispatcher()

    // Jitter Buffer parameters
    private val minBufferChunks = 3 // Wait for at least 3 chunks (~120ms-240ms) to prevent stutters
    private var isBuffering = true

    init {
        context?.let {
            audioManager = it.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false
        synchronized(focusLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (audioFocusRequest == null) {
                        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAcceptsDelayedFocusGain(false)
                            .setOnAudioFocusChangeListener { focusChange ->
                                Log.d("AudioPlayer", "OnAudioFocusChange: $focusChange")
                            }
                            .build()
                        audioFocusRequest = focusRequest
                    }
                    val res = audioManager?.requestAudioFocus(audioFocusRequest!!)
                    return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                } else {
                    @Suppress("DEPRECATION")
                    val res = audioManager?.requestAudioFocus(
                        { focusChange -> Log.d("AudioPlayer", "OnAudioFocusChange (legacy): $focusChange") },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                    return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error requesting audio focus", e)
                return false
            }
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        synchronized(focusLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let {
                        audioManager?.abandonAudioFocusRequest(it)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus { }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error abandoning audio focus", e)
            }
        }
    }

    private fun releaseAudioTrack() {
        synchronized(this) {
            try {
                audioTrack?.let { track ->
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.stop()
                        track.flush()
                        track.release()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error releasing audio track", e)
            }
            audioTrack = null
        }
    }

    fun playAudioChunk(base64Chunk: String, chunkSampleRate: Int, scope: CoroutineScope, onActive: () -> Unit = {}) {
        try {
            if (this.currentSampleRate != chunkSampleRate) {
                Log.i("AudioPlayer", "Sample rate changed from ${this.currentSampleRate} to $chunkSampleRate. Recreating AudioTrack.")
                this.currentSampleRate = chunkSampleRate
                releaseAudioTrack()
            }

            val chunk = Base64.decode(base64Chunk, Base64.DEFAULT)
            audioQueue.put(chunk)
            
            onActive()
            ensurePlaybackLoopRunning(scope)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error queueing audio chunk", e)
        }
    }
    private fun ensurePlaybackLoopRunning(scope: CoroutineScope) {
        synchronized(this) {
            if (isPlaying) return
            isPlaying = true
        }

        playbackJob?.cancel()
        playbackJob = scope.launch(playbackDispatcher) {
            requestAudioFocus()
            try {
                var isBuffering = true
                var lastActiveTime = System.currentTimeMillis()
                while (isActive) {
                    val currentTime = System.currentTimeMillis()

                    if (isBuffering) {
                        if (audioQueue.size >= minBufferChunks) {
                            isBuffering = false
                            lastActiveTime = currentTime
                            Log.d("AudioPlayer", "Jitter buffer filled (${audioQueue.size} chunks), starting playback")
                        } else {
                            if (currentTime - lastActiveTime > 2000L) {
                                Log.d("AudioPlayer", "Buffering timeout, stopping playback loop")
                                stopFromLoop()
                                break
                            }
                            delay(10)
                            continue
                        }
                    }

                    val chunk = audioQueue.poll(100, TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        playAudio(chunk)
                        lastActiveTime = System.currentTimeMillis()
                    } else {
                        if (currentTime - lastActiveTime > 1500L) {
                            Log.d("AudioPlayer", "Silence timeout, stopping playback loop")
                            stopFromLoop()
                            break
                        } else {
                            isBuffering = true
                            Log.d("AudioPlayer", "Jitter buffer underflow, rebuffering...")
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Task cancelled
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error in playback loop", e)
            } finally {
                synchronized(this@AudioPlayer) {
                    isPlaying = false
                }
            }
        }
    }

    private fun playAudio(byteArray: ByteArray) {
        try {
            var track = audioTrack
            if (track == null) {
                synchronized(this) {
                    track = audioTrack
                    if (track == null) {
                        val minBufferSize = AudioTrack.getMinBufferSize(
                            currentSampleRate,
                            AudioConfig.CHANNEL_OUT,
                            AudioConfig.ENCODING
                        )
                        val bufferSizeInBytes = minBufferSize * 2
                        
                        track = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                android.media.AudioFormat.Builder()
                                    .setChannelMask(AudioConfig.CHANNEL_OUT)
                                    .setEncoding(AudioConfig.ENCODING)
                                    .setSampleRate(currentSampleRate)
                                    .build()
                            )
                            .setBufferSizeInBytes(bufferSizeInBytes)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        audioTrack = track
                    }
                }
            }

            track?.let { t ->
                if (t.state == AudioTrack.STATE_INITIALIZED) {
                    t.write(byteArray, 0, byteArray.size)
                    onPlaybackActive?.invoke()
                    if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        t.play()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Exception in playAudio", e)
        }
    }

    private fun stopFromLoop() {
        audioQueue.clear()
        abandonAudioFocus()
        releaseAudioTrack()
        synchronized(this) {
            isPlaying = false
        }
    }

    fun stop() {
        audioQueue.clear()

        playbackJob?.cancel()
        playbackJob = null

        abandonAudioFocus()

        // Use the synchronized releaseAudioTrack() to prevent race condition
        // with the gemini-playback-thread that may still be writing to the track.
        releaseAudioTrack()

        synchronized(this) {
            isPlaying = false
        }
    }
    
    // Cleanup resources (called on Service destroy)
    fun release() {
        stop()
        try {
            playbackExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error shutting down playback executor", e)
        }
    }
}
