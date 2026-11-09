package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task handed to an agent (Delegation, M23). Sync rules: see SyncColumns.kt. */
@Entity(tableName = "delegation", indices = [Index("change_seq"), Index("task_id"), Index("status")])
data class DelegationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    /** PARTIAL, INTERACTIVE, AGENTIC. */
    val mode: String,
    /** `AI:<provider id>` or HERMES. */
    val agent: String,
    /** QUEUED, RUNNING, NEEDS_INPUT, NEEDS_APPROVAL, DONE, FAILED. */
    @ColumnInfo(defaultValue = "QUEUED") val status: String = "QUEUED",
    /** The agent's own id for the job (e.g. a Hermes task id). */
    @ColumnInfo(name = "remote_ref") val remoteRef: String? = null,
    /** Final text result, when there is one. */
    val result: String? = null,
    /** Mark the task done when the delegation finishes. */
    @ColumnInfo(name = "auto_complete", defaultValue = "0") val autoComplete: Boolean = false,
    @Embedded val sync: SyncColumns,
)
