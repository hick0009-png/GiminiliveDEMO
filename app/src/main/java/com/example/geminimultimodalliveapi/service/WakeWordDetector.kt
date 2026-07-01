package com.example.geminimultimodalliveapi.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class WakeWordDetector(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onWakeWordDetected(command: String)
        fun onError(error: Int)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var currentWakeWord = "กอหญ้า"
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                Log.d("WakeWordDetector", "Created Standard SpeechRecognizer")

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("WakeWordDetector", "SpeechRecognizer ready")
                        muteSystemStream(false)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d("WakeWordDetector", "Recognizer error: $error")
                        muteSystemStream(false)
                        isListening = false
                        listener.onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        handleRecognitionResults(results, isFinal = true)
                        listener.onError(0) // Signal completion to trigger reschedule
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        handleRecognitionResults(partialResults, isFinal = false)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            } catch (e: Exception) {
                Log.e("WakeWordDetector", "Failed to initialize SpeechRecognizer", e)
            }
        }
    }

    private fun handleRecognitionResults(results: Bundle?, isFinal: Boolean) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val rawWakeWord = currentWakeWord.lowercase().trim()
        val wakeWord = if (rawWakeWord.isBlank()) "กอหญ้า" else rawWakeWord
        val phoneticVariants = if (wakeWord == "กอหญ้า") {
            listOf("กอหญ้า", "กอยา", "กอหยา", "กอ หญ้า", "korya", "ก็หญ้า", "ก้อย่า", "เกาะหญ้า", "กอ ยา")
        } else {
            listOf(wakeWord)
        }

        for (match in matches) {
            val text = match.lowercase().trim()
            Log.d("WakeWordDetector", "Recognized text (isFinal=$isFinal): $text")

            val foundVariant = phoneticVariants.firstOrNull { variant ->
                variant.isNotBlank() && text.contains(variant)
            }

            if (foundVariant != null) {
                Log.i("WakeWordDetector", "Wake word '$foundVariant' detected (isFinal=$isFinal) in: '$text'")
                
                var commandText = ""
                val index = text.indexOf(foundVariant)
                if (index != -1) {
                    val afterWakeWord = text.substring(index + foundVariant.length).trim()
                    if (afterWakeWord.isNotEmpty()) {
                        commandText = afterWakeWord
                    }
                }
                listener.onWakeWordDetected(commandText)
                break
            }
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (e: Exception) {}
                }
                muteSystemStream(true)
                isListening = true
                speechRecognizer?.startListening(recognizerIntent)
                Log.d("WakeWordDetector", "Started local SpeechRecognizer listening")
            } catch (e: Exception) {
                Log.e("WakeWordDetector", "Error starting listening", e)
                muteSystemStream(false)
                isListening = false
            }
        }
    }

    fun stopListening() {
        val block = Runnable {
            try {
                isListening = false
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                muteSystemStream(false)
            } catch (e: Exception) {
                Log.e("WakeWordDetector", "Error stopping listening", e)
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block.run()
        } else {
            mainHandler.post(block)
        }
    }

    fun updateWakeWord(newWord: String) {
        currentWakeWord = newWord
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("WakeWordDetector", "Error destroying speech recognizer", e)
            }
        }
    }

    private fun muteSystemStream(mute: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, mute)
            }
        } catch (e: Exception) {
            Log.e("WakeWordDetector", "Error muting/unmuting stream: mute=$mute", e)
        }
    }
}
