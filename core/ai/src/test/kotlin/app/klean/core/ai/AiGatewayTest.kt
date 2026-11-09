package app.klean.core.ai

import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiConfigStore(initial: AiConfig = AiConfig()) : AiConfigStore {
    val state = MutableStateFlow(initial)
    override val config = state
    override suspend fun updateProviders(transform: (AiProvidersConfig) -> AiProvidersConfig) =
        state.update { it.copy(providers = transform(it.providers)) }
    override suspend fun assign(task: AiTask, provider: ProviderId?) = state.update {
        it.copy(assignments = if (provider == null) it.assignments - task else it.assignments + (task to provider))
    }
}

class FakeProvider(
    override val id: ProviderId = ProviderId.OPENAI_COMPAT,
    var enabled: Boolean = true,
    var caps: Set<Capability> = emptySet(),
    var photos: Boolean = true,
    override val needsNetwork: Boolean = true,
    var delayMs: Long = 0,
    override val timeoutMs: Long = AiGateway.TIMEOUT_MS,
    var answer: (StructuredRequest) -> AiOutcome = { AiOutcome.Success(JsonObject(mapOf("ok" to JsonPrimitive(true))), id, "m") },
) : AiProvider {
    val requests = mutableListOf<StructuredRequest>()
    override suspend fun capabilities() = caps
    override suspend fun isConfiguredAndEnabled() = enabled
    override suspend fun photosAllowed() = photos
    override suspend fun generate(req: StructuredRequest): AiOutcome {
        requests += req
        if (delayMs > 0) delay(delayMs)
        return answer(req)
    }
}

class AiGatewayTest {
    private val req = StructuredRequest("sys", "toast", JsonObject(emptyMap()), imageJpeg = byteArrayOf(1, 2, 3))

    private fun gateway(vararg p: AiProvider, assign: Map<AiTask, ProviderId> = mapOf(AiTask.ENTRY_PARSE to ProviderId.OPENAI_COMPAT)) =
        AiGateway(p.toSet(), FakeAiConfigStore(AiConfig(assignments = assign)))

    @Test fun unassignedTaskHasNoProvider() = runTest {
        val p = FakeProvider()
        val g = gateway(p, assign = emptyMap())
        assertEquals(AiOutcome.NoProvider, g.run(AiTask.ENTRY_PARSE, req))
        assertFalse(g.hasProviderFor(AiTask.ENTRY_PARSE))
        assertTrue(p.requests.isEmpty())
    }

    @Test fun assignedButDisabledSendsNothing() = runTest {
        // Cloud consent: nothing leaves the phone while the provider is switched off.
        val p = FakeProvider(id = ProviderId.GEMINI, enabled = false, caps = setOf(Capability.IMAGE_INPUT))
        val g = gateway(p, assign = mapOf(AiTask.ENTRY_PARSE to ProviderId.GEMINI))
        assertEquals(AiOutcome.NoProvider, g.run(AiTask.ENTRY_PARSE, req))
        assertFalse(g.hasProviderFor(AiTask.ENTRY_PARSE))
        assertTrue(p.requests.isEmpty())
    }

    @Test fun assignedAndEnabledIsUsed() = runTest {
        val p = FakeProvider()
        val g = gateway(p)
        assertTrue(g.hasProviderFor(AiTask.ENTRY_PARSE))
        assertTrue(g.run(AiTask.ENTRY_PARSE, req) is AiOutcome.Success)
        assertNull(g.providerFor(AiTask.CALORIE_ESTIMATE))
    }

    @Test fun imageIsStrippedForProviderWithoutImageInput() = runTest {
        val p = FakeProvider(caps = emptySet())
        gateway(p).run(AiTask.ENTRY_PARSE, req)
        assertNull(p.requests.single().imageJpeg)
        assertEquals("toast", p.requests.single().user)
    }

    @Test fun imageIsStrippedWhenPhotosNotAllowedOrNotRequested() = runTest {
        val p = FakeProvider(caps = setOf(Capability.IMAGE_INPUT), photos = false)
        gateway(p).run(AiTask.ENTRY_PARSE, req)
        assertNull(p.requests.last().imageJpeg)
        p.photos = true
        gateway(p).run(AiTask.ENTRY_PARSE, req, sendImage = false)
        assertNull(p.requests.last().imageJpeg)
    }

    @Test fun imageIsKeptForCapableProviderWithPhotosAllowed() = runTest {
        val p = FakeProvider(caps = setOf(Capability.IMAGE_INPUT), photos = true)
        gateway(p).run(AiTask.ENTRY_PARSE, req)
        assertNotNull(p.requests.single().imageJpeg)
    }

    @Test fun slowProviderIsUnavailableAfter45Seconds() = runTest {
        val p = FakeProvider(delayMs = 120_000)
        val out = gateway(p).run(AiTask.ENTRY_PARSE, req)
        assertEquals(AiOutcome.Unavailable("timeout"), out)
        assertEquals(45_000L, currentTime)
    }

    /** The on-device model loads hundreds of MB before its first answer, so it gets its own, longer limit. */
    @Test fun providerCanAskForALongerTimeout() = runTest {
        val p = FakeProvider(delayMs = 100_000, timeoutMs = 150_000)
        assertTrue(gateway(p).run(AiTask.ENTRY_PARSE, req) is AiOutcome.Success)
        val q = FakeProvider(delayMs = 200_000, timeoutMs = 150_000)
        assertEquals(AiOutcome.Unavailable("timeout"), gateway(q).run(AiTask.ENTRY_PARSE, req))
    }

    @Test fun throwingProviderIsUnavailableNotACrash() = runTest {
        val p = FakeProvider(answer = { throw java.io.IOException("connection refused") })
        assertTrue(gateway(p).run(AiTask.ENTRY_PARSE, req) is AiOutcome.Unavailable)
    }
}
