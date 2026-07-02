package com.example.geminimultimodalliveapi.network

import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiMeetingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val MODELS = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-3.1-pro")

    private val cacheMap = java.util.concurrent.ConcurrentHashMap<String, CacheInfo>()

    private data class CacheInfo(
        val cacheName: String,
        val createdAt: Long
    )

    interface Callback {
        fun onSuccess(summary: String, transcriptJson: String)
        fun onError(error: String)
    }

    interface CacheCallback {
        fun onCacheCreated(cacheName: String)
        fun onCacheFailed(error: String)
    }

    fun analyzeMeeting(apiKey: String, audioFile: File, durationSeconds: Long, callback: Callback) {
        if (!audioFile.exists()) {
            callback.onError("ไม่พบไฟล์เสียงสำหรับการวิเคราะห์")
            return
        }

        // Run network operation on background thread
        Thread {
            try {
                val filePath = audioFile.absolutePath
                val cachedInfo = cacheMap[filePath]
                val currentTime = System.currentTimeMillis()
                
                val mimeType = if (audioFile.name.endsWith(".wav", ignoreCase = true)) "audio/wav" else "audio/mp4"
                val base64Audio = encodeFileToBase64(audioFile)
                val inlineRequestBody = buildRequestBody(base64Audio, mimeType)

                // Only use Context Caching if audio duration is >= 128 seconds (exactly 32,768 tokens at 256 tokens/sec)
                if (durationSeconds >= 128) {
                    if (cachedInfo != null && (currentTime - cachedInfo.createdAt) < 280 * 1000) {
                        Log.i("GeminiMeetingService", "Found valid existing Context Cache: ${cachedInfo.cacheName}. Reusing to save input tokens...")
                        val cachedRequestBody = buildCachedRequestBody(cachedInfo.cacheName)
                        executeAnalysisWithFallback(apiKey, inlineRequestBody, cachedRequestBody, 0, object : Callback {
                            override fun onSuccess(summary: String, transcriptJson: String) {
                                callback.onSuccess(summary, transcriptJson)
                            }

                            override fun onError(error: String) {
                                // If 404 is returned, it means the cached content expired or was deleted from the Google server
                                if (error.contains("404") || error.contains("not found", ignoreCase = true)) {
                                    Log.w("GeminiMeetingService", "Context Cache ${cachedInfo.cacheName} expired on server. Recreating new cache...")
                                    cacheMap.remove(filePath)
                                    createAndRunNewCache(apiKey, audioFile, mimeType, inlineRequestBody, callback)
                                } else {
                                    callback.onError(error)
                                }
                            }
                        })
                    } else {
                        Log.i("GeminiMeetingService", "Audio duration ${durationSeconds}s >= 128s. Creating new Context Cache...")
                        createAndRunNewCache(apiKey, audioFile, mimeType, inlineRequestBody, callback)
                    }
                } else {
                    Log.i("GeminiMeetingService", "Audio duration is ${durationSeconds}s (< 128s). Running standard inline analysis...")
                    executeAnalysisWithFallback(apiKey, inlineRequestBody, null, 0, callback)
                }
            } catch (e: Exception) {
                Log.e("GeminiMeetingService", "Error preparing request", e)
                callback.onError("เกิดข้อผิดพลาดในการเตรียมไฟล์: ${e.message}")
            }
        }.start()
    }

    private fun createAndRunNewCache(apiKey: String, audioFile: File, mimeType: String, inlineRequestBody: JSONObject, callback: Callback) {
        val base64Audio = encodeFileToBase64(audioFile)
        createContextCache(apiKey, base64Audio, mimeType, "gemini-3.5-flash", object : CacheCallback {
            override fun onCacheCreated(cacheName: String) {
                Log.i("GeminiMeetingService", "Context Cache created: $cacheName. Saving to memory...")
                cacheMap[audioFile.absolutePath] = CacheInfo(cacheName, System.currentTimeMillis())
                val cachedRequestBody = buildCachedRequestBody(cacheName)
                executeAnalysisWithFallback(apiKey, inlineRequestBody, cachedRequestBody, 0, callback)
            }

            override fun onCacheFailed(error: String) {
                Log.w("GeminiMeetingService", "Cache creation failed: $error. Falling back to normal inline analysis...")
                executeAnalysisWithFallback(apiKey, inlineRequestBody, null, 0, callback)
            }
        })
    }

    private fun createContextCache(apiKey: String, base64Audio: String, mimeType: String, model: String, callback: CacheCallback) {
        val cacheRequestJson = JSONObject()
        cacheRequestJson.put("model", "models/$model")
        cacheRequestJson.put("ttl", "300s") // 5 minutes cache TTL

        // 1. Set systemInstruction in CachedContent request body
        val systemInstruction = JSONObject()
        val sysParts = JSONArray()
        val sysPartText = JSONObject()
        sysPartText.put("text", getSystemPrompt())
        sysParts.put(sysPartText)
        systemInstruction.put("parts", sysParts)
        cacheRequestJson.put("systemInstruction", systemInstruction)

        val contents = JSONArray()
        val content = JSONObject()
        content.put("role", "user") // FIX: Cached content is required to have content.role set.
        val parts = JSONArray()

        val audioPart = JSONObject()
        val inlineData = JSONObject()
        inlineData.put("mimeType", mimeType)
        inlineData.put("data", base64Audio)
        audioPart.put("inlineData", inlineData)
        parts.put(audioPart)

        content.put("parts", parts)
        contents.put(content)
        cacheRequestJson.put("contents", contents)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/cachedContents?key=$apiKey")
            .post(cacheRequestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onCacheFailed("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(body)
                        val cacheName = json.getString("name")
                        callback.onCacheCreated(cacheName)
                    } catch (e: Exception) {
                        callback.onCacheFailed("Parse failed: ${e.message}")
                    }
                } else {
                    callback.onCacheFailed("HTTP ${response.code}: $body")
                }
            }
        })
    }

    private fun executeAnalysisWithFallback(
        apiKey: String,
        inlineRequestBody: JSONObject,
        cachedRequestBody: JSONObject?,
        modelIndex: Int,
        callback: Callback
    ) {
        if (modelIndex >= MODELS.size) {
            callback.onError("ระบบวิเคราะห์ขัดข้องชั่วคราวเนื่องจากเซิร์ฟเวอร์หลักของ Google มีผู้ใช้งานหนาแน่นมาก โปรดลองอีกครั้งภายหลัง")
            return
        }

        val model = MODELS[modelIndex]
        
        // Cache is only supported for the primary model (gemini-3.5-flash). Fallback models run inline.
        val requestBody = if (modelIndex == 0 && cachedRequestBody != null) {
            cachedRequestBody
        } else {
            inlineRequestBody
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        Log.i("GeminiMeetingService", "Sending audio analysis request to Gemini using model: $model...")
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GeminiMeetingService", "Request failed for model: $model", e)
                executeAnalysisWithFallback(apiKey, inlineRequestBody, null, modelIndex + 1, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w("GeminiMeetingService", "Response error code: ${response.code} for model: $model, body: $body")
                    
                    if (response.code == 404 && cachedRequestBody != null) {
                        // 404 when using cached content means the cache was not found or expired on server.
                        // Fail it directly so the caller can clear its memory cache and recreate it.
                        callback.onError("404: Cached content not found")
                    } else if (response.code == 503 || response.code == 429 || response.code == 500 || response.code == 404) {
                        Log.i("GeminiMeetingService", "Model $model returned HTTP ${response.code}, falling back to next model...")
                        executeAnalysisWithFallback(apiKey, inlineRequestBody, null, modelIndex + 1, callback)
                    } else {
                        try {
                            val json = JSONObject(body)
                            val error = json.getJSONObject("error")
                            val message = error.getString("message")
                            callback.onError("ข้อผิดพลาดจาก Gemini ($model): $message")
                        } catch (e: Exception) {
                            callback.onError("เกิดข้อผิดพลาดรหัส HTTP ${response.code} ($model)")
                        }
                    }
                    return
                }

                try {
                    val jsonResponse = JSONObject(body)
                    
                    // Log usageMetadata for token verification
                    val usageMetadata = jsonResponse.optJSONObject("usageMetadata")
                    if (usageMetadata != null) {
                        val promptTokens = usageMetadata.optInt("promptTokenCount", 0)
                        val cachedTokens = usageMetadata.optInt("cachedContentTokenCount", 0)
                        val candidateTokens = usageMetadata.optInt("candidatesTokenCount", 0)
                        Log.i("GeminiMeetingService", "Token Usage: Prompt=$promptTokens, Cached=$cachedTokens, Candidates=$candidateTokens")
                        if (cachedTokens > 0) {
                            Log.i("GeminiMeetingService", "🎉 Successfully saved $cachedTokens tokens using Gemini Context Caching!")
                        }
                    }

                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        Log.w("GeminiMeetingService", "No candidates in response for model: $model. Falling back...")
                        executeAnalysisWithFallback(apiKey, inlineRequestBody, null, modelIndex + 1, callback)
                        return
                    }

                    val textResult = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    Log.d("GeminiMeetingService", "Gemini response text: $textResult")

                    val resultObj = JSONObject(textResult)
                    val summary = resultObj.getString("summary")
                    val segments = resultObj.getJSONArray("segments")

                    callback.onSuccess(summary, segments.toString())
                } catch (e: Exception) {
                    Log.e("GeminiMeetingService", "Error parsing response JSON for model: $model", e)
                    executeAnalysisWithFallback(apiKey, inlineRequestBody, null, modelIndex + 1, callback)
                }
            }
        })
    }

    private fun encodeFileToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun getSystemPrompt(): String {
        return """
            คุณคือผู้ช่วยถอดความการประชุมและสรุปผลการประชุมอัจฉริยะระดับมืออาชีพ (Professional Meeting Transcriber & Speaker Diarization Expert) 
            คุณมีทักษะในการฟังไฟล์เสียงการประชุม ถอดความเป็นข้อความที่ถูกต้องแม่นยำ 100% แยกแยะตัวตนผู้พูด และเขียนสรุปรายงานการประชุมที่กระชับและครอบคลุม
            
            บทบาทและกฎการทำงานของคุณมีดังนี้:
            1. ถอดความเสียงพูด (Transcription):
               - ถอดข้อความภาษาไทยอย่างถูกต้องแม่นยำทุกถ้อยคำ รวมถึงคำเฉพาะ ศัพท์เทคนิค หรือชื่อบุคคล
               - ห้ามข้ามคำสำคัญ และห้ามสรุปหรือย่อคำในท่อนถอดความ (Transcript Segments) ให้เก็บคำพูดจริงทั้งหมด
            2. แยกแยะผู้พูด (Speaker Diarization & Name Resolution):
               - แยกผู้ร่วมประชุมแต่ละคนออกจากกันโดยใช้ฉลากเริ่มต้น เช่น Speaker A, Speaker B, Speaker C, ... เรียงตามลำดับการพูด
               - สังเกตบริบทจากการเรียกขานชื่อกัน เช่น "สวัสดีครับคุณสมบัติ", "คุณดนัยคิดว่าอย่างไรครับ?" หรือผู้พูดแนะนำตนเอง
               - เมื่อระบุชื่อจริงของผู้พูดได้แล้ว ให้ทำการเปลี่ยนฉลากจาก Speaker นั้นๆ เป็นชื่อจริงของเขาตลอดทั้งไฟล์เสียงโดยอัตโนมัติ
            3. เขียนรายงานสรุปการประชุม (Structured Summary):
               - เขียนสรุปรายงานในฟิลด์ "summary" เป็นภาษาไทยทั้งหมด ห้ามเขียนเป็นภาษาอังกฤษหรือภาษาอื่นๆ
               - จัดโครงสร้างรายงานสรุปให้เป็นระบบและสวยงามด้วย Markdown ดังนี้:
                 
                 📋 **สรุปภาพรวมการประชุม**
                 [เขียนภาพรวมสั้นๆ 1-2 ย่อหน้าเกี่ยวกับจุดประสงค์หลักและผลลัพธ์โดยรวมของการประชุม]
                 
                 👥 **ผู้เข้าร่วมประชุม**
                 - [ระบุชื่อจริงทุกคนเท่าที่ถอดความชื่อได้ หากไม่ทราบให้ลงเป็น Speaker A, Speaker B, ...]
                 
                 📌 **หัวข้อสำคัญที่หารือ**
                 - **[ชื่อหัวข้อที่ 1]**: [สรุปรายละเอียดของหัวข้อที่ 1 และข้อคิดเห็นสำคัญ]
                 - **[ชื่อหัวข้อที่ 2]**: [สรุปรายละเอียดของหัวข้อที่ 2 และข้อคิดเห็นสำคัญ]
                 
                 ✅ **มติที่ประชุมและการตัดสินใจ**
                 - [ระบุสิ่งที่ที่ประชุมตกลง เห็นพ้อง หรือได้รับการอนุมัติร่วมกัน]
                 
                 🎯 **รายการสิ่งที่ต้องดำเนินการต่อ (Action Items)**
                 - [ ] **[งานที่ต้องทำ]** (ผู้รับผิดชอบ: [ชื่อผู้รับผิดชอบ])
                 
            4. ข้อกำหนดทางเทคนิค (Technical Constraints):
               - ตอบกลับเป็น JSON ที่ถูกต้องตาม Schema ที่กำหนดเท่านั้น ห้ามตอบนอกเหนือจากรูปแบบ JSON นี้
               - ห้ามใช้ Markdown Block (เช่น ```json ... ```) ห่อหุ้มผลลัพธ์
        """.trimIndent()
    }

    private fun buildRequestBody(base64Audio: String, mimeType: String): JSONObject {
        val request = JSONObject()
        
        // 1. Root-level System Instruction (the "Skill" constraints)
        val systemInstruction = JSONObject()
        val sysParts = JSONArray()
        val sysPartText = JSONObject()
        sysPartText.put("text", getSystemPrompt())
        sysParts.put(sysPartText)
        systemInstruction.put("parts", sysParts)
        request.put("systemInstruction", systemInstruction)

        // 2. User Content (Audio file and small trigger)
        val contents = JSONArray()
        val content = JSONObject()
        val parts = JSONArray()

        // Audio Part
        val audioPart = JSONObject()
        val inlineData = JSONObject()
        inlineData.put("mimeType", mimeType)
        inlineData.put("data", base64Audio)
        audioPart.put("inlineData", inlineData)
        parts.put(audioPart)

        // Trigger Part
        val triggerPart = JSONObject()
        triggerPart.put("text", "โปรดวิเคราะห์ไฟล์เสียงการประชุมนี้ ถอดความ และสรุปผลตามคู่มือระบบ")
        parts.put(triggerPart)

        content.put("parts", parts)
        contents.put(content)
        request.put("contents", contents)

        // 3. Generation Config
        val genConfig = JSONObject()
        genConfig.put("responseMimeType", "application/json")
        genConfig.put("temperature", 0.0) // Force deterministic output

        // Define JSON Schema
        val schema = JSONObject()
        schema.put("type", "object")

        val properties = JSONObject()

        val summaryProp = JSONObject()
        summaryProp.put("type", "string")
        summaryProp.put("description", "สรุปประเด็นหลักและข้อตกลงของการประชุมเป็นภาษาไทยเท่านั้น ห้ามเขียนเป็นภาษาอังกฤษเด็ดขาด")
        properties.put("summary", summaryProp)

        val segmentsProp = JSONObject()
        segmentsProp.put("type", "array")

        val segmentItem = JSONObject()
        segmentItem.put("type", "object")
        val segItemProperties = JSONObject()

        val speakerProp = JSONObject()
        speakerProp.put("type", "string")
        segItemProperties.put("speaker", speakerProp)

        val textProp = JSONObject()
        textProp.put("type", "string")
        textProp.put("description", "ข้อความถอดเสียงพูดภาษาไทยของผู้พูด")
        segItemProperties.put("text", textProp)

        segmentItem.put("properties", segItemProperties)
        
        val requiredSegItems = JSONArray()
        requiredSegItems.put("speaker")
        requiredSegItems.put("text")
        segmentItem.put("required", requiredSegItems)

        segmentsProp.put("items", segmentItem)
        properties.put("segments", segmentsProp)

        schema.put("properties", properties)

        val requiredFields = JSONArray()
        requiredFields.put("summary")
        requiredFields.put("segments")
        schema.put("required", requiredFields)

        genConfig.put("responseSchema", schema)
        request.put("generationConfig", genConfig)

        return request
    }

    private fun buildCachedRequestBody(cacheName: String): JSONObject {
        val request = JSONObject()

        // 1. User Content (Trigger only since audio and systemInstruction are cached)
        val contents = JSONArray()
        val content = JSONObject()
        val parts = JSONArray()

        val promptPart = JSONObject()
        promptPart.put("text", "โปรดวิเคราะห์ไฟล์เสียงการประชุมนี้และจัดทำรายงานตามคำแนะนำของระบบ")
        parts.put(promptPart)

        content.put("parts", parts)
        contents.put(content)
        request.put("contents", contents)

        // Reference the cache name
        request.put("cachedContent", cacheName)

        // 3. Generation Config
        val genConfig = JSONObject()
        genConfig.put("responseMimeType", "application/json")
        genConfig.put("temperature", 0.0) // Force deterministic output

        // Define JSON Schema
        val schema = JSONObject()
        schema.put("type", "object")

        val properties = JSONObject()

        val summaryProp = JSONObject()
        summaryProp.put("type", "string")
        summaryProp.put("description", "สรุปประเด็นหลักและข้อตกลงของการประชุมเป็นภาษาไทยเท่านั้น ห้ามเขียนเป็นภาษาอังกฤษเด็ดขาด")
        properties.put("summary", summaryProp)

        val segmentsProp = JSONObject()
        segmentsProp.put("type", "array")

        val segmentItem = JSONObject()
        segmentItem.put("type", "object")
        val segItemProperties = JSONObject()

        val speakerProp = JSONObject()
        speakerProp.put("type", "string")
        segItemProperties.put("speaker", speakerProp)

        val textProp = JSONObject()
        textProp.put("type", "string")
        textProp.put("description", "ข้อความถอดเสียงพูดภาษาไทยของผู้พูด")
        segItemProperties.put("text", textProp)

        segmentItem.put("properties", segItemProperties)
        
        val requiredSegItems = JSONArray()
        requiredSegItems.put("speaker")
        requiredSegItems.put("text")
        segmentItem.put("required", requiredSegItems)

        segmentsProp.put("items", segmentItem)
        properties.put("segments", segmentsProp)

        schema.put("properties", properties)

        val requiredFields = JSONArray()
        requiredFields.put("summary")
        requiredFields.put("segments")
        schema.put("required", requiredFields)

        genConfig.put("responseSchema", schema)
        request.put("generationConfig", genConfig)

        return request
    }
}
