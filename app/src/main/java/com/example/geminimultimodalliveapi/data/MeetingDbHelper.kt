package com.example.geminimultimodalliveapi.data

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import com.example.geminimultimodalliveapi.data.room.AppDatabase
import com.example.geminimultimodalliveapi.data.room.MeetingEntity
import java.io.File

class MeetingDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "AssistantMeetings.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: MeetingDbHelper? = null

        fun getInstance(context: Context): MeetingDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MeetingDbHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Managed by Room
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Managed by Room
    }

    private fun getDao() = AppDatabase.getInstance(context, dbPassword.toByteArray(Charsets.UTF_8)).meetingDao()

    fun insertMeeting(meeting: Meeting) {
        try {
            getDao().insert(
                MeetingEntity(
                    id = meeting.id,
                    title = meeting.title,
                    timestamp = meeting.timestamp,
                    duration = meeting.duration,
                    filePath = meeting.filePath,
                    summary = meeting.summary,
                    transcriptJson = meeting.transcriptJson
                )
            )
            Log.i("MeetingDbHelper", "Inserted/Updated meeting: ${meeting.id}")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error inserting meeting", e)
        }
    }

    fun deleteMeeting(id: String) {
        try {
            val dao = getDao()
            val filePath = dao.getFilePath(id)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    if (file.delete()) {
                        Log.i("MeetingDbHelper", "Deleted audio file: $filePath")
                    } else {
                        Log.w("MeetingDbHelper", "Failed to delete audio file: $filePath")
                    }
                }
            }
            dao.delete(id)
            Log.i("MeetingDbHelper", "Deleted meeting record: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error deleting meeting", e)
        }
    }

    fun getMeetingList(): List<Meeting> {
        val list = mutableListOf<Meeting>()
        try {
            val records = getDao().getAll()
            for (r in records) {
                list.add(
                    Meeting(
                        id = r.id,
                        title = r.title,
                        timestamp = r.timestamp,
                        duration = r.duration,
                        filePath = r.filePath,
                        summary = r.summary,
                        transcriptJson = r.transcriptJson
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error getting meeting list", e)
        }
        return list
    }

    fun getMeetingById(id: String): Meeting? {
        try {
            val r = getDao().getById(id) ?: return null
            return Meeting(
                id = r.id,
                title = r.title,
                timestamp = r.timestamp,
                duration = r.duration,
                filePath = r.filePath,
                summary = r.summary,
                transcriptJson = r.transcriptJson
            )
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error getting meeting by id: $id", e)
            return null
        }
    }

    fun updateMeetingTranscriptAndSummary(id: String, transcriptJson: String, summary: String) {
        try {
            getDao().updateTranscriptAndSummary(id, transcriptJson, summary)
            Log.i("MeetingDbHelper", "Updated transcript and summary for meeting: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating transcript/summary", e)
        }
    }

    fun updateMeetingTranscript(id: String, transcriptJson: String) {
        try {
            getDao().updateTranscript(id, transcriptJson)
            Log.i("MeetingDbHelper", "Updated transcript for meeting: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating transcript", e)
        }
    }

    fun updateSpeakerName(meetingId: String, oldName: String, newName: String): Boolean {
        // beginTransaction()
        // setTransactionSuccessful()
        // endTransaction()
        val dao = getDao()
        return try {
            val meeting = dao.getById(meetingId) ?: return false
            val currentJson = meeting.transcriptJson ?: ""
            if (currentJson.isNotEmpty()) {
                val array = org.json.JSONArray(currentJson)
                var updated = false
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val speaker = obj.optString("speaker", "")
                    if (speaker == oldName) {
                        obj.put("speaker", newName)
                        updated = true
                    }
                }
                if (updated) {
                    val newJsonStr = array.toString()
                    dao.updateTranscript(meetingId, newJsonStr)
                    Log.i("MeetingDbHelper", "Updated speaker name from $oldName to $newName in meeting $meetingId")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating speaker name", e)
            false
        }
    }
}
