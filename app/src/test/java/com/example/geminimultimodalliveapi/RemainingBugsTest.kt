package com.example.geminimultimodalliveapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemainingBugsTest {

    private fun getSourceFile(relativePath: String): File {
        val file = File(relativePath)
        return if (!file.exists()) {
            File("app/$relativePath")
        } else {
            file
        }
    }

    @Test
    fun testPhase1Bugs() {
        // 1. Test SpeakerLockManager thread safety and volatile check
        val lockManagerFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/utils/SpeakerLockManager.kt")
        assertTrue("SpeakerLockManager.kt should exist", lockManagerFile.exists())
        val lockContent = lockManagerFile.readText()
        assertTrue(
            "activeSpeakerId should be @Volatile in SpeakerLockManager",
            lockContent.contains("@Volatile") && lockContent.contains("var activeSpeakerId")
        )

        // 2. Test MainActivity camera offloading check
        val mainActivityFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
        assertTrue("MainActivity.kt should exist", mainActivityFile.exists())
        val mainActivityContent = mainActivityFile.readText()
        assertTrue(
            "processAndSendImage in MainActivity should run on Dispatchers.Default or Dispatchers.IO",
            mainActivityContent.contains("processAndSendImage") &&
                    (mainActivityContent.contains("Dispatchers.Default") || mainActivityContent.contains("Dispatchers.IO"))
        )

        // 3. Test FloatingWidgetService transitionToState Dispatchers.Main check
        val serviceFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/FloatingWidgetService.kt")
        assertTrue("FloatingWidgetService.kt should exist", serviceFile.exists())
        val serviceContent = serviceFile.readText()
        assertFalse(
            "transitionToState in FloatingWidgetService should not perform raw delay(100) on Dispatchers.Main",
            serviceContent.contains("fun transitionToState") &&
                    serviceContent.substringAfter("fun transitionToState").substringBefore("    fun ").contains("delay(100)")
        )
    }

    @Test
    fun testPhase2Bugs() {
        // 1. Test DatingSkillManager thread synchronization
        val skillManagerFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/data/DatingSkillManager.kt")
        assertTrue("DatingSkillManager.kt should exist", skillManagerFile.exists())
        val skillManagerContent = skillManagerFile.readText()
        assertTrue(
            "DatingSkillManager should synchronize access to files",
            skillManagerContent.contains("synchronized") || skillManagerContent.contains("@Synchronized")
        )

        // 2. Test DocumentParser offloads execution to Dispatchers.IO
        val docParserFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/utils/DocumentParser.kt")
        assertTrue("DocumentParser.kt should exist", docParserFile.exists())
        val docParserContent = docParserFile.readText()
        assertTrue(
            "DocumentParser should offload text extraction and query to Dispatchers.IO",
            docParserContent.contains("withContext(Dispatchers.IO)")
        )

        // 3. Test DatingAnalysisOrchestrator parallel agent try-catch
        val orchestratorFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/agent/DatingAnalysisOrchestrator.kt")
        assertTrue("DatingAnalysisOrchestrator.kt should exist", orchestratorFile.exists())
        val orchestratorContent = orchestratorFile.readText()
        assertTrue(
            "DatingAnalysisOrchestrator multi-agent should try-catch agent execution",
            orchestratorContent.contains("try {") &&
                    orchestratorContent.substringAfter("executeMultiAgent").substringBefore("awaitAll()").contains("catch")
        )

        // 4. Test MemoryViewModel contains loading and error states
        val vmFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/ui/memory/MemoryViewModel.kt")
        assertTrue("MemoryViewModel.kt should exist", vmFile.exists())
        val vmContent = vmFile.readText()
        assertTrue(
            "MemoryViewModel should expose isLoading state flow",
            vmContent.contains("isLoading") || vmContent.contains("_isLoading")
        )
        assertTrue(
            "MemoryViewModel should expose error state flow",
            vmContent.contains("error") || vmContent.contains("_error")
        )
    }

    @Test
    fun testPhase3Bugs() {
        // 1. Test WakeWordDetector does not abuse onError(0)
        val wwFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/service/WakeWordDetector.kt")
        assertTrue("WakeWordDetector.kt should exist", wwFile.exists())
        val wwContent = wwFile.readText()
        assertFalse(
            "WakeWordDetector should not invoke listener.onError(0) in onResults",
            wwContent.contains("listener.onError(0)")
        )

        // 2. Test PermissionHelper only requests required permissions in checkAndRequestPermissions
        val permFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/utils/PermissionHelper.kt")
        assertTrue("PermissionHelper.kt should exist", permFile.exists())
        val permContent = permFile.readText()
        assertFalse(
            "PermissionHelper should not request missingOptional inside checkAndRequestPermissions",
            permContent.substringAfter("fun checkAndRequestPermissions").substringBefore("fun ").contains("missingOptional")
        )

        // 3. Test MemoryDbHelper escapes wildcards and orders results
        val dbHelperFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/memory/MemoryDbHelper.kt")
        assertTrue("MemoryDbHelper.kt should exist", dbHelperFile.exists())
        val dbHelperContent = dbHelperFile.readText()
        assertTrue(
            "MemoryDbHelper searchMemories should escape SQL wildcards",
            dbHelperContent.contains("replace(\"%\",") && dbHelperContent.contains("replace(\"_\",")
        )
    }

    @Test
    fun testPhase4Bugs() {
        // 1. Test build.gradle.kts compatibility options align to version 11
        val gradleFile = getSourceFile("../build.gradle.kts")
        val appGradleFile = getSourceFile("build.gradle.kts")
        val activeGradleFile = if (appGradleFile.exists()) appGradleFile else gradleFile
        assertTrue("build.gradle.kts should exist", activeGradleFile.exists())
        val gradleContent = activeGradleFile.readText()
        assertTrue(
            "Gradle Java version should be aligned to 11",
            gradleContent.contains("VERSION_11") && gradleContent.contains("jvmTarget = \"11\"")
        )

        // 2. Test AndroidManifest theme match
        val manifestFile = getSourceFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()
        assertTrue(
            "AndroidManifest should use Theme.GeminiMultimodalLiveAPI style theme",
            manifestContent.contains("Theme.GeminiMultimodalLiveAPI")
        )
    }

    @Test
    fun testPerformanceOptimizations() {
        val lockManagerFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/utils/SpeakerLockManager.kt")
        assertTrue(lockManagerFile.exists())
        val lockContent = lockManagerFile.readText()
        assertTrue(
            "SpeakerLockManager should pre-compile Regex constants in companion object",
            lockContent.contains("TONE_MARKS_REGEX") && lockContent.contains("PUNCTUATION_REGEX")
        )

        val parserFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/utils/DocumentParser.kt")
        assertTrue(parserFile.exists())
        val parserContent = parserFile.readText()
        assertTrue(
            "DocumentParser should pre-compile MULTIPLE_SPACES_REGEX constant",
            parserContent.contains("MULTIPLE_SPACES_REGEX")
        )

        val selectorFile = getSourceFile("src/main/java/com/example/geminimultimodalliveapi/agent/DocumentSelector.kt")
        assertTrue(selectorFile.exists())
        val selectorContent = selectorFile.readText()
        assertTrue(
            "DocumentSelector should implement document cache properties",
            selectorContent.contains("cachedDocs") && selectorContent.contains("lastDirModifiedTime")
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun testSpeakerLockNormalizationAndParserCleanText() {
        val lockManager = com.example.geminimultimodalliveapi.utils.SpeakerLockManager(
            coroutineScope = kotlinx.coroutines.GlobalScope
        )
        val wordList = listOf(
            com.example.geminimultimodalliveapi.network.DeepgramLiveClient.WordDetail(
                word = "กอหญ้า",
                speaker = 1,
                start = 0.0,
                end = 0.5
            )
        )
        val result = lockManager.processIncomingWords(wordList, "กอหญ้า")
        assertEquals("กอหญ้า", result?.trim())
        assertEquals(1, lockManager.activeSpeakerId)

        val rawDocText = "Hello     world!   \n   This is    a   line."
        val cleaned = com.example.geminimultimodalliveapi.utils.DocumentParser.cleanText(rawDocText)
        assertEquals("Hello world!\nThis is a line.", cleaned)
    }
}
