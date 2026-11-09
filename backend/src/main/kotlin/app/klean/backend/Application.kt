package app.klean.backend

import app.klean.backend.auth.deviceAuthRoutes
import app.klean.backend.auth.publicAuthRoutes
import app.klean.backend.sync.syncRoutes
import io.ktor.serialization.kotlinx.json.json
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
        route("/api/v1") {
            get("/health") { call.respond(HealthResponse("ok")) }
            publicAuthRoutes(services.auth)
            authenticate(AUTH_DEVICE) {
                deviceAuthRoutes(services.auth)
                syncRoutes(services.records)
            }
        }
    }
}
