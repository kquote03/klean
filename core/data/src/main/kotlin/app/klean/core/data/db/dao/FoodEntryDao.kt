package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

/** A past text and when it was logged; feeds quick-repeat ranking. */
data class RepeatRow(val text: String, val loggedAt: Long)

@Dao
interface FoodEntryDao {
    @Upsert suspend fun upsert(e: FoodEntryEntity)

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun get(id: String): FoodEntryEntity?

    @Query("SELECT * FROM food_entry WHERE id = :id")
    fun observe(id: String): Flow<FoodEntryEntity?>

    @Query("SELECT * FROM food_entry WHERE deleted = 0 AND eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at ASC")
    fun observeRange(from: Long, to: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entry WHERE deleted = 0 AND eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at ASC")
    suspend fun range(from: Long, to: Long): List<FoodEntryEntity>

    @Query("SELECT eaten_at FROM food_entry WHERE deleted = 0 AND eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at ASC")
    fun observeEatenBetween(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT eaten_at FROM food_entry WHERE deleted = 0 AND eaten_at >= :since")
    suspend fun eatenSince(since: Long): List<Long>

    @Query("SELECT created_at FROM food_entry WHERE deleted = 0 AND created_at >= :since")
    suspend fun createdSince(since: Long): List<Long>

    @Query("SELECT MAX(eaten_at) FROM food_entry WHERE deleted = 0 AND eaten_at <= :now")
    suspend fun lastEatenAt(now: Long): Long?

    @Query("SELECT raw_text AS text, created_at AS loggedAt FROM food_entry WHERE deleted = 0 AND created_at >= :since")
    suspend fun repeatRows(since: Long): List<RepeatRow>

    @Query("SELECT COUNT(*) FROM food_entry WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT id FROM food_entry WHERE deleted = 0 AND created_at >= :since AND enrichment IN ('NOT_REQUESTED','NO_PROVIDER','FAILED')")
    suspend fun awaitingEnrichment(since: Long): List<String>

    /** Entries whose calories came from the user or AI (never from history), newest first: calorie history. */
    @Query("SELECT * FROM food_entry WHERE deleted = 0 AND kcal IS NOT NULL AND kcal_source != 'HISTORY' AND eaten_at >= :since ORDER BY eaten_at DESC LIMIT 2000")
    suspend fun withCaloriesSince(since: Long): List<FoodEntryEntity>

    @Query("SELECT photo_file FROM food_entry WHERE photo_file IS NOT NULL")
    suspend fun allPhotoFiles(): List<String>
}
