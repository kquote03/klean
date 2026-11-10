package app.klean.feature.workout.suggest

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.transact
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.QuietHoursService
import app.klean.core.notifications.Reschedulable
import app.klean.feature.workout.R
import app.klean.feature.workout.WorkoutKleanModule
import app.klean.feature.workout.data.WorkoutChangeListener
import app.klean.feature.workout.data.WorkoutKeys
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.shared.diet.prompt.FormatRotator
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptHistoryItem
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** Pure rules for "Suggest a workout after a few quiet days". */
object SuggestionPolicy {
    private const val DAY_MS = 86_400_000L

    /** No workout for [days] days (or none ever), and no suggestion in the last [days] days. */
    fun shouldSuggest(now: Long, lastSessionStart: Long?, lastSuggestedAt: Long, days: Int): Boolean {
        val gap = days.coerceAtLeast(1) * DAY_MS
        val quietLongEnough = lastSessionStart == null || now - lastSessionStart >= gap
        return quietLongEnough && now - lastSuggestedAt >= gap
    }

    /** Today at [minute] if still ahead, else tomorrow; then moved out of quiet hours. */
    inline fun nextAt(now: Long, minute: Int, zone: TimeZone, nextAllowed: (Long) -> Long): Long {
        val today = now.toLocal(zone).date
        val todayAt = atMinute(today, minute, zone)
        val at = if (todayAt > now) todayAt else atMinute(today.plus(DatePeriod(days = 1)), minute, zone)
        return nextAllowed(at)
    }
}

/** Suggestions in `prompt_log` (trigger WORKOUT): an ignored one changes format next time, never frequency. */
class WorkoutPromptLog @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
) {
    private val dao get() = db.promptLogDao()
    private fun now() = clock.now().toEpochMilliseconds()

    /** A new suggestion is going out: open ones count as ignored, then the rotation picks the format and phrasing. */
    suspend fun next(pool: List<String>, random: Random = Random.Default): Pair<PromptFormat, String> {
        dao.pendingForTrigger(PromptTrigger.WORKOUT).forEach { setOutcome(it, PromptOutcome.IGNORED) }
        val history = dao.recentForTrigger(PromptTrigger.WORKOUT, HISTORY).map { PromptHistoryItem(it.format, it.phrasingId, it.outcome) }
        return FormatRotator.nextFormat(history) to FormatRotator.nextPhrasing(pool, history, random)
    }

    suspend fun sent(format: PromptFormat, phrasingId: String, scheduledFor: Long) = db.transact {
        val now = now()
        dao.upsert(
            PromptLogEntity(
                id = UuidV7.generate(now), moduleId = ModuleId.WORKOUT.value, trigger = PromptTrigger.WORKOUT,
                triggerDetail = null, format = format, phrasingId = phrasingId, scheduledFor = scheduledFor,
                sentAt = now, outcome = PromptOutcome.PENDING, outcomeAt = null, sync = stamper.stamp(),
            ),
        )
    }

    /** Pending suggestions sent before [since] get [outcome] (LOGGED once a workout is logged after them). */
    suspend fun resolve(outcome: PromptOutcome, since: Long = Long.MAX_VALUE) =
        dao.pendingForTrigger(PromptTrigger.WORKOUT).filter { it.sentAt <= since }.forEach { setOutcome(it, outcome) }

    private suspend fun setOutcome(row: PromptLogEntity, outcome: PromptOutcome) = db.transact {
        dao.upsert(row.copy(outcome = outcome, outcomeAt = now(), sync = stamper.stamp(row.sync)))
    }

    private companion object {
        const val HISTORY = 10
    }
}

/**
 * The gentle suggestion: a daily alarm at 18:00 (moved out of quiet hours). When it fires on the main phone,
 * the setting is on and nothing was logged for N days, one notification in the rotated format, at most every
 * N days. Never escalates; the next one only changes format.
 */
@Singleton
class WorkoutSuggestion @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<WorkoutRepository>,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val prefs: DevicePrefs,
    private val quiet: QuietHoursService,
    private val alarms: AlarmScheduler,
    private val log: WorkoutPromptLog,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : Reschedulable, WorkoutChangeListener {
    private fun now() = clock.now().toEpochMilliseconds()

    override suspend fun reschedule() {
        WorkoutChannels.ensure(ctx)
        if (!gate.isEnabled(ModuleId.WORKOUT) || !settings.get(WorkoutKeys.SuggestEnabled)) {
            cancel()
            return
        }
        val at = SuggestionPolicy.nextAt(now(), settings.get(WorkoutKeys.SuggestMinute), zone.current()) { quiet.nextAllowed(it) }
        alarms.scheduleInexact(at, pending(ctx, create = true)!!)
    }

    fun cancel() {
        pending(ctx, create = false)?.let(alarms::cancel)
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }

    /** A workout was logged: a suggestion still showing has done its job. */
    override suspend fun onWorkoutsChanged() {
        val last = repo.get().lastSessionStart() ?: return
        log.resolve(PromptOutcome.LOGGED, since = last)
    }

    /** The alarm (or [force], the debug broadcast). Returns true when a suggestion was posted. */
    suspend fun onAlarm(force: Boolean = false): Boolean {
        try {
            if (!gate.isEnabled(ModuleId.WORKOUT)) return false
            if (!force) {
                if (!settings.get(WorkoutKeys.SuggestEnabled) || !prefs.isMainPhone.first() || quiet.isQuiet()) return false
                val ok = SuggestionPolicy.shouldSuggest(now(), repo.get().lastSessionStart(), settings.get(WorkoutKeys.SuggestLastAt), settings.get(WorkoutKeys.SuggestDays))
                if (!ok) return false
            }
            val pool = PHRASINGS.keys.toList()
            val (format, phrasing) = log.next(pool)
            if (!post(format, phrasing)) return false
            log.sent(format, phrasing, now())
            settings.set(WorkoutKeys.SuggestLastAt, now())
            return true
        } finally {
            if (!force) reschedule()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun post(format: PromptFormat, phrasing: String): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return false
        WorkoutChannels.ensure(ctx)
        val first = repo.get().observeTemplates().first().firstOrNull()
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(Intent.ACTION_VIEW, WorkoutKleanModule.DEEP_LINK.toUri()).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val start = PendingIntent.getActivity(
            ctx, 1, Intent(Intent.ACTION_VIEW, WorkoutKleanModule.startLink(first?.id).toUri()).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismissed = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, WorkoutSuggestionReceiver::class.java).setAction(ACTION_DISMISSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = first?.let { ctx.getString(R.string.workout_start_named, it.name) } ?: ctx.getString(R.string.workout_start_empty)
        val n = NotificationCompat.Builder(ctx, WorkoutChannels.forFormat(format))
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle(ctx.getString(PHRASINGS.getValue(phrasing)))
            .setContentText(ctx.getString(R.string.workout_suggest_body))
            .setPriority(if (format == PromptFormat.HEADS_UP) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .addAction(0, label, start)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        return true
    }

    suspend fun dismissed() = log.resolve(PromptOutcome.DISMISSED)

    companion object {
        const val NOTIFICATION_ID = 5101
        const val REQUEST_CODE = 5101
        const val ACTION_SUGGEST = "app.klean.workout.SUGGEST"
        const val ACTION_DISMISSED = "app.klean.workout.SUGGEST_DISMISSED"
        val PHRASINGS = mapOf(
            "suggest_1" to R.string.workout_suggest_1,
            "suggest_2" to R.string.workout_suggest_2,
            "suggest_3" to R.string.workout_suggest_3,
        )

        fun pending(ctx: Context, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, Intent(ctx, WorkoutSuggestionReceiver::class.java).setAction(ACTION_SUGGEST),
            PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )
    }
}

/** One channel per rotated format, created by the module itself so `Channels.kt` stays untouched. */
object WorkoutChannels {
    const val SUGGEST = "workout_suggest"
    const val SUGGEST_HEADS_UP = "workout_suggest_heads_up"
    const val SUGGEST_VIBRATE = "workout_suggest_vibrate"
    const val SUGGEST_QUIET = "workout_suggest_quiet"

    fun forFormat(f: PromptFormat): String = when (f) {
        PromptFormat.STANDARD -> SUGGEST
        PromptFormat.HEADS_UP -> SUGGEST_HEADS_UP
        PromptFormat.SILENT_VIBRATE -> SUGGEST_VIBRATE
        PromptFormat.WIDGET_HIGHLIGHT -> SUGGEST_QUIET
    }

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(SUGGEST, ctx.getString(R.string.workout_channel_suggest), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(SUGGEST_HEADS_UP, ctx.getString(R.string.workout_channel_suggest_heads_up), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SUGGEST_VIBRATE, ctx.getString(R.string.workout_channel_suggest_vibrate), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 80, 120, 80)
                },
                NotificationChannel(SUGGEST_QUIET, ctx.getString(R.string.workout_channel_suggest_quiet), NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}

/**
 * The suggestion alarm and the swipe-away. Debug builds also accept `<applicationId>.WORKOUT_SUGGEST`
 * (debug manifest), which posts a suggestion at once.
 */
@AndroidEntryPoint
class WorkoutSuggestionReceiver : BroadcastReceiver() {
    @Inject lateinit var suggestion: WorkoutSuggestion
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val debug = intent.action == context.packageName + ".WORKOUT_SUGGEST"
        val action = intent.action
        if (action != WorkoutSuggestion.ACTION_SUGGEST && action != WorkoutSuggestion.ACTION_DISMISSED && !debug) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    if (action == WorkoutSuggestion.ACTION_DISMISSED) suggestion.dismissed() else suggestion.onAlarm(force = debug)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "workout suggestion failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutSuggestionModule {
    @Binds @IntoSet abstract fun reschedulable(s: WorkoutSuggestion): Reschedulable
    @Binds @IntoSet abstract fun listener(s: WorkoutSuggestion): WorkoutChangeListener
}
