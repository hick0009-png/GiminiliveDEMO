package com.example.geminimultimodalliveapi.agent

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

class DocumentSelector(private val context: Context) {

    companion object {
        private const val TAG = "DocumentSelector"
        private const val MAX_MATCH = 2
    }

    data class ScoredDoc(
        val fileName: String,
        val content: String,
        val score: Float,
        val category: String
    )

    fun selectRelevantDocuments(
        transcript: String,
        skillId: String
    ): List<ScoredDoc> {
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists() || !docsDir.isDirectory) return emptyList()

        val jsonFiles = docsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?: return emptyList()

        val docs = mutableListOf<ScoredDoc>()
        for (file in jsonFiles) {
            try {
                val raw = file.readText()
                val json = JSONObject(raw)
                val category = json.optString("category", "")
                val title = json.optString("title", "")
                val description = json.optString("description", "")

                val searchableText = buildString {
                    append("$title $description $category ")
                    val principles = json.optJSONArray("principles")
                    if (principles != null) {
                        for (i in 0 until principles.length()) {
                            val p = principles.getJSONObject(i)
                            append(p.optString("name", "") + " ")
                            append(p.optString("description", "") + " ")
                            val keywords = p.optJSONArray("keywords")
                            if (keywords != null) {
                                for (j in 0 until keywords.length()) {
                                    append(keywords.getString(j) + " ")
                                }
                            }
                        }
                    }
                }

                val score = computeTfIdfScore(transcript, skillId, searchableText, category)
                if (score > 0.1f) {
                    docs.add(ScoredDoc(file.name, raw, score, category))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading ${file.name}", e)
            }
        }

        docs.sortByDescending { it.score }
        val selected = docs.take(MAX_MATCH)
        Log.i(TAG, "Selected ${selected.size} docs from ${jsonFiles.size} files (top score: ${selected.firstOrNull()?.score})")
        return selected
    }

    private fun computeTfIdfScore(
        transcript: String,
        skillId: String,
        docText: String,
        docCategory: String
    ): Float {
        val query = "$transcript $skillId".lowercase()
        val doc = docText.lowercase()

        val stopWords = setOf(
            "และ", "หรือ", "ของ", "ที่", "มี", "เป็น", "การ", "ความ", "ใน", "กับ",
            "ได้", "ให้", "ตาม", "จะ", "ไป", "มา", "ต้อง", "ผู้", "จาก", "ถึง",
            "เพื่อ", "สำหรับ", "อัน", "ซึ่ง", "นี้", "นั้น", "แล้ว", "ทาง", "โดย",
            "ด้วย", "อย่างไร", "ไหน", "อะไร", "ใคร", "ทำไม", "the", "a", "an",
            "is", "are", "was", "were", "be", "been", "being", "have", "has",
            "had", "do", "does", "did", "but", "not", "this", "that", "it"
        )

        val queryTokens = query.split(Regex("[\\s,./\\-:;!?()]+"))
            .map { it.trim() }
            .filter { it.length > 1 && it !in stopWords }
            .toSet()

        if (queryTokens.isEmpty()) return 0f

        val docTokens = doc.split(Regex("[\\s,./\\-:;!?()]+"))
            .map { it.trim() }
            .filter { it.length > 1 && it !in stopWords }

        val totalDocTokens = docTokens.size.toFloat()
        if (totalDocTokens == 0f) return 0f

        val termFreq = queryTokens.associateWith { token ->
            docTokens.count { it.contains(token) || token.contains(it) }
        }

        var score = 0f
        for ((_, freq) in termFreq) {
            if (freq > 0) {
                val tf = 1f + kotlin.math.log10(freq.toFloat())
                val idf = kotlin.math.log10((totalDocTokens + 1f) / (freq + 0.5f))
                score += tf * idf
            }
        }

        if (docCategory.lowercase() in query) {
            score *= 1.5f
        }

        score /= queryTokens.size.toFloat()
        return score.coerceIn(0f, 1f)
    }
}
