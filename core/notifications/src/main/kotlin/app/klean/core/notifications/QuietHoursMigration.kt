package app.klean.core.notifications

import app.klean.core.data.sleep.SleepScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * M20: moves the old synced quiet-hours key into the sleep schedule on the first start after the update
 * (and is a no-op ever after). Runs with every reschedule (app start, boot, update), so no startup hook is
 * needed; readers see the same window before and after it runs.
 */
class QuietHoursMigration @Inject constructor(private val schedule: SleepScheduleRepository) : Reschedulable {
    override suspend fun reschedule() = schedule.ensureMigrated()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QuietHoursMigrationModule {
    @Binds @IntoSet abstract fun migration(m: QuietHoursMigration): Reschedulable
}
