package app.klean.core.ai.provider

import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.Capability
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.secret.SecretStore
import app.klean.shared.ai.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Any server that speaks the OpenAI chat-completions API: Ollama, llama.cpp, LM Studio, vLLM, or a
 * hosted one. Local servers are usually plain HTTP on the LAN (see network_security_config.xml).
 */
@Singleton
class OpenAiCompatProvider @Inject constructor(
    private val http: HttpClient,
    private val config: AiConfigStore,
    private val secrets: SecretStore,
) : AiProvider {
    override val id = ProviderId.OPENAI_COMPAT
    override val needsNetwork = true

    override suspend fun capabilities(): Set<Capability> =
        if (config.current().providers.openAi.acceptsImages) setOf(Capability.IMAGE_INPUT) else emptySet()

    override suspend fun isConfiguredAndEnabled(): Boolean {
        val c = config.current().providers.openAi
        return c.enabled && c.model.isNotBlank() && isHttpUrl(c.baseUrl)
    }

    override suspend fun photosAllowed() = config.current().providers.openAi.acceptsImages

    override suspend fun generate(req: StructuredRequest): AiOutcome {
        val c = config.current().providers.openAi
        if (!isHttpUrl(c.baseUrl)) return AiOutcome.Failed(AiReasons.NOT_CONFIGURED)
        val url = c.baseUrl.trim().trimEnd('/') + "/chat/completions"
        val key = secrets.get(SecretStore.OPENAI_API_KEY)
        val model = c.model.trim()
        suspend fun call(strict: Boolean) = http.postJson(url, body(req, model, strict)) {
            if (key != null) header(HttpHeaders.Authorization, "Bearer $key")
        }
        var res = call(strict = true) ?: return AiOutcome.Unavailable(AiReasons.NETWORK)
        // Many local servers don't support json_schema; fall back to plain JSON mode once.
        if (res.status.value == 400) res = call(strict = false) ?: return AiOutcome.Unavailable(AiReasons.NETWORK)
        return when {
            res.status.value in 200..299 -> parse(res.body)?.let { AiOutcome.Success(it, id, model) }
                ?: AiOutcome.Failed(AiReasons.BAD_RESPONSE)
            res.status.isRetryable() -> AiOutcome.Unavailable(AiReasons.BUSY)
            res.status.value == 401 || res.status.value == 403 -> AiOutcome.Failed(AiReasons.AUTH)
            res.status.value == 404 -> AiOutcome.Failed(AiReasons.NOT_FOUND)
            else -> AiOutcome.Failed(AiReasons.REJECTED)
        }
    }

    private fun body(req: StructuredRequest, model: String, strict: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        put("temperature", 0.2)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", req.system)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", req.user)
                    }
                    req.imageJpeg?.let { img ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(img)) }
                        }
                    }
                }
            }
        }
        putJsonObject("response_format") {
            if (strict) {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "klean")
                    put("schema", req.schema)
                    put("strict", true)
                }
            } else {
                put("type", "json_object")
            }
        }
    }

    private fun parse(body: String) = runCatching {
        val content = AiJson.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        extractJsonObject(content)
    }.getOrNull()

    private fun isHttpUrl(s: String): Boolean = runCatching {
        val t = s.trim()
        (t.startsWith("http://") || t.startsWith("https://")) && Url(t).host.isNotBlank()
    }.getOrDefault(false)
}
