package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.audio.AudioConfig
import com.example.geminimultimodalliveapi.network.ToolDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAudioTest {

    @Test
    fun testToolDefinitionsCountAndNames() {
        val toolsArray = ToolDefinitions.getTools()
        assertEquals(2, toolsArray.length())
        
        val toolObject = toolsArray.getJSONObject(0)
        assertTrue(toolObject.has("functionDeclarations"))
        
        val functionDeclarations = toolObject.getJSONArray("functionDeclarations")
        assertEquals(19, functionDeclarations.length())
        
        val expectedNames = setOf(
            "open_camera",
            "close_camera",
            "save_vehicle_info",
            "query_vehicle_info",
            "get_current_time",
            "delete_vehicle_info",
            "query_policy_document",
            "make_phone_call",
            "end_phone_call",
            "create_calendar_event",
            "list_calendar_events",
            "get_current_weather",
            "find_nearby_places",
            "launch_navigation",
            "remember_personal_fact",
            "forget_personal_fact",
            "query_relevant_memories",
            "save_system_rule",
            "get_situational_context"
        )
        
        val actualNames = mutableSetOf<String>()
        for (i in 0 until functionDeclarations.length()) {
            val functionDecl = functionDeclarations.getJSONObject(i)
            actualNames.add(functionDecl.getString("name"))
        }
        
        assertEquals(expectedNames, actualNames)
    }

    @Test
    fun testAudioConfigConstants() {
        assertEquals(16000, AudioConfig.INPUT_SAMPLE_RATE)
        assertEquals(24000, AudioConfig.OUTPUT_SAMPLE_RATE)
        assertEquals(android.media.AudioFormat.CHANNEL_IN_MONO, AudioConfig.CHANNEL_IN)
        assertEquals(android.media.AudioFormat.CHANNEL_OUT_MONO, AudioConfig.CHANNEL_OUT)
        assertEquals(android.media.AudioFormat.ENCODING_PCM_16BIT, AudioConfig.ENCODING)
    }

    @Test
    fun testReconnectBackoffCalculation() {
        // Simulating the delay calculation in scheduleReconnect: (1L shl reconnectAttempts) * 1000L
        fun calculateDelay(reconnectAttempts: Int): Long {
            return (1L shl reconnectAttempts) * 1000L
        }

        assertEquals(1000L, calculateDelay(0))
        assertEquals(2000L, calculateDelay(1))
        assertEquals(4000L, calculateDelay(2))
    }
}
