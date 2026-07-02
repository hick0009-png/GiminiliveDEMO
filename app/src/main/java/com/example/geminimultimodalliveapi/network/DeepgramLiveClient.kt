package com.example.geminimultimodalliveapi.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DeepgramLiveClient(private val callback: Callback) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastApiKey: String? = null
    private var lastWakeWord: String? = null
    private var reconnectAttempts = 0
    private var isExplicitDisconnect = false

    private val reconnectRunnable = Runnable {
        Log.i("DeepgramLiveClient", "Attempting to reconnect (Attempt ${reconnectAttempts + 1}/3)...")
        reconnectAttempts++
        val apiKey = lastApiKey
        val wakeWord = lastWakeWord
        if (apiKey != null && wakeWord != null) {
            connect(apiKey, wakeWord)
        }
    }

    private fun scheduleReconnect() {
        val delay = 2000L * (reconnectAttempts + 1)
        Log.i("DeepgramLiveClient", "Scheduling reconnect in ${delay}ms...")
        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    interface Callback {
        fun onTranscriptReceived(transcript: String, speakerId: Int, isFinal: Boolean, wordDetails: List<WordDetail>)
        fun onUtteranceEnd()
        fun onOpen()
        fun onClose()
        fun onError(t: Throwable, responseCode: Int?)
    }

    data class WordDetail(
        val word: String,
        val speaker: Int,
        val start: Double,
        val end: Double
    )

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun connect(apiKey: String, wakeWord: String) {
        lastApiKey = apiKey
        lastWakeWord = wakeWord
        isExplicitDisconnect = false
        mainHandler.removeCallbacks(reconnectRunnable)

        if (webSocket != null) {
            Log.w("DeepgramLiveClient", "WebSocket is already connected or connecting. Disconnecting first...")
            disconnect()
        }

        val encodedWakeWord = try {
            URLEncoder.encode(wakeWord, "UTF-8")
        } catch (e: Exception) {
            wakeWord
        }

        var queryParams = "diarize=true" +
                "&model=nova-2" +
                "&language=th" +
                "&encoding=linear16" +
                "&sample_rate=16000" +
                "&channels=1" +
                "&interim_results=true" +
                "&smart_format=true" +
                "&utterance_end_ms=1000"

        if (wakeWord.trim().isNotEmpty()) {
            queryParams += "&keywords=$encodedWakeWord:2"
        }

        val url = "wss://api.deepgram.com/v1/listen?$queryParams"

        Log.i("DeepgramLiveClient", "Connecting to Deepgram WebSocket: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("DeepgramLiveClient", "Deepgram WebSocket connection established successfully.")
                reconnectAttempts = 0
                mainHandler.post {
                    callback.onOpen()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseJsonMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("DeepgramLiveClient", "Deepgram WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("DeepgramLiveClient", "Deepgram WebSocket closed.")
                if (this@DeepgramLiveClient.webSocket === webSocket) {
                    this@DeepgramLiveClient.webSocket = null
                }
                mainHandler.post {
                    if (!isExplicitDisconnect && reconnectAttempts < 3) {
                        scheduleReconnect()
                    } else {
                        callback.onClose()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.e("DeepgramLiveClient", "Deepgram WebSocket failure: ${t.message} (HTTP Code: $code)", t)
                if (this@DeepgramLiveClient.webSocket === webSocket) {
                    this@DeepgramLiveClient.webSocket = null
                }
                mainHandler.post {
                    if (!isExplicitDisconnect && reconnectAttempts < 3) {
                        scheduleReconnect()
                    } else {
                        callback.onError(t, code)
                    }
                }
            }
        })
    }

    fun sendAudio(audioBytes: ByteArray, length: Int) {
        val socket = webSocket
        if (socket != null && length > 0) {
            try {
                val byteString = audioBytes.toByteString(0, length)
                socket.send(byteString)
            } catch (e: Exception) {
                Log.e("DeepgramLiveClient", "Error sending audio bytes: ${e.message}", e)
            }
        }
    }

    fun sendKeepAlive() {
        val socket = webSocket
        if (socket != null) {
            try {
                socket.send("{\"type\": \"KeepAlive\"}")
            } catch (e: Exception) {
                Log.e("DeepgramLiveClient", "Error sending KeepAlive text frame: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempts = 0
        val socket = webSocket
        if (socket != null) {
            try {
                Log.i("DeepgramLiveClient", "Sending CloseStream control frame to Deepgram...")
                // Graceful close control frame required by Deepgram
                socket.send("{\"type\": \"CloseStream\"}")
                socket.close(1000, "User requested disconnect")
            } catch (e: Exception) {
                Log.e("DeepgramLiveClient", "Error during graceful WebSocket close: ${e.message}", e)
            } finally {
                webSocket = null
            }
        }
    }

    private fun parseJsonMessage(text: String) {
        try {
            Log.d("DeepgramLiveClient", "Received JSON: $text")
            val json = JSONObject(text)

            // Handle UtteranceEnd event from Deepgram
            if (json.has("type") && json.getString("type") == "UtteranceEnd") {
                Log.d("DeepgramLiveClient", "Detected UtteranceEnd event")
                mainHandler.post {
                    callback.onUtteranceEnd()
                }
                return
            }

            val channel = json.optJSONObject("channel") ?: return
            val alternatives = channel.optJSONArray("alternatives") ?: return
            if (alternatives.length() == 0) return
            val alternative = alternatives.getJSONObject(0)

            val transcript = alternative.optString("transcript", "")
            val wordsArray = alternative.optJSONArray("words")
            val isFinal = json.optBoolean("is_final", false)
            val speechFinal = json.optBoolean("speech_final", false)

            val wordDetails = mutableListOf<WordDetail>()
            var majoritySpeaker = 0

            if (wordsArray != null && wordsArray.length() > 0) {
                val speakerCount = HashMap<Int, Int>()
                for (i in 0 until wordsArray.length()) {
                    val wordObj = wordsArray.getJSONObject(i)
                    val wordText = wordObj.optString("word", "")
                    val speaker = wordObj.optInt("speaker", 0)
                    val start = wordObj.optDouble("start", 0.0)
                    val end = wordObj.optDouble("end", 0.0)

                    wordDetails.add(WordDetail(wordText, speaker, start, end))
                    speakerCount[speaker] = (speakerCount[speaker] ?: 0) + 1
                }
                // Resolve majority speaker for this chunk
                majoritySpeaker = speakerCount.maxByOrNull { it.value }?.key ?: 0
            }

            if (transcript.isNotEmpty()) {
                mainHandler.post {
                    callback.onTranscriptReceived(transcript, majoritySpeaker, isFinal, wordDetails)
                }
            }

            if (speechFinal) {
                Log.d("DeepgramLiveClient", "Detected speech_final: true in results chunk, triggering onUtteranceEnd")
                mainHandler.post {
                    callback.onUtteranceEnd()
                }
            }
        } catch (e: Exception) {
            Log.e("DeepgramLiveClient", "Error parsing Deepgram JSON response: ${e.message}", e)
        }
    }
}
