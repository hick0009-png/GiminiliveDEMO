package com.example.geminimultimodalliveapi.network

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object ApiKeyValidator {
    private val client = OkHttpClient()

    fun verifyApiKey(apiKey: String, callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d("ApiKeyValidator", "Models list body: $body")
                if (response.isSuccessful) {
                    callback(true, "API Key is valid!")
                } else {
                    try {
                        val json = JSONObject(body)
                        val error = json.getJSONObject("error")
                        val message = error.getString("message")
                        callback(false, "API Key Error: $message")
                    } catch (e: Exception) {
                        callback(false, "API Key Error: HTTP ${response.code} (failed to parse error details)")
                    }
                }
            }
        })
    }
}
