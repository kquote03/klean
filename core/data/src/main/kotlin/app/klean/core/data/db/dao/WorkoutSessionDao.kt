package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Upsert suspend fun upsert(x: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun get(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE deleted = 0 ORDER BY started_at DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    /** Sessions that started in [from, to). */
    @Query("SELECT * FROM workout_session WHERE deleted = 0 AND started_at >= :from AND started_at < :to ORDER BY started_at DESC")
    fun observeRange(from: Long, to: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT MAX(started_at) FROM workout_session WHERE deleted = 0")
    suspend fun lastSessionStart(): Long?
}
