package app.klean.core.notifications

import app.klean.core.common.ZoneProvider
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.QuietHoursSource
import app.klean.core.data.sleep.SettingsQuietHoursSource
import app.klean.shared.time.QuietHours
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * The global quiet-hours rule (the sleep window). Every prompt, sheet and delivery nudge asks here
 * before it interrupts. Exact user alarms (future reminders/alarms module) are exempt.
 *
 * M20: the window comes from the sleep schedule ([QuietHoursSource] = `SleepScheduleRepository`), whether
 * or not the Sleep module is on. The settings-only constructor keeps older tests and callers working.
 */
@Singleton
class QuietHoursService @Inject constructor(
    private val source: QuietHoursSource,
    private val zone: ZoneProvider,
    private val clock: Clock,
) {
    constructor(settings: SettingsRepository, zone: ZoneProvider, clock: Clock) : this(SettingsQuietHoursSource(settings), zone, clock)

    val quietHours: Flow<QuietHours> = source.quietHours

    /** [exactUserAlarm]: an alarm the user set for an exact time; those ring even in quiet hours. */
    suspend fun isQuiet(at: Long = clock.now().toEpochMilliseconds(), exactUserAlarm: Boolean = false): Boolean =
        !exactUserAlarm && source.current().contains(at, zone.current())

    /** First instant at or after [at] outside quiet hours. */
    suspend fun nextAllowed(at: Long): Long = source.current().nextAllowed(at, zone.current())
}
