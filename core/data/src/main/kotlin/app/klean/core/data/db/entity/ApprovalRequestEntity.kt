package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An action an agent wants to take, waiting for the user (Delegation, M23). Nothing is sent before a decision. */
@Entity(tableName = "approval_request", indices = [Index("change_seq"), Index("delegation_id"), Index("decision")])
data class ApprovalRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "delegation_id") val delegationId: String,
    /** What the agent wants to do, e.g. `send_email`. */
    val action: String,
    /** The draft text the user reviews. */
    val draft: String,
    /** JSON array of recipient strings. */
    @ColumnInfo(defaultValue = "[]") val recipients: String = "[]",
    /** JSON object describing which fields the user may edit. */
    @ColumnInfo(defaultValue = "{}") val editable: String = "{}",
    /** PENDING, APPROVED, EDITED, REJECTED. */
    @ColumnInfo(defaultValue = "PENDING") val decision: String = "PENDING",
    @ColumnInfo(name = "decided_at") val decidedAt: Long? = null,
    @Embedded val sync: SyncColumns,
)
