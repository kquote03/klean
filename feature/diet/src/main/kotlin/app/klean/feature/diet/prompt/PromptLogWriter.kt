package app.klean.feature.diet.prompt

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.diet.prompt.PromptOutcome
import javax.inject.Inject
import kotlin.time.Clock

/** Every `prompt_log` write, stamped inside one transaction (sync rules, plan §3). */
class PromptLogWriter @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
) {
    suspend fun insert(row: (sync: app.klean.core.data.db.entity.SyncColumns) -> PromptLogEntity) = db.transact {
        db.promptLogDao().upsert(row(stamper.stamp()))
    }

    /** Sets [outcome] (and `outcome_at` = now). With [onlyIfPending], resolved rows are left alone. Returns whether it changed. */
    suspend fun setOutcome(id: String, outcome: PromptOutcome, onlyIfPending: Boolean = true): Boolean = db.transact {
        val prev = db.promptLogDao().get(id) ?: return@transact false
        if (prev.outcome == outcome || (onlyIfPending && prev.outcome != PromptOutcome.PENDING)) return@transact false
        val now = clock.now().toEpochMilliseconds()
        db.promptLogDao().upsert(prev.copy(outcome = outcome, outcomeAt = now, sync = stamper.stamp(prev.sync)))
        true
    }

    /** The user swiped the check-in away. */
    suspend fun markDismissed(id: String) = setOutcome(id, PromptOutcome.DISMISSED)
}
