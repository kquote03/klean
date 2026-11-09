package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A calendar shown in Klean (Calendar module): Klean's own, a device calendar, Google or DAV. */
@Entity(tableName = "calendar_source", indices = [Index("change_seq")])
data class CalendarSourceEntity(
    @PrimaryKey val id: String,
    /** KLEAN, DEVICE, GOOGLE, DAV. */
    val kind: String,
    val name: String,
    /** ARGB colour; null = the theme's default. */
    val color: Int? = null,
    /** Id in the source system (CalendarContract _ID, Google calendar id, DAV href). */
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "read_only", defaultValue = "0") val readOnly: Boolean = false,
    /** Shown in Klean's views. */
    @ColumnInfo(defaultValue = "1") val selected: Boolean = true,
    @Embedded val sync: SyncColumns,
)
