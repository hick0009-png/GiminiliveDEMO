package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateHolderTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        SessionStateHolder.updateState(SessionState.Disconnected)
        SessionStateHolder.clearChatLogs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStateTransitions() = runBlocking {
        assertEquals(SessionState.Disconnected, SessionStateHolder.state.value)

        SessionStateHolder.updateState(SessionState.Connecting)
        assertEquals(SessionState.Connecting, SessionStateHolder.state.value)

        SessionStateHolder.updateState(SessionState.Standby("กอหญ้า"))
        val currentState = SessionStateHolder.state.value
        assertTrue(currentState is SessionState.Standby)
        assertEquals("กอหญ้า", (currentState as SessionState.Standby).wakeWord)

        SessionStateHolder.updateState(SessionState.Active(true))
        assertTrue(SessionStateHolder.state.value is SessionState.Active)
        assertTrue((SessionStateHolder.state.value as SessionState.Active).isRecording)
    }

    @Test
    fun testChatLogsAppend() = runBlocking {
        assertEquals("", SessionStateHolder.chatLogs.value)

        SessionStateHolder.appendChatLog("USER: hello")
        assertEquals("USER: hello", SessionStateHolder.chatLogs.value)

        SessionStateHolder.appendChatLog("GEMINI: hi there")
        assertEquals("USER: hello\nGEMINI: hi there", SessionStateHolder.chatLogs.value)
    }

    @Test
    fun testAppErrorsPublish() = runBlocking {
        val errorsList = mutableListOf<String>()
        val collectJob = launch(Dispatchers.Unconfined) {
            SessionStateHolder.appErrors.take(2).toList(errorsList)
        }

        SessionStateHolder.postError("Network error")
        SessionStateHolder.postError("Timeout error")
        
        collectJob.join()
        assertEquals(2, errorsList.size)
        assertEquals("Network error", errorsList[0])
        assertEquals("Timeout error", errorsList[1])
    }

    @Test
    fun testDateAssistantModeFlow() = runBlocking {
        val statesList = mutableListOf<Boolean>()
        val collectJob = launch(Dispatchers.Unconfined) {
            SessionStateHolder.isDateAssistantModeActiveFlow.take(3).toList(statesList)
        }

        SessionStateHolder.isDateAssistantModeActive = true
        SessionStateHolder.isDateAssistantModeActive = false
        
        collectJob.join()
        assertEquals(3, statesList.size)
        assertEquals(false, statesList[0]) // Initial value
        assertEquals(true, statesList[1])
        assertEquals(false, statesList[2])
    }

    @Test
    fun testAppErrorsDeDuplication() = runBlocking {
        val errorsList = mutableListOf<com.example.geminimultimodalliveapi.error.AppError>()
        val collectJob = launch(Dispatchers.Unconfined) {
            SessionStateHolder.errorFlow.take(2).toList(errorsList)
        }

        val error1 = com.example.geminimultimodalliveapi.error.AppError.Network("Socket timeout")
        val error2 = com.example.geminimultimodalliveapi.error.AppError.Network("Socket timeout")
        val error3 = com.example.geminimultimodalliveapi.error.AppError.Permission("camera")

        SessionStateHolder.postError(error1)
        SessionStateHolder.postError(error2) // Should be de-duplicated (ignored)
        SessionStateHolder.postError(error3) // Different error, should go through

        collectJob.join()
        assertEquals(2, errorsList.size)
        assertEquals(error1, errorsList[0])
        assertEquals(error3, errorsList[1])
    }
}
