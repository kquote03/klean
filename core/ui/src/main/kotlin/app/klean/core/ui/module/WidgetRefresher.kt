package app.klean.core.ui.module

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Re-renders every module's home-screen widgets, e.g. after the colour scheme changed. */
fun interface WidgetRefresher {
    fun refreshAll()
}

/** Sends APPWIDGET_UPDATE to each module's [KleanModule.widgetReceiver] that has widgets placed. */
class ModuleWidgetRefresher @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val registry: ModuleRegistry,
) : WidgetRefresher {
    override fun refreshAll() {
        val awm = AppWidgetManager.getInstance(ctx) ?: return
        registry.all.mapNotNull { it.widgetReceiver }.forEach { receiver ->
            try {
                val component = ComponentName(ctx, receiver)
                val ids = awm.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    ctx.sendBroadcast(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .setComponent(component)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                    )
                }
            } catch (e: RuntimeException) {
                Log.w("Klean", "widget refresh failed for ${receiver.simpleName}", e)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetRefresherModule {
    @Binds abstract fun refresher(impl: ModuleWidgetRefresher): WidgetRefresher
}
