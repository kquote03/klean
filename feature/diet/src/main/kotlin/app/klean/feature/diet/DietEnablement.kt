package app.klean.feature.diet

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.klean.core.capture.CaptureTileService
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.feature.diet.prompt.DietPrompts
import app.klean.feature.diet.widget.DietWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** What switching the diet module off or on does outside the UI. */
fun interface DietEnablement {
    suspend fun onEnabledChanged(enabled: Boolean)
}

/**
 * Off: every diet notification goes, check-ins stop, the widget shows "Klean / Open", the tile
 * re-renders. On: status line, check-ins and widget come back. Each step runs even if another fails.
 */
class DefaultDietEnablement @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val status: DietStatusNotifier,
    private val prompts: DietPrompts,
    private val widget: DietWidgetUpdater,
) : DietEnablement {
    override suspend fun onEnabledChanged(enabled: Boolean) {
        if (enabled) {
            step("status") { status.refresh() }
            step("prompts") { prompts.reschedule() }
        } else {
            val nm = NotificationManagerCompat.from(ctx)
            DIET_NOTIFICATIONS.forEach(nm::cancel)
            step("prompts") { prompts.cancel() }
        }
        step("widget") { widget.update() }
        CaptureTileService.requestUpdate(ctx)
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Klean", "diet toggle step $name failed", e)
        }
    }

    companion object {
        val DIET_NOTIFICATIONS = listOf(
            NotificationIds.DIET_STATUS, NotificationIds.DIET_PROMPT, NotificationIds.UNLOCK,
            NotificationIds.DELIVERY, NotificationIds.GEOFENCE,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietEnablementModule {
    @Binds abstract fun enablement(impl: DefaultDietEnablement): DietEnablement
}
