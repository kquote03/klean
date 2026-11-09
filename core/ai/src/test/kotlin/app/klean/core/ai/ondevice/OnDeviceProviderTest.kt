package app.klean.core.ai.ondevice

import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.Capability
import app.klean.core.ai.FakeAiConfigStore
import app.klean.core.ai.OnDeviceConfig
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.provider.AiReasons
import app.klean.shared.ai.ProviderId
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeEngine(private val answers: MutableList<String>) : InferenceEngine {
    val prompts = mutableListOf<Pair<String, String>>()
    var closed = false
    var throwOnGenerate: Throwable? = null
    override fun generate(system: String, user: String): String {
        prompts += system to user
        throwOnGenerate?.let { throw it }
        return answers.removeAt(0)
    }
    override fun close() { closed = true }
}

class FakeTrim : MemoryTrimSource {
    private val listeners = mutableListOf<() -> Unit>()
    override fun onTrim(block: () -> Unit) { listeners += block }
    fun trim() = listeners.forEach { it() }
}

class OnDeviceProviderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val schema = JsonObject(mapOf("type" to JsonPrimitive("object")))
    private val req = StructuredRequest("You read food entries.", "2 manakish and tea", schema)

    private fun model(): File = File(tmp.root, "model.litertlm").apply { if (!exists()) writeBytes(ByteArray(16)) }

    private fun config(enabled: Boolean = true, path: String? = null) =
        FakeAiConfigStore(AiConfig(providers = AiProvidersConfig(onDevice = OnDeviceConfig(enabled = enabled, modelPath = path))))

    private class Setup(val provider: OnDeviceProvider, val engines: MutableList<FakeEngine>, val trim: FakeTrim)

    private fun TestScope.setup(
        cfg: FakeAiConfigStore,
        answers: List<String> = emptyList(),
        createError: Throwable? = null,
    ): Setup {
        val engines = mutableListOf<FakeEngine>()
        val trim = FakeTrim()
        val factory = InferenceEngineFactory { _ ->
            createError?.let { throw it }
            FakeEngine(answers.toMutableList()).also { engines += it }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        return Setup(OnDeviceProvider(cfg, factory, backgroundScope, dispatcher, trim), engines, trim)
    }

    @Test fun offlineAndNeedsNoNetwork() = runTest {
        val s = setup(config(path = model().path))
        assertEquals(ProviderId.ON_DEVICE, s.provider.id)
        assertFalse(s.provider.needsNetwork)
        assertEquals(setOf(Capability.OFFLINE), s.provider.capabilities())
    }

    @Test fun onOnlyWhenEnabledAndTheModelFileExists() = runTest {
        val path = model().path
        assertTrue(setup(config(path = path)).provider.isConfiguredAndEnabled())
        assertFalse(setup(config(enabled = false, path = path)).provider.isConfiguredAndEnabled())
        assertFalse(setup(config(path = null)).provider.isConfiguredAndEnabled())
        assertFalse(setup(config(path = File(tmp.root, "gone.litertlm").path)).provider.isConfiguredAndEnabled())
    }

    @Test fun promptCarriesTheSchemaAndAnswerIsExtractedLeniently() = runTest {
        val s = setup(config(path = model().path), answers = listOf("Sure! ```json\n{\"items\":[{\"name\":\"manakish\"}]}\n```"))
        val out = s.provider.generate(req) as AiOutcome.Success
        assertEquals("manakish", out.json.jsonObject["items"].toString().let { Regex("\"name\":\"(\\w+)\"").find(it)!!.groupValues[1] })
        assertEquals(ProviderId.ON_DEVICE, out.provider)
        assertEquals("model.litertlm", out.model)
        val (system, user) = s.engines.single().prompts.single()
        assertEquals("You read food entries.", system)
        assertTrue(user.contains("2 manakish and tea"))
        assertTrue(user.contains("Reply with JSON matching this schema"))
        assertTrue(user.contains(schema.toString()))
    }

    @Test fun thinkingBlockIsIgnored() = runTest {
        val s = setup(config(path = model().path), answers = listOf("<think>maybe {\"x\":1}</think>\n{\"ok\":true}"))
        val out = s.provider.generate(req) as AiOutcome.Success
        assertEquals("true", out.json.jsonObject["ok"]!!.jsonPrimitive.content)
    }

    @Test fun retriesOnceOnUnreadableOutput() = runTest {
        val s = setup(config(path = model().path), answers = listOf("I think it is tea.", "{\"ok\":1}"))
        assertTrue(s.provider.generate(req) is AiOutcome.Success)
        assertEquals(2, s.engines.single().prompts.size)

        val bad = setup(config(path = model().path), answers = listOf("no", "still no"))
        assertEquals(AiOutcome.Failed(AiReasons.BAD_RESPONSE), bad.provider.generate(req))
    }

    @Test fun engineIsCreatedOnceAndReused() = runTest {
        val s = setup(config(path = model().path), answers = listOf("{}", "{}"))
        s.provider.generate(req)
        s.provider.generate(req)
        assertEquals(1, s.engines.size)
    }

    @Test fun aModelThatWontLoadFailsCalmly() = runTest {
        val s = setup(config(path = model().path), createError = IllegalStateException("not a model"))
        assertEquals(AiOutcome.Failed(AiReasons.MODEL_UNUSABLE), s.provider.generate(req))
        // Native library missing on this phone (e.g. 32-bit): an Error, still no crash.
        val n = setup(config(path = model().path), createError = UnsatisfiedLinkError("no liblitertlm_jni"))
        assertEquals(AiOutcome.Failed(AiReasons.MODEL_UNUSABLE), n.provider.generate(req))
    }

    @Test fun runtimeErrorDuringGenerationIsUnavailableAndDropsTheEngine() = runTest {
        val s = setup(config(path = model().path), answers = listOf("{}"))
        s.provider.generate(req)
        s.engines.single().throwOnGenerate = RuntimeException("boom")
        assertTrue(s.provider.generate(req) is AiOutcome.Unavailable)
        assertTrue(s.engines.single().closed)
    }

    @Test fun missingModelIsNotConfigured() = runTest {
        val s = setup(config(path = null))
        assertEquals(AiOutcome.Failed(AiReasons.NOT_CONFIGURED), s.provider.generate(req))
    }

    @Test fun memoryPressureReleasesTheEngine() = runTest {
        val s = setup(config(path = model().path), answers = listOf("{}"))
        s.provider.generate(req)
        s.trim.trim()
        runCurrent()
        assertTrue(s.engines.single().closed)
    }

    @Test fun idleEngineIsReleasedAfterAWhile() = runTest {
        val s = setup(config(path = model().path), answers = listOf("{}", "{}"))
        s.provider.generate(req)
        advanceTimeBy(OnDeviceProvider.IDLE_RELEASE_MS - 1_000)
        runCurrent()
        assertFalse(s.engines.single().closed)
        s.provider.generate(req) // use restarts the idle timer
        advanceTimeBy(OnDeviceProvider.IDLE_RELEASE_MS + 1_000)
        runCurrent()
        assertTrue(s.engines.single().closed)
    }

    @Test fun swappingTheModelFileClosesTheOldEngine() = runTest {
        val cfg = config(path = model().path)
        val s = setup(cfg, answers = listOf("{}", "{}"))
        s.provider.generate(req)
        val other = tmp.newFile("other.litertlm").apply { writeBytes(ByteArray(4)) }
        cfg.updateProviders { it.copy(onDevice = it.onDevice.copy(modelPath = other.path)) }
        s.provider.generate(req)
        assertEquals(2, s.engines.size)
        assertTrue(s.engines[0].closed)
    }

    @Test fun waitsLongerThanCloudProviders() = runTest {
        val s = setup(config(path = model().path))
        assertTrue(s.provider.timeoutMs > app.klean.core.ai.AiGateway.TIMEOUT_MS)
    }
}
