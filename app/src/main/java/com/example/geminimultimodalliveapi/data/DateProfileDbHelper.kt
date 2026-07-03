package com.example.geminimultimodalliveapi.data

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import com.example.geminimultimodalliveapi.data.room.AppDatabase
import com.example.geminimultimodalliveapi.data.room.DateProfileEntity

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
        // Managed by Room
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Managed by Room
    }

    private fun getDao() = AppDatabase.getInstance(context, dbPassword.toByteArray(Charsets.UTF_8)).dateProfileDao()

    fun saveProfile(profileName: String, likes: List<String>, dislikes: List<String>, personality: List<String>) {
        getDao().save(
            DateProfileEntity(
                profileName = profileName,
                likes = likes.joinToString("||"),
                dislikes = dislikes.joinToString("||"),
                personality = personality.joinToString("||"),
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    fun getProfile(profileName: String): DateInsight? {
        val p = getDao().getByName(profileName) ?: return null
        val likesStr = p.likes
        val dislikesStr = p.dislikes
        val personalityStr = p.personality
        
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

    fun getAllProfileNames(): List<String> {
        return getDao().getAllNames()
    }

    fun deleteProfile(profileName: String) {
        getDao().delete(profileName)
    }
}
