package com.example.geminimultimodalliveapi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MajorBugsPhase1Test {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testFloatingWidgetServiceWakeLockAndAudioFocus() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/FloatingWidgetService.kt")
        assertTrue("FloatingWidgetService.kt should exist", file.exists())
        val content = file.readText()

        // Verify WakeLock usage
        assertTrue(
            "FloatingWidgetService should contain WakeLock variables/methods",
            content.contains("wakeLock") && content.contains("PARTIAL_WAKE_LOCK")
        )

        // Verify Audio Focus change handling
        assertTrue(
            "FloatingWidgetService should handle audio focus loss by stopping playback",
            content.contains("AUDIOFOCUS_LOSS") && content.contains("audioPlayer?.stop")
        )
    }

    @Test
    fun testMeetingRecordingServiceCommittedSegmentsAndMediaRecorder() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/MeetingRecordingService.kt")
        assertTrue("MeetingRecordingService.kt should exist", file.exists())
        val content = file.readText()

        // Verify CopyOnWriteArrayList usage for thread safety
        assertTrue(
            "MeetingRecordingService should use CopyOnWriteArrayList for committedSegments",
            content.contains("CopyOnWriteArrayList")
        )

        // Verify MediaRecorder release on failure path
        assertTrue(
            "MeetingRecordingService should release mediaRecorder in start catch block",
            content.contains("mediaRecorder?.release()") && content.contains("Failed to start offline recording")
        )
    }
}
