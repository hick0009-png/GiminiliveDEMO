package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.error.AppError
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
class Phase5UnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAppErrorFlowAndLegacyCompatibility() = runBlocking {
        val errorFlowList = mutableListOf<AppError>()
        val legacyErrorsList = mutableListOf<String>()

        val collectFlowJob = launch(Dispatchers.Unconfined) {
            SessionStateHolder.errorFlow.take(3).toList(errorFlowList)
        }

        val collectLegacyJob = launch(Dispatchers.Unconfined) {
            SessionStateHolder.appErrors.take(3).toList(legacyErrorsList)
        }

        // Emit AppError.Network
        SessionStateHolder.postError(AppError.Network("Socket timeout"))
        // Emit AppError.AuthExpired
        SessionStateHolder.postError(AppError.AuthExpired)
        // Emit AppError.Api
        SessionStateHolder.postError(AppError.Api(500, "Internal Server Error"))

        collectFlowJob.join()
        collectLegacyJob.join()

        // Verify errorFlow items
        assertEquals(3, errorFlowList.size)
        assertTrue(errorFlowList[0] is AppError.Network)
        assertEquals("Socket timeout", (errorFlowList[0] as AppError.Network).message)
        assertTrue(errorFlowList[1] is AppError.AuthExpired)
        assertTrue(errorFlowList[2] is AppError.Api)
        assertEquals(500, (errorFlowList[2] as AppError.Api).code)
        assertEquals("Internal Server Error", (errorFlowList[2] as AppError.Api).message)

        // Verify legacy compat strings
        assertEquals(3, legacyErrorsList.size)
        assertEquals("Network error: Socket timeout", legacyErrorsList[0])
        assertEquals("Auth expired", legacyErrorsList[1])
        assertEquals("Api error (500): Internal Server Error", legacyErrorsList[2])
    }

    @Test
    fun testAppErrorFromThrowableMapping() {
        // Test Auth Exception mapping
        val authException = com.google.android.gms.auth.UserRecoverableAuthException("Auth required", android.content.Intent())
        val mappedAuth = AppError.fromThrowable(authException)
        assertTrue(mappedAuth is AppError.AuthExpired)

        // Test Network Exception mapping
        val networkException = java.net.SocketTimeoutException("Read timed out")
        val mappedNetwork = AppError.fromThrowable(networkException)
        assertTrue(mappedNetwork is AppError.Network)
        assertEquals("Read timed out", (mappedNetwork as AppError.Network).message)

        // Test other generic Exceptions
        val genericException = RuntimeException("Something went wrong")
        val mappedGeneric = AppError.fromThrowable(genericException)
        assertTrue(mappedGeneric is AppError.Api)
        assertEquals(-1, (mappedGeneric as AppError.Api).code)
        assertEquals("Something went wrong", (mappedGeneric as AppError.Api).message)
    }
}
