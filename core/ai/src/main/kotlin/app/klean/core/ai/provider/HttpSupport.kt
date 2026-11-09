package app.klean.core.ai.provider

import app.klean.core.ai.AiOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Stable reason codes inside [AiOutcome.Unavailable] / [AiOutcome.Failed]. The settings screen maps
 * them to copy in strings.xml; they are never shown raw.
 */
object AiReasons {
    const val NETWORK = "network"
    const val TIMEOUT = "timeout"
    const val BUSY = "busy"
    const val NOT_CONFIGURED = "not_configured"
    const val GEMINI_REJECTED = "gemini_rejected"
    const val REJECTED = "rejected"
    const val AUTH = "auth"
    const val NOT_FOUND = "not_found"
    const val BAD_RESPONSE = "bad_response"
}

internal val AiJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal data class HttpResult(val status: HttpStatusCode, val body: String)

/** POSTs a JSON body. Network failures come back as null so callers map them to Unavailable. */
internal suspend fun HttpClient.postJson(url: String, body: JsonObject, headers: HttpRequestBuilder.() -> Unit = {}): HttpResult? = try {
    val resp = post(url) {
        headers()
        setBody(TextContent(body.toString(), ContentType.Application.Json))
    }
    HttpResult(resp.status, resp.bodyAsText())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** 429 and 5xx are worth retrying later. */
internal fun HttpStatusCode.isRetryable() = value == 429 || value == 408 || value >= 500

/** Parses [text] as a JSON object, or failing that the first balanced `{…}` block inside it. */
internal fun extractJsonObject(text: String): JsonElement? {
    val t = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    runCatching { AiJson.parseToJsonElement(t) }.getOrNull()?.let { if (it is JsonObject) return it }
    val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> if (--depth == 0) {
                return runCatching { AiJson.parseToJsonElement(text.substring(start, i + 1)) }.getOrNull()?.takeIf { it is JsonObject }
            }
        }
    }
    return null
}

@Module
@InstallIn(SingletonComponent::class)
object AiHttpModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 44_000
        }
    }
}
