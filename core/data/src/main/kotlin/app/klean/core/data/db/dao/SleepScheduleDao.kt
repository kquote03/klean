package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.SleepScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepScheduleDao {
    @Upsert suspend fun upsert(x: SleepScheduleEntity)

    @Query("SELECT * FROM sleep_schedule WHERE id = :id")
    suspend fun get(id: String): SleepScheduleEntity?

    @Query("SELECT * FROM sleep_schedule WHERE deleted = 0 ORDER BY bed_minutes")
    fun observeAll(): Flow<List<SleepScheduleEntity>>

    @Query("SELECT * FROM sleep_schedule WHERE deleted = 0 ORDER BY bed_minutes")
    suspend fun all(): List<SleepScheduleEntity>
}
