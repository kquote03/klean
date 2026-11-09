package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A reusable workout ("3×15 pushups, 1 min plank") (Workout module). */
@Entity(tableName = "workout_template", indices = [Index("change_seq")])
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON array of WorkoutItem (defined in :feature:workout). */
    val items: String,
    val position: Int = 0,
    @Embedded val sync: SyncColumns,
)
