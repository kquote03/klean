package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local, never synced. Where this device is in the sync stream; one row per server (`id = "server"`). */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val id: String = SERVER,
    /** Server cursor of the last pulled page. */
    val cursor: Long = 0,
    /** Highest local `change_seq` the server has accepted. */
    @ColumnInfo(name = "last_push_seq") val lastPushSeq: Long = 0,
) {
    companion object {
        const val SERVER = "server"
    }
}
