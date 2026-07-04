package com.example.geminimultimodalliveapi.data

data class DateInsight(
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val personality: List<String> = emptyList(),
    val tip: String = "",
    val engagementLevel: String = "Warm",
    val hasRedFlag: Boolean = false,
    val activeAgentId: String = "",
    val activeAgentName: String = "",
    val routerReasoning: String = "",
    val isCached: Boolean = false
)
