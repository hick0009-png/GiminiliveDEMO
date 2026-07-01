package com.example.geminimultimodalliveapi.agent

import android.util.Log
import com.example.geminimultimodalliveapi.data.DatingSkill
import com.example.geminimultimodalliveapi.data.DatingSkillManager
import com.example.geminimultimodalliveapi.network.GeminiTextService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DatingRouterAgent(
    private val apiKey: String,
    private val skillManager: DatingSkillManager
) {
    data class RouterResult(
        val selectedSkillId: String,
        val confidence: Float,
        val reasoning: String,
        val contextAnnotation: String
    )

    companion object {
        private const val TAG = "DatingRouterAgent"
        private const val CONFIDENCE_HIGH = 0.7f
        private const val CONFIDENCE_LOW = 0.4f
        const val DEFAULT_SKILL_ID = "icebreaker"
    }

    private var lastResult: RouterResult? = null
    private var lastTranscriptLineCount = 0

    suspend fun selectSkill(
        transcriptLines: List<Pair<String, String>>,
        sensorContext: SensorContext
    ): RouterResult = withContext(Dispatchers.IO) {
        val currentLineCount = transcriptLines.size
        if (lastResult != null && (currentLineCount - lastTranscriptLineCount) < 3) {
            return@withContext lastResult!!
        }

        val allSkills = skillManager.getAllSkills()
        if (allSkills.isEmpty()) {
            return@withContext RouterResult(DEFAULT_SKILL_ID, 0.5f, "No skills available", "")
        }

        val skillsSummary = allSkills.joinToString("\n") { s ->
            "- id: ${s.id}\n  name: ${s.name}\n  description: ${s.description}"
        }

        val recentTranscript = transcriptLines.takeLast(6).joinToString("\n") { "${it.first}: ${it.second}" }

        val prompt = """
            บทสนทนาล่าสุด:
            $recentTranscript

            สถานการณ์:
            - สถานที่: ${sensorContext.location}
            - การเคลื่อนไหว: ${sensorContext.motion}
            - เวลา: ${sensorContext.currentTime}

            ทักษะที่มีให้เลือก:
            $skillsSummary

            วิเคราะห์บทสนทนาและเลือกทักษะที่เหมาะสมที่สุด 1 อย่าง
            ตอบเป็น JSON เท่านั้น:
            {
              "selected_skill_id": "id ของทักษะที่เลือก",
              "confidence": 0.0-1.0,
              "reasoning": "เหตุผลสั้นๆ ที่เลือกทักษะนี้",
              "context_annotation": "ข้อสังเกตเกี่ยวกับบรรยากาศ/สถานการณ์"
            }
        """.trimIndent()

        val systemPrompt = """
            คุณคือ Router Agent ผู้เชี่ยวชาญด้านการเลือกทักษะการสนทนาที่เหมาะสม
            วิเคราะห์บริบทของบทสนทนาและเลือกทักษะที่ดีที่สุดจากรายการ
            ตอบเป็น JSON เท่านั้น ไม่มีคำอธิบายอื่น
        """.trimIndent()

        val deferred = CompletableDeferred<RouterResult>()
        GeminiTextService.generateTextWithSystemInstruction(
            apiKey = apiKey,
            prompt = prompt,
            systemInstructionText = systemPrompt
        ) { result ->
            val parsed = parseRouterResult(result, allSkills)
            deferred.complete(parsed)
        }

        val result = deferred.await()
        lastResult = result
        lastTranscriptLineCount = currentLineCount
        Log.i(TAG, "Router selected: ${result.selectedSkillId} (confidence=${result.confidence}, reason=${result.reasoning})")
        result
    }

    private fun parseRouterResult(raw: String?, allSkills: List<DatingSkill>): RouterResult {
        if (raw == null) return fallback()

        try {
            var cleaned = raw.trim()
            if (cleaned.startsWith("```json")) cleaned = cleaned.removePrefix("```json")
            if (cleaned.startsWith("```")) cleaned = cleaned.removePrefix("```")
            if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```")
            cleaned = cleaned.trim()

            val json = JSONObject(cleaned)
            val skillId = json.optString("selected_skill_id", "")
            val confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            val reasoning = json.optString("reasoning", "")
            val annotation = json.optString("context_annotation", "")

            val validSkill = allSkills.any { it.id == skillId }
            if (!validSkill || confidence < CONFIDENCE_LOW) {
                return RouterResult(DEFAULT_SKILL_ID, confidence.coerceAtMost(CONFIDENCE_LOW), reasoning, annotation)
            }

            return RouterResult(skillId, confidence, reasoning, annotation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse router result", e)
            return fallback()
        }
    }

    private fun fallback() = RouterResult(DEFAULT_SKILL_ID, 0.5f, "Fallback to default", "")

    fun resetCache() {
        lastResult = null
        lastTranscriptLineCount = 0
    }
}
