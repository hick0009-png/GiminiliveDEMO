package com.example.geminimultimodalliveapi

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class DatabaseCodeTest {

    @Test
    fun testLocalVehicleDbHelperUsesUseBlock() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/utils/LocalVehicleDbHelper.kt")
        val finalFile = if (!file.exists()) {
            val altFile = File("app/src/main/java/com/example/geminimultimodalliveapi/utils/LocalVehicleDbHelper.kt")
            assertTrue("File should exist at either location", altFile.exists())
            altFile
        } else {
            file
        }
        checkFile(finalFile)
    }

    private fun checkFile(file: File) {
        val content = file.readText()
        assertTrue("Should contain .use block on cursors", content.contains(".use {"))
        assertFalse("Should not call cursor.close() directly", content.contains("cursor.close()"))
    }

    @Test
    fun testMemoryDbHelperContainsRequiredMethods() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/memory/MemoryDbHelper.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/memory/MemoryDbHelper.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        assertTrue("MemoryDbHelper should have updateMemoryPinState", content.contains("fun updateMemoryPinState"))
        assertTrue("MemoryDbHelper should have insertOrUpdateWithEviction", content.contains("fun insertOrUpdateWithEviction"))
    }

    @Test
    fun testMemoryManagerOptimization() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/memory/MemoryManager.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/memory/MemoryManager.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        assertTrue("MemoryManager should call updateMemoryPinState", content.contains("dbHelper.updateMemoryPinState"))
        
        val updatePinBody = content.substringAfter("fun updateMemoryPin").substringBefore("}")
        assertFalse("updateMemoryPin should not fetch memory list", updatePinBody.contains("getMemoryList"))
        
        val addFactBody = content.substringAfter("fun addFact").substringBefore("fun recordAccess")
        assertFalse("addFact should not call insertOrUpdateMemory separately", addFactBody.contains("insertOrUpdateMemory"))
        assertFalse("addFact should not call evictLowestUtilityMemories separately", addFactBody.contains("evictLowestUtilityMemories"))
        assertTrue("addFact should call insertOrUpdateWithEviction", addFactBody.contains("insertOrUpdateWithEviction"))
    }

    @Test
    fun testMeetingDbHelperUpdateSpeakerNameTransaction() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/data/MeetingDbHelper.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/data/MeetingDbHelper.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val updateSpeakerBody = content.substringAfter("fun updateSpeakerName").substringBefore("return")
        assertTrue("updateSpeakerName should start transaction", updateSpeakerBody.contains("beginTransaction()"))
        assertTrue("updateSpeakerName should mark transaction successful", updateSpeakerBody.contains("setTransactionSuccessful()"))
        assertTrue("updateSpeakerName should end transaction", updateSpeakerBody.contains("endTransaction()"))
    }

    @Test
    fun testMeetingActivityDialogGuards() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/MeetingActivity.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/MeetingActivity.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        assertTrue("showRenameSpeakerDialog should guard dialog.show()", 
            content.contains("if (!isFinishing && !isDestroyed) {\n            dialog.show()\n        }") || 
            content.contains("if (!isFinishing && !isDestroyed) dialog.show()") ||
            content.contains("if (isFinishing || isDestroyed) return") ||
            content.contains("if (!isFinishing && !isDestroyed)"))

        val deletePart = content.substringAfter("holder.deleteBtn.setOnClickListener").substringBefore(".show()")
        assertTrue("deleteBtn listener should guard against finishes", 
            deletePart.contains("isFinishing") || deletePart.contains("isDestroyed") || deletePart.contains("isFinishing || isDestroyed"))
    }

    @Test
    fun testSettingsActivityDialogGuards() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/SettingsActivity.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/SettingsActivity.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val showProgressPart = content.substringAfter("private fun showProgressDialog").substringBefore("private fun updateProgressDialog")
        assertTrue("showProgressDialog should guard against activity finishing/destruction", 
            showProgressPart.contains("isFinishing") || showProgressPart.contains("isDestroyed") || showProgressPart.contains("isFinishing || isDestroyed"))
    }

    @Test
    fun testMemoryViewModelLoadDataOffMainThread() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/ui/memory/MemoryViewModel.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/ui/memory/MemoryViewModel.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val loadDataBody = content.substringAfter("fun loadData").substringBefore("fun saveVehicleInfo")
        assertTrue("loadData should launch on Dispatchers.IO", loadDataBody.contains("Dispatchers.IO"))
        assertTrue("loadData should switch to Dispatchers.Main", loadDataBody.contains("Dispatchers.Main"))
    }

    @Test
    fun testMeetingRecordingServiceNoRunBlocking() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/service/MeetingRecordingService.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/service/MeetingRecordingService.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val stopRecordingBody = content.substringAfter("private fun stopRecording()").substringBefore("private fun startTimer()")
        assertFalse("stopRecording should not use runBlocking", stopRecordingBody.contains("runBlocking"))
        assertTrue("stopRecording should launch join on coroutine scope", 
            stopRecordingBody.contains("CoroutineScope(Dispatchers.IO).launch") || 
            stopRecordingBody.contains("launch(Dispatchers.IO)") || 
            stopRecordingBody.contains("serviceScope.launch") || 
            stopRecordingBody.contains("launch"))
    }

    @Test
    fun testAudioPlayerReleaseFinally() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/audio/AudioPlayer.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/audio/AudioPlayer.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val playLoopFinallyPart = content.substringAfter("} finally {")
        assertTrue("finally block should call abandonAudioFocus", playLoopFinallyPart.contains("abandonAudioFocus()"))
        assertTrue("finally block should call releaseAudioTrack", playLoopFinallyPart.contains("releaseAudioTrack()"))
    }

    @Test
    fun testSituationLogManagerRotation() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/architecture/SituationLogManager.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/architecture/SituationLogManager.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val saveReportPart = content.substringAfter("private fun saveReportToDisk")
        assertTrue("saveReportToDisk should contain deletion logic", saveReportPart.contains("delete()"))
        assertTrue("saveReportToDisk should calculate cutoff time", 
            saveReportPart.contains("7 * 24 * 60 * 60 * 1000L") || 
            saveReportPart.contains("604800000") || 
            saveReportPart.contains("604_800_000") || 
            saveReportPart.contains("7 * 24"))
    }

    @Test
    fun testPerceptionEngineIsCloseable() {
        val clazz = com.example.geminimultimodalliveapi.architecture.PerceptionEngine::class.java
        assertTrue("PerceptionEngine should implement Closeable or AutoCloseable", 
            java.io.Closeable::class.java.isAssignableFrom(clazz) || 
            java.lang.AutoCloseable::class.java.isAssignableFrom(clazz))
    }

    @Test
    fun testToolDispatcherHandlersHaveTryCatch() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        
        val saveVehicleBody = content.substringAfter("fun handleSaveVehicleInfo").substringBefore("fun handleQueryVehicleInfo")
        assertTrue("handleSaveVehicleInfo should have try-catch", saveVehicleBody.contains("try") && saveVehicleBody.contains("catch"))

        val queryVehicleBody = content.substringAfter("fun handleQueryVehicleInfo").substringBefore("fun handleGetCurrentTime")
        assertTrue("handleQueryVehicleInfo should have try-catch", queryVehicleBody.contains("try") && queryVehicleBody.contains("catch"))

        val getCurrentTimeBody = content.substringAfter("fun handleGetCurrentTime").substringBefore("private fun isLocationEnabled")
        assertTrue("handleGetCurrentTime should have try-catch", getCurrentTimeBody.contains("try") && getCurrentTimeBody.contains("catch"))
    }

    @Test
    fun testToolDispatcherNoInfiniteTimeouts() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        assertFalse("Should not use infinite URL.readText()", content.contains("URL(urlString).readText()"))
    }

    @Test
    fun testToolDispatcherCameraBackgroundStartGuarded() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/service/GeminiToolDispatcher.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val cameraOpenBody = content.substringAfter("fun handleCameraOpen").substringBefore("fun handleCameraClose")
        assertTrue("handleCameraOpen should check canDrawOverlays or SDK version", 
            cameraOpenBody.contains("canDrawOverlays") || cameraOpenBody.contains("SDK_INT"))
    }

    @Test
    fun testMainActivityProcessImageOffMainThread() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val processImageBody = content.substringAfter("private fun processAndSendImage").substringBefore("private fun scaleBitmap")
        assertTrue("processAndSendImage should launch on Dispatchers.Default", 
            processImageBody.contains("launch(Dispatchers.Default)") || 
            processImageBody.contains("Dispatchers.Default"))
    }

    @Test
    fun testMainActivityPermissionCodesNotConflated() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/MainActivity.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val extraPermLine = content.substringAfter("if (permissionsToRequest.isNotEmpty()) {")
            .substringBefore("}")
        assertFalse("Extra permissions should not use REQUIRED_PERMISSIONS_REQUEST_CODE", 
            extraPermLine.contains("REQUIRED_PERMISSIONS_REQUEST_CODE"))
    }

    @Test
    fun testAppPreferencesUsesAesGcm() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val encryptFallbackBody = content.substringAfter("private fun encryptFallback").substringBefore("private fun decryptFallback")
        assertTrue("encryptFallback should use AES/GCM/NoPadding", 
            encryptFallbackBody.contains("AES/GCM/NoPadding") || encryptFallbackBody.contains("GCM"))
    }

    @Test
    fun testAppPreferencesUsesSignatureFingerprint() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/data/AppPreferences.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        assertTrue("AppPreferences should contain signature fingerprint retrieval logic", 
            content.contains("GET_SIGNING_CERTIFICATES") || content.contains("GET_SIGNATURES") || content.contains("MessageDigest.getInstance(\"SHA-256\")"))
    }

    @Test
    fun testDatingAnalysisOrchestratorParallelExecution() {
        val file = File("src/main/java/com/example/geminimultimodalliveapi/agent/DatingAnalysisOrchestrator.kt")
        val finalFile = if (!file.exists()) {
            val alt = File("app/src/main/java/com/example/geminimultimodalliveapi/agent/DatingAnalysisOrchestrator.kt")
            assertTrue(alt.exists())
            alt
        } else {
            file
        }
        val content = finalFile.readText()
        val multiAgentBody = content.substringAfter("private suspend fun executeMultiAgent").substringBefore("private suspend fun findTop2Skills")
        assertTrue("executeMultiAgent should contain async block", multiAgentBody.contains("async"))
        assertTrue("executeMultiAgent should contain awaitAll", multiAgentBody.contains("awaitAll()"))
    }
}







