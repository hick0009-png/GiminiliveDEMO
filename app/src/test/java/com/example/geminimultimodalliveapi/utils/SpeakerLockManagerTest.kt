package com.example.geminimultimodalliveapi.utils

import com.example.geminimultimodalliveapi.network.DeepgramLiveClient.WordDetail
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeakerLockManagerTest {

    @Before
    fun setUp() {
        SessionStateHolder.updateState(SessionState.Disconnected)
        SessionStateHolder.clearChatLogs()
    }

    @Test
    fun testWakeWordDetectionAndLocking() = runTest {
        val manager = SpeakerLockManager(this)

        // Scenario: Speaker 1 says "สวัสดี" and Speaker 2 says "กอหญ้า"
        val wordsChunk1 = listOf(
            WordDetail("สวัสดี", speaker = 1, start = 0.0, end = 0.5),
            WordDetail("ครับ", speaker = 1, start = 0.5, end = 1.0)
        )
        val result1 = manager.processIncomingWords(wordsChunk1, "กอหญ้า")
        assertNull("Should not return text since no one has said the wake word yet", result1)
        assertNull("Speaker ID should not be locked yet", manager.activeSpeakerId)

        val wordsChunk2 = listOf(
            WordDetail("กอหญ้า", speaker = 2, start = 1.2, end = 1.8),
            WordDetail("ช่วย", speaker = 2, start = 1.8, end = 2.2),
            WordDetail("สรุป", speaker = 2, start = 2.2, end = 2.5)
        )
        val result2 = manager.processIncomingWords(wordsChunk2, "กอหญ้า")
        assertNotNull(result2)
        assertEquals("กอหญ้า ช่วย สรุป", result2)
        assertEquals("Should lock to speaker 2", 2, manager.activeSpeakerId)
    }

    @Test
    fun testSpeakerFiltering() = runTest {
        val manager = SpeakerLockManager(this)

        // Lock onto speaker 2 first
        val wordsLock = listOf(
            WordDetail("กอหญ้า", speaker = 2, start = 0.0, end = 0.5)
        )
        manager.processIncomingWords(wordsLock, "กอหญ้า")
        assertEquals(2, manager.activeSpeakerId)

        // Mix of speaker 2 (locked) and speaker 1 (unlocked)
        val wordsMix = listOf(
            WordDetail("ฉัน", speaker = 2, start = 0.6, end = 1.0),
            WordDetail("แทรกแซง", speaker = 1, start = 0.7, end = 1.2),
            WordDetail("หิวข้าว", speaker = 2, start = 1.2, end = 1.5)
        )
        val result = manager.processIncomingWords(wordsMix, "กอหญ้า")
        assertEquals("Should only contain words from speaker 2", "ฉัน หิวข้าว", result)
    }

    @Test
    fun testLockExpiry() = runTest {
        val manager = SpeakerLockManager(this)

        // Lock onto speaker 3
        val wordsLock = listOf(
            WordDetail("กอหญ้า", speaker = 3, start = 0.0, end = 0.5),
            WordDetail("เริ่ม", speaker = 3, start = 0.5, end = 1.0)
        )
        manager.processIncomingWords(wordsLock, "กอหญ้า")
        assertEquals(3, manager.activeSpeakerId)

        // Advance time by 4 seconds (less than 8 seconds duration)
        advanceTimeBy(4000)
        assertEquals("Lock should still be active after 4 seconds", 3, manager.activeSpeakerId)

        // Advance time past 8 seconds total (8000ms duration)
        advanceTimeBy(4100)
        assertNull("Lock should have expired after 8 seconds of inactivity", manager.activeSpeakerId)
    }
}
