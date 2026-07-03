package com.example.geminimultimodalliveapi.architecture

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File

enum class TriggerType { USER_CORRECTION, MANUAL_CANCEL, SYSTEM_ERROR }

data class ChatTurn(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

data class IncidentReport(
    val incidentId: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: TriggerType,
    val triggerDetail: String,
    val recentConversation: List<ChatTurn>,
    val contextSnapshot: ContextSnapshot,
    val activeRules: List<DynamicSystemRule>
)

class SituationLogManager(
    private val context: Context,
    private val contextManager: ContextManager,
    private val rulesManager: DynamicRulesManager
) {
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val conversationBuffer = mutableListOf<ChatTurn>()
    private val maxBufferSize = 5
    private val gson = Gson()

    // Common Thai phrases indicating frustration or correction of AI errors
    private val correctionKeywords = listOf(
        "ไม่ใช่", "ตอบผิด", "เข้าใจผิดแล้ว", "พูดอะไรนะ", "กอหญ้าหยุด", "เงียบก่อน", "พูดใหม่", "ไม่ใช่แล้ว", "หยุดพูด", "มั่วแล้ว"
    )

    @Synchronized
    fun logChatTurn(role: String, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return

        conversationBuffer.add(ChatTurn(role, normalizedText))
        if (conversationBuffer.size > maxBufferSize) {
            conversationBuffer.removeAt(0)
        }

        Log.d("SituationLogManager", "Logged chat turn: $role -> $normalizedText")

        // Auto-detect user correction triggers
        if (role.equals("user", ignoreCase = true) && containsCorrectionKeyword(normalizedText)) {
            Log.w("SituationLogManager", "Correction keyword detected in user input: '$normalizedText'")
            triggerIncidentRecording(
                TriggerType.USER_CORRECTION,
                "ผู้ใช้พูดคำทักท้วงคำตอบ: '$normalizedText'"
            )
        }
    }

    fun logManualCancel() {
        triggerIncidentRecording(
            TriggerType.MANUAL_CANCEL,
            "ผู้ใช้ปัดยกเลิกหน้าต่าง/ตัดการเชื่อมต่อแบบแมนนวล (Manual Reset)"
        )
    }

    fun logSystemError(errorDescription: String) {
        triggerIncidentRecording(
            TriggerType.SYSTEM_ERROR,
            "ระบบขัดข้องทางเทคนิค: $errorDescription"
        )
    }

    private fun containsCorrectionKeyword(text: String): Boolean {
        val normalized = text.lowercase().replace("\\s".toRegex(), "")
        return correctionKeywords.any { normalized.contains(it.replace("\\s".toRegex(), "")) }
    }

    @Synchronized
    private fun triggerIncidentRecording(type: TriggerType, reason: String) {
        val snapshot = contextManager.getCurrentSnapshot()
        val allRules = rulesManager.getAllRules()
        
        val report = IncidentReport(
            triggerType = type,
            triggerDetail = reason,
            recentConversation = ArrayList(conversationBuffer),
            contextSnapshot = snapshot,
            activeRules = allRules
        )

        ioScope.launch { saveReportToDisk(report) }
    }

    private fun saveReportToDisk(report: IncidentReport) {
        try {
            val jsonString = gson.toJson(report)
            val logDir = File(context.filesDir, "situational_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, "incident_${report.timestamp}.json")
            logFile.writeText(jsonString)
            
            val logMsg = "[Telemetry] Incident logged: ${report.triggerType} (Saved: files/situational_logs/${logFile.name})"
            Log.w("SituationLogManager", logMsg)
            com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(logMsg)
            
            // Rotate logs: delete files older than 7 days
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            logDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("incident_") && file.name.endsWith(".json")) {
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("SituationLogManager", "Failed to write incident log to disk", e)
        }
    }
}
