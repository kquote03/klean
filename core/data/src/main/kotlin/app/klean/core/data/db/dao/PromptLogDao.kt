package app.klean.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.shared.diet.prompt.PromptTrigger

/**
 * Diet's queries ([pending], [recent], [lastSentAt]) read only diet rows: other modules (reminders, M18) log
 * their own prompts here for format rotation, and those must not settle, pace or rotate diet check-ins.
 */
@Dao
interface PromptLogDao {
    @Upsert suspend fun upsert(p: PromptLogEntity)

    @Query("SELECT * FROM prompt_log WHERE outcome = 'PENDING' AND deleted = 0 AND module_id = 'diet' ORDER BY sent_at ASC")
    suspend fun pending(): List<PromptLogEntity>

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 AND module_id = 'diet' ORDER BY sent_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PromptLogEntity>

    @Query("SELECT MAX(sent_at) FROM prompt_log WHERE deleted = 0 AND module_id = 'diet'")
    suspend fun lastSentAt(): Long?

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 AND `trigger` = :trigger AND trigger_detail IS :detail ORDER BY sent_at DESC LIMIT 1")
    suspend fun lastForTrigger(trigger: PromptTrigger, detail: String?): PromptLogEntity?

    @Query("SELECT * FROM prompt_log WHERE id = :id")
    suspend fun get(id: String): PromptLogEntity?

    // --- M18 (Tasks lane): reminder prompts rotate formats from their own history. ---

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 AND `trigger` = :trigger ORDER BY sent_at DESC LIMIT :limit")
    suspend fun recentForTrigger(trigger: PromptTrigger, limit: Int): List<PromptLogEntity>

    @Query("SELECT * FROM prompt_log WHERE deleted = 0 AND `trigger` = :trigger AND outcome = 'PENDING' ORDER BY sent_at ASC")
    suspend fun pendingForTrigger(trigger: PromptTrigger): List<PromptLogEntity>
}
