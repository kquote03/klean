package app.klean.core.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.device.DeviceRepository
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.notifications.NotificationIds
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.sync.SyncRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The notification a prompt was posted as: `(tag, id)`, as its module posts it. */
data class PromptNotification(val tag: String?, val id: Int)

object PromptNotifications {
    /** Kept equal to `ReminderNotifier.NOTIFICATION_ID` and `WorkoutSuggestion.NOTIFICATION_ID` (checked in `:app` tests). */
    const val REMINDER_ID = 3001
    const val WORKOUT_ID = 5101

    fun of(trigger: String?, detail: String?): PromptNotification? = when (trigger) {
        PromptTrigger.CADENCE.name, PromptTrigger.LEARNED_MEAL.name -> PromptNotification(null, NotificationIds.DIET_PROMPT)
        PromptTrigger.DELIVERY.name -> PromptNotification(null, NotificationIds.DELIVERY)
        PromptTrigger.GEOFENCE.name -> PromptNotification(null, NotificationIds.GEOFENCE)
        PromptTrigger.UNLOCK_SHEET.name -> PromptNotification(null, NotificationIds.UNLOCK)
        PromptTrigger.REMINDER.name -> detail?.let { PromptNotification(it, REMINDER_ID) }
        PromptTrigger.WORKOUT.name -> PromptNotification(null, WORKOUT_ID)
        else -> null
    }

    /** Prompts answered on another device: LOGGED or DISMISSED there. */
    fun answeredElsewhere(records: List<SyncRecord>, me: String): List<PromptNotification> = records
        .filter { it.table == "prompt_log" && it.deviceId != me && !it.deleted }
        .filter { (it.json["outcome"] as? JsonPrimitive)?.contentOrNull in ANSWERED }
        .mapNotNull { of((it.json["trigger"] as? JsonPrimitive)?.contentOrNull, (it.json["trigger_detail"] as? JsonPrimitive)?.contentOrNull) }
        .distinct()

    private val ANSWERED = setOf(PromptOutcome.LOGGED.name, PromptOutcome.DISMISSED.name)
}

/** Clears this phone's copy of a prompt someone answered on another device. */
class PromptAnsweredElsewhere @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: DevicePrefs,
) : SyncApplyListener {
    override suspend fun onApplied(records: List<SyncRecord>) {
        val nm = NotificationManagerCompat.from(ctx)
        PromptNotifications.answeredElsewhere(records, prefs.deviceId()).forEach { nm.cancel(it.tag, it.id) }
    }
}

/**
 * Started once from `KleanApp`: keeps the periodic sync scheduled while paired, asks for a run 10 s after the
 * last local change (any `SyncStamper` write bumps `change_counter`; pulled rows don't), and refreshes this
 * phone's `device.last_seen_at` when Klean comes to the foreground, so prompts follow the phone in use.
 */
@Singleton
class SyncStartup @Inject constructor(
    private val db: KleanDatabase,
    private val accounts: SyncAccounts,
    private val scheduler: SyncScheduler,
    private val devices: DeviceRepository,
    private val clock: Clock,
) {
    private val lastSeenTouch = AtomicLong(0)

    @OptIn(FlowPreview::class)
    fun start(app: Application, scope: CoroutineScope) {
        scope.launch {
            runCatching { if (accounts.account() != null) scheduler.schedulePeriodic() }
                .onFailure { Log.w("Klean", "sync: schedule failed", it) }
            runCatching {
                db.invalidationTracker.createFlow(CHANGE_COUNTER, emitInitialState = false)
                    .debounce(DEBOUNCE)
                    .collect { if (accounts.account() != null) scheduler.syncNow() }
            }.onFailure { Log.w("Klean", "sync: change trigger stopped", it) }
        }
        lastSeenTouch.set(clock.now().toEpochMilliseconds()) // KleanApp registers the device at start
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val now = clock.now().toEpochMilliseconds()
                val last = lastSeenTouch.get()
                if (now - last < TOUCH_EVERY_MS || !lastSeenTouch.compareAndSet(last, now)) return
                scope.launch { runCatching { devices.registerThisDevice() } }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private companion object {
        const val CHANGE_COUNTER = "change_counter"
        val DEBOUNCE = 10.seconds
        const val TOUCH_EVERY_MS = 10 * 60_000L
    }
}
