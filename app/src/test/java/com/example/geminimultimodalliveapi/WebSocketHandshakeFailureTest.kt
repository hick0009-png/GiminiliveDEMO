package com.example.geminimultimodalliveapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebSocketHandshakeFailureTest {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testGeminiLiveClientOnFailureMitigation() {
        val clientFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/network/GeminiLiveClient.kt")
        assertTrue("GeminiLiveClient.kt should exist", clientFile.exists())
        val content = clientFile.readText()

        // 1. Verify that onFailure inspects the response code/message
        assertTrue(
            "GeminiLiveClient onFailure should inspect response code",
            content.contains("response?.code") || content.contains("response.code")
        )

        // 2. Verify that onFailure parses JSON error from body
        assertTrue(
            "GeminiLiveClient onFailure should attempt to parse JSON error message from response body",
            content.contains("JSONObject") && content.contains("\"error\"") && content.contains("\"message\"")
        )

        // 3. Verify that reconnect is suppressed for HTTP error responses
        assertTrue(
            "GeminiLiveClient should suppress reconnect attempts when response is not null (HTTP >= 400)",
            content.contains("shouldRetry") || content.contains("response == null")
        )

        // 4. Verify that listener.onError is called upon failure
        assertTrue(
            "GeminiLiveClient should notify listener with exact error when aborting reconnects",
            content.contains("listener.onError")
        )
    }

    @Test
    fun testFloatingWidgetServiceOnErrorToast() {
        val serviceFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/FloatingWidgetService.kt")
        assertTrue("FloatingWidgetService.kt should exist", serviceFile.exists())
        val content = serviceFile.readText()

        // Verify that clientListener.onError shows a Toast to alert the user
        assertTrue(
            "FloatingWidgetService clientListener.onError should show Toast notification on main thread",
            content.contains("override fun onError") && content.substringAfter("override fun onError").substringBefore("}").contains("Toast.makeText")
        )
    }
}
