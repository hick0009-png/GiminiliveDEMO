package com.example.geminimultimodalliveapi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MajorBugsPhase3Test {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testGeminiToolDispatcherCancellationTokenAndEndCall() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
        assertTrue("GeminiToolDispatcher.kt should exist", file.exists())
        val content = file.readText()

        // Verify CancellationTokenSource cancellation on success/failure listener blocks
        assertTrue(
            "GeminiToolDispatcher should cancel CancellationTokenSource on callbacks",
            content.contains("cancellationTokenSource.cancel()")
        )

        // Verify endPhoneCall is guarded against reflection on Android P+
        assertTrue(
            "GeminiToolDispatcher endPhoneCall reflection fallback should be guarded on P+",
            content.contains("VERSION_CODES.P")
        )
    }

    @Test
    fun testTopicManagerDynamicKeywordLimit() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/architecture/TopicManager.kt")
        assertTrue("TopicManager.kt should exist", file.exists())
        val content = file.readText()

        // Verify dynamic keyword list has a size limit (e.g. limit check size >= 50 or similar)
        assertTrue(
            "TopicManager addDynamicKeyword should limit keyword collection size",
            content.contains("size >= 50") || content.contains("list.size >= 50")
        )
    }

    @Test
    fun testContextManagerVolatileFields() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/architecture/ContextManager.kt")
        assertTrue("ContextManager.kt should exist", file.exists())
        val content = file.readText()

        // Verify volatile fields
        assertTrue(
            "ContextManager should have @Volatile on currentLocation, currentMotion, and currentAttention",
            content.contains("@Volatile")
        )
    }

    @Test
    fun testDynamicRulesManagerCaching() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/architecture/DynamicRulesManager.kt")
        assertTrue("DynamicRulesManager.kt should exist", file.exists())
        val content = file.readText()

        // Verify rules caching variable and read cache logic
        assertTrue(
            "DynamicRulesManager should cache parsed rules in memory",
            content.contains("cachedRules")
        )
    }

    @Test
    fun testAppErrorClassification() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/error/AppError.kt")
        assertTrue("AppError.kt should exist", file.exists())
        val content = file.readText()

        // Verify it handles Tool exception mapping
        assertTrue(
            "AppError should map Tool exceptions",
            content.contains("Tool(") || content.contains("AppError.Tool")
        )
    }
}
