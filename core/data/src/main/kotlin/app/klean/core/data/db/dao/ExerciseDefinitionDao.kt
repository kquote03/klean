package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDefinitionDao {
    @Upsert suspend fun upsert(x: ExerciseDefinitionEntity)

    @Query("SELECT * FROM exercise_definition WHERE id = :id")
    suspend fun get(id: String): ExerciseDefinitionEntity?

    @Query("SELECT * FROM exercise_definition WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseDefinitionEntity>>

    /** Case-insensitive exact name match, e.g. to reuse "Pushups" for "pushups". */
    @Query("SELECT * FROM exercise_definition WHERE deleted = 0 AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): ExerciseDefinitionEntity?
}
