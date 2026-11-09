package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.SleepSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Upsert suspend fun upsert(x: SleepSessionEntity)

    @Query("SELECT * FROM sleep_session WHERE id = :id")
    suspend fun get(id: String): SleepSessionEntity?

    @Query("SELECT * FROM sleep_session WHERE deleted = 0 ORDER BY bed_at DESC")
    fun observeAll(): Flow<List<SleepSessionEntity>>

    /** Sessions whose bed time is in [from, to). */
    @Query("SELECT * FROM sleep_session WHERE deleted = 0 AND bed_at >= :from AND bed_at < :to ORDER BY bed_at DESC")
    fun observeRange(from: Long, to: Long): Flow<List<SleepSessionEntity>>

    /** The session still waiting for "I'm up", if any. */
    @Query("SELECT * FROM sleep_session WHERE wake_at IS NULL AND deleted = 0 ORDER BY bed_at DESC LIMIT 1")
    suspend fun open(): SleepSessionEntity?
}
