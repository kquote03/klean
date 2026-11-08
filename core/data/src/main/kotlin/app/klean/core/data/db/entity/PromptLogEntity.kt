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
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger

@Entity(tableName = "prompt_log", indices = [Index("sent_at"), Index("outcome"), Index("change_seq")])
data class PromptLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "module_id") val moduleId: String,
    @ColumnInfo(name = "trigger") val trigger: PromptTrigger,
    /** Delivery package or geofence id. Never notification content. */
    @ColumnInfo(name = "trigger_detail") val triggerDetail: String?,
    @ColumnInfo(name = "format") val format: PromptFormat,
    @ColumnInfo(name = "phrasing_id") val phrasingId: String,
    @ColumnInfo(name = "scheduled_for") val scheduledFor: Long,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "outcome") val outcome: PromptOutcome,
    @ColumnInfo(name = "outcome_at") val outcomeAt: Long?,
    @Embedded val sync: SyncColumns,
)
