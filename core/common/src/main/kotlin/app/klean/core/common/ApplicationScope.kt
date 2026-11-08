package app.klean.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone

/** Scope that outlives any screen. Use for fire-and-forget work started from receivers or repositories. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Current device time zone. Injected so logic and tests never read the system zone directly. */
interface ZoneProvider {
    fun current(): TimeZone
}

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun zone(): ZoneProvider = object : ZoneProvider {
        override fun current() = TimeZone.currentSystemDefault()
    }
}
