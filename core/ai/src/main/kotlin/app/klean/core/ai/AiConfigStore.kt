package app.klean.core.ai

import android.util.Log
import app.klean.core.data.prefs.DevicePrefs
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Keys live in SecretStore, never here. Everything is off until the user turns it on. */
@Serializable
data class GeminiConfig(val enabled: Boolean = false, val model: String = "gemini-2.5-flash", val sendPhotos: Boolean = false)

@Serializable
data class OpenAiConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "http://192.168.1.10:11434/v1",
    val model: String = "llama3.2",
    val acceptsImages: Boolean = false,
)

/** [modelPath] is set only by [app.klean.core.ai.ondevice.OnDeviceModels] once a model is in place. */
@Serializable
data class OnDeviceConfig(
    val enabled: Boolean = false,
    val modelPath: String? = null,
    val downloadUrl: String = DEFAULT_MODEL_URL,
) {
    companion object {
        /** Qwen3 0.6B for LiteRT-LM, ~614 MB, Apache-2.0, not gated (see core/ai/ON_DEVICE.md). */
        const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
        const val DEFAULT_MODEL_MB = 614
    }
}

@Serializable
data class AiProvidersConfig(
    val gemini: GeminiConfig = GeminiConfig(),
    val openAi: OpenAiConfig = OpenAiConfig(),
    val onDevice: OnDeviceConfig = OnDeviceConfig(),
)

data class AiConfig(
    val providers: AiProvidersConfig = AiProvidersConfig(),
    val assignments: Map<AiTask, ProviderId> = emptyMap(),
)

interface AiConfigStore {
    val config: Flow<AiConfig>
    suspend fun current(): AiConfig = config.first()
    suspend fun assignment(task: AiTask): ProviderId? = current().assignments[task]
    suspend fun updateProviders(transform: (AiProvidersConfig) -> AiProvidersConfig)
    suspend fun assign(task: AiTask, provider: ProviderId?)
}

/** Something that reacts to provider or assignment changes (e.g. the enrichment sweep). */
fun interface AiConfigListener {
    suspend fun onAiConfigChanged()
}

/** Device-local (DataStore `device_prefs`): provider configs and task assignments as JSON. */
@Singleton
class DevicePrefsAiConfigStore @Inject constructor(
    private val prefs: DevicePrefs,
    private val listeners: Set<@JvmSuppressWildcards AiConfigListener>,
) : AiConfigStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    override val config: Flow<AiConfig> = combine(prefs.aiProviders, prefs.aiTaskAssignment) { p, a ->
        AiConfig(decodeProviders(p), decodeAssignments(a))
    }

    override suspend fun updateProviders(transform: (AiProvidersConfig) -> AiProvidersConfig) {
        val changed = lock.withLock {
            val prev = decodeProviders(prefs.aiProviders.first())
            val next = transform(prev)
            if (next != prev) prefs.setAiProviders(json.encodeToString(AiProvidersConfig.serializer(), next))
            next != prev
        }
        if (changed) notifyListeners()
    }

    override suspend fun assign(task: AiTask, provider: ProviderId?) {
        val changed = lock.withLock {
            val prev = decodeAssignments(prefs.aiTaskAssignment.first())
            val next = if (provider == null) prev - task else prev + (task to provider)
            if (next != prev) prefs.setAiTaskAssignment(json.encodeToString(next.map { (k, v) -> k.name to v.name }.toMap()))
            next != prev
        }
        if (changed) notifyListeners()
    }

    private suspend fun notifyListeners() = listeners.forEach {
        try {
            it.onAiConfigChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Klean", "AI config listener failed", e)
        }
    }

    private fun decodeProviders(s: String?): AiProvidersConfig =
        s?.let { runCatching { json.decodeFromString(AiProvidersConfig.serializer(), it) }.getOrNull() } ?: AiProvidersConfig()

    private fun decodeAssignments(s: String?): Map<AiTask, ProviderId> {
        if (s == null) return emptyMap()
        val raw = runCatching { json.decodeFromString<Map<String, String>>(s) }.getOrNull() ?: return emptyMap()
        return raw.mapNotNull { (k, v) ->
            val t = AiTask.entries.firstOrNull { it.name == k }
            val p = ProviderId.entries.firstOrNull { it.name == v }
            if (t != null && p != null) t to p else null
        }.toMap()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiConfigModule {
    @Binds abstract fun store(impl: DevicePrefsAiConfigStore): AiConfigStore
    @Multibinds abstract fun listeners(): Set<AiConfigListener>
}
