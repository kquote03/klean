package app.klean.watch

import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.core.notifications.AlarmRingListener
import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WearCodec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** An alarm ringing on the main phone buzzes every connected watch (`/klean/alarm`). Fire and forget. */
class WearAlarmPing @Inject constructor(
    private val layer: WearDataLayer,
    @ApplicationScope private val scope: CoroutineScope,
) : AlarmRingListener {
    override fun onAlarm(reminderId: String, title: String, ringing: Boolean) {
        if (!layer.available) return
        val (path, bytes) = WearCodec.encode(WatchMessage.Alarm(reminderId, title, ringing))
        scope.launch {
            runCatching { layer.sendToWatches(path, bytes) }.onFailure { Log.w("Klean", "watch alarm ping failed", it) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WearAlarmModule {
    @Binds @IntoSet abstract fun ping(p: WearAlarmPing): AlarmRingListener
}
