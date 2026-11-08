package app.klean.feature.diet.prompt

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.Reschedulable
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.feature.diet.widget.DietWidgetUpdater
import app.klean.shared.module.ModuleId
import app.klean.shared.time.DayWindows
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * At the start of each logical day (the middle of the sleep window) the status line and the widget
 * reset to the new day. An inexact alarm: a few minutes late is fine, nobody is awake.
 */
@Singleton
class DayRollover @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val alarms: AlarmScheduler,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val status: DietStatusNotifier,
    private val widget: DietWidgetUpdater,
) : Reschedulable, DietChangeListener {
    /** Cheap, and keeps the alarm right after a sleep-window change made while the app was open. */
    override suspend fun onChange(change: DietChange) = reschedule()

    override suspend fun reschedule() {
        if (!gate.isEnabled(ModuleId.DIET)) {
            alarms.cancel(pendingIntent())
            return
        }
        alarms.scheduleInexact(nextDayStart(), pendingIntent())
    }

    /** The day turned: show the new (empty) day everywhere, then arm the next turn. */
    suspend fun onRollover() {
        if (gate.isEnabled(ModuleId.DIET)) {
            runCatching { status.refresh() }.onFailure { Log.w("Klean", "rollover status refresh failed", it) }
            runCatching { widget.update() }.onFailure { Log.w("Klean", "rollover widget update failed", it) }
        }
        reschedule()
    }

    internal suspend fun nextDayStart(): Long {
        val now = clock.now().toEpochMilliseconds()
        val q = settings.get(SyncedKeys.QuietHoursKey)
        val tz = zone.current()
        val today = DayWindows.logicalDate(now, q, tz)
        return DayWindows.forDate(today.plus(1, DateTimeUnit.DAY), q, tz).start
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQUEST_CODE, Intent(ctx, DayRolloverReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val REQUEST_CODE = 21
    }
}

@AndroidEntryPoint
class DayRolloverReceiver : BroadcastReceiver() {
    @Inject lateinit var rollover: DayRollover
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { rollover.onRollover() }
            } catch (e: Exception) {
                Log.w("Klean", "day rollover failed", e)
            } finally {
                pending?.finish()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DayRolloverModule {
    @Binds @IntoSet abstract fun reschedulable(r: DayRollover): Reschedulable
    @Binds @IntoSet abstract fun listener(r: DayRollover): DietChangeListener
}
