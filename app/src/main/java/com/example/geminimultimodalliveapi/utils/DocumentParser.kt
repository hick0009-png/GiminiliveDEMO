package com.example.geminimultimodalliveapi.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object DocumentParser {

    suspend fun extractText(context: Context, uri: Uri, mimeType: String?): String = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext ""

            val rawText = when {
                mimeType == "application/pdf" || uri.path?.endsWith(".pdf", ignoreCase = true) == true -> {
                    extractTextFromPdf(context, inputStream)
                }
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || 
                        uri.path?.endsWith(".docx", ignoreCase = true) == true -> {
                    extractTextFromDocx(inputStream)
                }
                else -> {
                    // Default to reading as text
                    inputStream.bufferedReader().use { it.readText() }
                }
            }
            cleanText(rawText)
        } catch (e: Exception) {
            Log.e("DocumentParser", "Error extracting text from file", e)
            ""
        }
    }

    fun cleanText(text: String): String {
        val lines = text.replace("\r\n", "\n").split("\n")
        val cleanedLines = mutableListOf<String>()
        var lastWasEmpty = false
        
        val pageNumRegex = Regex("^(page|หน้า)?\\s*\\d+\\s*(of|/|จาก)?\\s*\\d*\\s*$", RegexOption.IGNORE_CASE)
        
        for (line in lines) {
            val trimmed = line.trim().replace("\\s+".toRegex(), " ")
            if (trimmed.isEmpty()) {
                if (!lastWasEmpty) {
                    cleanedLines.add("")
                    lastWasEmpty = true
                }
            } else {
                if (pageNumRegex.matches(trimmed)) {
                    continue
                }
                cleanedLines.add(trimmed)
                lastWasEmpty = false
            }
        }
        return cleanedLines.joinToString("\n").trim()
    }

    private fun extractTextFromPdf(context: Context, inputStream: InputStream): String {
        return try {
            PDFBoxResourceLoader.init(context)
            inputStream.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.getText(doc)
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentParser", "PDFBox extraction failed", e)
            ""
        }
    }

    internal fun extractTextFromDocx(inputStream: InputStream): String {
        return try {
            val content = ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        return@use zipStream.bufferedReader().readText()
                    }
                    entry = zipStream.nextEntry
                }
                ""
            }
            
            if (content.isEmpty()) return ""
            
            val sb = java.lang.StringBuilder()
            var i = 0
            val len = content.length
            while (i < len) {
                if (content[i] == '<') {
                    val endTag = content.indexOf('>', i)
                    if (endTag == -1) break
                    val tag = content.substring(i + 1, endTag)
                    val tagName = tag.split(" ")[0]
                    if (tagName == "w:p" || tagName == "w:br" || tagName == "w:cr") {
                        sb.append("\n")
                    }
                    i = endTag + 1
                } else {
                    val nextTag = content.indexOf('<', i)
                    val text = if (nextTag == -1) {
                        content.substring(i)
                    } else {
                        content.substring(i, nextTag)
                    }
                    
                    // Only extract text that lies inside <w:t> tags
                    val prevTagStart = content.lastIndexOf('<', i - 1)
                    if (prevTagStart != -1) {
                        val prevTagEnd = content.indexOf('>', prevTagStart)
                        if (prevTagEnd != -1) {
                            val prevTag = content.substring(prevTagStart + 1, prevTagEnd)
                            if (prevTag.startsWith("w:t")) {
                                val decoded = text
                                    .replace("&amp;", "&")
                                    .replace("&lt;", "<")
                                    .replace("&gt;", ">")
                                    .replace("&quot;", "\"")
                                    .replace("&apos;", "'")
                                sb.append(decoded)
                            }
                        }
                    }
                    i = if (nextTag == -1) len else nextTag
                }
            }
            sb.toString().trim()
        } catch (e: Exception) {
            Log.e("DocumentParser", "DOCX extraction failed", e)
            ""
        }
    }

    suspend fun queryDocuments(context: Context, query: String): String = withContext(Dispatchers.IO) {
        try {
            val docsDir = File(context.filesDir, "documents")
            if (!docsDir.exists() || !docsDir.isDirectory) {
                return@withContext "ไม่พบโฟลเดอร์เอกสารสำหรับค้นหา"
            }

            val files = docsDir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            if (files.isNullOrEmpty()) {
                return@withContext "ยังไม่มีเอกสารประกันภัยหรือคู่มือที่อัปโหลดไว้ในเครื่อง"
            }

            // Extended list of Thai stop words to remove semantic noise
            val stopWords = setOf(
                "และ", "หรือ", "ของ", "ที่", "มี", "เป็น", "การ", "ความ", "ใน", "กับ",
                "ได้", "ให้", "ตาม", "จะ", "ไป", "มา", "ต้อง", "ผู้", "รับ", "ส่ง",
                "จาก", "ถึง", "เพื่อ", "สำหรับ", "อัน", "ซึ่ง", "นี้", "นั้น", "แล้ว",
                "ทาง", "โดย", "ด้วย", "อย่างไร", "ไหน", "อะไร", "ใคร", "เมื่อไหร่", "ทำไม"
            )

            // Extract features: English words and Thai character trigrams
            val rawTokens = query.lowercase()
                .split(" ", ",", "　")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !stopWords.contains(it) }

            val features = mutableListOf<String>()
            for (token in rawTokens) {
                if (token.matches(Regex("^[a-zA-Z0-9]+$"))) {
                    if (token.length > 1) {
                        features.add(token)
                    }
                } else {
                    if (token.length <= 3) {
                        features.add(token)
                    } else {
                        for (i in 0..token.length - 3) {
                            features.add(token.substring(i, i + 3))
                        }
                    }
                }
            }
            val distinctFeatures = features.distinct()

            if (distinctFeatures.isEmpty()) {
                return@withContext "คำค้นหาไม่ถูกต้องหรือกว้างเกินไป"
            }

            val fileMatches = mutableListOf<FileMatch>()

            for (file in files) {
                val fileContent = file.readText()
                
                // Split by double newline first to get paragraphs, fallback to single newline
                val rawPassages = if (fileContent.contains("\n\n")) {
                    fileContent.split("\n\n")
                } else {
                    fileContent.split("\n")
                }
                
                val passages = rawPassages
                    .map { it.trim() }
                    .filter { it.length > 15 } // Filter out very short lines

                if (passages.isEmpty()) continue

                val totalPassages = passages.size
                
                // 1. Calculate document frequency for each feature in this file
                val featureDocFreqs = distinctFeatures.associateWith { feature ->
                    passages.count { it.lowercase().contains(feature) }
                }

                // 2. Compute TF-IDF weights dynamically
                val featureWeights = distinctFeatures.associateWith { feature ->
                    val docFreq = featureDocFreqs[feature] ?: 0
                    val isEnglish = feature.matches(Regex("^[a-zA-Z0-9]+$"))
                    val baseWeight = if (isEnglish) 15.0f else 5.0f
                    
                    if (docFreq > 0) {
                        // IDF = ln((totalPassages + 1) / (docFreq + 0.5))
                        val idf = java.lang.Math.log((totalPassages + 1.0) / (docFreq + 0.5))
                        maxOf(1.0f, baseWeight * idf.toFloat())
                    } else {
                        baseWeight
                    }
                }

                // 3. Score passages using computed TF-IDF weights
                val passageScores = passages.map { passage ->
                    val lowerPara = passage.lowercase()
                    var score = 0.0f
                    var matchCount = 0
                    
                    for (feature in distinctFeatures) {
                        if (lowerPara.contains(feature)) {
                            val weight = featureWeights[feature] ?: 5.0f
                            score += weight
                            
                            // Term frequency weight scaled by IDF weight
                            val count = (lowerPara.length - lowerPara.replace(feature, "").length) / feature.length
                            score += count * (weight * 0.1f)
                            matchCount++
                        }
                    }

                    if (matchCount > 0) {
                        score += (matchCount - 1) * 8.0f
                    }
                    score
                }

                val maxScore = passageScores.maxOrNull() ?: 0.0f
                if (maxScore > 0.0f) {
                    val bestIndex = passageScores.indexOf(maxScore)
                    // Take a contiguous window of 5 paragraphs (2 before and 2 after the best match)
                    val startIndex = maxOf(0, bestIndex - 2)
                    val endIndex = minOf(passages.size - 1, bestIndex + 2)
                    
                    val contiguousText = passages.subList(startIndex, endIndex + 1).joinToString("\n\n")
                    fileMatches.add(FileMatch(file.name.replace(".txt", ""), maxScore, contiguousText))
                }
            }

            if (fileMatches.isEmpty()) {
                return@withContext "ไม่พบข้อมูลที่เกี่ยวข้องโดยตรงในเอกสารคู่มือหรือประกันภัยในเครื่อง"
            }

            // Sort by score descending
            fileMatches.sortByDescending { it.maxScore }

            val sb = StringBuilder()
            sb.append("ผลการค้นหาจากเอกสารในเครื่องของคุณ:\n\n")
            
            // Take top matches and build context
            var currentLength = sb.length
            for (match in fileMatches.take(2)) {
                val formattedText = match.contiguousText
                val addition = "[จากไฟล์: ${match.fileName}]\n$formattedText\n\n"
                if (currentLength + addition.length > 1200) {
                    if (currentLength < 800) {
                        val trimmedAddition = addition.take(1200 - currentLength) + "... [ข้อความถูกตัดให้สั้นลง]"
                        sb.append(trimmedAddition)
                    }
                    break
                }
                sb.append(addition)
                currentLength = sb.length
            }

            sb.toString().trim()
        } catch (e: Exception) {
            Log.e("DocumentParser", "Error querying documents", e)
            "เกิดข้อผิดพลาดในการรันระบบสืบค้นข้อมูล: ${e.message}"
        }
    }

    private data class FileMatch(
        val fileName: String,
        val maxScore: Float,
        val contiguousText: String
    )
}
