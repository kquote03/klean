package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Upsert suspend fun upsert(x: TaskEntity)

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM task WHERE deleted = 0 ORDER BY completed_at IS NOT NULL, due_date IS NULL, due_date, due_minutes, position")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deleted = 0 AND list_id = :listId ORDER BY completed_at IS NOT NULL, due_date IS NULL, due_date, due_minutes, position")
    fun observeList(listId: String): Flow<List<TaskEntity>>

    /** Tasks due between two local dates, both inclusive (ISO yyyy-MM-dd compares as text). */
    @Query("SELECT * FROM task WHERE deleted = 0 AND due_date >= :fromIso AND due_date <= :toIso ORDER BY completed_at IS NOT NULL, due_date IS NULL, due_date, due_minutes, position")
    fun observeDue(fromIso: String, toIso: String): Flow<List<TaskEntity>>

    /** Open tasks due on an earlier day than [todayIso]; feeds the "Still want these?" review. */
    @Query("SELECT * FROM task WHERE deleted = 0 AND completed_at IS NULL AND due_date < :todayIso ORDER BY due_date, due_minutes, position")
    suspend fun pastDue(todayIso: String): List<TaskEntity>

    // --- M18 (Tasks lane): one-shot reads for writes inside db.transact {} (no Flow there). ---

    @Query("SELECT * FROM task WHERE deleted = 0 AND parent_id = :parentId ORDER BY position, created_at")
    suspend fun children(parentId: String): List<TaskEntity>

    @Query("SELECT * FROM task WHERE deleted = 0 AND list_id = :listId")
    suspend fun inList(listId: String): List<TaskEntity>

    /** Done tasks still in the Completed view that were finished before [cutoff] (auto-archive). */
    @Query("SELECT * FROM task WHERE deleted = 0 AND archived_at IS NULL AND completed_at IS NOT NULL AND completed_at < :cutoff")
    suspend fun completedBefore(cutoff: Long): List<TaskEntity>
}
