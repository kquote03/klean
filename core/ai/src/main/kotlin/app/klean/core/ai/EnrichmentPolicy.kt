package app.klean.core.ai

import app.klean.shared.ai.AiTask

/**
 * Whether a write should be queued for AI enrichment. Never blocks a log: callers only read a flag.
 * Bound to [AiGateway].
 */
interface EnrichmentPolicy {
    suspend fun hasProviderFor(task: AiTask): Boolean
}
