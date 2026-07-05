package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.session.SessionStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveTranslateFeatureTest {

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
    fun testAppPreferencesContainsTranslateSettings() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
        val content = file.readText()
        
        assertTrue(content.contains("var isTranslateModeEnabled: Boolean"))
        assertTrue(content.contains("var translateTargetLanguage: String"))
        assertTrue(content.contains("var coachingIntensity: String"))
        assertTrue(content.contains("var selectedContextProfile: String"))
    }

    @Test
    fun testSessionStateHolderRamCache() {
        SessionStateHolder.liveTranscripts.clear()
        SessionStateHolder.liveAdviceLog.clear()
        
        SessionStateHolder.liveTranscripts.add("test_transcript")
        SessionStateHolder.liveAdviceLog.add("test_advice")

        assertEquals(1, SessionStateHolder.liveTranscripts.size)
        assertEquals("test_transcript", SessionStateHolder.liveTranscripts[0])
        
        assertEquals(1, SessionStateHolder.liveAdviceLog.size)
        assertEquals("test_advice", SessionStateHolder.liveAdviceLog[0])
    }

    @Test
    fun testSessionStateHolderTranslateStateFlows() {
        SessionStateHolder.clearTranslateSessionData()
        assertEquals("", SessionStateHolder.liveUserTranscript.value)
        assertEquals("", SessionStateHolder.liveTranslateTranscript.value)
        
        SessionStateHolder.updateUserTranscript("hello user")
        SessionStateHolder.updateTranslateTranscript("hello translation")
        
        assertEquals("hello user", SessionStateHolder.liveUserTranscript.value)
        assertEquals("hello translation", SessionStateHolder.liveTranslateTranscript.value)
        
        SessionStateHolder.clearTranslateSessionData()
        assertEquals("", SessionStateHolder.liveUserTranscript.value)
        assertEquals("", SessionStateHolder.liveTranslateTranscript.value)
    }

    @Test
    fun testGeminiLiveClientTranslationConfigNesting() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/network/GeminiLiveClient.kt")
        val content = file.readText()

        // translationConfig should be nested inside generationConfig
        assertTrue(
            "translationConfig must be put in generationConfig",
            content.contains("generationConfig.put(\"translationConfig\"")
        )
        
        // inputAudioTranscriptionConfig should be added to setup
        assertTrue(
            "inputAudioTranscriptionConfig must be put in setup",
            content.contains("setup.put(\"inputAudioTranscriptionConfig\"")
        )
        
        // Listener callback for input audio transcription
        assertTrue(
            "Listener should have onInputAudioTranscriptionReceived callback",
            content.contains("fun onInputAudioTranscriptionReceived")
        )
    }

    @Test
    fun testFloatingWidgetServiceTranslationRouting() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/FloatingWidgetService.kt")
        val content = file.readText()

        // FloatingWidgetService should call updateUserTranscript and updateTranslateTranscript
        assertTrue(
            "FloatingWidgetService must update user transcript",
            content.contains("updateUserTranscript")
        )
        assertTrue(
            "FloatingWidgetService must update translate transcript",
            content.contains("updateTranslateTranscript")
        )
    }

    @Test
    fun testTranslateActivityTranscriptsCollection() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/ui/translate/TranslateActivity.kt")
        val content = file.readText()

        // TranslateActivity should collect from liveUserTranscript and liveTranslateTranscript
        assertTrue(
            "TranslateActivity must collect liveUserTranscript",
            content.contains("liveUserTranscript.collectLatest") || content.contains("liveUserTranscript.collect")
        )
        assertTrue(
            "TranslateActivity must collect liveTranslateTranscript",
            content.contains("liveTranslateTranscript.collectLatest") || content.contains("liveTranslateTranscript.collect")
        )
    }
}
