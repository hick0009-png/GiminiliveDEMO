package com.example.geminimultimodalliveapi.agent

import android.util.Log
import com.example.geminimultimodalliveapi.data.DateInsight
import com.example.geminimultimodalliveapi.data.DatingSkill
import com.example.geminimultimodalliveapi.network.GeminiTextService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class DatingSkillAgentImpl(
    private val apiKey: String
) : SkillAgent {

    override val agentId = "skill_analyzer"
    override val agentName = "Skill Analysis Agent"
    override val description = "Analyzes conversation using the selected skill's instructions"

    companion object {
        private const val TAG = "DatingSkillAgent"
    }

    override suspend fun analyze(
        skill: DatingSkill,
        transcript: String,
        sensorContext: SensorContext,
        relatedDocs: List<String>,
        onResult: (DateInsight) -> Unit
    ) = withContext(Dispatchers.IO) {
        val docSection = if (relatedDocs.isNotEmpty()) {
            relatedDocs.joinToString("\n---\n") { doc ->
                "เอกสารอ้างอิงที่เกี่ยวข้อง:\n$doc"
            } + "\n\n"
        } else ""

        val systemPrompt = """
            คุณคือผู้ช่วยส่วนตัววิเคราะห์บทสนทนาและการเจรจาอัจฉริยะ (Conversation & Negotiation Assistant) 
            หน้าที่ของคุณคือรับฟังการถอดความบทสนทนาเรียลไทม์ และประเมินวิเคราะห์แนวทางชวนคุยหรือเทคนิคเจรจาต่อรองให้ผู้ใช้
            คุณต้องวิเคราะห์ข้อมูลตามเป้าหมาย (Skill) ที่เลือก และประยุกต์ใช้แนวทางจากคำแนะนำของ skill นั้น
            สำคัญมาก:
            - ให้วิเคราะห์เฉพาะบทสนทนาของ User และ Partner ที่เกี่ยวข้อง
            - คัดกรองและตัดประโยคที่ไม่เกี่ยวข้องหรือเบี่ยงเบนทางหัวเรื่องอย่างสิ้นเชิง (เช่น เสียงพูดขัดจังหวะของพนักงานเสิร์ฟ, เสียงโต๊ะข้างๆ เช็คบิล) ออกจากการวิเคราะห์โดยสิ้นเชิง
            - แสดงผลลัพธ์เป็น JSON โครงสร้างที่กำหนดเท่านั้น ห้ามมีคำอธิบายอื่น
        """.trimIndent()

        val cachedDocsAndSkill = """
            เป้าหมาย/ทักษะการสนทนาปัจจุบัน:
            name: ${skill.name}
            description: ${skill.description}
            instructions: ${skill.instructions}

            ${docSection}
        """.trimIndent()

        val staticContentToCache = systemPrompt + "\n---\n" + cachedDocsAndSkill
        val staticContentHash = staticContentToCache.hashCode()

        // 2,000 chars threshold for cache eligibility
        val isEligibleForCache = staticContentToCache.length >= 2000
        var cachedContentId: String? = null
        var isCacheUsed = false

        if (isEligibleForCache) {
            val now = System.currentTimeMillis()
            val existingCacheId = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheId
            val existingExpireTime = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheExpireTime
            val existingHash = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheContentHash

            if (existingCacheId != null && now < existingExpireTime && existingHash == staticContentHash) {
                cachedContentId = existingCacheId
                isCacheUsed = true
            } else {
                // Create cache
                val deferredCache = CompletableDeferred<Pair<String?, Long?>>()
                GeminiTextService.createContextCache(
                    apiKey = apiKey,
                    modelName = "gemini-3.5-flash",
                    systemInstructionText = systemPrompt,
                    docContents = listOf(cachedDocsAndSkill),
                    ttlSeconds = 300,
                    callback = { name, expireTime ->
                        deferredCache.complete(Pair(name, expireTime))
                    }
                )
                val (newCacheId, newExpireTime) = deferredCache.await()
                if (newCacheId != null && newExpireTime != null) {
                    com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheId = newCacheId
                    com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheExpireTime = newExpireTime
                    com.example.geminimultimodalliveapi.session.SessionStateHolder.activeCacheContentHash = staticContentHash
                    cachedContentId = newCacheId
                    isCacheUsed = true
                }
            }
        }

        val prompt = if (isCacheUsed && cachedContentId != null) {
            """
                บทสนทนาล่าสุด:
                $transcript

                บริบทแวดล้อม:
                - สถานที่: ${sensorContext.location}
                - การเคลื่อนไหว: ${sensorContext.motion}
                - ความดังเสียงรอบตัว: ${sensorContext.noiseDb} dB
                - เวลาปัจจุบัน: ${sensorContext.currentTime}

                โปรดวิเคราะห์การพูดคุยนี้และส่งผลลัพธ์เป็น JSON ในรูปแบบนี้เท่านั้น:
                {
                  "likes": ["สิ่งที่อีกฝ่ายพูดว่าชอบหรือแสดงความสนใจ 2-4 หัวข้อสั้นๆ"],
                  "dislikes": ["สิ่งที่อีกฝ่ายพูดว่าไม่ชอบ เกลียด หรือแสดงความกังวล 2-4 หัวข้อสั้นๆ"],
                  "personality": ["ลักษณะนิสัย/Traits ของอีกฝ่ายที่สะท้อนออกมา 2-3 คำสำคัญ"],
                  "tip": "คำแนะนำสั้นๆ กระชับในการชวนคุยหรือต่อรองเจรจาต่อไป ตามทักษะ/เป้าหมาย และเอกสารแนวทางที่แนบ",
                  "engagementLevel": "ระดับความน่าสนใจ/บรรยากาศ (Cold หรือ Warm หรือ Hot)",
                  "hasRedFlag": true/false
                }
            """.trimIndent()
        } else {
            """
                ${docSection}บทสนทนาล่าสุด:
                $transcript

                เป้าหมาย/ทักษะการสนทนาปัจจุบัน:
                name: ${skill.name}
                description: ${skill.description}
                instructions: ${skill.instructions}

                บริบทแวดล้อม:
                - สถานที่: ${sensorContext.location}
                - การเคลื่อนไหว: ${sensorContext.motion}
                - ความดังเสียงรอบตัว: ${sensorContext.noiseDb} dB
                - เวลาปัจจุบัน: ${sensorContext.currentTime}

                โปรดวิเคราะห์การพูดคุยนี้และส่งผลลัพธ์เป็น JSON ในรูปแบบนี้เท่านั้น:
                {
                  "likes": ["สิ่งที่อีกฝ่ายพูดว่าชอบหรือแสดงความสนใจ 2-4 หัวข้อสั้นๆ"],
                  "dislikes": ["สิ่งที่อีกฝ่ายพูดว่าไม่ชอบ เกลียด หรือแสดงความกังวล 2-4 หัวข้อสั้นๆ"],
                  "personality": ["ลักษณะนิสัย/Traits ของอีกฝ่ายที่สะท้อนออกมา 2-3 คำสำคัญ"],
                  "tip": "คำแนะนำสั้นๆ กระชับในการชวนคุยหรือต่อรองเจรจาต่อไป ตามทักษะ/เป้าหมาย และเอกสารแนวทางที่แนบ",
                  "engagementLevel": "ระดับความน่าสนใจ/บรรยากาศ (Cold หรือ Warm หรือ Hot)",
                  "hasRedFlag": true/false
                }
            """.trimIndent()
        }

        val deferred = CompletableDeferred<DateInsight>()
        GeminiTextService.generateTextWithSystemInstruction(
            apiKey = apiKey,
            prompt = prompt,
            systemInstructionText = systemPrompt,
            cachedContentName = cachedContentId,
            callback = { resultText ->
                val insight = parseAnalysisResult(resultText)
                val finalInsight = insight.copy(isCached = isCacheUsed)
                deferred.complete(finalInsight)
            }
        )

        val insight = withTimeout(30_000) { deferred.await() }
        Log.i(TAG, "Analysis complete for skill '${skill.id}': engagement=${insight.engagementLevel}, redFlag=${insight.hasRedFlag}, cached=${insight.isCached}")
        onResult(insight)
    }

    private fun parseAnalysisResult(raw: String?): DateInsight {
        if (raw == null) return DateInsight(tip = "การวิเคราะห์ล้มเหลว โปรดลองอีกครั้ง")

        try {
            var cleaned = raw.trim()
            if (cleaned.startsWith("```json")) cleaned = cleaned.removePrefix("```json")
            if (cleaned.startsWith("```")) cleaned = cleaned.removePrefix("```")
            if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```")
            cleaned = cleaned.trim()

            val json = JSONObject(cleaned)
            return DateInsight(
                likes = parseJsonArray(json.optJSONArray("likes")),
                dislikes = parseJsonArray(json.optJSONArray("dislikes")),
                personality = parseJsonArray(json.optJSONArray("personality")),
                tip = json.optString("tip", ""),
                engagementLevel = json.optString("engagementLevel", "Warm"),
                hasRedFlag = json.optBoolean("hasRedFlag", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse analysis result", e)
            return DateInsight(tip = "รูปแบบข้อมูลไม่ถูกต้อง โปรดลองอีกครั้ง")
        }
    }

    private fun parseJsonArray(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }
}
