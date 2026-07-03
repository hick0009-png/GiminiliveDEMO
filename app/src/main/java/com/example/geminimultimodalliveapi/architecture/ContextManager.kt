package com.example.geminimultimodalliveapi.architecture

import android.util.Log

data class ContextSnapshot(
    val user: String = "owner",
    val location: String = "home",
    val motion: String = "STILL",
    val attentionSession: String = "IDLE",
    val currentTopic: String = "general_chat"
)

class ContextManager(
    private val rulesManager: DynamicRulesManager,
    private val topicManager: TopicManager
) {
    // Current environmental states kept up-to-date by the service
    @Volatile var currentLocation = "home"
    @Volatile var currentMotion = "STILL"
    @Volatile var currentAttention = "IDLE"

    fun getCurrentSnapshot(): ContextSnapshot {
        return ContextSnapshot(
            user = "owner",
            location = currentLocation,
            motion = currentMotion,
            attentionSession = currentAttention,
            currentTopic = topicManager.currentTopic?.name ?: "general_chat"
        )
    }

    /**
     * Builds the combined system prompt by aggregating base instructions, 
     * situational snapshots, and active dynamic voice rules.
     */
    fun getCombinedSystemPrompt(baseSystemInstruction: String): String {
        val snapshot = getCurrentSnapshot()
        val customRules = rulesManager.getActiveInstructionsForContext(
            currentMotion = snapshot.motion,
            currentLocation = snapshot.location,
            currentTopic = snapshot.currentTopic
        )

        val combinedPrompt = """
            $baseSystemInstruction
            
            [REALTIME_SITUATIONAL_CONTEXT]
            - User Name: ${snapshot.user}
            - Location Type: ${snapshot.location}
            - User Physical Motion: ${snapshot.motion}
            - System Attention State: ${snapshot.attentionSession}
            - Active Conversation Topic: ${snapshot.currentTopic}
            
            [USER_CUSTOM_SITUATIONAL_RULES]$customRules
            
            Context Instructions:
            1. Keep answers relevant to the Active Conversation Topic (${snapshot.currentTopic}) to resolve pronouns (e.g. "it", "how much" refers to the current topic).
            2. Adhere strictly to any custom rules in [USER_CUSTOM_SITUATIONAL_RULES] that match the user's current situation.
        """.trimIndent()
        
        Log.d("ContextManager", "Compiled combined system prompt (Rules Applied: ${if (customRules.isBlank()) "None" else "Yes"})")
        return combinedPrompt
    }
}
