package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Upsert suspend fun upsertAll(items: List<FoodItemEntity>)

    @Query("SELECT * FROM food_item WHERE entry_id = :entryId AND deleted = 0 ORDER BY position")
    fun observeForEntry(entryId: String): Flow<List<FoodItemEntity>>

    /** Includes tombstoned rows, so callers can tombstone or restore them alongside the entry. */
    @Query("SELECT * FROM food_item WHERE entry_id = :entryId ORDER BY position")
    suspend fun forEntry(entryId: String): List<FoodItemEntity>

    @Query("SELECT * FROM food_item WHERE entry_id IN (:ids) AND deleted = 0 ORDER BY entry_id, position")
    fun observeForEntries(ids: List<String>): Flow<List<FoodItemEntity>>

    @Query("SELECT name AS text, created_at AS loggedAt FROM food_item WHERE deleted = 0 AND state != 'REJECTED' AND created_at >= :since")
    suspend fun acceptedNamesSince(since: Long): List<RepeatRow>
}
