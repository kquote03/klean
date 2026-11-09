package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.GeofenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    @Upsert suspend fun upsert(g: GeofenceEntity)

    @Query("SELECT * FROM geofence WHERE deleted = 0 ORDER BY label")
    fun observeAll(): Flow<List<GeofenceEntity>>

    @Query("SELECT * FROM geofence WHERE deleted = 0 AND enabled = 1")
    suspend fun enabled(): List<GeofenceEntity>

    /** Including tombstoned rows, so their registrations can be removed too. */
    @Query("SELECT id FROM geofence")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM geofence WHERE id = :id")
    suspend fun get(id: String): GeofenceEntity?
}
