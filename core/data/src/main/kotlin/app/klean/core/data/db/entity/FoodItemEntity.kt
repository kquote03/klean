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
import app.klean.shared.diet.FoodItemState

/** One item parsed from an entry (by AI or the user). `entry_id` references [FoodEntryEntity.id]. */
@Entity(tableName = "food_item", indices = [Index("entry_id"), Index("change_seq")])
data class FoodItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "portion") val portion: String?,
    @ColumnInfo(name = "calories") val calories: Int?,
    /** `gemini` / `openai_compat` / `on_device` / `user`. */
    @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "model") val model: String?,
    @ColumnInfo(name = "state") val state: FoodItemState = FoodItemState.ACCEPTED,
    @Embedded val sync: SyncColumns,
)
