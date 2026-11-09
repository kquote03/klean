package app.klean.feature.diet.unlock

import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.diet.prompt.UnlockSheetPolicy
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shows the unlock sheet for an already-recorded `prompt_log` row. Implemented by [UnlockSheetNotifier]. */
fun interface UnlockSheetPoster {
    suspend fun post(logId: String)
}

/**
 * Turns screen-state events into the unlock sheet (plan §6.3, M11). Pure decision in
 * [UnlockSheetPolicy]; this class keeps the state (`lastScreenOffAt` in DevicePrefs, the last sheet
 * in `prompt_log`) and posts. Android plumbing lives in [ScreenStateMonitor].
 */
@Singleton
class UnlockSheetTrigger @Inject constructor(
    private val db: KleanDatabase,
    private val writer: PromptLogWriter,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val prefs: DevicePrefs,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val poster: UnlockSheetPoster,
) {
    private val mutex = Mutex()

    suspend fun onScreenOff() = prefs.setLastScreenOffAt(now())

    /**
     * The process just started. With the screen off we can't know since when, so count from now:
     * a sheet too few is better than one after a short break. With the screen on, keep what is stored.
     */
    suspend fun onProcessStart(interactive: Boolean) {
        if (!interactive) prefs.setLastScreenOffAt(now())
    }

    /** The screen came on or the user unlocked. Returns whether a sheet was posted. */
    suspend fun onWake(): Boolean = mutex.withLock {
        val now = now()
        val pretend = prefs.unlockSheetPretend.first()
        val enabled = gate.isEnabled(ModuleId.DIET) && settings.get(SyncedKeys.DietUnlockSheet)
        val lastOff = if (pretend) now - UnlockSheetPolicy.MIN_SCREEN_OFF_MS else prefs.lastScreenOffAt.first()
        val lastShown = if (pretend) null else db.promptLogDao().lastForTrigger(PromptTrigger.UNLOCK_SHEET, null)?.sentAt
        val show = UnlockSheetPolicy.shouldShow(
            now = now, lastScreenOffAt = lastOff, lastShownAt = lastShown,
            quiet = settings.get(SyncedKeys.QuietHoursKey), zone = zone.current(), enabled = enabled,
        )
        if (!show) return@withLock false
        if (pretend) prefs.setUnlockSheetPretend(false)
        val id = UuidV7.generate(now)
        writer.insert { sync ->
            PromptLogEntity(
                id = id, moduleId = ModuleId.DIET.value, trigger = PromptTrigger.UNLOCK_SHEET, triggerDetail = null,
                format = PromptFormat.HEADS_UP, phrasingId = PHRASING_ID, scheduledFor = now, sentAt = now,
                outcome = PromptOutcome.PENDING, outcomeAt = null, sync = sync,
            )
        }
        poster.post(id)
        true
    }

    /** Debug only: the next wake is treated as the end of a 3 h break (and ignores the last sheet). */
    suspend fun pretendLongBreak() = prefs.setUnlockSheetPretend(true)

    private fun now() = clock.now().toEpochMilliseconds()

    companion object {
        /** The sheet has one fixed wording ("Today so far"). */
        const val PHRASING_ID = "unlock_today_so_far"
    }
}
