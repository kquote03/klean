package app.klean.feature.delegation.agent

import app.klean.feature.delegation.StaticHermes
import app.klean.shared.delegation.KleanBlock
import app.klean.shared.delegation.KleanBlocks
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scripted Hermes API Server (Runs API, `docs/hermes.md`): run 1 asks a question, run 2 asks to send to 6
 * people, run 3 (after an edited decision) finishes. Checks the events Klean sees and every request it sends.
 */
class HermesAgentTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()
    private var runs = 0

    private fun body(r: HttpRequestData) = (r.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        ?: (r.body as? io.ktor.http.content.TextContent)?.text.orEmpty()

    private fun sse(vararg events: String) = events.joinToString("") { "data: $it\n\n" }

    private val six = (1..6).map { "p$it@example.com" }
    private val approvalBlock = """{"klean":"approval","action":"send_email","draft":"Hi all, the offsite is on the 20th.","recipients":${six.joinToString(",", "[", "]") { "\"$it\"" }}}"""

    private val engine = MockEngine { r ->
        val b = body(r)
        requests += r to b
        val path = r.url.encodedPath
        when {
            r.method == HttpMethod.Post && path == "/v1/runs" -> {
                runs++
                respond("""{"run_id":"run_$runs","status":"started"}""", HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            path == "/v1/runs/run_1/events" -> respond(
                sse(
                    """{"event":"run.started","run_id":"run_1","seq":0}""",
                    """{"event":"message.delta","delta":"I need one thing. ","seq":1}""",
                    """{"event":"message.delta","delta":"{\"klean\":\"question\",\"text\":\"Which date is the offsite?\"}","seq":2}""",
                    """{"event":"run.completed","output":"","seq":3}""",
                ),
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
            path == "/v1/runs/run_2/events" -> respond(
                sse(
                    """{"event":"message.delta","delta":${kotlinx.serialization.json.JsonPrimitive("Draft ready. $approvalBlock")},"seq":0}""",
                    """{"event":"run.completed","seq":1}""",
                ),
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
            path == "/v1/runs/run_3/events" -> respond(
                sse("""{"event":"run.completed","output":"Sent the invitation to 5 people.","seq":0}"""),
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
            else -> respond("nope", HttpStatusCode.NotFound)
        }
    }

    private val agent = HermesAgent(HttpClient(engine), StaticHermes())

    @Test fun questionThenApprovalWithSixRecipientsThenResult() = runBlocking {
        val ref = agent.start(DelegationPayload("d1", "Invite the team to the offsite", "Use the usual list", "2026-11-12", "INTERACTIVE"))
        assertEquals("klean-d1", ref)
        val seen = mutableListOf<AgentEvent>()
        withTimeout(10_000) {
            val job = launch {
                agent.events(ref).collect { e ->
                    seen += e
                    when (e) {
                        is AgentEvent.Question -> agent.answer(ref, "The 20th")
                        is AgentEvent.ApprovalNeeded -> agent.decide(
                            ref, ApprovalDecision(ApprovalDecision.EDITED, "Hi all, the offsite is on the 20th. Lunch is included.", six.dropLast(1)),
                        )
                        else -> Unit
                    }
                }
            }
            job.join()
        }
        assertEquals(AgentEvent.Question("Which date is the offsite?"), seen[0])
        val approval = seen[1] as AgentEvent.ApprovalNeeded
        assertEquals(six, approval.recipients)
        assertEquals("send_email", approval.action)
        assertEquals(AgentEvent.Result("Sent the invitation to 5 people."), seen[2])
        assertEquals(3, seen.size)

        // Every request is authenticated; the three runs share one Hermes session.
        assertTrue(requests.all { it.first.headers[HttpHeaders.Authorization] == "Bearer k" })
        val starts = requests.filter { it.first.method == HttpMethod.Post && it.first.url.encodedPath == "/v1/runs" }.map { Json.parseToJsonElement(it.second).jsonObject }
        assertEquals(3, starts.size)
        assertTrue(starts.all { it["session_id"]!!.jsonPrimitive.content == "klean-d1" })
        assertTrue(starts[0]["instructions"]!!.jsonPrimitive.content.contains("\"klean\":\"approval\""))
        assertTrue(starts[0]["input"]!!.jsonPrimitive.content.startsWith("Invite the team to the offsite"))
        assertEquals(listOf(KleanBlock.Answer("The 20th")), KleanBlocks.parse(starts[1].str("input")))
        val decision = KleanBlocks.parse(starts[2].str("input")).single() as KleanBlock.Decision
        assertEquals("EDITED", decision.decision)
        assertEquals(5, decision.recipients.size)
        assertTrue(decision.draft.endsWith("Lunch is included."))
    }

    @Test fun nativeClarifyIsAnsweredOnTheSameRun() = runBlocking {
        val calls = mutableListOf<String>()
        val e = MockEngine { r ->
            calls += "${r.method.value} ${r.url.encodedPath} ${body(r)}"
            when (r.url.encodedPath) {
                "/v1/runs" -> respond("""{"run_id":"run_9"}""", HttpStatusCode.Accepted)
                "/v1/runs/run_9/events" -> respond(
                    sse(
                        """{"event":"clarify.request","request_id":"c1","execution_generation":2,"question":"Window or aisle?"}""",
                        """{"event":"run.completed","output":"Booked an aisle seat."}""",
                    ),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                )
                else -> respond("""{"ok":true}""")
            }
        }
        val a = HermesAgent(HttpClient(e), StaticHermes())
        val ref = a.start(DelegationPayload("d2", "Book the train", null, null, "AGENTIC"))
        val events = mutableListOf<AgentEvent>()
        a.events(ref).collect { ev ->
            events += ev
            if (ev is AgentEvent.Question) a.answer(ref, "aisle")
        }
        assertEquals(AgentEvent.Question("Window or aisle?", "c1"), events[0])
        assertEquals(AgentEvent.Result("Booked an aisle seat."), events[1])
        // Hermes' own clarify prompt is answered on the same run, not with a new one.
        val clarify = calls.single { it.startsWith("POST /v1/runs/run_9/clarify") }
        assertTrue(clarify, clarify.contains("\"request_id\":\"c1\"") && clarify.contains("\"execution_generation\":2") && clarify.contains("\"answer\":\"aisle\""))
        assertEquals(1, calls.count { it.startsWith("POST /v1/runs ") })
    }

    @Test fun withoutAUrlStartFailsCalmly() = runBlocking {
        val a = HermesAgent(HttpClient(engine), StaticHermes(url = null))
        val r = runCatching { a.start(DelegationPayload("d3", "x", null, null, "INTERACTIVE")) }
        assertTrue(r.exceptionOrNull() is HermesUnavailable)
    }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
}
