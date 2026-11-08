package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Upsert suspend fun upsert(s: SettingEntity)

    @Query("SELECT * FROM setting WHERE `key` = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM setting WHERE `key` = :key")
    fun observe(key: String): Flow<SettingEntity?>

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>
}
