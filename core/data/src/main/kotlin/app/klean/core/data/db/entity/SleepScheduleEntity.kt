package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A planned bed/wake time for some weekdays (Sleep module). */
@Entity(tableName = "sleep_schedule", indices = [Index("change_seq")])
data class SleepScheduleEntity(
    @PrimaryKey val id: String,
    /** Minutes after local midnight. */
    @ColumnInfo(name = "bed_minutes") val bedMinutes: Int,
    @ColumnInfo(name = "wake_minutes") val wakeMinutes: Int,
    /** Bitmask of ISO weekdays: bit 0 = Monday … bit 6 = Sunday. 127 = every day. */
    val days: Int,
    /** How long before [bedMinutes] the wind-down reminder comes. */
    @ColumnInfo(name = "reminder_minutes_before", defaultValue = "30") val reminderMinutesBefore: Int = 30,
    @Embedded val sync: SyncColumns,
)
