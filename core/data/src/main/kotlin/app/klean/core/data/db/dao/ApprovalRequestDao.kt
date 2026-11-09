package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.ApprovalRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalRequestDao {
    @Upsert suspend fun upsert(x: ApprovalRequestEntity)

    @Query("SELECT * FROM approval_request WHERE id = :id")
    suspend fun get(id: String): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_request WHERE deleted = 0 ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT * FROM approval_request WHERE deleted = 0 AND delegation_id = :delegationId ORDER BY created_at")
    fun observeForDelegation(delegationId: String): Flow<List<ApprovalRequestEntity>>

    /** Waiting for the user, oldest first. */
    @Query("SELECT * FROM approval_request WHERE deleted = 0 AND decision = 'PENDING' ORDER BY created_at")
    fun observePending(): Flow<List<ApprovalRequestEntity>>
}
