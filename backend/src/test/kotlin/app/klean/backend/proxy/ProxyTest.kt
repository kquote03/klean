package app.klean.backend.proxy

import app.klean.backend.KleanConfig
import app.klean.backend.Services
import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.backend.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val seenBodies = mutableListOf<String>()
    private val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\ndata: [DONE]\n\n"

    private val engine = MockEngine { req ->
        seen += req
        seenBodies += (req.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        when (req.url.encodedPath) {
            "/v1/chat/completions" -> respond(ByteReadChannel(sse), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            "/v1/models" -> respond("""{"data":[{"id":"llama3"}]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            "/hermes-api/tasks" -> respond("""{"id":"h1"}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
            "/hermes-api/tasks/h1/events" -> respond(ByteReadChannel("event: question\ndata: {\"klean\":\"question\"}\n\n"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            else -> respond("nope", HttpStatusCode.NotFound)
        }
    }

    private val config: KleanConfig = testConfig().copy(
        aiUpstream = "http://ollama.local:11434/v1", aiApiKey = "upstream-key",
        hermesUrl = "http://hermes.local:8642/hermes-api", hermesToken = "hermes-secret",
    )
    private val services: (KleanConfig) -> Services = { Services.create(it, httpClient = HttpClient(engine)) }

    @Test fun aiChatCompletionsStreamThroughWithTheServerKey() = kleanTest(config, services) {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val r = c.post("/api/v1/ai/v1/chat/completions?stream=true") {
            bearerAuth(phone.token); contentType(ContentType.Application.Json)
            setBody("""{"model":"llama3","stream":true,"messages":[{"role":"user","content":"hi"}]}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.headers[HttpHeaders.ContentType]!!.startsWith("text/event-stream"))
        assertEquals(sse, r.bodyAsText())

        val up = seen.single()
        assertEquals("http://ollama.local:11434/v1/chat/completions?stream=true", up.url.toString())
        assertEquals("Bearer upstream-key", up.headers[HttpHeaders.Authorization])
        assertTrue("\"model\":\"llama3\"" in seenBodies.single())
    }

    @Test fun aiModelsListIsForwarded() = kleanTest(config, services) {
        val c = jsonClient()
        val web = c.login()
        assertTrue("llama3" in c.get("/api/v1/ai/v1/models") { bearerAuth(web.token) }.bodyAsText())
    }

    @Test fun hermesRelayUsesTheServerHeldTokenAndStreamsEvents() = kleanTest(config, services) {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val created = c.post("/api/v1/hermes/tasks") {
            bearerAuth(phone.token); contentType(ContentType.Application.Json); setBody("""{"text":"email the team"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("""{"id":"h1"}""", created.bodyAsText())
        val up = seen.last()
        assertEquals("http://hermes.local:8642/hermes-api/tasks", up.url.toString())
        assertEquals("Bearer hermes-secret", up.headers[HttpHeaders.Authorization])
        assertFalse(phone.token in up.headers.entries().joinToString { it.value.joinToString() })

        val events = c.get("/api/v1/hermes/tasks/h1/events") { bearerAuth(phone.token) }
        assertEquals("event: question\ndata: {\"klean\":\"question\"}\n\n", events.bodyAsText())
        assertEquals("no", events.headers["X-Accel-Buffering"])
    }

    @Test fun relayNeedsADeviceAndRejectsTraversal() = kleanTest(config, services) {
        val c = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/hermes/tasks").status)
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/ai/v1/chat/completions").status)
        val web = c.login()
        assertEquals(HttpStatusCode.BadRequest, c.get("/api/v1/hermes/..%2F..%2Fadmin") { bearerAuth(web.token) }.status)
        assertTrue(seen.isEmpty())
    }

    @Test fun unconfiguredUpstreamsAre503() = kleanTest(services = services) {
        val c = jsonClient()
        val web = c.login()
        assertEquals(HttpStatusCode.ServiceUnavailable, c.post("/api/v1/ai/v1/chat/completions") { bearerAuth(web.token) }.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, c.post("/api/v1/hermes/tasks") { bearerAuth(web.token) }.status)
    }
}
