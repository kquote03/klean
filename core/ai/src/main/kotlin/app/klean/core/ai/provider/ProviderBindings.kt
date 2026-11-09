package app.klean.core.ai.provider

import app.klean.core.ai.AiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** The providers AiGateway can choose from. The on-device provider joins in M14. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderBindings {
    @Binds @IntoSet abstract fun gemini(p: GeminiProvider): AiProvider
}
