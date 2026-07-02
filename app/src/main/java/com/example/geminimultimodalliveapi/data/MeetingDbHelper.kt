package com.example.geminimultimodalliveapi.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File
// removed unused serialization imports

class MeetingDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "AssistantMeetings.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "meetings"

        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_DURATION = "duration"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_SUMMARY = "summary"
        private const val COL_TRANSCRIPT_JSON = "transcript_json"

        @Volatile
        private var INSTANCE: MeetingDbHelper? = null

        fun getInstance(context: Context): MeetingDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MeetingDbHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT,
                $COL_TIMESTAMP INTEGER,
                $COL_DURATION INTEGER,
                $COL_FILE_PATH TEXT,
                $COL_SUMMARY TEXT,
                $COL_TRANSCRIPT_JSON TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertMeeting(meeting: Meeting) {
        try {
            val db = getWritableDatabase(dbPassword)
            val values = ContentValues().apply {
                put(COL_ID, meeting.id)
                put(COL_TITLE, meeting.title)
                put(COL_TIMESTAMP, meeting.timestamp)
                put(COL_DURATION, meeting.duration)
                put(COL_FILE_PATH, meeting.filePath)
                put(COL_SUMMARY, meeting.summary)
                put(COL_TRANSCRIPT_JSON, meeting.transcriptJson)
            }
            db.replace(TABLE_NAME, null, values)
            Log.i("MeetingDbHelper", "Inserted/Updated meeting: ${meeting.id}")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error inserting meeting", e)
        }
    }

    fun deleteMeeting(id: String) {
        try {
            val db = getWritableDatabase(dbPassword)
            // First, find file path and delete physical file
            val cursor = db.query(TABLE_NAME, arrayOf(COL_FILE_PATH), "$COL_ID = ?", arrayOf(id), null, null, null)
            var filePath: String? = null
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    filePath = c.getString(0)
                }
            }
            if (filePath != null) {
                val file = File(filePath!!)
                if (file.exists()) {
                    if (file.delete()) {
                        Log.i("MeetingDbHelper", "Deleted audio file: $filePath")
                    } else {
                        Log.w("MeetingDbHelper", "Failed to delete audio file: $filePath")
                    }
                }
            }
            db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id))
            Log.i("MeetingDbHelper", "Deleted meeting record: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error deleting meeting", e)
        }
    }

    fun getMeetingList(): List<Meeting> {
        val list = mutableListOf<Meeting>()
        try {
            val db = getReadableDatabase(dbPassword)
            val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COL_TIMESTAMP DESC")
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(COL_ID)
                val titleIdx = c.getColumnIndex(COL_TITLE)
                val timeIdx = c.getColumnIndex(COL_TIMESTAMP)
                val durIdx = c.getColumnIndex(COL_DURATION)
                val pathIdx = c.getColumnIndex(COL_FILE_PATH)
                val sumIdx = c.getColumnIndex(COL_SUMMARY)
                val transIdx = c.getColumnIndex(COL_TRANSCRIPT_JSON)

                while (c.moveToNext()) {
                    val id = c.getString(idIdx)
                    val title = c.getString(titleIdx)
                    val timestamp = c.getLong(timeIdx)
                    val duration = c.getLong(durIdx)
                    val filePath = c.getString(pathIdx)
                    val summary = c.getString(sumIdx)
                    val transcript = c.getString(transIdx)

                    list.add(Meeting(id, title, timestamp, duration, filePath, summary, transcript))
                }
            }
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error reading meetings list", e)
        }
        return list
    }

    fun updateMeetingTranscriptAndSummary(id: String, transcriptJson: String, summary: String) {
        try {
            val db = getWritableDatabase(dbPassword)
            val values = ContentValues().apply {
                put(COL_TRANSCRIPT_JSON, transcriptJson)
                put(COL_SUMMARY, summary)
            }
            db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id))
            Log.i("MeetingDbHelper", "Updated transcript and summary for meeting: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating transcript/summary", e)
        }
    }

    fun updateMeetingTranscript(id: String, transcriptJson: String) {
        try {
            val db = getWritableDatabase(dbPassword)
            val values = ContentValues().apply {
                put(COL_TRANSCRIPT_JSON, transcriptJson)
            }
            db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(id))
            Log.i("MeetingDbHelper", "Updated transcript for meeting: $id")
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating transcript", e)
        }
    }


    fun updateSpeakerName(meetingId: String, oldName: String, newName: String): Boolean {
        var success = false
        try {
            val db = getWritableDatabase(dbPassword)
            db.beginTransaction()
            try {
                val cursor = db.query(TABLE_NAME, arrayOf(COL_TRANSCRIPT_JSON), "$COL_ID = ?", arrayOf(meetingId), null, null, null)
                var jsonStr: String? = null
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        jsonStr = c.getString(0)
                    }
                }

                if (!jsonStr.isNullOrEmpty()) {
                    val array = org.json.JSONArray(jsonStr!!)
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
                        val values = ContentValues().apply {
                            put(COL_TRANSCRIPT_JSON, newJsonStr)
                        }
                        db.update(TABLE_NAME, values, "$COL_ID = ?", arrayOf(meetingId))
                        Log.i("MeetingDbHelper", "Updated speaker name from $oldName to $newName in meeting $meetingId")
                        success = true
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e("MeetingDbHelper", "Error updating speaker name", e)
        }
        return success
    }
}
