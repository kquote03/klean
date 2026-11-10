package app.klean.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.transact
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SCHEDULED
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SNOOZED
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.QuietHoursService
import app.klean.core.notifications.Reschedulable
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.reminders.notify.ReminderChannels
import app.klean.feature.reminders.notify.ReminderNotifier
import app.klean.shared.diet.prompt.FormatRotator
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptHistoryItem
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** A fired reminder that hasn't been handled yet ("Earlier" on the Reminders tab). */
const val RINGING = "RINGING"

/**
 * Arms one system alarm per reminder that still has to go off. Listens to every reminder write (so
 * Tasks and Calendar never touch alarms) and re-arms everything after boot, updates and clock changes.
 * Alarms use `setAlarmClock` (shown in the status bar, exempt from Doze); pings use exact idle-safe
 * alarms, or inexact ones when exact alarms aren't allowed (see Reminders settings).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: KleanDatabase,
    private val gate: ModuleGate,
    private val quiet: QuietHoursService,
    private val clock: Clock,
    private val alarms: AlarmScheduler,
) : ReminderChangeListener, Reschedulable {
    private val am: AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    override suspend fun onChanged(id: String) {
        val r = db.reminderDao().get(id)
        if (r == null) cancel(id) else sync(r, gate.isEnabled(ModuleId.REMINDERS))
    }

    override suspend fun reschedule() {
        ReminderChannels.ensure(ctx)
        val on = gate.isEnabled(ModuleId.REMINDERS)
        db.reminderDao().upcoming().forEach { sync(it, on) }
    }

    /** Module off: nothing stays armed. */
    suspend fun cancelAll() = db.reminderDao().upcoming().forEach { cancel(it.id) }

    private suspend fun sync(r: ReminderEntity, enabled: Boolean) {
        if (enabled && !r.sync.deleted && (r.state == SCHEDULED || r.state == SNOOZED)) arm(r) else cancel(r.id)
    }

    /** Arms (or re-arms) [r] at its next allowed time. */
    suspend fun arm(r: ReminderEntity) {
        val at = ReminderTiming.armAt(r, clock.now().toEpochMilliseconds()) { quiet.nextAllowed(it) }
        val pi = PendingIntent.getBroadcast(ctx, 0, fireIntent(ctx, r.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (r.isAlarm && alarms.canExact()) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi)
                return
            } catch (e: SecurityException) {
                Log.w("Klean", "alarm clock refused, using a plain alarm", e)
            }
        }
        alarms.schedule(at, pi)
    }

    fun cancel(id: String) {
        PendingIntent.getBroadcast(ctx, 0, fireIntent(ctx, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?.let(alarms::cancel)
    }

    /** True when an alarm is armed for [id] (tests and the disable check). */
    fun isArmed(id: String): Boolean =
        PendingIntent.getBroadcast(ctx, 0, fireIntent(ctx, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null

    companion object {
        fun fireIntent(ctx: Context, id: String): Intent = Intent(ctx, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData("klean-reminder://$id/fire".toUri())
            .putExtra(EXTRA_ID, id)

        /** What the status-bar alarm icon opens: the Reminders tab. */
        private fun showIntent(ctx: Context): PendingIntent = app.klean.feature.reminders.notify.AndroidReminderNotifier.openReminders(ctx)

        const val ACTION_FIRE = "app.klean.reminders.FIRE"
        const val EXTRA_ID = "reminderId"
    }
}

/** Reminder prompts in `prompt_log` (trigger REMINDER), so pings rotate formats when they're ignored. */
class ReminderPromptLog @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
) {
    private val dao get() = db.promptLogDao()
    private fun now() = clock.now().toEpochMilliseconds()

    /** A new ping is going out: the open ones count as ignored, then the format follows the rotation. */
    suspend fun nextFormat(): PromptFormat {
        dao.pendingForTrigger(PromptTrigger.REMINDER).forEach { setOutcome(it, PromptOutcome.IGNORED) }
        val history = dao.recentForTrigger(PromptTrigger.REMINDER, HISTORY).map { PromptHistoryItem(it.format, it.phrasingId, it.outcome) }
        return FormatRotator.nextFormat(history)
    }

    suspend fun sent(reminderId: String, format: PromptFormat, scheduledFor: Long) = db.transact {
        val now = now()
        dao.upsert(
            PromptLogEntity(
                id = UuidV7.generate(now), moduleId = ModuleId.REMINDERS.value, trigger = PromptTrigger.REMINDER,
                triggerDetail = reminderId, format = format, phrasingId = PHRASING, scheduledFor = scheduledFor,
                sentAt = now, outcome = PromptOutcome.PENDING, outcomeAt = null, sync = stamper.stamp(),
            ),
        )
    }

    /** The user acted on (LOGGED) or swiped away (DISMISSED) the ping for [reminderId]. */
    suspend fun outcome(reminderId: String, outcome: PromptOutcome) =
        dao.pendingForTrigger(PromptTrigger.REMINDER).filter { it.triggerDetail == reminderId }.forEach { setOutcome(it, outcome) }

    private suspend fun setOutcome(row: PromptLogEntity, outcome: PromptOutcome) = db.transact {
        dao.upsert(row.copy(outcome = outcome, outcomeAt = now(), sync = stamper.stamp(row.sync)))
    }

    private companion object {
        const val HISTORY = 10
        const val PHRASING = "reminder"
    }
}

/** Starts and stops the ringing of an alarm (a foreground service). False when it couldn't start. */
interface AlarmRinger {
    fun ring(r: ReminderEntity): Boolean
    fun stop(id: String)
}

/**
 * What happens when a reminder's alarm goes off. Alarms ring on the main phone (full screen, looping
 * sound) and arrive as a quiet notification elsewhere. Pings respect quiet hours and rotate formats.
 */
@Singleton
class ReminderFirer @Inject constructor(
    private val db: KleanDatabase,
    private val repo: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val gate: ModuleGate,
    private val prefs: DevicePrefs,
    private val quiet: QuietHoursService,
    private val notifier: ReminderNotifier,
    private val ringer: AlarmRinger,
    private val log: ReminderPromptLog,
    private val clock: Clock,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
    private val audience: app.klean.core.notifications.PromptAudience,
    /** E.g. the watch ping (M27); told once the alarm rings here. */
    private val ringListeners: Set<@JvmSuppressWildcards app.klean.core.notifications.AlarmRingListener> = emptySet(),
) {
    enum class Result { SKIPPED, REARMED, RANG, ALARM_ELSEWHERE, PINGED }

    suspend fun fire(id: String): Result {
        val r = db.reminderDao().get(id) ?: return Result.SKIPPED
        if (r.sync.deleted || (r.state != SCHEDULED && r.state != SNOOZED)) return Result.SKIPPED
        if (!gate.isEnabled(ModuleId.REMINDERS)) return Result.SKIPPED
        val now = clock.now().toEpochMilliseconds()
        val due = ReminderTiming.dueAt(r)
        // Early (the clock moved) or, for a ping, inside quiet hours that changed after it was armed.
        if (due > now + EARLY_SLACK_MS || (!r.isAlarm && quiet.isQuiet(now))) {
            scheduler.arm(r)
            return Result.REARMED
        }
        repo.update(r.copy(state = RINGING, lastFiredAt = now, snoozedUntil = null))
        val result = if (r.isAlarm) {
            if (prefs.isMainPhone.first()) {
                if (!ringer.ring(r)) notifier.alarmFallback(r)
                ringListeners.tell(r.id, r.title, ringing = true)
                Result.RANG
            } else {
                notifier.ping(r, PromptFormat.STANDARD, quiet = true)
                Result.ALARM_ELSEWHERE
            }
        } else {
            val format = log.nextFormat()
            log.sent(r.id, format, due)
            // Several synced phones (M25): the one used last gets the sound, the others a silent copy.
            notifier.ping(r, format, quiet = !audience.isLoudHere())
            Result.PINGED
        }
        statusRefresh.get().refresh()
        return result
    }

    private companion object {
        const val EARLY_SLACK_MS = 60_000L
    }
}

/**
 * The buttons on a reminder (notification, alarm screen, Reminders tab). Done on a recurring reminder
 * moves it to its next time; on a one-off it's DONE (and an attached one-off task is completed by the
 * repository). Dismiss only stops it: it never completes a task.
 */
@Singleton
class ReminderActions @Inject constructor(
    private val db: KleanDatabase,
    private val repo: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val ringer: AlarmRinger,
    private val log: ReminderPromptLog,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
    private val ringListeners: Set<@JvmSuppressWildcards app.klean.core.notifications.AlarmRingListener> = emptySet(),
) {
    private fun now() = clock.now().toEpochMilliseconds()

    suspend fun done(id: String) = handled(id, PromptOutcome.LOGGED) { r ->
        val next = ReminderTiming.nextOccurrence(r, now())
        if (next != null) repo.update(r.copy(state = SCHEDULED, fireAt = next.fireAt, recurrence = next.recurrence, snoozedUntil = null))
        else repo.markDone(id)
    }

    suspend fun dismiss(id: String) = handled(id, PromptOutcome.LOGGED) { r ->
        val next = ReminderTiming.nextOccurrence(r, now())
        if (next != null) repo.update(r.copy(state = SCHEDULED, fireAt = next.fireAt, recurrence = next.recurrence, snoozedUntil = null))
        else repo.dismiss(id)
    }

    suspend fun snooze(id: String, option: Snooze) = handled(id, PromptOutcome.LOGGED) { r ->
        repo.snooze(id, ReminderTiming.snoozeUntil(option, now(), r, zone.current()))
    }

    /** "Pick a time": [minuteOfDay] today, or tomorrow if that has passed. */
    suspend fun snoozeTo(id: String, minuteOfDay: Int) = handled(id, PromptOutcome.LOGGED) {
        repo.snooze(id, ReminderTiming.pickedTime(minuteOfDay, now(), zone.current()))
    }

    /** Swiped away: it stays under "Earlier" on the Reminders tab; the next ping changes format. */
    suspend fun swiped(id: String) {
        log.outcome(id, PromptOutcome.DISMISSED)
    }

    private suspend fun handled(id: String, outcome: PromptOutcome, change: suspend (ReminderEntity) -> Unit) {
        ringer.stop(id)
        notifier.cancel(id)
        val r = db.reminderDao().get(id)?.takeUnless { it.sync.deleted } ?: return
        // A ringing alarm handled here (or on the alarm screen) stops buzzing the watch too.
        if (r.isAlarm && r.state == RINGING) ringListeners.tell(r.id, r.title, ringing = false)
        change(r)
        log.outcome(id, outcome)
        statusRefresh.get().refresh()
    }
}

private fun Set<app.klean.core.notifications.AlarmRingListener>.tell(id: String, title: String, ringing: Boolean) = forEach {
    runCatching { it.onAlarm(id, title, ringing) }.onFailure { e -> Log.w("Klean", "alarm listener failed", e) }
}

/** Receives a reminder's system alarm. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: ReminderFirer
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { firer.fire(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "reminder fire failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderEngineModule {
    @Binds @IntoSet abstract fun listener(s: ReminderScheduler): ReminderChangeListener
    @Binds @IntoSet abstract fun reschedulable(s: ReminderScheduler): Reschedulable
}
