package app.klean.core.ai.provider

import app.klean.core.ai.AiProvider
import app.klean.core.ai.ondevice.OnDeviceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** The providers AiGateway can choose from. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderBindings {
    @Binds @IntoSet abstract fun gemini(p: GeminiProvider): AiProvider
    @Binds @IntoSet abstract fun openAiCompat(p: OpenAiCompatProvider): AiProvider
    @Binds @IntoSet abstract fun onDevice(p: OnDeviceProvider): AiProvider
}
