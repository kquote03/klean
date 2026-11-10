package app.klean

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.klean.core.capture.status.KleanStatusNotifier
import app.klean.core.common.ApplicationScope
import app.klean.core.data.device.DeviceRepository
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Rescheduler
import app.klean.core.sync.SyncStartup
import app.klean.core.ui.module.ModuleRegistry
import app.klean.nav.NewModulesStore
import app.klean.feature.diet.unlock.ScreenStateMonitor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class KleanApp : Application(), Configuration.Provider {
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var status: KleanStatusNotifier
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var rescheduler: Rescheduler
    @Inject lateinit var screenState: ScreenStateMonitor
    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var newModules: NewModulesStore
    @Inject lateinit var devices: DeviceRepository
    @Inject lateinit var sync: SyncStartup

    /** WorkManager is initialised on demand with Hilt's factory; the default initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ChannelRegistrar.ensure(this)
        // Unlock sheet: screen on/off can only be received by a runtime receiver, for the life of the process.
        runCatching { screenState.start() }.onFailure { Log.w("Klean", "screen monitor failed", it) }
        // Sync (M25): periodic work while paired, a run 10 s after local changes, last-seen on foreground.
        runCatching { sync.start(this, appScope) }.onFailure { Log.w("Klean", "sync startup failed", it) }
        appScope.launch {
            // Modules added by an update arrive on; an existing install lists them once ("New in Klean").
            // Runs first, so the reschedule below already sees every module.
            runCatching { newModules.add(registry.seed()) }.onFailure { Log.w("Klean", "module seeding failed", it) }
            // Arm prompts and rollover right after install or process start, without waiting for a boot.
            // Never let a startup failure crash the process (or a Robolectric test without a SQLite driver).
            launch { runCatching { rescheduler.rescheduleAll() }.onFailure { Log.w("Klean", "startup reschedule failed", it) } }
            // This install's `device` row (name, main phone, last seen) for sync.
            launch { runCatching { devices.registerThisDevice() }.onFailure { Log.w("Klean", "device registration failed", it) } }
            // Re-post the Today status line after install, update, or process death, then follow its line.
            // Follow only when the database works: a Robolectric :app test has no SQLite JNI, and a live
            // Room flow there fails in Room's own scope, where no catch can reach it.
            runCatching { status.refresh() }.onSuccess { status.start(this) }
        }
    }
}
