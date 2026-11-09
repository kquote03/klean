package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.DelegationMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DelegationMessageDao {
    @Upsert suspend fun upsert(x: DelegationMessageEntity)

    @Query("SELECT * FROM delegation_message WHERE id = :id")
    suspend fun get(id: String): DelegationMessageEntity?

    @Query("SELECT * FROM delegation_message WHERE deleted = 0 ORDER BY at")
    fun observeAll(): Flow<List<DelegationMessageEntity>>

    @Query("SELECT * FROM delegation_message WHERE deleted = 0 AND delegation_id = :delegationId ORDER BY at")
    fun observeForDelegation(delegationId: String): Flow<List<DelegationMessageEntity>>

    @Query("SELECT * FROM delegation_message WHERE deleted = 0 AND delegation_id = :delegationId ORDER BY at")
    suspend fun forDelegation(delegationId: String): List<DelegationMessageEntity>
}
