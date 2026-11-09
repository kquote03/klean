package app.klean.core.ai.ondevice

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.Capability
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.provider.AiReasons
import app.klean.core.ai.provider.extractJsonObject
import app.klean.core.common.ApplicationScope
import app.klean.shared.ai.ProviderId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Tells the provider when the system wants memory back. An interface so tests can pull the trigger. */
fun interface MemoryTrimSource {
    fun onTrim(block: () -> Unit)
}

class AndroidMemoryTrimSource @Inject constructor(@ApplicationContext private val ctx: Context) : MemoryTrimSource {
    override fun onTrim(block: () -> Unit) = ctx.registerComponentCallbacks(
        object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION") // still delivered; the only signal that Klean went to the background
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) block()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = block()
        },
    )
}

/**
 * A language model running on this phone (Task 14.2). Nothing leaves the phone, so it works offline and
 * is the only provider allowed to sort quick capture ([Capability.OFFLINE]).
 *
 * The model (~600 MB) is loaded on first use on a background dispatcher, kept while in use, and released
 * after [IDLE_RELEASE_MS] idle or when the system trims memory. One request at a time.
 * There is no constrained decoding: the prompt carries the schema and the answer goes through the same
 * lenient JSON extraction as the OpenAI-compatible provider, with one retry. Callers validate the shape
 * (e.g. `ParsedEntryDto.sanitized()`), as they do for every provider.
 */
@Singleton
class OnDeviceProvider(
    private val config: AiConfigStore,
    private val factory: InferenceEngineFactory,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    trim: MemoryTrimSource,
) : AiProvider {
    @Inject constructor(
        config: AiConfigStore,
        factory: InferenceEngineFactory,
        @ApplicationScope scope: CoroutineScope,
        trim: MemoryTrimSource,
    ) : this(config, factory, scope, Dispatchers.Default, trim)

    override val id = ProviderId.ON_DEVICE
    override val needsNetwork = false
    override val timeoutMs = 180_000L

    private val mutex = Mutex()
    private var engine: InferenceEngine? = null
    private var enginePath: String? = null
    private var idleRelease: Job? = null

    init {
        trim.onTrim { scope.launch { release() } }
    }

    override suspend fun capabilities(): Set<Capability> = setOf(Capability.OFFLINE)

    override suspend fun photosAllowed() = false

    override suspend fun isConfiguredAndEnabled(): Boolean {
        val c = config.current().providers.onDevice
        return c.enabled && modelFile(c.modelPath) != null
    }

    override suspend fun generate(req: StructuredRequest): AiOutcome {
        val file = modelFile(config.current().providers.onDevice.modelPath) ?: return AiOutcome.Failed(AiReasons.NOT_CONFIGURED)
        val user = prompt(req)
        return mutex.withLock {
            idleRelease?.cancel()
            try {
                withContext(dispatcher) { answer(file, req.system, user) }
            } finally {
                idleRelease = scope.launch {
                    delay(IDLE_RELEASE_MS)
                    release()
                }
            }
        }
    }

    /** Frees the model's memory now. The next request loads it again. */
    suspend fun release() = mutex.withLock { closeEngine() }

    private fun answer(file: File, system: String, user: String): AiOutcome {
        val e = try {
            engineFor(file)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // A wrong or damaged file, or a phone the runtime doesn't support (missing native library).
            warn("on-device model won't load: ${file.name}", t)
            return AiOutcome.Failed(AiReasons.MODEL_UNUSABLE)
        }
        repeat(ATTEMPTS) {
            val text = try {
                e.generate(system, user)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                warn("on-device generation failed", t)
                closeEngine()
                return AiOutcome.Unavailable(AiReasons.ENGINE)
            }
            extractJsonObject(stripThinking(text))?.let { return AiOutcome.Success(it, id, file.name) }
        }
        return AiOutcome.Failed(AiReasons.BAD_RESPONSE)
    }

    private fun engineFor(file: File): InferenceEngine {
        engine?.let { if (enginePath == file.path) return it }
        closeEngine()
        // The native loader can abort the whole process on a bad file (seen on Waydroid), which no catch
        // survives. So: only hand it files that look like models, and leave a marker while loading; a
        // marker still there means the last load took the process down, so don't try that file again.
        if (!looksLikeModel(file)) throw IllegalArgumentException("not a model file: ${file.name}")
        val marker = File(file.path + LOADING_SUFFIX)
        if (marker.exists()) throw IllegalStateException("loading ${file.name} crashed before")
        runCatching { marker.writeText("") }
        val created = try {
            factory.create(file)
        } finally {
            marker.delete()
        }
        return created.also {
            engine = it
            enginePath = file.path
        }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        enginePath = null
    }

    private fun warn(msg: String, t: Throwable) {
        runCatching { Log.w("Klean", msg, t) }
    }

    companion object {
        const val IDLE_RELEASE_MS = 2 * 60_000L
        private const val ATTEMPTS = 2
        private val THINKING = Regex("<think>[\\s\\S]*?</think>")

        const val LOADING_SUFFIX = ".loading"
        private val LITERTLM_MAGIC = "LITERTLM".toByteArray()
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

        /** `.litertlm` files start with "LITERTLM"; older `.task` bundles are zip files. */
        fun looksLikeModel(file: File): Boolean = runCatching {
            val head = ByteArray(8)
            val n = file.inputStream().use { it.read(head) }
            (n >= 8 && head.contentEquals(LITERTLM_MAGIC)) || (n >= 4 && head.copyOf(4).contentEquals(ZIP_MAGIC))
        }.getOrDefault(false)

        fun modelFile(path: String?): File? = path?.let(::File)?.takeIf { it.isFile && it.length() > 0 }

        internal fun stripThinking(text: String) = THINKING.replace(text, "")

        /** Small models follow a plain instruction better than a system-only one, so the schema goes with the entry. */
        internal fun prompt(req: StructuredRequest): String =
            req.user + "\n\nReply with JSON matching this schema: " + req.schema.toString() +
                "\nReply with the JSON object only, no other text. /no_think"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnDeviceModule {
    @Binds abstract fun engineFactory(impl: LiteRtLmEngineFactory): InferenceEngineFactory
    @Binds abstract fun trimSource(impl: AndroidMemoryTrimSource): MemoryTrimSource
    @Binds abstract fun modelHttp(impl: UrlConnectionModelHttp): ModelHttp
    @Binds abstract fun downloads(impl: WorkManagerModelDownloads): ModelDownloads
    @Binds abstract fun modelStore(impl: OnDeviceModels): OnDeviceModelStore
}
