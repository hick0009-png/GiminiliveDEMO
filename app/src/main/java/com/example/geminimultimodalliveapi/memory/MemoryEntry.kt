package com.example.geminimultimodalliveapi.memory

data class MemoryEntry(
    val id: String,
    val content: String,
    val isPinned: Boolean = false,
    var baseImportance: Int = 3, // Range 1 to 5
    var accessCount: Int = 1,
    var lastAccessedTime: Long = System.currentTimeMillis(),
    val category: String = "general"
) {
    /**
     * Utility Score formula: baseImportance + (accessCount * 2)
     * This determines relevance during eviction or when selecting which memories to inject.
     */
    fun getUtilityScore(): Int {
        return baseImportance + (accessCount * 2)
    }
}
