package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.architecture.AttentionManager
import com.example.geminimultimodalliveapi.architecture.AttentionState
import com.example.geminimultimodalliveapi.architecture.PerceptionEvent
import com.example.geminimultimodalliveapi.architecture.TopicManager
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationalArchitectureTest {

    @Before
    fun setUp() {
        SessionStateHolder.updateState(SessionState.Disconnected)
        SessionStateHolder.clearChatLogs()
    }

    @Test
    fun testAttentionStateTransitions() {
        var attentionChangedCalled = false
        var activeState = false

        val attentionManager = AttentionManager { active ->
            attentionChangedCalled = true
            activeState = active
        }

        // 1. Initial state should be IDLE, and attention not active
        assertEquals(AttentionState.IDLE, attentionManager.state.value)
        assertFalse(attentionManager.isAttentionActive.value)

        // 2. IDLE -> LISTENING on Wakeword
        attentionManager.onNewEvent(PerceptionEvent.WakewordDetected("กอหญ้า"))
        assertEquals(AttentionState.LISTENING, attentionManager.state.value)
        assertTrue(attentionManager.isAttentionActive.value)
        assertTrue(attentionChangedCalled)

        // Reset tracking flag
        attentionChangedCalled = false

        // 3. LISTENING -> ACTIVE_SESSION on Owner Speech
        attentionManager.onNewEvent(PerceptionEvent.SpeechDetected("owner", 0.95f))
        assertEquals(AttentionState.ACTIVE_SESSION, attentionManager.state.value)
        assertTrue(attentionManager.isAttentionActive.value)
        
        // 4. ACTIVE_SESSION -> BACKGROUND on Stranger Speech (Interruption)
        attentionManager.onNewEvent(PerceptionEvent.SpeechDetected("stranger", 0.80f))
        assertEquals(AttentionState.BACKGROUND, attentionManager.state.value)
        assertFalse(attentionManager.isAttentionActive.value) // Suspend streaming during background noise

        // 5. BACKGROUND -> ACTIVE_SESSION on Wakeword again
        attentionManager.onNewEvent(PerceptionEvent.WakewordDetected("กอหญ้า"))
        assertEquals(AttentionState.ACTIVE_SESSION, attentionManager.state.value)
        assertTrue(attentionManager.isAttentionActive.value)
    }

    @Test
    fun testAttentionIgnoreOtherSpeakersRule() {
        val attentionManager = AttentionManager { }

        // Start active session
        attentionManager.onNewEvent(PerceptionEvent.WakewordDetected("กอหญ้า"))
        attentionManager.onNewEvent(PerceptionEvent.SpeechDetected("owner", 0.95f))
        assertEquals(AttentionState.ACTIVE_SESSION, attentionManager.state.value)

        // Enable ignore other speakers rule (tuning rule)
        attentionManager.setIgnoreOtherSpeakers(true)

        // Stranger speaks, state should remain ACTIVE_SESSION
        attentionManager.onNewEvent(PerceptionEvent.SpeechDetected("stranger", 0.85f))
        assertEquals(AttentionState.ACTIVE_SESSION, attentionManager.state.value)
        assertTrue(attentionManager.isAttentionActive.value)

        // Disable rule
        attentionManager.setIgnoreOtherSpeakers(false)

        // Stranger speaks, state should shift to BACKGROUND
        attentionManager.onNewEvent(PerceptionEvent.SpeechDetected("stranger", 0.85f))
        assertEquals(AttentionState.BACKGROUND, attentionManager.state.value)
        assertFalse(attentionManager.isAttentionActive.value)
    }

    @Test
    fun testTopicManagerClassificationAndTimeout() {
        val topicManager = TopicManager(timeoutLimitMs = 1000L) // Set short 1s timeout for testing

        // 1. Initial topic should be null
        assertTrue(topicManager.currentTopic == null)

        // 2. Query used car keywords -> should classify as used_car
        val topic1 = topicManager.processQueryIntent("ค่างวดผ่อนรถมือสองคิดยังไง")
        assertNotNull(topic1)
        assertEquals("used_car", topic1.name)
        assertEquals("used_car", topicManager.currentTopic?.name)

        // 3. Query general words within 5 seconds -> should keep used_car topic
        val topic2 = topicManager.processQueryIntent("คิดยังไงล่ะ")
        assertEquals("used_car", topic2.name)

        // 4. Query navigation keyword -> should switch topic immediately
        val topic3 = topicManager.processQueryIntent("เปิดแผนที่นำทางไปห้างหน่อย")
        assertEquals("navigation", topic3.name)

        // 5. Let it timeout (wait 1.2s)
        Thread.sleep(1200L)

        // 6. Query general text after timeout -> should close navigation and create general_chat
        val topic4 = topicManager.processQueryIntent("สวัสดีครับ")
        assertEquals("general_chat", topic4.name)
    }

    @Test
    fun testTopicManagerDynamicKeywords() {
        val topicManager = TopicManager()

        // Querying "ผ่อนมือถือ" should default to general_chat as it's not a known used_car keyword
        val topic1 = topicManager.processQueryIntent("ผ่อนมือถือคิดยังไง")
        assertEquals("general_chat", topic1.name)

        // Dynamically add keyword "ผ่อนมือถือ" to "used_car" topic (simulating voice tuning)
        topicManager.addDynamicKeyword("used_car", "ผ่อนมือถือ")

        // Querying again should now successfully classify under used_car
        val topic2 = topicManager.processQueryIntent("ผ่อนมือถือคิดยังไง")
        assertEquals("used_car", topic2.name)
    }
}
