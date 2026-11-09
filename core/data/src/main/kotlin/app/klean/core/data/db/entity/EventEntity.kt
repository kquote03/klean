package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Klean calendar event (Calendar module). Room is the source of truth; the provider row is a mirror. */
@Entity(tableName = "event", indices = [Index("change_seq"), Index("start_at")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    @ColumnInfo(name = "all_day") val allDay: Boolean = false,
    val zone: String,
    val location: String? = null,
    val notes: String? = null,
    val recurrence: String? = null,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int? = null,
    /** _ID of the mirrored row in CalendarContract.Events, device-local. */
    @ColumnInfo(name = "provider_event_id") val providerEventId: Long? = null,
    @Embedded val sync: SyncColumns,
)
