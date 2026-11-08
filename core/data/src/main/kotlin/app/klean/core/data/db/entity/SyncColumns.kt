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

/** Sync metadata embedded in every syncable row. Times are UTC epoch milliseconds. */
data class SyncColumns(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
)
