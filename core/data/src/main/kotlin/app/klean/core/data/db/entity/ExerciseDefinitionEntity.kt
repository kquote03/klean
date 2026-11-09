package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A named exercise ("pushups") with defaults (Workout module); `workout_set.exercise_id` points here. */
@Entity(tableName = "exercise_definition", indices = [Index("change_seq"), Index("name")])
data class ExerciseDefinitionEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** STRENGTH, TIMED, CARDIO. */
    val kind: String,
    @ColumnInfo(name = "default_reps") val defaultReps: Int? = null,
    @ColumnInfo(name = "default_duration_s") val defaultDurationS: Int? = null,
    @Embedded val sync: SyncColumns,
)
