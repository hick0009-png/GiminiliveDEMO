package com.example.geminimultimodalliveapi.network

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiTextService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun generateText(
        apiKey: String,
        prompt: String,
        history: List<Pair<String, String>>,
        callback: (String?) -> Unit
    ) {
        generateTextWithSystemInstruction(
            apiKey = apiKey,
            prompt = prompt,
            systemInstructionText = "คุณคือผู้ช่วยอัจฉริยะในที่ประชุม ตอบคำถามของผู้ใช้สั้นๆ กระชับ และตรงประเด็น เป็นภาษาไทย",
            history = history,
            callback = callback
        )
    }

    fun generateTextWithSystemInstruction(
        apiKey: String,
        prompt: String,
        systemInstructionText: String,
        history: List<Pair<String, String>> = emptyList(),
        callback: (String?) -> Unit
    ) {
        try {
            val requestBody = JSONObject()
            val contents = JSONArray()

            // Add history
            for (turn in history) {
                val contentObj = JSONObject()
                contentObj.put("role", turn.first)
                val parts = JSONArray()
                val partText = JSONObject()
                partText.put("text", turn.second)
                parts.put(partText)
                contentObj.put("parts", parts)
                contents.put(contentObj)
            }

            // Add current prompt
            val currentContentObj = JSONObject()
            currentContentObj.put("role", "user")
            val currentParts = JSONArray()
            val currentPartText = JSONObject()
            currentPartText.put("text", prompt)
            currentParts.put(currentPartText)
            currentContentObj.put("parts", currentParts)
            contents.put(currentContentObj)

            requestBody.put("contents", contents)

            // Custom System Instruction
            val systemInstruction = JSONObject()
            val sysParts = JSONArray()
            val sysPartText = JSONObject()
            sysPartText.put("text", systemInstructionText)
            sysParts.put(sysPartText)
            systemInstruction.put("parts", sysParts)
            requestBody.put("systemInstruction", systemInstruction)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("GeminiTextService", "API request failed: ${e.message}", e)
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val respStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(respStr)
                            val candidates = json.getJSONArray("candidates")
                            val textResult = candidates.getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                            callback(textResult)
                        } catch (e: Exception) {
                            Log.e("GeminiTextService", "Error parsing response: ${e.message}", e)
                            callback(null)
                        }
                    } else {
                        Log.e("GeminiTextService", "API error: ${response.code} / $respStr")
                        callback(null)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("GeminiTextService", "Error building request: ${e.message}", e)
            callback(null)
        }
    }
}
