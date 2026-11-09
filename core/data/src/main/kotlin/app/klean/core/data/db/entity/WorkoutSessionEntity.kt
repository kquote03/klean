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
    @Embedded val sync: SyncColumns,
)
