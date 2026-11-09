package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A device in this Klean install's sync group. `id` = that device's `DevicePrefs.deviceId()`. */
@Entity(tableName = "device", indices = [Index("change_seq")])
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ANDROID, WEAR, WEB. */
    val platform: String,
    /** The phone that owns prompts and alarms when several devices sync. */
    @ColumnInfo(name = "is_main_phone", defaultValue = "0") val isMainPhone: Boolean = false,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @Embedded val sync: SyncColumns,
)
