package com.example.geminimultimodalliveapi.agent

import com.example.geminimultimodalliveapi.data.DateInsight
import com.example.geminimultimodalliveapi.data.DatingSkill

data class SensorContext(
    val location: String = "home",
    val motion: String = "STILL",
    val noiseDb: Double = 30.0,
    val currentTime: String = ""
)

interface SkillAgent {
    val agentId: String
    val agentName: String
    val description: String

    suspend fun analyze(
        skill: DatingSkill,
        transcript: String,
        sensorContext: SensorContext,
        relatedDocs: List<String> = emptyList(),
        onResult: (DateInsight) -> Unit
    )
}
