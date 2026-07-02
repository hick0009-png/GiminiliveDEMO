package com.example.geminimultimodalliveapi.utils

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import android.util.Log
import com.example.geminimultimodalliveapi.data.AppPreferences

class LocalVehicleDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    private val dbPassword = AppPreferences.getInstance(context).getOrCreateDatabasePassword()

    companion object {
        private const val DATABASE_NAME = "vehicle_memory.db"
        private const val DATABASE_VERSION = 1
        
        const val TABLE_NAME = "vehicle_memory"
        const val COL_ID = "id"
        const val COL_CATEGORY = "category"
        const val COL_KEY_NAME = "key_name"
        const val COL_INFO_VALUE = "info_value"
        const val COL_UPDATED_AT = "updated_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CATEGORY TEXT,
                $COL_KEY_NAME TEXT,
                $COL_INFO_VALUE TEXT,
                $COL_UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
        Log.i("LocalVehicleDbHelper", "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveInfo(category: String, keyName: String, infoValue: String): Boolean {
        return try {
            val db = getWritableDatabase(dbPassword)
            val contentValues = ContentValues().apply {
                put(COL_CATEGORY, category.lowercase().trim())
                put(COL_KEY_NAME, keyName.lowercase().trim())
                put(COL_INFO_VALUE, infoValue.trim())
            }

            // Check if this category and key combination already exists
            val exists = db.query(
                TABLE_NAME,
                arrayOf(COL_ID),
                "$COL_CATEGORY = ? AND $COL_KEY_NAME = ?",
                arrayOf(category.lowercase().trim(), keyName.lowercase().trim()),
                null, null, null
            ).use { it.moveToFirst() }

            val success = if (exists) {
                db.update(
                    TABLE_NAME,
                    contentValues,
                    "$COL_CATEGORY = ? AND $COL_KEY_NAME = ?",
                    arrayOf(category.lowercase().trim(), keyName.lowercase().trim())
                ) > 0
            } else {
                db.insert(TABLE_NAME, null, contentValues) != -1L
            }
            Log.i("LocalVehicleDbHelper", "Saved to SQLite: category=$category, key=$keyName, val=$infoValue, success=$success")
            success
        } catch (e: Exception) {
            Log.e("LocalVehicleDbHelper", "Failed to save info to database", e)
            false
        }
    }

    fun queryInfo(category: String?): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val db = getReadableDatabase(dbPassword)
            val selection = if (category.isNullOrEmpty()) null else "$COL_CATEGORY = ?"
            val selectionArgs = if (category.isNullOrEmpty()) null else arrayOf(category.lowercase().trim())
            
            db.query(
                TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null, null,
                "$COL_UPDATED_AT DESC"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val map = mapOf(
                            "category" to cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
                            "key_name" to cursor.getString(cursor.getColumnIndexOrThrow(COL_KEY_NAME)),
                            "info_value" to cursor.getString(cursor.getColumnIndexOrThrow(COL_INFO_VALUE)),
                            "updated_at" to cursor.getString(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))
                        )
                        list.add(map)
                    } while (cursor.moveToNext())
                }
            }
            Log.i("LocalVehicleDbHelper", "Queried SQLite: category=$category, found ${list.size} records")
        } catch (e: Exception) {
            Log.e("LocalVehicleDbHelper", "Failed to query info from database", e)
        }
        return list
    }

    fun deleteInfo(category: String, keyName: String?): Boolean {
        return try {
            val db = getWritableDatabase(dbPassword)
            val whereClause = if (keyName.isNullOrEmpty()) {
                "$COL_CATEGORY = ?"
            } else {
                "$COL_CATEGORY = ? AND $COL_KEY_NAME = ?"
            }
            val whereArgs = if (keyName.isNullOrEmpty()) {
                arrayOf(category.lowercase().trim())
            } else {
                arrayOf(category.lowercase().trim(), keyName.lowercase().trim())
            }
            val deletedRows = db.delete(TABLE_NAME, whereClause, whereArgs)
            Log.i("LocalVehicleDbHelper", "Deleted from SQLite: category=$category, key=$keyName, rows=$deletedRows")
            deletedRows > 0
        } catch (e: Exception) {
            Log.e("LocalVehicleDbHelper", "Failed to delete info from database", e)
            false
        }
    }
}
