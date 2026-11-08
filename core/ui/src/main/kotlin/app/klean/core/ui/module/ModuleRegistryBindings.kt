package app.klean.core.ui.module

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the set so the app builds even when no feature contributes a module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleSetModule {
    @Multibinds abstract fun kleanModules(): Set<KleanModule>
}
