package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Upsert suspend fun upsert(x: WorkoutSetEntity)

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun get(id: String): WorkoutSetEntity?

    @Query("SELECT * FROM workout_set WHERE deleted = 0 ORDER BY session_id, position")
    fun observeAll(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE deleted = 0 AND session_id = :id ORDER BY position")
    suspend fun forSession(id: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE deleted = 0 AND session_id = :id ORDER BY position")
    fun observeForSession(id: String): Flow<List<WorkoutSetEntity>>
}
