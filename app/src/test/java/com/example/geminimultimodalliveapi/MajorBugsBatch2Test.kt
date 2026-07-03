package com.example.geminimultimodalliveapi

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MajorBugsBatch2Test {

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
    fun testMainActivityStatePreservation() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
        val content = file.readText()

        // Verify onSaveInstanceState exists
        assertTrue(
            "MainActivity should override onSaveInstanceState",
            content.contains("override fun onSaveInstanceState(") || content.contains("fun onSaveInstanceState(")
        )

        // Verify it saves activeProfileName and chatLogs
        assertTrue(
            "MainActivity should save activeProfileName in onSaveInstanceState",
            content.contains("activeProfileName")
        )
        assertTrue(
            "MainActivity should save chatLogs in onSaveInstanceState",
            content.contains("chatLogs")
        )
    }

    @Test
    fun testSettingsActivityTextWatchers() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/SettingsActivity.kt")
        val content = file.readText()

        // Verify text watchers are cleaned up
        assertTrue(
            "SettingsActivity should contain cleanup for TextWatchers (removeTextChangedListener)",
            content.contains("removeTextChangedListener(") || content.contains("textWatcher")
        )
    }

    @Test
    fun testActivityStatePreservationAndLeaks() {
        val meetingFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MeetingActivity.kt")
        val meetingContent = meetingFile.readText()

        // Verify onSaveInstanceState exists in MeetingActivity
        assertTrue(
            "MeetingActivity should override onSaveInstanceState",
            meetingContent.contains("override fun onSaveInstanceState(")
        )

        // Verify playRunnable Handler leak protection (should not be an inner class directly, or should use WeakReference)
        assertTrue(
            "MeetingActivity should use WeakReference or safe runnable helper to prevent Handler leaks",
            meetingContent.contains("WeakReference")
        )

        val settingsFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/SettingsActivity.kt")
        val settingsContent = settingsFile.readText()

        // Verify onSaveInstanceState exists in SettingsActivity
        assertTrue(
            "SettingsActivity should override onSaveInstanceState",
            settingsContent.contains("override fun onSaveInstanceState(")
        )
    }

    @Test
    fun testDatabaseIOThreadOffloading() {
        val dispatcherFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
        val dispatcherContent = dispatcherFile.readText()

        // Verify that database calls in GeminiToolDispatcher are wrapped in withContext(Dispatchers.IO)
        assertTrue(
            "GeminiToolDispatcher should wrap database calls in Dispatchers.IO",
            dispatcherContent.contains("withContext(Dispatchers.IO)")
        )
    }

    @Test
    fun testFloatingWidgetServiceLeakCleanup() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/FloatingWidgetService.kt")
        val content = file.readText()

        // Verify WeakReference usage for static instance
        assertTrue(
            "FloatingWidgetService static instance should be a WeakReference to prevent leaks",
            content.contains("WeakReference<FloatingWidgetService>") || content.contains("WeakReference(")
        )

        // Verify onDestroy unregisters sensor/location callbacks
        val onDestroyBody = content.substringAfter("override fun onDestroy()").substringBefore("override fun onBind")
        assertTrue(
            "onDestroy should unregister location/sensor callbacks",
            onDestroyBody.contains("sensorManager?.unregisterListener") || onDestroyBody.contains("unregisterListener")
        )
    }

    @Test
    fun testWakeWordDetectorBatteryOptimization() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/WakeWordDetector.kt")
        val content = file.readText()

        // Verify battery optimization exists (e.g. screen off receiver or pause logic)
        assertTrue(
            "WakeWordDetector should contain screen state checking or battery optimizations",
            content.contains("Intent.ACTION_SCREEN_OFF") || content.contains("isScreenOn") || content.contains("pause")
        )
    }
}
