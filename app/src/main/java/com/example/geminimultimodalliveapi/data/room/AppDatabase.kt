package com.example.geminimultimodalliveapi.data.room

import android.content.Context
import androidx.room.*
import net.sqlcipher.database.SupportFactory

// --- Entities ---

@Entity(tableName = "memories")
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val content: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "base_importance") val baseImportance: Int,
    @ColumnInfo(name = "access_count") val accessCount: Int,
    @ColumnInfo(name = "last_accessed_time") val lastAccessedTime: Long,
    val category: String
)

@Entity(tableName = "vehicle_memory")
data class VehicleMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    @ColumnInfo(name = "key_name") val keyName: String,
    @ColumnInfo(name = "info_value") val infoValue: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Entity(tableName = "profiles")
data class DateProfileEntity(
    @PrimaryKey @ColumnInfo(name = "profile_name") val profileName: String,
    val likes: String,
    val dislikes: String,
    val personality: String,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val timestamp: Long,
    val duration: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    val summary: String?,
    @ColumnInfo(name = "transcript_json") val transcriptJson: String?
)

// --- DAOs ---

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(memory: MemoryEntryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    fun delete(id: String)

    @Query("SELECT * FROM memories ORDER BY last_accessed_time DESC")
    fun getAll(): List<MemoryEntryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    fun search(query: String): List<MemoryEntryEntity>

    @Query("UPDATE memories SET access_count = access_count + 1, last_accessed_time = :time WHERE id = :id")
    fun recordAccess(id: String, time: Long)

    @Query("UPDATE memories SET is_pinned = :isPinned WHERE id = :id")
    fun updatePinned(id: String, isPinned: Boolean)

    @Query("SELECT COUNT(*) FROM memories WHERE is_pinned = 0")
    fun getUnpinnedCount(): Int

    @Query("SELECT * FROM memories WHERE is_pinned = 0 ORDER BY last_accessed_time ASC, base_importance ASC LIMIT 1")
    fun getOldestUnpinned(): MemoryEntryEntity?
}

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vehicle: VehicleMemoryEntity)

    @Query("UPDATE vehicle_memory SET info_value = :infoValue, updated_at = :updatedAt WHERE category = :category AND key_name = :keyName")
    fun update(category: String, keyName: String, infoValue: String, updatedAt: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM vehicle_memory WHERE category = :category AND key_name = :keyName)")
    fun exists(category: String, keyName: String): Boolean

    @Query("SELECT * FROM vehicle_memory WHERE category = :category ORDER BY updated_at DESC")
    fun getByCategory(category: String): List<VehicleMemoryEntity>

    @Query("SELECT * FROM vehicle_memory ORDER BY updated_at DESC")
    fun getAll(): List<VehicleMemoryEntity>

    @Query("DELETE FROM vehicle_memory WHERE category = :category AND (:keyName IS NULL OR key_name = :keyName)")
    fun delete(category: String, keyName: String?): Int
}

@Dao
interface DateProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(profile: DateProfileEntity)

    @Query("SELECT * FROM profiles WHERE profile_name = :name")
    fun getByName(name: String): DateProfileEntity?

    @Query("SELECT profile_name FROM profiles ORDER BY last_updated DESC")
    fun getAllNames(): List<String>

    @Query("DELETE FROM profiles WHERE profile_name = :name")
    fun delete(name: String)
}

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(meeting: MeetingEntity)

    @Query("SELECT file_path FROM meetings WHERE id = :id")
    fun getFilePath(id: String): String?

    @Query("DELETE FROM meetings WHERE id = :id")
    fun delete(id: String)

    @Query("SELECT * FROM meetings ORDER BY timestamp DESC")
    fun getAll(): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getById(id: String): MeetingEntity?

    @Query("UPDATE meetings SET transcript_json = :transcriptJson, summary = :summary WHERE id = :id")
    fun updateTranscriptAndSummary(id: String, transcriptJson: String, summary: String)

    @Query("UPDATE meetings SET transcript_json = :transcriptJson WHERE id = :id")
    fun updateTranscript(id: String, transcriptJson: String)
}

// --- Database ---

@Database(
    entities = [MemoryEntryEntity::class, VehicleMemoryEntity::class, DateProfileEntity::class, MeetingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun dateProfileDao(): DateProfileDao
    abstract fun meetingDao(): MeetingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, passwordBytes: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database.db"
                ).openHelperFactory(SupportFactory(passwordBytes))
                 .allowMainThreadQueries() // Maintain compatibility with legacy synchronous caller threads
                 .build().also { INSTANCE = it }
            }
        }
    }
}
