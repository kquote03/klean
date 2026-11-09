package app.klean.core.ai.provider

import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.Capability
import app.klean.core.ai.FakeAiConfigStore
import app.klean.core.ai.OpenAiConfig
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.secret.SecretStore
import app.klean.shared.ai.ENTRY_PARSE_SCHEMA
import app.klean.shared.ai.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatProviderTest {
    private val secrets = testSecrets()
    private val config = FakeAiConfigStore(
        AiConfig(AiProvidersConfig(openAi = OpenAiConfig(enabled = true, baseUrl = "http://192.168.240.1:8765/v1/", model = "llama3.2"))),
    )
    private val req = StructuredRequest("sys", "Logged at 13:05 local time: shawarma and fries", ENTRY_PARSE_SCHEMA)

    private fun completion(content: String): String {
        val msg = buildJsonObject { put("role", "assistant"); put("content", content) }
        return """{"id":"x","choices":[{"index":0,"message":$msg,"finish_reason":"stop"}]}"""
    }

    private val items = """{"items":[{"name":"shawarma","portion":"1 wrap","calories":450},{"name":"fries","portion":"medium","calories":320}],"mealType":"lunch"}"""

    private fun provider(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<OpenAiCompatProvider, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        return OpenAiCompatProvider(HttpClient(MockEngine { r -> seen += r; handler(r) }), config, secrets) to seen
    }

    @Test fun requestFollowsThePlanFormat() = runBlocking {
        val (p, seen) = provider { json(HttpStatusCode.OK, completion(items)) }
        val out = p.generate(StructuredRequest(req.system, req.user, req.schema, imageJpeg = byteArrayOf(1)))
        assertTrue(out is AiOutcome.Success)
        val r = seen.single()
        assertEquals("http://192.168.240.1:8765/v1/chat/completions", r.url.toString())
        assertNull("no key → no Authorization header", r.headers["Authorization"])
        val body = Json.parseToJsonElement(r.bodyText()).jsonObject
        assertEquals("llama3.2", body["model"]!!.jsonPrimitive.content)
        assertEquals(0.2, body["temperature"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        val msgs = body["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("sys", msgs[0].jsonObject["content"]!!.jsonPrimitive.content)
        val parts = msgs[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(req.user, parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
        val rf = body["response_format"]!!.jsonObject
        assertEquals("json_schema", rf["type"]!!.jsonPrimitive.content)
        val js = rf["json_schema"]!!.jsonObject
        assertEquals("klean", js["name"]!!.jsonPrimitive.content)
        assertEquals(ENTRY_PARSE_SCHEMA, js["schema"])
        assertEquals("true", js["strict"]!!.jsonPrimitive.content)
    }

    @Test fun keySendsBearerHeader() = runBlocking {
        secrets.put(SecretStore.OPENAI_API_KEY, "sk-local")
        val (p, seen) = provider { json(HttpStatusCode.OK, completion(items)) }
        p.generate(req)
        assertEquals("Bearer sk-local", seen.single().headers["Authorization"])
    }

    @Test fun http400RetriesOnceWithJsonObject() = runBlocking {
        val (p, seen) = provider { r ->
            val type = Json.parseToJsonElement(r.bodyText()).jsonObject["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content
            if (type == "json_schema") json(HttpStatusCode.BadRequest, """{"error":"json_schema unsupported"}""")
            else json(HttpStatusCode.OK, completion(items))
        }
        val out = p.generate(req) as AiOutcome.Success
        assertEquals(2, seen.size)
        assertEquals(ProviderId.OPENAI_COMPAT, out.provider)
        assertEquals("llama3.2", out.model)
        assertEquals(2, (out.json as JsonObject)["items"]!!.jsonArray.size)
    }

    @Test fun secondHttp400IsFailedWithoutAThirdTry() = runBlocking {
        val (p, seen) = provider { json(HttpStatusCode.BadRequest, "{}") }
        assertTrue(p.generate(req) is AiOutcome.Failed)
        assertEquals(2, seen.size)
    }

    @Test fun contentWrappedInProseStillParses() = runBlocking {
        val (p, _) = provider { json(HttpStatusCode.OK, completion("Sure! Here is the JSON:\n```json\n$items\n```\nEnjoy {not json}")) }
        val out = p.generate(req) as AiOutcome.Success
        assertEquals("shawarma", (out.json as JsonObject)["items"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test fun statusMapping() = runBlocking {
        assertTrue(provider { json(HttpStatusCode.InternalServerError, "") }.first.generate(req) is AiOutcome.Unavailable)
        assertTrue(provider { json(HttpStatusCode.TooManyRequests, "") }.first.generate(req) is AiOutcome.Unavailable)
        assertEquals(AiOutcome.Failed(AiReasons.AUTH), provider { json(HttpStatusCode.Unauthorized, "") }.first.generate(req))
        assertTrue(provider { throw java.net.ConnectException("refused") }.first.generate(req) is AiOutcome.Unavailable)
        assertEquals(AiOutcome.Failed(AiReasons.BAD_RESPONSE), provider { json(HttpStatusCode.OK, completion("no json here")) }.first.generate(req))
    }

    @Test fun capabilitiesFollowAcceptsImages() = runBlocking {
        val (p, _) = provider { json(HttpStatusCode.OK, "") }
        assertEquals(emptySet<Capability>(), p.capabilities())
        assertFalse(p.photosAllowed())
        config.updateProviders { it.copy(openAi = it.openAi.copy(acceptsImages = true)) }
        assertEquals(setOf(Capability.IMAGE_INPUT), p.capabilities())
        assertTrue(p.photosAllowed())
    }

    @Test fun enabledNeedsSwitchAndAnHttpUrl() = runBlocking {
        val (p, _) = provider { json(HttpStatusCode.OK, "") }
        assertTrue(p.isConfiguredAndEnabled())
        config.updateProviders { it.copy(openAi = it.openAi.copy(baseUrl = "not a url")) }
        assertFalse(p.isConfiguredAndEnabled())
        config.updateProviders { it.copy(openAi = it.openAi.copy(baseUrl = "http://10.0.0.2:11434/v1", enabled = false)) }
        assertFalse(p.isConfiguredAndEnabled())
    }
}
