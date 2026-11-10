package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.TaskListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    @Upsert suspend fun upsert(x: TaskListEntity)

    @Query("SELECT * FROM task_list WHERE id = :id")
    suspend fun get(id: String): TaskListEntity?

    @Query("SELECT * FROM task_list WHERE deleted = 0 ORDER BY position, name")
    fun observeAll(): Flow<List<TaskListEntity>>

    // --- M18 (Tasks lane) ---
    @Query("SELECT MAX(position) FROM task_list WHERE deleted = 0")
    suspend fun maxPosition(): Int?
}
