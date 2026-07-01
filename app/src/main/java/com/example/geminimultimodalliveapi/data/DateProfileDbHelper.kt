package com.example.geminimultimodalliveapi.data

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

class DateProfileDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "DateProfiles.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "profiles"

        private const val COL_NAME = "profile_name"
        private const val COL_LIKES = "likes"
        private const val COL_DISLIKES = "dislikes"
        private const val COL_PERSONALITY = "personality"
        private const val COL_LAST_UPDATED = "last_updated"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_NAME TEXT PRIMARY KEY,
                $COL_LIKES TEXT,
                $COL_DISLIKES TEXT,
                $COL_PERSONALITY TEXT,
                $COL_LAST_UPDATED INTEGER
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveProfile(profileName: String, likes: List<String>, dislikes: List<String>, personality: List<String>) {
        val db = getWritableDatabase(dbPassword)
        val values = ContentValues().apply {
            put(COL_NAME, profileName)
            put(COL_LIKES, likes.joinToString("||"))
            put(COL_DISLIKES, dislikes.joinToString("||"))
            put(COL_PERSONALITY, personality.joinToString("||"))
            put(COL_LAST_UPDATED, System.currentTimeMillis())
        }
        db.replace(TABLE_NAME, null, values)
    }

    fun getProfile(profileName: String): DateInsight? {
        val db = getReadableDatabase(dbPassword)
        val cursor = db.query(
            TABLE_NAME,
            null,
            "$COL_NAME = ?",
            arrayOf(profileName),
            null,
            null,
            null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val likesStr = c.getString(c.getColumnIndexOrThrow(COL_LIKES)) ?: ""
                val dislikesStr = c.getString(c.getColumnIndexOrThrow(COL_DISLIKES)) ?: ""
                val personalityStr = c.getString(c.getColumnIndexOrThrow(COL_PERSONALITY)) ?: ""
                
                val likesList = if (likesStr.isEmpty()) emptyList() else likesStr.split("||")
                val dislikesList = if (dislikesStr.isEmpty()) emptyList() else dislikesStr.split("||")
                val personalityList = if (personalityStr.isEmpty()) emptyList() else personalityStr.split("||")
                
                return DateInsight(
                    likes = likesList,
                    dislikes = dislikesList,
                    personality = personalityList,
                    tip = ""
                )
            }
        }
        return null
    }

    fun getAllProfileNames(): List<String> {
        val names = mutableListOf<String>()
        val db = getReadableDatabase(dbPassword)
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COL_NAME),
            null,
            null,
            null,
            null,
            "$COL_LAST_UPDATED DESC"
        )
        cursor?.use { c ->
            val nameIndex = c.getColumnIndexOrThrow(COL_NAME)
            while (c.moveToNext()) {
                names.add(c.getString(nameIndex))
            }
        }
        return names
    }

    fun deleteProfile(profileName: String) {
        val db = getWritableDatabase(dbPassword)
        db.delete(TABLE_NAME, "$COL_NAME = ?", arrayOf(profileName))
    }
}
