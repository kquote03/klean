package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.SyncCursorEntity

/** Local only: written by the sync engine (M25), never stamped or synced. */
@Dao
interface SyncCursorDao {
    @Upsert suspend fun upsert(x: SyncCursorEntity)

    @Query("SELECT * FROM sync_cursor WHERE id = :id")
    suspend fun get(id: String = SyncCursorEntity.SERVER): SyncCursorEntity?
}
