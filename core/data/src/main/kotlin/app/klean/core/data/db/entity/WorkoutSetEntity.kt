package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One set or item inside a workout session (Workout module). */
@Entity(tableName = "workout_set", indices = [Index("change_seq"), Index("session_id"), Index("exercise_id")])
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val exercise: String,
    /** STRENGTH, TIMED, CARDIO. */
    val kind: String,
    val position: Int,
    val reps: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    @ColumnInfo(name = "duration_s") val durationS: Int? = null,
    @ColumnInfo(name = "distance_m") val distanceM: Int? = null,
    // v3 (M17)
    /** `exercise_definition.id`; [exercise] keeps the typed name. Null on rows from before v3. */
    @ColumnInfo(name = "exercise_id") val exerciseId: String? = null,
    @Embedded val sync: SyncColumns,
)
