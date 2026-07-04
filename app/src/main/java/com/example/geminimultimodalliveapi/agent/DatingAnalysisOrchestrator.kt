package com.example.geminimultimodalliveapi.agent

import android.util.Log
import com.example.geminimultimodalliveapi.data.DateInsight
import com.example.geminimultimodalliveapi.data.DatingSkill
import com.example.geminimultimodalliveapi.data.DatingSkillManager
import com.example.geminimultimodalliveapi.network.GeminiTextService
import kotlinx.coroutines.*

class DatingAnalysisOrchestrator(
    private val apiKey: String,
    private val skillManager: DatingSkillManager,
    private val routerAgent: DatingRouterAgent,
    private val primaryAgent: SkillAgent,
    private val secondaryAgent: SkillAgent? = null,
    private val documentSelector: DocumentSelector? = null
) {
    companion object {
        private const val TAG = "DatingOrchestrator"
        private const val CONFIDENCE_HIGH = 0.7f
        private const val CONFIDENCE_LOW = 0.4f
    }

    data class OrchestrationResult(
        val insight: DateInsight,
        val selectedSkillId: String,
        val confidence: Float,
        val reasoning: String,
        val contextAnnotation: String,
        val agentsUsed: List<String>
    )

    suspend fun analyze(
        transcriptHistory: List<Pair<String, String>>,
        sensorContext: SensorContext,
        skillIdOverride: String? = null
    ): OrchestrationResult {
        val transcriptStr = transcriptHistory.joinToString("\n") { "${it.first}: ${it.second}" }

        val route = if (skillIdOverride != null) {
            val allSkills = skillManager.getAllSkills()
            val isValid = allSkills.any { it.id == skillIdOverride }
            if (isValid) {
                DatingRouterAgent.RouterResult(skillIdOverride, 1.0f, "Manual override", "")
            } else {
                routerAgent.selectSkill(transcriptHistory, sensorContext)
            }
        } else {
            routerAgent.selectSkill(transcriptHistory, sensorContext)
        }

        Log.i(TAG, "Routing decision: skill=${route.selectedSkillId}, confidence=${route.confidence}, reason=${route.reasoning}")

        val activeSkill = skillManager.getSkill(route.selectedSkillId)
        if (activeSkill == null) {
            Log.w(TAG, "Selected skill '${route.selectedSkillId}' not found, using default")
            val defaultSkill = skillManager.getAllSkills().firstOrNull()
                ?: return OrchestrationResult(
                    insight = DateInsight(tip = "ไม่พบทักษะที่เหมาะสมในระบบ"),
                    selectedSkillId = "none",
                    confidence = 0f,
                    reasoning = "No skills available",
                    contextAnnotation = "",
                    agentsUsed = emptyList()
                )
            return executeWithSkill(defaultSkill, transcriptStr, sensorContext, route)
        }

        return when {
            route.confidence >= CONFIDENCE_HIGH -> {
                executeWithSkill(activeSkill, transcriptStr, sensorContext, route)
            }
            route.confidence >= CONFIDENCE_LOW -> {
                val candidates = findTop2Skills(activeSkill, transcriptHistory, sensorContext)
                executeMultiAgent(candidates, transcriptStr, sensorContext, route)
            }
            else -> {
                executeWithSkill(activeSkill, transcriptStr, sensorContext, route)
            }
        }
    }

    private suspend fun executeWithSkill(
        skill: DatingSkill,
        transcript: String,
        sensorContext: SensorContext,
        route: DatingRouterAgent.RouterResult
    ): OrchestrationResult {
        val docs = documentSelector?.selectRelevantDocuments(transcript, skill.id)
            ?: emptyList()
        val docContents = docs.map { it.content }
        Log.i(TAG, "DocumentSelector found ${docs.size} relevant docs for skill '${skill.id}'")

        var result = DateInsight()
        primaryAgent.analyze(skill, transcript, sensorContext, docContents) { result = it }

        return OrchestrationResult(
            insight = result,
            selectedSkillId = skill.id,
            confidence = route.confidence,
            reasoning = route.reasoning,
            contextAnnotation = route.contextAnnotation,
            agentsUsed = listOf(primaryAgent.agentId)
        )
    }

    private suspend fun executeMultiAgent(
        candidates: List<DatingSkillAgentPair>,
        transcript: String,
        sensorContext: SensorContext,
        route: DatingRouterAgent.RouterResult
    ): OrchestrationResult = kotlinx.coroutines.coroutineScope {
        val deferreds = candidates.map { (skill, agent) ->
            async(Dispatchers.Default) {
                try {
                    val docs = documentSelector?.selectRelevantDocuments(transcript, skill.id)
                        ?: emptyList()
                    val docContents = docs.map { it.content }
                    Log.i(TAG, "DocumentSelector found ${docs.size} relevant docs for skill '${skill.id}' (multi-agent)")

                    val deferredResult = CompletableDeferred<DateInsight>()
                    agent.analyze(skill, transcript, sensorContext, docContents) {
                        deferredResult.complete(it)
                    }
                    val result = withTimeout(30_000) { deferredResult.await() }
                    Pair(result, agent.agentId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing agent ${agent.agentId} for skill ${skill.id}", e)
                    null
                }
            }
        }

        val pairs = deferreds.awaitAll().filterNotNull()
        val results = pairs.map { it.first }
        val agentIds = pairs.map { it.second }

        val merged = mergeResults(results)
        OrchestrationResult(
            insight = merged,
            selectedSkillId = route.selectedSkillId,
            confidence = route.confidence,
            reasoning = route.reasoning,
            contextAnnotation = route.contextAnnotation,
            agentsUsed = agentIds
        )
    }

    private suspend fun findTop2Skills(
        primary: DatingSkill,
        transcriptHistory: List<Pair<String, String>>,
        sensorContext: SensorContext
    ): List<DatingSkillAgentPair> {
        val allSkills = skillManager.getAllSkills().filter { it.id != primary.id }
        if (allSkills.isEmpty() || secondaryAgent == null) {
            return listOf(DatingSkillAgentPair(primary, primaryAgent))
        }

        val recentTranscript = transcriptHistory.takeLast(4).joinToString("\n") { "${it.first}: ${it.second}" }
        val systemPrompt = "เลือก 1 ทักษะที่เหมาะสมรองลงมาจากบริบทนี้ ตอบเฉพาะ id เท่านั้น"

        val listSummary = allSkills.joinToString("\n") { "${it.id}: ${it.name} - ${it.description}" }
        val prompt = "ทักษะหลัก: ${primary.id}\n\nทักษะอื่น:\n$listSummary\n\nบทสนทนาล่าสุด:\n$recentTranscript"

        val deferred = kotlinx.coroutines.CompletableDeferred<String>()
        GeminiTextService.generateTextWithSystemInstruction(
            apiKey = apiKey,
            prompt = prompt,
            systemInstructionText = systemPrompt
        ) { result ->
            deferred.complete(result ?: primary.id)
        }

        val secondarySkillId = withTimeout(30_000) { deferred.await() }.let { raw ->
            if (raw != primary.id) {
                val cleaned = raw.trim().lowercase()
                allSkills.find { cleaned.contains(it.id.lowercase()) }?.id ?: primary.id
            } else primary.id
        }

        val primaryPair = DatingSkillAgentPair(primary, primaryAgent)
        val secondary = skillManager.getSkill(secondarySkillId)
        return if (secondary != null && secondary.id != primary.id) {
            listOf(primaryPair, DatingSkillAgentPair(secondary, secondaryAgent!!))
        } else {
            listOf(primaryPair)
        }
    }

    private fun mergeResults(results: List<DateInsight>): DateInsight {
        if (results.isEmpty()) return DateInsight(tip = "ไม่สามารถวิเคราะห์ได้")
        if (results.size == 1) return results[0]

        val allLikes = results.flatMap { it.likes }.distinct().take(4)
        val allDislikes = results.flatMap { it.dislikes }.distinct().take(4)
        val allPersonality = results.flatMap { it.personality }.distinct().take(3)
        val engagementLevels = results.map { it.engagementLevel }
        val bestEngagement = when {
            "Hot" in engagementLevels -> "Hot"
            "Warm" in engagementLevels -> "Warm"
            else -> "Cold"
        }
        val hasRedFlag = results.any { it.hasRedFlag }
        val bestTip = results.maxByOrNull { it.tip.length }?.tip ?: ""

        return DateInsight(
            likes = allLikes,
            dislikes = allDislikes,
            personality = allPersonality,
            tip = bestTip,
            engagementLevel = bestEngagement,
            hasRedFlag = hasRedFlag
        )
    }

    private data class DatingSkillAgentPair(
        val skill: DatingSkill,
        val agent: SkillAgent
    )

    fun resetRoutingCache() {
        routerAgent.resetCache()
    }
}
