package com.example.geminimultimodalliveapi.memory

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import android.util.Log
import com.example.geminimultimodalliveapi.data.AppPreferences

class MemoryDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "AssistantMemory.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "memories"

        private const val COL_ID = "id"
        private const val COL_CONTENT = "content"
        private const val COL_IS_PINNED = "is_pinned"
        private const val COL_BASE_IMPORTANCE = "base_importance"
        private const val COL_ACCESS_COUNT = "access_count"
        private const val COL_LAST_ACCESSED_TIME = "last_accessed_time"
        private const val COL_CATEGORY = "category"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY,
                $COL_CONTENT TEXT,
                $COL_IS_PINNED INTEGER,
                $COL_BASE_IMPORTANCE INTEGER,
                $COL_ACCESS_COUNT INTEGER,
                $COL_LAST_ACCESSED_TIME INTEGER,
                $COL_CATEGORY TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertOrUpdateMemory(entry: MemoryEntry) {
        val db = getWritableDatabase(dbPassword)
        val values = ContentValues().apply {
            put(COL_ID, entry.id)
            put(COL_CONTENT, entry.content)
            put(COL_IS_PINNED, if (entry.isPinned) 1 else 0)
            put(COL_BASE_IMPORTANCE, entry.baseImportance)
            put(COL_ACCESS_COUNT, entry.accessCount)
            put(COL_LAST_ACCESSED_TIME, entry.lastAccessedTime)
            put(COL_CATEGORY, entry.category)
        }
        db.replace(TABLE_NAME, null, values)
    }

    fun deleteMemory(id: String) {
        val db = getWritableDatabase(dbPassword)
        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id))
    }

    fun getMemoryList(): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        val db = getReadableDatabase(dbPassword)
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)
        cursor?.use { c ->
            val idIndex = c.getColumnIndex(COL_ID)
            val contentIndex = c.getColumnIndex(COL_CONTENT)
            val pinnedIndex = c.getColumnIndex(COL_IS_PINNED)
            val impIndex = c.getColumnIndex(COL_BASE_IMPORTANCE)
            val accessIndex = c.getColumnIndex(COL_ACCESS_COUNT)
            val timeIndex = c.getColumnIndex(COL_LAST_ACCESSED_TIME)
            val catIndex = c.getColumnIndex(COL_CATEGORY)

            while (c.moveToNext()) {
                val id = c.getString(idIndex)
                val content = c.getString(contentIndex)
                val isPinned = c.getInt(pinnedIndex) == 1
                val baseImp = c.getInt(impIndex)
                val accessCount = c.getInt(accessIndex)
                val lastTime = c.getLong(timeIndex)
                val category = c.getString(catIndex)

                list.add(MemoryEntry(id, content, isPinned, baseImp, accessCount, lastTime, category))
            }
        }
        return list
    }

    fun recordAccess(id: String) {
        val db = getWritableDatabase(dbPassword)
        val query = "SELECT $COL_ACCESS_COUNT FROM $TABLE_NAME WHERE $COL_ID = ?"
        val cursor = db.rawQuery(query, arrayOf(id))
        var currentCount = 0
        cursor.use { c ->
            if (c.moveToFirst()) {
                currentCount = c.getInt(0)
            }
        }
        
        val values = ContentValues().apply {
            put(COL_ACCESS_COUNT, currentCount + 1)
            put(COL_LAST_ACCESSED_TIME, System.currentTimeMillis())
        }
        db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id))
        Log.i("MemoryDbHelper", "Recorded access for memory: $id (New Count: ${currentCount + 1})")
    }

    fun decayMemories(decayIntervalMs: Long = 24 * 60 * 60 * 1000L) {
        val db = getWritableDatabase(dbPassword)
        val now = System.currentTimeMillis()
        val query = "SELECT * FROM $TABLE_NAME WHERE $COL_IS_PINNED = 0"
        val cursor = db.rawQuery(query, null)
        val memoriesToUpdate = mutableListOf<MemoryEntry>()
        val memoriesToDelete = mutableListOf<String>()

        cursor.use { c ->
            val idIdx = c.getColumnIndex(COL_ID)
            val contentIdx = c.getColumnIndex(COL_CONTENT)
            val impIdx = c.getColumnIndex(COL_BASE_IMPORTANCE)
            val accessIdx = c.getColumnIndex(COL_ACCESS_COUNT)
            val timeIdx = c.getColumnIndex(COL_LAST_ACCESSED_TIME)
            val catIdx = c.getColumnIndex(COL_CATEGORY)

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val content = c.getString(contentIdx)
                val baseImp = c.getInt(impIdx)
                val accessCount = c.getInt(accessIdx)
                val lastTime = c.getLong(timeIdx)
                val category = c.getString(catIdx)

                if (now - lastTime > decayIntervalMs) {
                    val newImp = baseImp - 1
                    if (newImp <= 0) {
                        memoriesToDelete.add(id)
                    } else {
                        memoriesToUpdate.add(MemoryEntry(id, content, false, newImp, accessCount, lastTime, category))
                    }
                }
            }
        }

        db.beginTransaction()
        try {
            for (id in memoriesToDelete) {
                db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id))
                Log.i("MemoryDbHelper", "Decayed & Forgotten memory (deleted): $id")
            }
            for (entry in memoriesToUpdate) {
                val values = ContentValues().apply {
                    put(COL_BASE_IMPORTANCE, entry.baseImportance)
                }
                db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(entry.id))
                Log.i("MemoryDbHelper", "Decayed memory importance for: ${entry.id} to ${entry.baseImportance}")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun evictLowestUtilityMemories(maxBudget: Int = 50) {
        val db = getWritableDatabase(dbPassword)
        val countQuery = "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COL_IS_PINNED = 0"
        val cursor = db.rawQuery(countQuery, null)
        var unpinnedCount = 0
        cursor.use { c ->
            if (c.moveToFirst()) unpinnedCount = c.getInt(0)
        }

        if (unpinnedCount <= maxBudget) return

        val excess = unpinnedCount - maxBudget
        Log.i("MemoryDbHelper", "Unpinned memory count ($unpinnedCount) exceeds budget ($maxBudget). Evicting $excess items.")

        db.execSQL("""
            DELETE FROM $TABLE_NAME WHERE $COL_ID IN (
                SELECT $COL_ID FROM $TABLE_NAME WHERE $COL_IS_PINNED = 0 
                ORDER BY ($COL_BASE_IMPORTANCE + ($COL_ACCESS_COUNT * 2)) ASC 
                LIMIT $excess
            )
        """.trimIndent())
        Log.i("MemoryDbHelper", "Evicted $excess low utility memories using SQL")
    }

    fun searchMemories(searchQuery: String): List<MemoryEntry> {
        val list = mutableListOf<MemoryEntry>()
        val db = getReadableDatabase(dbPassword)
        val query = "SELECT * FROM $TABLE_NAME WHERE $COL_CONTENT LIKE ?"
        val cursor = db.rawQuery(query, arrayOf("%$searchQuery%"))
        cursor?.use { c ->
            val idIdx = c.getColumnIndex(COL_ID)
            val contentIdx = c.getColumnIndex(COL_CONTENT)
            val pinnedIdx = c.getColumnIndex(COL_IS_PINNED)
            val impIdx = c.getColumnIndex(COL_BASE_IMPORTANCE)
            val accessIdx = c.getColumnIndex(COL_ACCESS_COUNT)
            val timeIdx = c.getColumnIndex(COL_LAST_ACCESSED_TIME)
            val catIdx = c.getColumnIndex(COL_CATEGORY)

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val content = c.getString(contentIdx)
                val isPinned = c.getInt(pinnedIdx) == 1
                val baseImp = c.getInt(impIdx)
                val accessCount = c.getInt(accessIdx)
                val lastTime = c.getLong(timeIdx)
                val category = c.getString(catIdx)

                list.add(MemoryEntry(id, content, isPinned, baseImp, accessCount, lastTime, category))
            }
        }
        return list
    }
}
