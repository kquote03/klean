package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local, never synced. A single row (id = 1) holding the last issued `change_seq`. */
@Entity(tableName = "change_counter")
data class ChangeCounterEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "value") val value: Long,
)
