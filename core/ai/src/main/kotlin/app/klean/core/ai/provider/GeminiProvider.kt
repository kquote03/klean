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

/** Google Gemini over REST (`generateContent` with a JSON schema). Sends only while the user has it switched on. */
@Singleton
class GeminiProvider @Inject constructor(
    private val http: HttpClient,
    private val config: AiConfigStore,
    private val secrets: SecretStore,
) : AiProvider {
    override val id = ProviderId.GEMINI
    override val needsNetwork = true

    override suspend fun capabilities() = setOf(Capability.IMAGE_INPUT)

    override suspend fun isConfiguredAndEnabled(): Boolean {
        val c = config.current().providers.gemini
        return c.enabled && c.model.isNotBlank() && secrets.get(SecretStore.GEMINI_API_KEY) != null
    }

    override suspend fun photosAllowed() = config.current().providers.gemini.sendPhotos

    override suspend fun generate(req: StructuredRequest): AiOutcome {
        val model = config.current().providers.gemini.model.trim()
        val key = secrets.get(SecretStore.GEMINI_API_KEY) ?: return AiOutcome.Failed(AiReasons.GEMINI_REJECTED)
        val res = http.postJson("$BASE/$model:generateContent", body(req)) { header("x-goog-api-key", key) }
            ?: return AiOutcome.Unavailable(AiReasons.NETWORK)
        return when {
            res.status.value in 200..299 -> parse(res.body)?.let { AiOutcome.Success(it, id, model) }
                ?: AiOutcome.Failed(AiReasons.BAD_RESPONSE)
            res.status.isRetryable() -> AiOutcome.Unavailable(AiReasons.BUSY)
            res.status.value in setOf(400, 401, 403) -> AiOutcome.Failed(AiReasons.GEMINI_REJECTED)
            res.status.value == 404 -> AiOutcome.Failed(AiReasons.NOT_FOUND)
            else -> AiOutcome.Failed(AiReasons.REJECTED)
        }
    }

    private fun body(req: StructuredRequest): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", req.system) } } }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", req.user) }
                    req.imageJpeg?.let { img ->
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", Base64.getEncoder().encodeToString(img))
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseJsonSchema", req.schema)
            put("temperature", 0.2)
        }
    }

    private fun parse(body: String) = runCatching {
        val text = AiJson.parseToJsonElement(body).jsonObject["candidates"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        extractJsonObject(text)
    }.getOrNull()

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
