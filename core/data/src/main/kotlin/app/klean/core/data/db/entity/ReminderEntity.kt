package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A ping or a full alarm (Reminders module), optionally attached to a task. */
@Entity(tableName = "reminder", indices = [Index("change_seq"), Index("task_id"), Index("fire_at")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    val title: String,
    @ColumnInfo(name = "fire_at") val fireAt: Long,
    /** IANA zone the time was set in, so "5 pm" stays 5 pm after travel. */
    val zone: String,
    @ColumnInfo(name = "is_alarm") val isAlarm: Boolean = false,
    val recurrence: String? = null,
    /** SCHEDULED, RINGING, SNOOZED, DONE, DISMISSED. Dismissing never completes the task; see ReminderRepository. */
    val state: String = "SCHEDULED",
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long? = null,
    @ColumnInfo(name = "last_fired_at") val lastFiredAt: Long? = null,
    @Embedded val sync: SyncColumns,
)
