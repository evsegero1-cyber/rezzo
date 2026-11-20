package ru.rezzo.academy.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Прогресс изучения деталей (используется базой знаний и будущим разделом «Обучение»). */
@Entity(tableName = "part_progress")
data class PartProgressEntity(
    @PrimaryKey val partId: String,
    val viewCount: Int,
    val firstViewedAt: Long,
    val studied: Boolean,
    val studiedAt: Long?,
)

/** Запись истории обслуживания и ремонта (раздел «Гараж», этап 8). */
@Entity(tableName = "maintenance_records")
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val mileageKm: Int?,
    val title: String,
    /** maintenance | repair | part_replacement | planned */
    val category: String,
    val cost: Double?,
    val note: String?,
    /** id деталей из базы знаний через запятую */
    val partIds: String,
    val done: Boolean,
)

/** Показания одометра (раздел «Гараж», этап 8). */
@Entity(tableName = "mileage_log")
data class MileageEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val mileageKm: Int,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM part_progress")
    fun observeAll(): Flow<List<PartProgressEntity>>

    @Query("SELECT * FROM part_progress WHERE partId = :partId")
    suspend fun get(partId: String): PartProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: PartProgressEntity)
}

@Dao
interface GarageDao {
    @Query("SELECT * FROM maintenance_records ORDER BY date DESC")
    fun observeRecords(): Flow<List<MaintenanceRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecord(record: MaintenanceRecordEntity): Long

    @Delete
    suspend fun deleteRecord(record: MaintenanceRecordEntity)

    @Query("SELECT * FROM mileage_log ORDER BY date DESC")
    fun observeMileage(): Flow<List<MileageEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMileage(entry: MileageEntryEntity): Long
}

@Database(
    entities = [PartProgressEntity::class, MaintenanceRecordEntity::class, MileageEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun garageDao(): GarageDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "rezzo.db").build()
    }
}
