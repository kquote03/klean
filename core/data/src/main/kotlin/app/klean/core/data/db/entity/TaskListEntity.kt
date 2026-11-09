package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task list (Tasks module). Sync rules: see SyncColumns.kt. */
@Entity(tableName = "task_list", indices = [Index("change_seq")])
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** WORK, PERSONAL or CUSTOM. */
    val kind: String,
    val position: Int,
    // v3 (M17)
    /** KLEAN or GOOGLE. */
    @ColumnInfo(defaultValue = "KLEAN") val source: String = "KLEAN",
    /** Id in the remote system (Google Tasks tasklist id). */
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    /** Shown in Klean (for Google lists, the user picks which ones). */
    @ColumnInfo(defaultValue = "1") val selected: Boolean = true,
    @Embedded val sync: SyncColumns,
)
