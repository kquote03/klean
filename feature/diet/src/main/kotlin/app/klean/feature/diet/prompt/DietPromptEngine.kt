package app.klean.feature.diet.prompt

import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.QuietHoursService
import app.klean.core.notifications.Reschedulable
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import app.klean.feature.diet.notification.PromptPhrasings
import app.klean.shared.diet.prompt.FormatRotator
import app.klean.shared.diet.prompt.MealTimeLearner
import app.klean.shared.diet.prompt.PlannedPrompt
import app.klean.shared.diet.prompt.PromptContext
import app.klean.shared.diet.prompt.PromptHistoryItem
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptOutcomeResolver
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the module toggle needs from check-ins. */
interface DietPrompts {
    /** Plans and arms the next check-in alarm (or cancels, if check-ins are off). */
    suspend fun reschedule()

    /** Cancels the pending alarm and any visible check-in. */
    suspend fun cancel()

    /** Debug only: shows a check-in right now, skipping quiet hours and the recently-logged pause. */
    suspend fun fireNow() = Unit
}

/**
 * Diet check-ins (plan §5.3). Plans the next one with [app.klean.shared.diet.prompt.PromptPlanner],
 * fires it through [PromptPresenter], and records outcomes in `prompt_log`.
 *
 * Frequency never depends on outcomes: the planner sees only the last prompt time, the last entry
 * and learned meal times. Outcomes only rotate the *format* and the wording.
 */
@Singleton
class DietPromptEngine @Inject constructor(
    private val db: KleanDatabase,
    private val writer: PromptLogWriter,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val quiet: QuietHoursService,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val scheduler: PromptAlarmScheduling,
    private val presenter: PromptPresenter,
    private val prefs: DevicePrefs,
    private val random: Random,
) : DietPrompts, Reschedulable, DietChangeListener {
    private val mutex = Mutex()

    override suspend fun onChange(change: DietChange) = mutex.withLock {
        if (change is DietChange.Logged) markLogged()
        rescheduleLocked()
    }

    override suspend fun reschedule() = mutex.withLock { rescheduleLocked() }

    override suspend fun cancel() = mutex.withLock { cancelLocked() }

    override suspend fun fireNow() = onAlarm(PlannedPrompt(now(), PromptTrigger.CADENCE, null), force = true)

    /**
     * The alarm fired. [force] (debug "fire a check-in now") skips the quiet-hours and
     * recently-logged checks, but never the module or check-in switches.
     */
    suspend fun onAlarm(plan: PlannedPrompt, force: Boolean = false) = mutex.withLock {
        if (!enabled()) { cancelLocked(); return@withLock }
        val now = now()
        val recent = db.foodEntryDao().lastEatenAt(now)
        if (!force && (quiet.isQuiet(now) || (recent != null && now - recent < RECENT_LOG_MS))) {
            rescheduleLocked()
            return@withLock
        }
        // A new check-in replaces any still-open one, which then counts as ignored.
        resolvePending(forceIgnoreAll = true)
        // Formats don't replace each other (a highlight posts no notification), so clear the old one.
        presenter.cancelActive()
        val history = db.promptLogDao().recent(HISTORY).map { PromptHistoryItem(it.format, it.phrasingId, it.outcome) }
        val format = FormatRotator.nextFormat(history)
        val pool = if (plan.trigger == PromptTrigger.LEARNED_MEAL && plan.meal != null) {
            PromptPhrasings.sinceMeal(plan.meal!!)
        } else {
            PromptPhrasings.generic
        }
        val phrasingId = FormatRotator.nextPhrasing(pool.map { it.id }, history, random)
        val id = UuidV7.generate(now)
        writer.insert { sync ->
            PromptLogEntity(
                id = id, moduleId = ModuleId.DIET.value, trigger = plan.trigger, triggerDetail = null,
                format = format, phrasingId = phrasingId, scheduledFor = plan.at, sentAt = now,
                outcome = PromptOutcome.PENDING, outcomeAt = null, sync = sync,
            )
        }
        presenter.present(id, format, phrasingId, plan.trigger)
        rescheduleLocked()
    }

    private suspend fun rescheduleLocked() {
        if (!enabled()) { cancelLocked(); return }
        resolvePending()
        val now = now()
        val tz = zone.current()
        val slots = if (settings.get(SyncedKeys.DietLearnMealTimes)) {
            MealTimeLearner.learn(db.foodEntryDao().eatenSince(now - MealTimeLearner.WINDOW_DAYS * DAY_MS), now, tz)
        } else {
            emptyList()
        }
        val plan = app.klean.shared.diet.prompt.PromptPlanner.next(
            PromptContext(
                now = now, zone = tz, quiet = settings.get(SyncedKeys.QuietHoursKey),
                lastPromptAt = db.promptLogDao().lastSentAt(), lastEntryAt = db.foodEntryDao().lastEatenAt(now),
                learnedSlots = slots, cadenceMs = settings.get(SyncedKeys.DietCadenceHours) * HOUR_MS,
            ),
        )
        scheduler.schedule(plan)
        prefs.setNextPromptAt(plan.at)
    }

    private suspend fun cancelLocked() {
        scheduler.cancel()
        presenter.cancelActive()
        prefs.setNextPromptAt(null)
    }

    private suspend fun enabled() = gate.isEnabled(ModuleId.DIET) && settings.get(SyncedKeys.DietPromptsEnabled)

    /**
     * Settles open check-ins: LOGGED if something was logged within the window after it was sent,
     * IGNORED after 2 h (or at once with [forceIgnoreAll]). Returns how many were settled.
     */
    private suspend fun resolvePending(forceIgnoreAll: Boolean = false): Int {
        val now = now()
        var settled = 0
        for (row in db.promptLogDao().pending()) {
            val created = db.foodEntryDao().createdSince(row.sentAt)
            var outcome = PromptOutcomeResolver.resolve(row.sentAt, created, now)
            if (forceIgnoreAll && outcome == PromptOutcome.PENDING) outcome = PromptOutcome.IGNORED
            if (outcome != PromptOutcome.PENDING && writer.setOutcome(row.id, outcome)) settled++
        }
        return settled
    }

    /** Something was just logged: settle open check-ins and take the visible one away. */
    private suspend fun markLogged() {
        resolvePending()
        presenter.cancelActive()
    }

    private fun now() = clock.now().toEpochMilliseconds()

    companion object {
        /** No check-in within this long after an entry. */
        const val RECENT_LOG_MS = 90 * MINUTE_MS
        private const val HISTORY = 10
        private const val DAY_MS = 24 * HOUR_MS
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietPromptModule {
    @Binds abstract fun prompts(e: DietPromptEngine): DietPrompts
    @Binds @IntoSet abstract fun reschedulable(e: DietPromptEngine): Reschedulable
    @Binds @IntoSet abstract fun listener(e: DietPromptEngine): DietChangeListener
    @Binds abstract fun alarms(a: PromptAlarms): PromptAlarmScheduling

    companion object {
        @Provides fun random(): Random = Random.Default
    }
}
