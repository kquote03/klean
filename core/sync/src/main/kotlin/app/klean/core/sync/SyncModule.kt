package app.klean.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.sync.PairedBackend
import app.klean.core.ui.module.OnboardingStepProvider
import app.klean.core.sync.ui.SyncOnboardingStep
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Multibinds abstract fun applyListeners(): Set<SyncApplyListener>

    @Binds @IntoSet abstract fun promptAnswered(l: PromptAnsweredElsewhere): SyncApplyListener

    @Binds abstract fun pairedBackend(a: SyncAccounts): PairedBackend

    @Binds @IntoSet abstract fun onboarding(s: SyncOnboardingStep): OnboardingStepProvider

    companion object {
        @Provides @SyncPrefsStore
        fun store(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.applicationContext.syncDataStore

        @Provides @Singleton @SyncHttp
        fun http(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 30_000
            }
        }

        @Provides @Singleton
        fun engine(
            db: KleanDatabase,
            prefs: DevicePrefs,
            @ApplicationContext ctx: Context,
            listeners: Set<@JvmSuppressWildcards SyncApplyListener>,
        ): SyncEngine = SyncEngine(RoomLocalStore(db), BlobFiles(ctx.filesDir), { prefs.deviceId() }, listeners)
    }
}
