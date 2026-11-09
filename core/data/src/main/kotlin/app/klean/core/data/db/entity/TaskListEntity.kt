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
    @Embedded val sync: SyncColumns,
)
