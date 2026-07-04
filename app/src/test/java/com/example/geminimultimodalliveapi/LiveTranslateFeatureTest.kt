package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.session.SessionStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveTranslateFeatureTest {

    @Test
    fun testAppPreferencesContainsTranslateSettings() {
        val file = File("app/src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
        val content = if (file.exists()) file.readText() else File("src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt").readText()
        
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
}
