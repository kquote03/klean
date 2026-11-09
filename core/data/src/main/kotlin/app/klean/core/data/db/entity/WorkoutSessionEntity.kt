package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One logged workout (Workout module); its sets live in `workout_set`. */
@Entity(tableName = "workout_session", indices = [Index("change_seq"), Index("started_at")])
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_id") val templateId: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val notes: String? = null,
    /** MANUAL, CAPTURE, HEALTH_CONNECT. */
    val source: String = "MANUAL",
    // v3 (M17)
    /** Blob-store key of the GPS route (file name), synced as a blob; null = none. */
    @ColumnInfo(name = "route_blob") val routeBlob: String? = null,
    /** Total distance in metres, for sessions with a route or a Health Connect record. */
    @ColumnInfo(name = "distance_m") val distanceM: Int? = null,
    /** Health Connect record id when imported or written there. */
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String? = null,
    @Embedded val sync: SyncColumns,
)
