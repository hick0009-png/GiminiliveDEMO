package com.example.geminimultimodalliveapi.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object ApiKeyValidator {
    private val client = OkHttpClient()

    fun verifyApiKey(apiKey: String, callback: (Boolean, String) -> Unit) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val jsonPayload = "{\"contents\":[{\"parts\":[{\"text\":\"a\"}]}]}"
        val body = jsonPayload.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                Log.d("ApiKeyValidator", "Response body: $bodyStr")
                if (response.isSuccessful) {
                    callback(true, "API Key is valid and has quota!")
                } else {
                    try {
                        val json = JSONObject(bodyStr)
                        val error = json.getJSONObject("error")
                        val message = error.getString("message")
                        callback(false, "API Key Error: $message")
                    } catch (e: Exception) {
                        callback(false, "API Key Error: HTTP ${response.code} (failed to parse details)")
                    }
                }
            }
        })
    }
}
