package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Upsert suspend fun upsert(x: ReminderEntity)

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE deleted = 0 ORDER BY fire_at")
    fun observeAll(): Flow<List<ReminderEntity>>

    /** Every reminder that still has to fire (to arm alarms after boot or a change). */
    @Query("SELECT * FROM reminder WHERE deleted = 0 AND state IN ('SCHEDULED', 'SNOOZED') ORDER BY fire_at")
    suspend fun upcoming(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE deleted = 0 AND state IN ('SCHEDULED', 'SNOOZED') AND COALESCE(snoozed_until, fire_at) >= :from ORDER BY COALESCE(snoozed_until, fire_at)")
    fun observeUpcoming(from: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE deleted = 0 AND task_id = :taskId ORDER BY fire_at")
    fun forTask(taskId: String): Flow<List<ReminderEntity>>
}
