package app.klean.feature.settings.quiet

import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.Rescheduler
import app.klean.shared.time.QuietHours
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The sleep window (synced `core.quietHours`). An interface so setup and settings can be tested with a fake. */
interface QuietHoursStore {
    val quietHours: Flow<QuietHours>
    suspend fun set(value: QuietHours)
}

/**
 * Writes the synced setting, then replans everything time-based: check-ins planned under the old
 * window must not land inside the new one.
 */
class SettingsQuietHoursStore @Inject constructor(
    private val settings: SettingsRepository,
    private val rescheduler: Rescheduler,
) : QuietHoursStore {
    override val quietHours: Flow<QuietHours> = settings.observe(SyncedKeys.QuietHoursKey)

    override suspend fun set(value: QuietHours) {
        settings.set(SyncedKeys.QuietHoursKey, value)
        rescheduler.rescheduleAll()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QuietHoursStoreModule {
    @Binds abstract fun store(impl: SettingsQuietHoursStore): QuietHoursStore
}
