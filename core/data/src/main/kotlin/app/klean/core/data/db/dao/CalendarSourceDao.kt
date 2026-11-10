package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.CalendarSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSourceDao {
    @Upsert suspend fun upsert(x: CalendarSourceEntity)

    @Query("SELECT * FROM calendar_source WHERE id = :id")
    suspend fun get(id: String): CalendarSourceEntity?

    @Query("SELECT * FROM calendar_source WHERE deleted = 0 ORDER BY kind, name")
    fun observeAll(): Flow<List<CalendarSourceEntity>>

    // M19 calendar: one-shot read for the status line.
    @Query("SELECT * FROM calendar_source WHERE deleted = 0 ORDER BY kind, name")
    suspend fun all(): List<CalendarSourceEntity>

    @Query("SELECT * FROM calendar_source WHERE deleted = 0 AND selected = 1 ORDER BY kind, name")
    fun observeSelected(): Flow<List<CalendarSourceEntity>>

    @Query("SELECT * FROM calendar_source WHERE deleted = 0 AND kind = :kind ORDER BY name")
    suspend fun byKind(kind: String): List<CalendarSourceEntity>
}
