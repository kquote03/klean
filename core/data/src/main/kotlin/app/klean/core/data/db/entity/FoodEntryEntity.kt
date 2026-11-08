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
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.MealType

@Entity(tableName = "food_entry", indices = [Index("eaten_at"), Index("created_at"), Index("change_seq")])
data class FoodEntryEntity(
    @PrimaryKey val id: String,
    /** What the user wrote. Never modified by AI. */
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
    /** IANA zone at capture time, so history can show the local time the user saw. */
    @ColumnInfo(name = "eaten_at_zone") val eatenAtZone: String,
    /** File name relative to `filesDir/photos/`. */
    @ColumnInfo(name = "photo_file") val photoFile: String?,
    @ColumnInfo(name = "source") val source: CaptureSource,
    @ColumnInfo(name = "via_voice") val viaVoice: Boolean,
    @ColumnInfo(name = "meal_type") val mealType: MealType?,
    @ColumnInfo(name = "meal_type_provider") val mealTypeProvider: String?,
    @ColumnInfo(name = "enrichment") val enrichment: EnrichmentStatus,
    @Embedded val sync: SyncColumns,
)
