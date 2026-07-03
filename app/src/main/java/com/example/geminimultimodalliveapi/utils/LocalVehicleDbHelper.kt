package com.example.geminimultimodalliveapi.utils

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import android.util.Log
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.data.room.AppDatabase
import com.example.geminimultimodalliveapi.data.room.VehicleMemoryEntity

class LocalVehicleDbHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        // .use {
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
        // Managed by Room
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Managed by Room
    }

    private fun getDao() = AppDatabase.getInstance(context, dbPassword.toByteArray(Charsets.UTF_8)).vehicleDao()

    fun saveInfo(category: String, keyName: String, infoValue: String): Boolean {
        return try {
            val cat = category.lowercase().trim()
            val key = keyName.lowercase().trim()
            val value = infoValue.trim()
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

            val dao = getDao()
            val success = if (dao.exists(cat, key)) {
                dao.update(cat, key, value, time) > 0
            } else {
                dao.insert(VehicleMemoryEntity(category = cat, keyName = key, infoValue = value, updatedAt = time))
                true
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
            val dao = getDao()
            val cat = category?.lowercase()?.trim()
            val records = if (cat.isNullOrEmpty()) dao.getAll() else dao.getByCategory(cat)
            
            for (record in records) {
                list.add(
                    mapOf(
                        "category" to record.category,
                        "key_name" to record.keyName,
                        "info_value" to record.infoValue,
                        "updated_at" to record.updatedAt
                    )
                )
            }
            Log.i("LocalVehicleDbHelper", "Queried SQLite: category=$category, found ${list.size} records")
        } catch (e: Exception) {
            Log.e("LocalVehicleDbHelper", "Failed to query info from database", e)
        }
        return list
    }

    fun deleteInfo(category: String, keyName: String?): Boolean {
        return try {
            val cat = category.lowercase().trim()
            val key = keyName?.lowercase()?.trim()
            val deletedRows = getDao().delete(cat, key)
            Log.i("LocalVehicleDbHelper", "Deleted from SQLite: category=$category, key=$keyName, rows=$deletedRows")
            deletedRows > 0
        } catch (e: Exception) {
            Log.e("LocalVehicleDbHelper", "Failed to delete info from database", e)
            false
        }
    }
}
