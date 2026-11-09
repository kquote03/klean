package app.klean.backend

import app.klean.backend.auth.deviceAuthRoutes
import app.klean.backend.auth.publicAuthRoutes
import app.klean.backend.sync.syncRoutes
import app.klean.backend.blobs.blobRoutes
import app.klean.backend.dav.davRoutes
import app.klean.backend.push.pushRoutes
import app.klean.backend.google.googlePublicRoutes
import app.klean.backend.google.googleRoutes
import app.klean.backend.proxy.aiProxyRoutes
import app.klean.backend.proxy.hermesRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val KleanJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class HealthResponse(val status: String)

const val AUTH_DEVICE = "device"
const val AUTH_DAV = "dav"

fun Application.kleanModule(services: Services) {
    install(ContentNegotiation) { json(KleanJson) }
    install(DefaultHeaders) { header("X-Content-Type-Options", "nosniff") }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { randomBytes(8).toHex() }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("callId")
        // Never log query strings (OAuth codes) or headers (tokens): method, path, status and duration only.
        format { call ->
            val status = call.response.status()?.value ?: 0
            "${call.request.httpMethod.value} ${call.request.path()} $status ${call.processingTimeMillis()}ms"
        }
        mdc("status") { it.response.status()?.value?.toString() }
        mdc("path") { it.request.path() }
        filter { it.request.path() != "/api/v1/health" }
    }
    installErrors()
    install(Authentication) {
        bearer(AUTH_DEVICE) {
            authenticate { credential -> services.auth.authenticate(credential.token) }
        }
        basic(AUTH_DAV) {
            realm = "Klean"
            validate { credential -> services.auth.authenticate(credential.password) }
        }
    }
    routing {
        davRoutes(services.dav)
        route("/api/v1") {
            get("/health") {
                // Liveness plus a trivial DB round trip; no auth, no details.
                val dbOk = runCatching { services.db.read { exec("SELECT 1") { } } }.isSuccess
                if (dbOk) call.respond(HealthResponse("ok"))
                else call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("unavailable"))
            }
            publicAuthRoutes(services.auth)
            googlePublicRoutes(services.google)
            authenticate(AUTH_DEVICE) {
                deviceAuthRoutes(services.auth)
                syncRoutes(services.records)
                blobRoutes(services.blobs)
                pushRoutes(services.push)
                googleRoutes(services.google)
                hermesRoutes(services.proxy, services.config)
                aiProxyRoutes(services.proxy, services.config)
            }
        }
    }
}
