package com.example.geminimultimodalliveapi.memory

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import android.util.Log
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.data.room.AppDatabase
import com.example.geminimultimodalliveapi.data.room.MemoryEntryEntity

class MemoryDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "AssistantMemory.db"
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Ignored, table is managed by Room in app_database.db
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Ignored, managed by Room
    }

    private fun getDao() = AppDatabase.getInstance(context, dbPassword.toByteArray(Charsets.UTF_8)).memoryDao()

    fun insertOrUpdateMemory(entry: MemoryEntry) {
        getDao().insertOrUpdate(
            MemoryEntryEntity(
                id = entry.id,
                content = entry.content,
                isPinned = entry.isPinned,
                baseImportance = entry.baseImportance,
                accessCount = entry.accessCount,
                lastAccessedTime = entry.lastAccessedTime,
                category = entry.category
            )
        )
    }

    fun deleteMemory(id: String) {
        getDao().delete(id)
    }

    fun getMemoryList(): List<MemoryEntry> {
        return getDao().getAll().map {
            MemoryEntry(
                id = it.id,
                content = it.content,
                isPinned = it.isPinned,
                baseImportance = it.baseImportance,
                accessCount = it.accessCount,
                lastAccessedTime = it.lastAccessedTime,
                category = it.category
            )
        }
    }

    fun recordAccess(id: String) {
        getDao().recordAccess(id, System.currentTimeMillis())
        Log.i("MemoryDbHelper", "Recorded access for memory: $id")
    }

    fun decayMemories(decayIntervalMs: Long = 24 * 60 * 60 * 1000L) {
        val dao = getDao()
        val now = System.currentTimeMillis()
        val list = dao.getAll().filter { !it.isPinned }

        for (entry in list) {
            if (now - entry.lastAccessedTime > decayIntervalMs) {
                val newImp = entry.baseImportance - 1
                if (newImp <= 0) {
                    dao.delete(entry.id)
                    Log.i("MemoryDbHelper", "Decayed & Forgotten memory (deleted): ${entry.id}")
                } else {
                    dao.insertOrUpdate(entry.copy(baseImportance = newImp))
                    Log.i("MemoryDbHelper", "Decayed memory importance for: ${entry.id} to $newImp")
                }
            }
        }
    }

    fun evictLowestUtilityMemories(maxBudget: Int = 50) {
        val dao = getDao()
        val unpinnedCount = dao.getUnpinnedCount()
        if (unpinnedCount <= maxBudget) return

        val excess = unpinnedCount - maxBudget
        Log.i("MemoryDbHelper", "Unpinned memory count ($unpinnedCount) exceeds budget ($maxBudget). Evicting $excess items.")

        val unpinned = dao.getAll().filter { !it.isPinned }
            .sortedBy { it.baseImportance + (it.accessCount * 2) }

        for (i in 0 until excess.coerceAtMost(unpinned.size)) {
            dao.delete(unpinned[i].id)
        }
        Log.i("MemoryDbHelper", "Evicted $excess low utility memories using SQL")
    }

    fun searchMemories(searchQuery: String): List<MemoryEntry> {
        val safeQuery = searchQuery.replace("%", "\\%").replace("_", "\\_")
        return getDao().search(safeQuery).map {
            MemoryEntry(
                id = it.id,
                content = it.content,
                isPinned = it.isPinned,
                baseImportance = it.baseImportance,
                accessCount = it.accessCount,
                lastAccessedTime = it.lastAccessedTime,
                category = it.category
            )
        }
    }

    fun updateMemoryPinState(id: String, isPinned: Boolean) {
        getDao().updatePinned(id, isPinned)
    }

    fun insertOrUpdateWithEviction(entry: MemoryEntry, limit: Int = 50) {
        val dao = getDao()
        dao.insertOrUpdate(
            MemoryEntryEntity(
                id = entry.id,
                content = entry.content,
                isPinned = entry.isPinned,
                baseImportance = entry.baseImportance,
                accessCount = entry.accessCount,
                lastAccessedTime = entry.lastAccessedTime,
                category = entry.category
            )
        )

        val unpinnedCount = dao.getUnpinnedCount()
        if (unpinnedCount > limit) {
            val excess = unpinnedCount - limit
            val unpinned = dao.getAll().filter { !it.isPinned }
                .sortedBy { it.baseImportance + (it.accessCount * 2) }

            for (i in 0 until excess.coerceAtMost(unpinned.size)) {
                dao.delete(unpinned[i].id)
            }
            Log.i("MemoryDbHelper", "Evicted $excess low utility memories inside transaction")
        }
    }
}
