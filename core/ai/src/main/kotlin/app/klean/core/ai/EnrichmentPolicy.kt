package app.klean.core.ai

import app.klean.shared.ai.AiTask
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Whether a write should be queued for AI enrichment. Never blocks a log: callers only read a flag. */
interface EnrichmentPolicy {
    suspend fun hasProviderFor(task: AiTask): Boolean
}

/** Until the provider layer exists (M10), nothing is enriched. */
class NoAiPolicy @Inject constructor() : EnrichmentPolicy {
    override suspend fun hasProviderFor(task: AiTask): Boolean = false
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EnrichmentPolicyModule {
    @Binds abstract fun policy(impl: NoAiPolicy): EnrichmentPolicy
}
