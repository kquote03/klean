package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task (Tasks module). "Done" ([completedAt]) is separate from any reminder attached to it. */
@Entity(tableName = "task", indices = [Index("change_seq"), Index("list_id"), Index("due_date"), Index("parent_id"), Index("status")])
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    val title: String,
    val notes: String? = null,
    /** Local date the task is due, ISO yyyy-MM-dd; null = no date. */
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    /** Minutes after local midnight; null = all day. */
    @ColumnInfo(name = "due_minutes") val dueMinutes: Int? = null,
    /** 0 none, 1 low, 2 medium, 3 high. */
    val priority: Int = 0,
    /** RRULE subset (see shared RecurrenceRule); null = once. */
    val recurrence: String? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    val position: Int = 0,
    /** MANUAL or CAPTURE. */
    val source: String = "MANUAL",
    // v3 (M17)
    /** The parent task's id when this is a subtask; null = top level. */
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    /** OPEN, DONE, SOMEDAY. DONE always comes with [completedAt]. */
    @ColumnInfo(defaultValue = "OPEN") val status: String = "OPEN",
    /** When a done task left the Completed view (auto-archive after 30 days); null = not archived. */
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    /** Where the task lives: KLEAN or GOOGLE. (Plan name `source` is taken by MANUAL/CAPTURE above.) */
    @ColumnInfo(defaultValue = "KLEAN") val origin: String = "KLEAN",
    /** Id in the remote system (Google Tasks task id). */
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    /** Remote list id (Google Tasks tasklist id). */
    @ColumnInfo(name = "remote_list_id") val remoteListId: String? = null,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @Embedded val sync: SyncColumns,
)
