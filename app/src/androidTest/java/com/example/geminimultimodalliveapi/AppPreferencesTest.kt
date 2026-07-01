package com.example.geminimultimodalliveapi

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.geminimultimodalliveapi.data.AppPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {

    private lateinit var appPrefs: AppPreferences

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appPrefs = AppPreferences.getInstance(appContext)
    }

    @Test
    fun testAppPreferencesWriteAndRead() {
        val testApiKey = "test_api_key_12345"
        appPrefs.apiKey = testApiKey
        assertEquals(testApiKey, appPrefs.apiKey)

        val testVoice = "Kore"
        appPrefs.selectedVoice = testVoice
        assertEquals(testVoice, appPrefs.selectedVoice)

        val testWakeWord = "จาร์วิส"
        appPrefs.wakeWord = testWakeWord
        assertEquals(testWakeWord, appPrefs.wakeWord)

        appPrefs.isChimeEnabled = false
        assertFalse(appPrefs.isChimeEnabled)

        appPrefs.isFloatingWidgetEnabled = true
        assertTrue(appPrefs.isFloatingWidgetEnabled)
    }
}


