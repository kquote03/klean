package app.klean

import android.content.Context
import android.content.Intent
import app.klean.core.data.backup.BuildConfigProvider
import app.klean.core.data.backup.ProcessRestarter
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.nav.DevicePrefsNewModules
import app.klean.nav.NewModulesStore
import app.klean.today.DevicePrefsTodayLayout
import app.klean.today.QuietHoursSummary
import app.klean.today.TodayLayoutStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlin.system.exitProcess

@Module
@InstallIn(SingletonComponent::class)
object AppBindingsModule {
    /** The app's own tile on Today ("Quiet hours · until 8:00 AM"). */
    @Provides @IntoSet
    fun quietHoursTile(impl: QuietHoursSummary): DashboardSummaryProvider = impl

    @Provides
    fun newModules(impl: DevicePrefsNewModules): NewModulesStore = impl

    @Provides
    fun todayLayout(impl: DevicePrefsTodayLayout): TodayLayoutStore = impl

    @Provides
    fun buildConfig(): BuildConfigProvider = object : BuildConfigProvider {
        override val versionName: String = BuildConfig.VERSION_NAME
    }

    /**
     * After a restore: start MainActivity in a fresh task, then end this process so every Hilt
     * singleton (database, DataStore, caches) is rebuilt on the restored files.
     */
    @Provides
    fun processRestarter(@ApplicationContext ctx: Context): ProcessRestarter = ProcessRestarter {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        exitProcess(0)
    }
}
