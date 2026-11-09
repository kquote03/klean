package app.klean.backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

fun Application.kleanModule(services: Services) {
    install(ContentNegotiation) { json(KleanJson) }
    routing {
        route("/api/v1") {
            get("/health") { call.respond(HealthResponse("ok")) }
        }
    }
}
