package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.DelegationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DelegationDao {
    @Upsert suspend fun upsert(x: DelegationEntity)

    @Query("SELECT * FROM delegation WHERE id = :id")
    suspend fun get(id: String): DelegationEntity?

    @Query("SELECT * FROM delegation WHERE deleted = 0 ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DelegationEntity>>

    @Query("SELECT * FROM delegation WHERE deleted = 0 AND task_id = :taskId ORDER BY created_at DESC")
    fun observeForTask(taskId: String): Flow<List<DelegationEntity>>

    /** Not finished yet: QUEUED, RUNNING, NEEDS_INPUT or NEEDS_APPROVAL. */
    @Query("SELECT * FROM delegation WHERE deleted = 0 AND status IN ('QUEUED', 'RUNNING', 'NEEDS_INPUT', 'NEEDS_APPROVAL') ORDER BY created_at")
    fun observeActive(): Flow<List<DelegationEntity>>

    @Query("SELECT * FROM delegation WHERE deleted = 0 AND status IN ('QUEUED', 'RUNNING', 'NEEDS_INPUT', 'NEEDS_APPROVAL') ORDER BY created_at")
    suspend fun active(): List<DelegationEntity>
}
