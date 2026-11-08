package app.klean.feature.diet.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes every placed diet widget after a write, a module toggle or a day rollover. */
@Singleton
class DietWidgetUpdater @Inject constructor(@ApplicationContext private val ctx: Context) : DietChangeListener {
    override suspend fun onChange(change: DietChange) = update()

    suspend fun update() = DietWidget().updateAll(ctx)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietWidgetModule {
    @Binds @IntoSet abstract fun widgetListener(u: DietWidgetUpdater): DietChangeListener
}
