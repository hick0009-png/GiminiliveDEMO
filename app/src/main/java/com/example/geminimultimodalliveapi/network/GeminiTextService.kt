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
        cachedContentName: String? = null,
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

            // Custom System Instruction (only if not using cache)
            if (cachedContentName == null) {
                val systemInstruction = JSONObject()
                val sysParts = JSONArray()
                val sysPartText = JSONObject()
                sysPartText.put("text", systemInstructionText)
                sysParts.put(sysPartText)
                systemInstruction.put("parts", sysParts)
                requestBody.put("systemInstruction", systemInstruction)
            } else {
                requestBody.put("cachedContent", cachedContentName)
            }

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

    fun createContextCache(
        apiKey: String,
        modelName: String,
        systemInstructionText: String,
        docContents: List<String>,
        ttlSeconds: Int = 300,
        callback: (String?, Long?) -> Unit
    ) {
        try {
            val requestBody = JSONObject()
            requestBody.put("model", "models/$modelName")

            val systemInstruction = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", systemInstructionText))
            systemInstruction.put("parts", sysParts)
            requestBody.put("systemInstruction", systemInstruction)

            if (docContents.isNotEmpty()) {
                val contents = JSONArray()
                val contentObj = JSONObject()
                contentObj.put("role", "user")
                val parts = JSONArray()
                val docText = docContents.joinToString("\n---\n") { "เอกสารอ้างอิงที่เกี่ยวข้อง:\n$it" }
                parts.put(JSONObject().put("text", docText))
                contentObj.put("parts", parts)
                contents.put(contentObj)
                requestBody.put("contents", contents)
            }

            requestBody.put("ttl", "${ttlSeconds}s")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/cachedContents?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("GeminiTextService", "Cache creation request failed: ${e.message}", e)
                    callback(null, null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val respStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(respStr)
                            val name = json.getString("name")
                            val expireTimeStr = json.getString("expireTime")
                            val formatter = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                java.time.format.DateTimeFormatter.ISO_DATE_TIME
                            } else null
                            val epochMs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && formatter != null) {
                                java.time.Instant.from(formatter.parse(expireTimeStr)).toEpochMilli()
                            } else {
                                try {
                                    val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    }
                                    df.parse(expireTimeStr)?.time
                                } catch (ex: Exception) {
                                    try {
                                        val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        }
                                        df.parse(expireTimeStr)?.time
                                    } catch (ex2: Exception) {
                                        System.currentTimeMillis() + (ttlSeconds * 1000L)
                                    }
                                }
                            }
                            callback(name, epochMs)
                        } catch (e: Exception) {
                            Log.e("GeminiTextService", "Error parsing cache creation response: ${e.message}", e)
                            callback(null, null)
                        }
                    } else {
                        Log.e("GeminiTextService", "Cache creation API error: ${response.code} / $respStr")
                        callback(null, null)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("GeminiTextService", "Error building cache creation request: ${e.message}", e)
            callback(null, null)
        }
    }
}
