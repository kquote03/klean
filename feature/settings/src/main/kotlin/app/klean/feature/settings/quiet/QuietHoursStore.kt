package app.klean.feature.settings.quiet

import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.notifications.Rescheduler
import app.klean.shared.time.QuietHours
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The sleep window. An interface so setup and settings can be tested with a fake. */
interface QuietHoursStore {
    val quietHours: Flow<QuietHours>
    suspend fun set(value: QuietHours)
}

/**
 * M20: an editor of the sleep schedule (which is the source of quiet hours; the old synced key is kept as
 * a mirror). Writes, then replans everything time-based: check-ins planned under the old window must not
 * land inside the new one, and the bedtime reminder follows the new bedtime.
 */
class SettingsQuietHoursStore @Inject constructor(
    private val schedule: SleepScheduleRepository,
    private val rescheduler: Rescheduler,
) : QuietHoursStore {
    override val quietHours: Flow<QuietHours> = schedule.quietHours

    override suspend fun set(value: QuietHours) {
        schedule.setWindow(value)
        rescheduler.rescheduleAll()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QuietHoursStoreModule {
    @Binds abstract fun store(impl: SettingsQuietHoursStore): QuietHoursStore
}
