package com.example.geminimultimodalliveapi

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MajorBugsPhase2Test {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testMeetingActivityTextWatcherAndLifecycle() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MeetingActivity.kt")
        assertTrue("MeetingActivity.kt should exist", file.exists())
        val content = file.readText()

        // Verify it overrides onPause and calls pauseAudio
        assertTrue(
            "MeetingActivity should override onPause and call pauseAudio",
            content.contains("override fun onPause(") && content.contains("pauseAudio()")
        )

        // Verify it removes search TextWatcher inside onDestroy
        assertTrue(
            "MeetingActivity should remove TextWatcher inside onDestroy",
            content.contains("removeTextChangedListener") && content.contains("onDestroy()")
        )
    }

    @Test
    fun testOverlayWidgetControllerTouchAndClick() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/OverlayWidgetController.kt")
        assertTrue("OverlayWidgetController.kt should exist", file.exists())
        val content = file.readText()

        // Verify no direct setOnClickListener on floatingView to avoid double click race
        assertFalse(
            "OverlayWidgetController should not call setOnClickListener on floatingView",
            content.contains("floatingView.setOnClickListener")
        )
    }

    @Test
    fun testGeminiTileServiceOptimisticState() {
        val file = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/GeminiTileService.kt")
        assertTrue("GeminiTileService.kt should exist", file.exists())
        val content = file.readText()

        // Verify no optimistic updateTileState(true/false) inside onClick
        val onClickBody = content.substringAfter("fun onClick()").substringBefore("fun updateTileState")
        assertFalse(
            "GeminiTileService onClick should not optimistically update tile state to true or false",
            onClickBody.contains("updateTileState(true)") || onClickBody.contains("updateTileState(false)")
        )
    }
}
