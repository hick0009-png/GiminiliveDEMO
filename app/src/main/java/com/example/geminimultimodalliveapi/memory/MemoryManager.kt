package com.example.geminimultimodalliveapi.memory

import android.content.Context
import android.util.Log

class MemoryManager(context: Context) {
    private val dbHelper = MemoryDbHelper(context)

    companion object {
        private const val MAX_BUDGET_LIMIT = 50
    }

    /**
     * Add or update a fact in the database.
     * Automatically triggers the eviction policy to stay within the budget.
     */
    fun addFact(
        id: String,
        content: String,
        isPinned: Boolean = false,
        baseImportance: Int = 3,
        category: String = "fact"
    ) {
        val entry = MemoryEntry(
            id = id,
            content = content,
            isPinned = isPinned,
            baseImportance = baseImportance,
            accessCount = 1,
            lastAccessedTime = System.currentTimeMillis(),
            category = category
        )
        dbHelper.insertOrUpdateWithEviction(entry, MAX_BUDGET_LIMIT)
        Log.d("MemoryManager", "Added fact: $id ($content), Pinned: $isPinned")
    }

    /**
     * Record that a specific memory was accessed or referenced in conversation.
     * This increases its utility score and prevents it from being decayed or evicted.
     */
    fun recordAccess(id: String) {
        dbHelper.recordAccess(id)
    }

    /**
     * Delete a memory entry by ID.
     */
    fun deleteMemory(id: String) {
        dbHelper.deleteMemory(id)
        Log.d("MemoryManager", "Deleted memory: $id")
    }

    /**
     * Get all memory entries from the database.
     */
    fun getAllMemories(): List<MemoryEntry> {
        return dbHelper.getMemoryList()
    }

    /**
     * Update the pinned state of a specific memory.
     */
    fun updateMemoryPin(id: String, isPinned: Boolean) {
        dbHelper.updateMemoryPinState(id, isPinned)
        Log.d("MemoryManager", "Updated pin state of memory $id to $isPinned")
    }

    /**
     * Triggers the temporal decay policy.
     * Reduces the base importance of memories that have not been accessed recently.
     */
    fun decayAll() {
        // Run daily check (24 hour decay interval)
        dbHelper.decayMemories(24 * 60 * 60 * 1000L)
    }

    /**
     * Search memory entries containing search query keywords using SQL LIKE query.
     */
    fun searchMemories(query: String): List<MemoryEntry> {
        return dbHelper.searchMemories(query)
    }

    /**
     * Get the formatted system prompt context representing the current active memories.
     * Groups memories hierarchically: Core/Pinned, Active Session, and General.
     * Limits unpinned general memories to 10 to optimize token usage.
     * Optimized O(N) single-pass categorization.
     */
    fun getFormattedContextPrompt(): String {
        val allMemories = dbHelper.getMemoryList()
        if (allMemories.isEmpty()) {
            return ""
        }

        val coreFacts = ArrayList<MemoryEntry>()
        val activeSessionFacts = ArrayList<MemoryEntry>()
        val generalFactsRaw = ArrayList<MemoryEntry>()

        val now = System.currentTimeMillis()
        // Single-pass categorization to avoid nested list lookup overhead
        for (m in allMemories) {
            val categoryLower = m.category.lowercase()
            if (m.isPinned || categoryLower in listOf("core", "vehicle", "health")) {
                coreFacts.add(m)
            } else if (categoryLower in listOf("session", "temporary") || (now - m.lastAccessedTime < 2 * 60 * 60 * 1000L)) {
                activeSessionFacts.add(m)
            } else {
                generalFactsRaw.add(m)
            }
        }

        // Sort and limit general facts
        val generalFacts = generalFactsRaw.sortedByDescending { it.getUtilityScore() }.take(10)

        val sb = StringBuilder()
        sb.append("\n=== SYSTEM MEMORY CONTEXT ===\n")
        
        if (coreFacts.isNotEmpty()) {
            sb.append("[ความจำสำคัญถาวร (Core/Vehicle/Health Facts)]:\n")
            for (p in coreFacts) {
                sb.append("- ${p.content} (หมวดหมู่: ${p.category})\n")
            }
            sb.append("\n")
        }

        if (activeSessionFacts.isNotEmpty()) {
            sb.append("[ความจำชั่วคราวในเซสชันนี้ (Active Session/Recent Facts)]:\n")
            for (s in activeSessionFacts) {
                sb.append("- ${s.content}\n")
            }
            sb.append("\n")
        }

        if (generalFacts.isNotEmpty()) {
            sb.append("[ประวัติและความจำทั่วไปที่เกี่ยวข้อง (General/Daily Journal)]:\n")
            for (up in generalFacts) {
                sb.append("- ${up.content}\n")
            }
        }
        sb.append("=============================\n")
        return sb.toString()
    }
}
