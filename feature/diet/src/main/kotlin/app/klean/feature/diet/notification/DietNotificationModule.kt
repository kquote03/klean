package app.klean.feature.diet.notification

import app.klean.feature.diet.data.DietChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DietNotificationModule {
    @Binds @IntoSet abstract fun statusListener(n: DietStatusNotifier): DietChangeListener
}
