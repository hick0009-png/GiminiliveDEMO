package com.example.geminimultimodalliveapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MajorBugsBatch1Test {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            val altFile = File("app/$relativePath")
            assertTrue("File should exist at either location: $relativePath", altFile.exists())
            altFile
        } else {
            file
        }
    }

    @Test
    fun testGeminiLiveClientWebSocketSecurityAndLeaks() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/network/GeminiLiveClient.kt")
        val content = file.readText()

        // Verify API key is removed from URL and added to Header
        assertFalse(
            "WebSocket URL should not contain query parameter key=apiKey",
            content.contains("?key=\$apiKey") || content.contains("?key=")
        )
        assertTrue(
            "WebSocket request should include x-goog-api-key header",
            content.contains("addHeader(\"x-goog-api-key\", apiKey)") ||
                    content.contains(".addHeader(\"x-goog-api-key\",")
        )

        // Verify protection against stale onClosed/onFailure reference nulling
        assertTrue(
            "onClosed callback should verify if it is closing the current webSocket reference",
            content.contains("if (this@GeminiLiveClient.webSocket === webSocket)") ||
                    content.contains("if (this@GeminiLiveClient.webSocket === webSocket) {")
        )
        assertTrue(
            "onFailure callback should verify if it is failing the current webSocket reference",
            content.contains("if (this@GeminiLiveClient.webSocket === webSocket)")
        )

        // Verify activeToolCalls clearing on disconnect
        val disconnectBody = content.substringAfter("fun disconnect()").substringBefore("fun sendMediaChunk")
        assertTrue(
            "disconnect should clear activeToolCalls",
            disconnectBody.contains("activeToolCalls.clear()")
        )

        // Verify sensitive tool responses are not logged at INFO level
        val sendToolResponseBody = content.substringAfter("fun sendToolResponse").substringBefore("private fun sendInitialSetupMessage")
        assertFalse(
            "Full tool response JSON should not be logged at Log.i",
            sendToolResponseBody.lines().any { it.contains("Log.i") && it.contains("jsonString") }
        )
        assertTrue(
            "Full tool response JSON should be logged at Log.d",
            sendToolResponseBody.lines().any { it.contains("Log.d") && it.contains("jsonString") }
        )
    }

    @Test
    fun testDeepgramLiveClientReconnectionAndThreading() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/network/DeepgramLiveClient.kt")
        val content = file.readText()

        // Verify reconnect fields exist
        assertTrue(
            "DeepgramLiveClient should have reconnectAttempts field",
            content.contains("reconnectAttempts")
        )
        assertTrue(
            "DeepgramLiveClient should have reconnectRunnable or similar reconnect logic",
            content.contains("reconnectRunnable") || content.contains("scheduleReconnect")
        )

        // Verify main thread dispatch for callbacks
        assertTrue(
            "DeepgramLiveClient should use mainHandler to post callbacks",
            content.contains("mainHandler.post")
        )
    }

    @Test
    fun testGeminiTextServiceDeDuplication() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/network/GeminiTextService.kt")
        val content = file.readText()

        // Extract body of generateText
        val generateTextBody = content.substringAfter("fun generateText(").substringBefore("fun generateTextWithSystemInstruction")

        // Ensure generateText delegates to generateTextWithSystemInstruction and doesn't construct its own OkHttp call
        assertTrue(
            "generateText should delegate to generateTextWithSystemInstruction",
            generateTextBody.contains("generateTextWithSystemInstruction(")
        )
        assertFalse(
            "generateText should not build its own Request",
            generateTextBody.contains("Request.Builder()")
        )
        assertFalse(
            "generateText should not enqueue its own okhttp call",
            generateTextBody.contains("newCall(request).enqueue")
        )
    }
}
