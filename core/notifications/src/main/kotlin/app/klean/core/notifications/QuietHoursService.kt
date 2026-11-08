package app.klean.core.notifications

import app.klean.core.common.ZoneProvider
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.shared.time.QuietHours
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * The global quiet-hours rule (the sleep window). Every prompt, sheet and delivery nudge asks here
 * before it interrupts. Exact user alarms (future reminders/alarms module) are exempt.
 */
@Singleton
class QuietHoursService @Inject constructor(
    private val settings: SettingsRepository,
    private val zone: ZoneProvider,
    private val clock: Clock,
) {
    val quietHours: Flow<QuietHours> = settings.observe(SyncedKeys.QuietHoursKey)

    /** [exactUserAlarm]: an alarm the user set for an exact time; those ring even in quiet hours. */
    suspend fun isQuiet(at: Long = clock.now().toEpochMilliseconds(), exactUserAlarm: Boolean = false): Boolean =
        !exactUserAlarm && settings.get(SyncedKeys.QuietHoursKey).contains(at, zone.current())

    /** First instant at or after [at] outside quiet hours. */
    suspend fun nextAllowed(at: Long): Long = settings.get(SyncedKeys.QuietHoursKey).nextAllowed(at, zone.current())
}
