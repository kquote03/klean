package app.klean.core.ai

import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class Capability { IMAGE_INPUT, OFFLINE }

class StructuredRequest(
    val system: String,
    val user: String,
    val schema: JsonObject,
    val imageJpeg: ByteArray? = null,
) {
    fun withoutImage() = StructuredRequest(system, user, schema, null)
}

sealed interface AiOutcome {
    data class Success(val json: JsonElement, val provider: ProviderId, val model: String?) : AiOutcome
    data object NoProvider : AiOutcome
    /** Worth retrying later: offline, timeout, rate limit, server error. */
    data class Unavailable(val reason: String) : AiOutcome
    /** Retrying won't help: bad key, rejected request, unreadable answer. */
    data class Failed(val reason: String) : AiOutcome
}

interface AiProvider {
    val id: ProviderId
    val needsNetwork: Boolean
    suspend fun capabilities(): Set<Capability>
    /** Explicitly enabled by the user and has what it needs (key, URL). This is the cloud-consent gate. */
    suspend fun isConfiguredAndEnabled(): Boolean
    /** Whether the user allows photos to go to this provider. Checked on top of [Capability.IMAGE_INPUT]. */
    suspend fun photosAllowed(): Boolean = true
    suspend fun generate(req: StructuredRequest): AiOutcome
}

/**
 * The single way to use AI. Enforces: a provider is used only if assigned to the task and explicitly
 * enabled; images go only to providers that support them and only when the user allowed photos;
 * every call ends within [TIMEOUT_MS]. Never throws.
 */
@Singleton
class AiGateway @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards AiProvider>,
    private val config: AiConfigStore,
) : EnrichmentPolicy {
    suspend fun providerFor(task: AiTask): AiProvider? {
        val id = config.assignment(task) ?: return null
        return providers.firstOrNull { it.id == id && it.isConfiguredAndEnabled() }
    }

    /** The provider with [id], enabled or not (settings and the "Test connection" path). */
    fun provider(id: ProviderId): AiProvider? = providers.firstOrNull { it.id == id }

    override suspend fun hasProviderFor(task: AiTask): Boolean = providerFor(task) != null

    suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean = true): AiOutcome {
        val p = providerFor(task) ?: return AiOutcome.NoProvider
        return runWith(p, req, sendImage)
    }

    /** Calls [p] directly with the same image and timeout rules. Callers must have checked consent. */
    suspend fun runWith(p: AiProvider, req: StructuredRequest, sendImage: Boolean = true): AiOutcome {
        val keepImage = req.imageJpeg != null && sendImage &&
            Capability.IMAGE_INPUT in p.capabilities() && p.photosAllowed()
        val r = if (req.imageJpeg != null && !keepImage) req.withoutImage() else req
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { p.generate(r) } ?: AiOutcome.Unavailable("timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiOutcome.Unavailable(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    companion object {
        const val TIMEOUT_MS = 45_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiGatewayModule {
    @Binds abstract fun policy(impl: AiGateway): EnrichmentPolicy
    @Multibinds abstract fun providers(): Set<AiProvider>
}
