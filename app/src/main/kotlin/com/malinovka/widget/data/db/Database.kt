package com.malinovka.widget.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Последний удачный снимок по направлению — он же кэш (валиден 10 минут). */
@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey val stopKey: String,
    val etasJson: String,
    val source: String,
    val updatedAtMs: Long,
)

/** Зашитое плановое расписание (импорт assets/schedule.json при первом запуске). */
@Entity(
    tableName = "schedule",
    primaryKeys = ["stopKey", "route", "board", "dayType", "minute"],
)
data class ScheduleEntity(
    val stopKey: String,
    val route: String,
    val board: String,
    val destination: String,
    val dayType: String,
    val minute: Int,
)

@Dao
interface SnapshotDao {
    @Upsert
    suspend fun put(entity: SnapshotEntity)

    @Query("SELECT * FROM snapshots WHERE stopKey = :stopKey")
    suspend fun get(stopKey: String): SnapshotEntity?
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ScheduleEntity>)

    @Query("DELETE FROM schedule")
    suspend fun clear()

    @Query("SELECT minute FROM schedule WHERE stopKey = :stopKey AND route = :route AND dayType = :dayType ORDER BY minute")
    suspend fun minutes(stopKey: String, route: String, dayType: String): List<Int>
}

@Database(
    entities = [SnapshotEntity::class, ScheduleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
    abstract fun scheduleDao(): ScheduleDao
}
