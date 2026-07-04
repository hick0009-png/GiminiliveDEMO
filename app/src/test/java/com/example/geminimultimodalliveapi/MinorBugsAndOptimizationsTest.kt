package com.example.geminimultimodalliveapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MinorBugsAndOptimizationsTest {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testThemeConsolidationAndLauncherIconsInManifest() {
        val manifestFile = getSourceFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        // 1. Base theme should be consolidated Theme.GeminiMultimodalLiveAPI
        assertTrue(
            "Manifest should set Theme.GeminiMultimodalLiveAPI as the application theme",
            manifestContent.contains("android:theme=\"@style/Theme.GeminiMultimodalLiveAPI\"")
        )

        // 2. Base theme Theme.GeminiLiveDemo should be removed
        assertFalse(
            "Manifest should not reference Theme.GeminiLiveDemo",
            manifestContent.contains("Theme.GeminiLiveDemo")
        )

        // 3. Custom brand app_logo should be used as launcher icon
        assertTrue(
            "Manifest should set app_logo as android:icon",
            manifestContent.contains("android:icon=\"@drawable/app_logo\"")
        )
        assertTrue(
            "Manifest should set app_logo as android:roundIcon",
            manifestContent.contains("android:roundIcon=\"@drawable/app_logo\"")
        )

        // 4. Themes.xml consolidation
        val themesFile = getSourceFile("src/main/res/values/themes.xml")
        assertTrue("themes.xml should exist", themesFile.exists())
        val themesContent = themesFile.readText()

        assertFalse(
            "themes.xml should not contain Theme.GeminiLiveDemo",
            themesContent.contains("name=\"Theme.GeminiLiveDemo\"")
        )
        assertTrue(
            "Theme.GeminiMultimodalLiveAPI should define custom styles",
            themesContent.contains("name=\"colorPrimary\"") && themesContent.contains("android:statusBarColor")
        )
    }

    @Test
    fun testMainActivityStopWaveAnimationOnStop() {
        val mainActivityFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
        assertTrue("MainActivity.kt should exist", mainActivityFile.exists())
        val content = mainActivityFile.readText()

        // Verify stopWaveAnimation is invoked in onStop override
        assertTrue(
            "MainActivity onStop should call stopWaveAnimation() to prevent background GPU consumption",
            content.contains("override fun onStop()") && content.substringAfter("override fun onStop()").substringBefore("}").contains("stopWaveAnimation()")
        )
    }

    @Test
    fun testMeetingActivityDiffUtilLiveTranscript() {
        val meetingActivityFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MeetingActivity.kt")
        assertTrue("MeetingActivity.kt should exist", meetingActivityFile.exists())
        val content = meetingActivityFile.readText()

        // Verify updateLiveTranscriptUI uses DiffUtil
        assertTrue(
            "MeetingActivity updateLiveTranscriptUI should use DiffUtil for updates",
            content.substringAfter("fun updateLiveTranscriptUI").substringBefore("}").contains("DiffUtil.calculateDiff")
        )
    }
}
