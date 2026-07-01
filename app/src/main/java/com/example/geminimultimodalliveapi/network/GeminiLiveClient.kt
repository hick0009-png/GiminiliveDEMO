package com.example.geminimultimodalliveapi.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val selectedVoice: String,
    private val wakeWord: String,
    memoryContext: String,
    private val listener: Listener
) {
    private var currentMemoryContext = memoryContext
    private val activeToolCalls = ConcurrentHashMap<String, String>()
    
    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onReconnecting(attempt: Int)
        fun onError(errorMsg: String)
        fun onTextMessageReceived(text: String)
        fun onAudioChunkReceived(base64Audio: String, sampleRate: Int)
        fun onInterrupted()
        fun onCameraOpenRequested(callId: String)
        fun onCameraCloseRequested(callId: String)
        fun onSaveVehicleInfoRequested(callId: String, category: String, keyName: String, infoValue: String)
        fun onQueryVehicleInfoRequested(callId: String, category: String?)
        fun onGetCurrentTimeRequested(callId: String)
        fun onDeleteVehicleInfoRequested(callId: String, category: String, keyName: String?)
        fun onQueryPolicyDocumentRequested(callId: String, query: String)
        fun onMakePhoneCallRequested(callId: String, phoneNumber: String)
        fun onEndPhoneCallRequested(callId: String)
        fun onCreateCalendarEventRequested(callId: String, title: String, description: String, startTimeIso: String, durationMinutes: Int)
        fun onListCalendarEventsRequested(callId: String)
        fun onGetCurrentWeatherRequested(callId: String)
        fun onFindNearbyPlacesRequested(callId: String, placeType: String)
        fun onLaunchNavigationRequested(callId: String, destination: String)
        fun onRememberPersonalFactRequested(callId: String, factContent: String, importance: Int, category: String)
        fun onForgetPersonalFactRequested(callId: String, query: String)
        fun onQueryRelevantMemoriesRequested(callId: String, searchQuery: String)
        fun onSaveSystemRuleRequested(callId: String, conditionType: String?, conditionValue: String?, instruction: String?, action: String)
        fun onGetSituationalContextRequested(callId: String)
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    var isConnected = false
        private set

    private var reconnectAttempts = 0
    private var isExplicitDisconnect = false
    private val reconnectRunnable = Runnable {
        Log.i("GeminiLiveClient", "Attempting to reconnect (Attempt ${reconnectAttempts + 1}/3)...")
        reconnectAttempts++
        connect()
    }

    private val MODEL = "models/gemini-3.1-flash-live-preview"
    private val HOST = "generativelanguage.googleapis.com"

    fun connect() {
        if (isConnected) return
        isExplicitDisconnect = false
        mainHandler.removeCallbacks(reconnectRunnable)
        
        val wsUrl = "wss://$HOST/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        Log.i("GeminiLiveClient", "Connecting to WebSocket at wss://$HOST/... (key masked)")

        if (client == null) {
            client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(10, TimeUnit.SECONDS) // Send ping every 10s to keep connection alive
                .build()
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    isConnected = true
                    reconnectAttempts = 0
                    listener.onConnected()
                    sendInitialSetupMessage()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                receiveMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                receiveMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Managed connection close initiated by the server
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    isConnected = false
                    this@GeminiLiveClient.webSocket = null
                    if (!isExplicitDisconnect && reconnectAttempts < 3) {
                        scheduleReconnect()
                    } else {
                        listener.onDisconnected(reason.ifEmpty { "Connection closed" })
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    isConnected = false
                    this@GeminiLiveClient.webSocket = null
                    if (!isExplicitDisconnect && reconnectAttempts < 3) {
                        scheduleReconnect()
                    } else {
                        listener.onError(t.message ?: "WebSocket failure")
                    }
                }
            }
        })
    }

    private fun scheduleReconnect() {
        val delay = (1L shl reconnectAttempts) * 1000L
        Log.i("GeminiLiveClient", "Scheduling reconnect in $delay ms (Attempt ${reconnectAttempts + 1}/3)")
        mainHandler.removeCallbacks(reconnectRunnable)
        listener.onReconnecting(reconnectAttempts + 1)
        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    fun disconnect() {
        isExplicitDisconnect = true
        mainHandler.removeCallbacks(reconnectRunnable)
        try {
            webSocket?.close(1000, "Explicit disconnect")
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error closing WebSocket", e)
        }
        webSocket = null
    }

    fun sendMediaChunk(b64Data: String, mimeType: String) {
        if (!isConnected) return
        
        val key = if (mimeType.startsWith("audio/")) "audio" else "video"
        
        // Manual JSON String construction to avoid heavy kotlinx.serialization overhead
        // on large image/audio payloads, reducing Garbage Collection pressure.
        val jsonString = "{\"realtimeInput\":{\"$key\":{\"mimeType\":\"$mimeType\",\"data\":\"$b64Data\"}}}"
        try {
            webSocket?.send(jsonString)
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error sending media chunk", e)
        }
    }

    fun sendTextMessage(text: String) {
        if (!isConnected) return

        val msg = buildJsonObject {
            put("clientContent", buildJsonObject {
                put("turns", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        val jsonString = Json { prettyPrint = false }.encodeToString(msg)
        try {
            webSocket?.send(jsonString)
            Log.i("GeminiLiveClient", "Sent text message client turn: $text")
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error sending text message", e)
        }
    }

    fun sendToolResponse(callId: String, success: Boolean) {
        val output = JSONObject()
        output.put("success", success)
        sendToolResponse(callId, output)
    }

    fun sendToolResponse(callId: String, output: JSONObject) {
        if (!isConnected) return
        
        val name = activeToolCalls.remove(callId)
        if (name == null) {
            Log.w("GeminiLiveClient", "sendToolResponse: No active tool call found with ID $callId")
        }
        
        val setupMessage = JSONObject()
        val toolResponse = JSONObject()
        val functionResponses = org.json.JSONArray()
        val funcResp = JSONObject()
        funcResp.put("id", callId)
        if (name != null) {
            funcResp.put("name", name)
        }
        
        val response = JSONObject()
        val keys = output.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            response.put(key, output.get(key))
        }
        funcResp.put("response", response)
        
        functionResponses.put(funcResp)
        toolResponse.put("functionResponses", functionResponses)
        setupMessage.put("toolResponse", toolResponse)
        
        try {
            val jsonString = setupMessage.toString()
            Log.i("GeminiLiveClient", "Sending tool response (callId=$callId): $jsonString")
            val startTime = System.currentTimeMillis()
            webSocket?.send(jsonString)
            Log.d("GeminiLiveClient", "Tool response sent in ${System.currentTimeMillis() - startTime} ms")
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error sending tool response", e)
        }
    }

    private fun sendInitialSetupMessage() {
        val setupMessage = JSONObject()
        val setup = JSONObject()
        val generationConfig = JSONObject()
        val responseModalities = org.json.JSONArray()
        responseModalities.put("AUDIO")
        generationConfig.put("responseModalities", responseModalities)
        
        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", selectedVoice)
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)

        val systemInstructionText = "คุณคือผู้ช่วย AI แสนเป็นมิตรสำหรับช่วยเหลือขณะขับขี่มอเตอร์ไซค์ ชื่อของคุณคือ \"$wakeWord\" " +
                "กรุณาพูดและตอบโต้กับผู้ใช้เป็นภาษาไทยเสมออย่างเป็นธรรมชาติสั้นกระชับ " +
                "กฎเหล็กเรื่องเวลาและสภาพอากาศ: ห้ามคุณคาดเดาหรือแต่งเวลาปัจจุบันหรือสภาพอากาศด้วยตนเองเด็ดขาด! " +
                "เมื่อผู้ใช้ถามเวลา ตอนนี้กี่โมง หรือถามเกี่ยวกับเวลาปัจจุบัน คุณต้องเรียกใช้งานเครื่องมือ get_current_time ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ถามถึงสภาพอากาศ อุณหภูมิ ฝนตก หรือคำถามเกี่ยวกับลมฟ้าอากาศในปัจจุบัน คุณต้องเรียกใช้งานเครื่องมือ get_current_weather ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ต้องการค้นหาสถานที่ใกล้เคียง เช่น ปั๊มน้ำมัน ร้านซ่อมรถ โรงพยาบาล ร้านอาหาร ร้านกาแฟ หรือตู้เอทีเอ็ม คุณต้องเรียกใช้งานเครื่องมือ find_nearby_places ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ขอให้นำทาง บอกทาง หรือเปิดแผนที่นำทางไปยังสถานที่ใดๆ คุณต้องเรียกใช้งานเครื่องมือ launch_navigation ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้บอกเรื่องราวส่วนตัว ข้อมูลสำคัญ หรือสั่งให้คุณจดจำเรื่องราวใดๆ (เช่น เรื่องครอบครัว สิ่งที่ชอบ สิ่งที่ต้องทำ หรือเรื่องที่พูดคุยกัน) คุณต้องเรียกใช้งานเครื่องมือ remember_personal_fact เพื่อบันทึกความจำระยะยาวทันที " +
                "เมื่อผู้ใช้สั่งให้ลืม ลบความจำ หรือบอกว่าเรื่องนั้นไม่ถูกต้องแล้ว คุณต้องเรียกใช้งานเครื่องมือ forget_personal_fact เพื่อลบความจำนั้นออกจากระบบทันที " +
                "เมื่อผู้ใช้สั่งจูนการตั้งค่าพฤติกรรม บันทึกกฎความประพฤติ หรือสั่งแก้ไขกฎพฤติกรรมในสถานการณ์ต่างๆ คุณต้องเรียกใช้งานเครื่องมือ save_system_rule ทันทีทุกครั้ง " +
                "เรียกใช้งานเครื่องมือ get_situational_context เฉพาะเมื่อจำเป็นต้องทราบข้อมูลสถานการณ์แวดล้อมล่าสุด (เช่น พิกัดแผนที่, ความเร็ว/การเคลื่อนที่, กฎระบบควบคุมพฤติกรรม) เพื่อใช้ตอบคำถามที่เกี่ยวข้องกับสถานะขับขี่หรือสถานที่ หรือเมื่อต้องการปรับเปลี่ยนพฤติกรรมในหัวข้อใหม่ๆ เท่านั้น ห้ามเรียกใช้พร่ำเพรื่อทุกครั้งก่อนการตอบกลับ โดยเฉพาะอย่างยิ่งหากผู้ใช้ถามข้อมูลที่เรียกเครื่องมืออื่นตรงตัวได้อยู่แล้ว เช่น เมื่อถามเวลาหรือสภาพอากาศ ให้เรียกใช้งาน get_current_time หรือ get_current_weather โดยตรงทันทีเป็นคู่ขนานโดยไม่ต้องเรียก get_situational_context มาหน่วงเวลาเพิ่ม " +
                "คุณมีเครื่องมือช่วยเหลือในฐานข้อมูลความจำเครื่อง (SQLite) " +
                "เมื่อผู้ใช้สั่งให้จำ หรือผู้ใช้ถามถึงข้อมูล เช่น ทะเบียนรถ วันหมดอายุภาษี/ป้ายวงกลม ประวัติเช็คระยะ หรือที่จอดรถ " +
                "กรุณาเรียกใช้ฟังก์ชันบันทึกหรือค้นหาข้อมูลย้อนหลังให้สอดคล้องกันโดยอัตโนมัติ" +
                (if (currentMemoryContext.isNotEmpty()) "\n\n$currentMemoryContext" else "")

        val systemInstruction = JSONObject()
        val parts = org.json.JSONArray()
        val partText = JSONObject()
        partText.put("text", systemInstructionText)
        parts.put(partText)
        systemInstruction.put("parts", parts)

        setup.put("model", MODEL)
        setup.put("generationConfig", generationConfig)
        setup.put("systemInstruction", systemInstruction)
        
        val tools = ToolDefinitions.getTools()
        setup.put("tools", tools)

        val realtimeInputConfig = JSONObject()
        val automaticActivityDetection = JSONObject()
        automaticActivityDetection.put("disabled", false)
        automaticActivityDetection.put("silenceDurationMs", 400)
        realtimeInputConfig.put("automaticActivityDetection", automaticActivityDetection)
        setup.put("realtimeInputConfig", realtimeInputConfig)
        
        setupMessage.put("setup", setup)
        try {
            val setupStr = setupMessage.toString()
            Log.i("GeminiLiveClient", "Sending initial setup message")
            webSocket?.send(setupStr)
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error sending initial setup message", e)
        }
    }

    fun updateMemoryContext(contextPrompt: String) {
        currentMemoryContext = contextPrompt
        // Update local variable only. Do NOT send clientContent with role: "system" over WebSocket,
        // because the Gemini Live API protocol does not support mid-session system instruction updates.
        // Instead, the model will dynamically query the context on-demand via the get_situational_context tool.
        Log.i("GeminiLiveClient", "Updated local memory context.")
    }

    private fun receiveMessage(message: String?) {
        if (message == null) return
        try {
            val messageData = JSONObject(message)
            
            if (messageData.has("serverContent")) {
                val serverContent = messageData.getJSONObject("serverContent")
                
                if (serverContent.has("interrupted") && serverContent.getBoolean("interrupted")) {
                    listener.onInterrupted()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                val text = part.getString("text")
                                listener.onTextMessageReceived(text)
                            }
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                if (inlineData.has("mimeType") && inlineData.getString("mimeType").startsWith("audio/pcm")) {
                                    val mimeType = inlineData.getString("mimeType")
                                    val audioData = inlineData.getString("data")
                                    
                                    var rate = 24000
                                    if (mimeType.contains("rate=")) {
                                        try {
                                            val rateStr = mimeType.substringAfter("rate=").substringBefore(";").trim()
                                            rate = rateStr.toInt()
                                        } catch (e: Exception) {
                                            Log.e("GeminiLiveClient", "Error parsing sample rate from mimeType: $mimeType", e)
                                        }
                                    }
                                    listener.onAudioChunkReceived(audioData, rate)
                                }
                            }
                        }
                    }
                }
            }
            
            if (messageData.has("toolCall")) {
                val toolCall = messageData.getJSONObject("toolCall")
                if (toolCall.has("functionCalls")) {
                    val functionCalls = toolCall.getJSONArray("functionCalls")
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.getString("name")
                        val id = call.getString("id")
                        activeToolCalls[id] = name
                        if (name == "open_camera") {
                            mainHandler.post {
                                listener.onCameraOpenRequested(id)
                            }
                        } else if (name == "close_camera") {
                            mainHandler.post {
                                listener.onCameraCloseRequested(id)
                            }
                        } else if (name == "save_vehicle_info") {
                            val args = call.optJSONObject("args")
                            val category = args?.optString("category", "general") ?: "general"
                            val keyName = args?.optString("key_name", "info") ?: "info"
                            val infoValue = args?.optString("info_value", "") ?: ""
                            mainHandler.post {
                                listener.onSaveVehicleInfoRequested(id, category, keyName, infoValue)
                            }
                        } else if (name == "query_vehicle_info") {
                            val args = call.optJSONObject("args")
                            val category = args?.optString("category", null)
                            mainHandler.post {
                                listener.onQueryVehicleInfoRequested(id, category)
                            }
                        } else if (name == "get_current_time") {
                            mainHandler.post {
                                listener.onGetCurrentTimeRequested(id)
                            }
                        } else if (name == "delete_vehicle_info") {
                            val args = call.optJSONObject("args")
                            val category = args?.optString("category", "general") ?: "general"
                            val keyName = args?.optString("key_name", null)
                            mainHandler.post {
                                listener.onDeleteVehicleInfoRequested(id, category, keyName)
                            }
                        } else if (name == "query_policy_document") {
                            val args = call.optJSONObject("args")
                            val query = args?.optString("query", "") ?: ""
                            mainHandler.post {
                                listener.onQueryPolicyDocumentRequested(id, query)
                            }
                        } else if (name == "make_phone_call") {
                            val args = call.optJSONObject("args")
                            val phoneNumber = args?.optString("phone_number", "") ?: ""
                            mainHandler.post {
                                listener.onMakePhoneCallRequested(id, phoneNumber)
                            }
                        } else if (name == "end_phone_call") {
                            mainHandler.post {
                                listener.onEndPhoneCallRequested(id)
                            }
                        } else if (name == "create_calendar_event") {
                            val args = call.optJSONObject("args")
                            val title = args?.optString("title", "") ?: ""
                            val description = args?.optString("description", "") ?: ""
                            val startTimeIso = args?.optString("start_time", "") ?: ""
                            val durationMinutes = args?.optInt("duration_minutes", 60) ?: 60
                            mainHandler.post {
                                listener.onCreateCalendarEventRequested(id, title, description, startTimeIso, durationMinutes)
                            }
                        } else if (name == "list_calendar_events") {
                            mainHandler.post {
                                listener.onListCalendarEventsRequested(id)
                            }
                        } else if (name == "get_current_weather") {
                            mainHandler.post {
                                listener.onGetCurrentWeatherRequested(id)
                            }
                        } else if (name == "find_nearby_places") {
                            val args = call.optJSONObject("args")
                            val placeType = args?.optString("place_type", "") ?: ""
                            mainHandler.post {
                                listener.onFindNearbyPlacesRequested(id, placeType)
                            }
                        } else if (name == "launch_navigation") {
                            val args = call.optJSONObject("args")
                            val destination = args?.optString("destination", "") ?: ""
                            mainHandler.post {
                                listener.onLaunchNavigationRequested(id, destination)
                            }
                        } else if (name == "remember_personal_fact") {
                            val args = call.optJSONObject("args")
                            val factContent = args?.optString("fact_content", "") ?: ""
                            val importance = args?.optInt("importance", 3) ?: 3
                            val category = args?.optString("category", "general") ?: "general"
                            mainHandler.post {
                                listener.onRememberPersonalFactRequested(id, factContent, importance, category)
                            }
                        } else if (name == "forget_personal_fact") {
                            val args = call.optJSONObject("args")
                            val query = args?.optString("query", "") ?: ""
                            mainHandler.post {
                                listener.onForgetPersonalFactRequested(id, query)
                            }
                        } else if (name == "query_relevant_memories") {
                            val args = call.optJSONObject("args")
                            val searchQuery = args?.optString("search_query", "") ?: ""
                            mainHandler.post {
                                listener.onQueryRelevantMemoriesRequested(id, searchQuery)
                            }
                        } else if (name == "save_system_rule") {
                            val args = call.optJSONObject("args")
                            val conditionType = args?.optString("condition_type", null)
                            val conditionValue = args?.optString("condition_value", null)
                            val instruction = args?.optString("instruction", null)
                            val action = args?.optString("action", "SAVE") ?: "SAVE"
                            mainHandler.post {
                                listener.onSaveSystemRuleRequested(id, conditionType, conditionValue, instruction, action)
                            }
                        } else if (name == "get_situational_context") {
                            mainHandler.post {
                                listener.onGetSituationalContextRequested(id)
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("GeminiLiveClient", "Error parsing message", e)
        }
    }
}
