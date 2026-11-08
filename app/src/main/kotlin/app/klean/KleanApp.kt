package app.klean

import android.app.Application
import app.klean.core.common.ApplicationScope
import app.klean.core.notifications.ChannelRegistrar
import app.klean.feature.diet.notification.DietStatusNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class KleanApp : Application() {
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dietStatus: DietStatusNotifier

    override fun onCreate() {
        super.onCreate()
        ChannelRegistrar.ensure(this)
        // Re-post the status line after install, update, or process death.
        appScope.launch { runCatching { dietStatus.refresh() } }
    }
}
