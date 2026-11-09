package app.klean

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.klean.core.common.ApplicationScope
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Rescheduler
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.feature.diet.unlock.ScreenStateMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class KleanApp : Application(), Configuration.Provider {
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dietStatus: DietStatusNotifier
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var rescheduler: Rescheduler
    @Inject lateinit var screenState: ScreenStateMonitor

    /** WorkManager is initialised on demand with Hilt's factory; the default initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ChannelRegistrar.ensure(this)
        // Unlock sheet: screen on/off can only be received by a runtime receiver, for the life of the process.
        runCatching { screenState.start() }.onFailure { Log.w("Klean", "screen monitor failed", it) }
        // Re-post the status line after install, update, or process death.
        appScope.launch { runCatching { dietStatus.refresh() } }
        // Arm prompts and rollover right after install or process start, without waiting for a boot.
        // Never let a startup failure crash the process (or a Robolectric test without a SQLite driver).
        appScope.launch { runCatching { rescheduler.rescheduleAll() }.onFailure { Log.w("Klean", "startup reschedule failed", it) } }
    }
}
