/*
 * Sync rules for every syncable table (see plan §3):
 * - `id` is a client-generated UUIDv7 string (settings use a name-based UUID of their key so devices agree).
 * - No hard deletes: rows are tombstoned with `deleted = 1` and keep syncing.
 * - `change_seq` comes from the local monotonic [ChangeCounterEntity]; a future sync engine sends rows
 *   with `change_seq > lastSentSeq`.
 * - No SQL foreign keys, because sync may deliver children before parents. Reference columns are indexed instead.
 * - Enums are stored by name.
 */
package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "geofence", indices = [Index("change_seq")])
data class GeofenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusM: Int,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @Embedded val sync: SyncColumns,
)
