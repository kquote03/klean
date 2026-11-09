package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Upsert suspend fun upsert(x: WorkoutTemplateEntity)

    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun get(id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template WHERE deleted = 0 ORDER BY position, name")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>
}
