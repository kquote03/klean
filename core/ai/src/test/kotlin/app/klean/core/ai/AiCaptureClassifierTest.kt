package app.klean.core.ai

import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureOption
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCaptureClassifierTest {
    private val options = listOf(
        CaptureOption(ModuleId.DIET, ActionSpec("log_food", "Log something eaten or drunk")),
        CaptureOption(ModuleId.TASKS, ActionSpec("add_task", "Add a to-do")),
    )
    private val answer = """{"candidates":[{"moduleId":"tasks","actionType":"add_task","confidence":0.9},{"moduleId":"diet","actionType":"log_food","confidence":0.2},{"moduleId":"nope","actionType":"x","confidence":1}]}"""

    private fun gateway(p: FakeProvider?) = AiGateway(
        setOfNotNull(p),
        FakeAiConfigStore(AiConfig(assignments = if (p != null) mapOf(AiTask.CAPTURE_ROUTING to p.id) else emptyMap())),
    )

    private fun provider(offline: Boolean) = FakeProvider(
        id = if (offline) ProviderId.ON_DEVICE else ProviderId.GEMINI,
        caps = if (offline) setOf(Capability.OFFLINE) else emptySet(),
        needsNetwork = !offline,
        answer = { AiOutcome.Success(Json.parseToJsonElement(answer), ProviderId.ON_DEVICE, null) },
    )

    @Test fun nullWhenNoProviderIsAssigned() = runTest {
        assertNull(AiCaptureClassifier(gateway(null)).classify("buy milk", options))
    }

    @Test fun nullForANetworkProviderSoCaptureNeverWaitsOnTheNetwork() = runTest {
        val p = provider(offline = false)
        assertNull(AiCaptureClassifier(gateway(p)).classify("buy milk", options))
        assertTrue("nothing sent", p.requests.isEmpty())
    }

    @Test fun offlineProviderRanksKnownOptions() = runTest {
        val p = provider(offline = true)
        val out = AiCaptureClassifier(gateway(p)).classify("buy milk", options)!!
        assertEquals(listOf(ModuleId.TASKS, ModuleId.DIET), out.map { it.moduleId })
        assertEquals(0.9f, out[0].confidence, 1e-6f)
        val req = p.requests.single()
        assertEquals("buy milk", req.user)
        assertTrue(req.system.contains("diet/log_food") && req.system.contains("tasks/add_task"))
        assertEquals("object", req.schema["type"]!!.jsonPrimitive.content)
    }

    @Test fun routerWithOneHandlerNeverAsksTheClassifier() = runTest {
        val p = provider(offline = true)
        val diet = object : CaptureHandler {
            override val moduleId = ModuleId.DIET
            override val actionSpecs = listOf(options[0].action)
            override fun defaultAction(input: CaptureInput) = CandidateAction(ModuleId.DIET, "log_food", 1f)
            override suspend fun execute(input: CaptureInput, action: CandidateAction) = "id"
            override suspend fun undo(recordId: String) = Unit
        }
        val routed = DefaultCaptureRouter(AiCaptureClassifier(gateway(p))).route(CaptureInput("toast", CaptureSource.QS_TILE, 0L), listOf(diet))
        assertEquals(ModuleId.DIET, routed.single().moduleId)
        assertTrue(p.requests.isEmpty())
    }
}
