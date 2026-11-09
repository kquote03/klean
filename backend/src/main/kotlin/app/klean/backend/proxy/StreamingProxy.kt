package app.klean.backend.proxy

import app.klean.backend.ApiException
import app.klean.backend.KleanConfig
import app.klean.backend.device
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.copyTo
import io.ktor.client.statement.HttpResponse
import io.ktor.server.application.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Forwards a call to an upstream URL with a server-held credential, streaming the response back as it arrives
 * (SSE for chat streams and Hermes events). The caller's own Authorization/Cookie headers are never forwarded.
 */
class StreamingProxy(private val http: HttpClient) {
    private val log = LoggerFactory.getLogger(StreamingProxy::class.java)

    suspend fun forward(call: ApplicationCall, target: String, bearer: String?) {
        val method = call.request.httpMethod
        val body = if (method == HttpMethod.Get || method == HttpMethod.Head) null else call.receive<ByteArray>()
        val inType = call.request.headers[HttpHeaders.ContentType]?.let { runCatching { ContentType.parse(it) }.getOrNull() }
        // The raw query string, as the client encoded it.
        val query = call.request.local.uri.substringAfter('?', "").takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        // The engine may run the body writer after the handler returns, so the upstream response must stay open
        // until the writer has copied it: run the upstream call in its own coroutine and release it from the writer.
        val head = CompletableDeferred<HttpResponse>()
        val done = CompletableDeferred<Unit>()
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val upstream = CoroutineScope(call.application.coroutineContext + SupervisorJob()).launch {
            try {
                http.prepareRequest(target + query) {
                    this.method = method
                    bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    call.request.headers[HttpHeaders.Accept]?.let { header(HttpHeaders.Accept, it) }
                    if (body != null) setBody(ByteArrayContent(body, inType ?: ContentType.Application.Json))
                }.execute { response ->
                    head.complete(response)
                    done.await()
                }
            } catch (e: Throwable) {
                head.completeExceptionally(e)
            }
        }
        try {
            val response = head.await()
            val type = response.headers[HttpHeaders.ContentType]?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                ?: ContentType.Application.OctetStream
            if (type.match(ContentType.Text.EventStream)) {
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.response.header("X-Accel-Buffering", "no")
            }
            call.respondBytesWriter(type, response.status) {
                started.set(true)
                try {
                    response.bodyAsChannel().copyTo(this)
                } finally {
                    done.complete(Unit)
                }
            }
            // Safety net: if the engine never runs the writer (client gone), release the upstream after a while.
            upstream.launchWatchdog(started, done)
        } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
            done.complete(Unit)
            throw ApiException(HttpStatusCode.GatewayTimeout, "upstream timed out")
        } catch (e: java.io.IOException) {
            done.complete(Unit)
            log.warn("upstream unreachable: {}", e.toString())
            throw ApiException(HttpStatusCode.BadGateway, "upstream unreachable")
        } catch (e: kotlinx.io.IOException) {
            done.complete(Unit)
            log.warn("upstream unreachable: {}", e.toString())
            throw ApiException(HttpStatusCode.BadGateway, "upstream unreachable")
        } catch (e: java.nio.channels.UnresolvedAddressException) {
            done.complete(Unit)
            throw ApiException(HttpStatusCode.BadGateway, "upstream unreachable")
        }

    }

    private fun Job.launchWatchdog(started: java.util.concurrent.atomic.AtomicBoolean, done: CompletableDeferred<Unit>) {
        CoroutineScope(SupervisorJob()).launch {
            kotlinx.coroutines.delay(60_000)
            if (!started.get()) done.complete(Unit)
        }.also { watchdog -> invokeOnCompletion { watchdog.cancel() } }
    }

    companion object {
        private val SAFE_SEGMENT = Regex("^[A-Za-z0-9._~@:-]+$")

        /** Joins path segments onto [base], refusing `..`, empty or odd segments. */
        fun join(base: String, segments: List<String>): String {
            if (segments.any { it == ".." || it == "." || !SAFE_SEGMENT.matches(it) }) {
                throw ApiException(HttpStatusCode.BadRequest, "bad path")
            }
            return base.trimEnd('/') + "/" + segments.joinToString("/")
        }
    }
}

/** `/hermes/{path...}` → `KLEAN_HERMES_URL/{path}` with `KLEAN_HERMES_TOKEN`. */
fun Route.hermesRoutes(proxy: StreamingProxy, config: KleanConfig) {
    route("/hermes/{path...}") {
        handle {
            call.device
            val base = config.hermesUrl ?: throw ApiException(HttpStatusCode.ServiceUnavailable, "Hermes is not configured on this server")
            val segments = call.parameters.getAll("path").orEmpty()
            proxy.forward(call, StreamingProxy.join(base, segments), config.hermesToken)
        }
    }
}

/** `/ai/v1/chat/completions` and `/ai/v1/models` → `KLEAN_AI_UPSTREAM` (an OpenAI-compatible base ending in /v1). */
fun Route.aiProxyRoutes(proxy: StreamingProxy, config: KleanConfig) {
    suspend fun ApplicationCall.forwardAi(path: String) {
        device
        val base = config.aiUpstream ?: throw ApiException(HttpStatusCode.ServiceUnavailable, "No AI upstream is configured on this server")
        proxy.forward(this, base.trimEnd('/') + path, config.aiApiKey)
    }
    post("/ai/v1/chat/completions") { call.forwardAi("/chat/completions") }
    get("/ai/v1/models") { call.forwardAi("/models") }
}
