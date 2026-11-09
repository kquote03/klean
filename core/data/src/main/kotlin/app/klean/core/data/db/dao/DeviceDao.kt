package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Upsert suspend fun upsert(x: DeviceEntity)

    @Query("SELECT * FROM device WHERE id = :id")
    suspend fun get(id: String): DeviceEntity?

    @Query("SELECT * FROM device WHERE deleted = 0 ORDER BY last_seen_at DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM device WHERE deleted = 0 AND is_main_phone = 1 ORDER BY last_seen_at DESC LIMIT 1")
    suspend fun mainPhone(): DeviceEntity?
}
