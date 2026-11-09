package app.klean.core.ai.provider

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.Capability
import app.klean.core.ai.FakeAiConfigStore
import app.klean.core.ai.GeminiConfig
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.secret.SecretCipher
import app.klean.core.data.secret.SecretStore
import app.klean.shared.ai.ENTRY_PARSE_SCHEMA
import app.klean.shared.ai.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

object PlainCipher : SecretCipher {
    override fun encrypt(plain: ByteArray) = plain.reversedArray()
    override fun decrypt(blob: ByteArray) = blob.reversedArray()
}

fun testSecrets(): SecretStore {
    val f = File(createTempDirectory("secrets").toFile(), "secrets.preferences_pb")
    return SecretStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { f }), PlainCipher)
}

fun HttpRequestData.bodyText(): String = when (val b = body) {
    is TextContent -> b.text
    is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
    else -> error("unexpected body ${b::class}")
}

fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

class GeminiProviderTest {
    private val secrets = testSecrets()
    private val config = FakeAiConfigStore(AiConfig(AiProvidersConfig(gemini = GeminiConfig(enabled = true))))
    private val req = StructuredRequest("You parse food.", "Logged at 13:05 local time: toast", ENTRY_PARSE_SCHEMA)
    private val okBody = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"{\"items\":[{\"name\":\"toast\",\"portion\":\"2 slices\",\"calories\":180}],\"mealType\":\"breakfast\"}"}]}}]}
    """.trimIndent()

    private fun provider(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<GeminiProvider, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { r -> seen += r; handler(r) })
        return GeminiProvider(client, config, secrets) to seen
    }

    @Test fun requestHasKeyHeaderAndPlanBody() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "AIza-test")
        val (p, seen) = provider { json(HttpStatusCode.OK, okBody) }
        p.generate(StructuredRequest(req.system, req.user, req.schema, imageJpeg = byteArrayOf(9, 8, 7)))
        val r = seen.single()
        assertEquals(HttpMethod.Post, r.method)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", r.url.toString())
        assertEquals("AIza-test", r.headers["x-goog-api-key"])
        assertFalse("key never in the URL", r.url.toString().contains("AIza"))
        val body = Json.parseToJsonElement(r.bodyText()).jsonObject
        assertEquals("You parse food.", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val content = body["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        val parts = content["parts"]!!.jsonArray
        assertEquals(req.user, parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        val inline = parts[1].jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/jpeg", inline["mimeType"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(9, 8, 7)), inline["data"]!!.jsonPrimitive.content)
        val gen = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", gen["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals(ENTRY_PARSE_SCHEMA, gen["responseJsonSchema"])
        assertEquals(0.2, gen["temperature"]!!.jsonPrimitive.content.toDouble(), 1e-9)
    }

    @Test fun textOnlyRequestHasOnePart() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        val (p, seen) = provider { json(HttpStatusCode.OK, okBody) }
        p.generate(req)
        val parts = Json.parseToJsonElement(seen.single().bodyText()).jsonObject["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals(1, parts.size)
    }

    @Test fun okResponseParses() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        val (p, _) = provider { json(HttpStatusCode.OK, okBody) }
        val out = p.generate(req) as AiOutcome.Success
        assertEquals(ProviderId.GEMINI, out.provider)
        assertEquals("gemini-2.5-flash", out.model)
        val item = (out.json as JsonObject)["items"]!!.jsonArray[0].jsonObject
        assertEquals("toast", item["name"]!!.jsonPrimitive.content)
    }

    @Test fun rateLimitIsUnavailable() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        val (p, _) = provider { json(HttpStatusCode.TooManyRequests, "{}") }
        assertTrue(p.generate(req) is AiOutcome.Unavailable)
        val (p5, _) = provider { json(HttpStatusCode.ServiceUnavailable, "{}") }
        assertTrue(p5.generate(req) is AiOutcome.Unavailable)
    }

    @Test fun unauthorizedIsFailedWithKeyMessage() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "bad")
        val (p, _) = provider { json(HttpStatusCode.Unauthorized, """{"error":{"code":401}}""") }
        assertEquals(AiOutcome.Failed(AiReasons.GEMINI_REJECTED), p.generate(req))
    }

    @Test fun networkErrorIsUnavailable() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        val (p, _) = provider { throw java.io.IOException("no route") }
        assertTrue(p.generate(req) is AiOutcome.Unavailable)
    }

    @Test fun unreadableAnswerIsFailed() = runBlocking {
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        val (p, _) = provider { json(HttpStatusCode.OK, """{"candidates":[]}""") }
        assertEquals(AiOutcome.Failed(AiReasons.BAD_RESPONSE), p.generate(req))
    }

    @Test fun enabledOnlyWithKeyAndSwitchOn() = runBlocking {
        val (p, _) = provider { json(HttpStatusCode.OK, okBody) }
        assertFalse(p.isConfiguredAndEnabled())
        secrets.put(SecretStore.GEMINI_API_KEY, "k")
        assertTrue(p.isConfiguredAndEnabled())
        config.updateProviders { it.copy(gemini = it.gemini.copy(enabled = false)) }
        assertFalse(p.isConfiguredAndEnabled())
        assertEquals(setOf(Capability.IMAGE_INPUT), p.capabilities())
        assertTrue(p.needsNetwork)
        assertFalse(p.photosAllowed())
    }
}
