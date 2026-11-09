package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One message in a delegation's conversation (Delegation, M23). */
@Entity(tableName = "delegation_message", indices = [Index("change_seq"), Index("delegation_id")])
data class DelegationMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "delegation_id") val delegationId: String,
    /** AGENT or USER. */
    val role: String,
    val text: String,
    /** UTC ms the message was written. */
    val at: Long,
    @Embedded val sync: SyncColumns,
)
