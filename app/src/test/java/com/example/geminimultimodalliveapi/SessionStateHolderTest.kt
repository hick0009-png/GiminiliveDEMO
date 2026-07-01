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
}
