package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Upsert suspend fun upsert(x: EventEntity)

    @Query("SELECT * FROM event WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Query("SELECT * FROM event WHERE deleted = 0 ORDER BY start_at")
    fun observeAll(): Flow<List<EventEntity>>

    /** Events overlapping [from, to). */
    @Query("SELECT * FROM event WHERE deleted = 0 AND start_at < :to AND end_at > :from ORDER BY start_at")
    fun observeRange(from: Long, to: Long): Flow<List<EventEntity>>
}
