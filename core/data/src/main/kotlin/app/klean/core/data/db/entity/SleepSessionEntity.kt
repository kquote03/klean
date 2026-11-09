package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One night (or nap) of sleep (Sleep module); [wakeAt] null = still asleep. */
@Entity(tableName = "sleep_session", indices = [Index("change_seq"), Index("bed_at")])
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bed_at") val bedAt: Long,
    @ColumnInfo(name = "wake_at") val wakeAt: Long? = null,
    /** MANUAL, HEALTH_CONNECT. */
    val source: String = "MANUAL",
    val notes: String? = null,
    @Embedded val sync: SyncColumns,
)
