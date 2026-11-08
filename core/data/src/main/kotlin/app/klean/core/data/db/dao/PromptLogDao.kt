package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.shared.diet.prompt.PromptTrigger

@Dao
interface PromptLogDao {
    @Upsert suspend fun upsert(p: PromptLogEntity)

    @Query("SELECT * FROM prompt_log WHERE outcome = 'PENDING' AND deleted = 0 ORDER BY sent_at ASC")
    suspend fun pending(): List<PromptLogEntity>

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 ORDER BY sent_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PromptLogEntity>

    @Query("SELECT MAX(sent_at) FROM prompt_log WHERE deleted = 0")
    suspend fun lastSentAt(): Long?

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 AND `trigger` = :trigger AND trigger_detail IS :detail ORDER BY sent_at DESC LIMIT 1")
    suspend fun lastForTrigger(trigger: PromptTrigger, detail: String?): PromptLogEntity?

    @Query("SELECT * FROM prompt_log WHERE id = :id")
    suspend fun get(id: String): PromptLogEntity?
}
