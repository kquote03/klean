package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Upsert suspend fun upsert(x: ContactEntity)

    @Query("SELECT * FROM contact WHERE id = :id")
    suspend fun get(id: String): ContactEntity?

    @Query("SELECT * FROM contact WHERE deleted = 0 ORDER BY display_name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contact WHERE deleted = 0 AND birthday_month IS NOT NULL AND birthday_day IS NOT NULL ORDER BY birthday_month, birthday_day")
    suspend fun withBirthdays(): List<ContactEntity>

    @Query("SELECT * FROM contact WHERE deleted = 0 AND display_name LIKE '%' || :q || '%' ORDER BY display_name COLLATE NOCASE")
    fun observeSearch(q: String): Flow<List<ContactEntity>>
}
