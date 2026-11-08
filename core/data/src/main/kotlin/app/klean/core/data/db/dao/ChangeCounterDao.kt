package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChangeCounterDao {
    @Query("INSERT OR IGNORE INTO change_counter(id, value) VALUES (1, 0)")
    suspend fun ensure()

    @Query("UPDATE change_counter SET value = value + 1 WHERE id = 1")
    suspend fun increment()

    @Query("SELECT value FROM change_counter WHERE id = 1")
    suspend fun current(): Long

    /** Next value of the local monotonic change counter. */
    @Transaction
    suspend fun next(): Long {
        ensure()
        increment()
        return current()
    }
}
