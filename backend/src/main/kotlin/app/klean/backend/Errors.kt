package app.klean.backend

import app.klean.backend.auth.DevicePrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/** Thrown by services; rendered as `{"error": message}` with [status]. */
class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@Serializable data class ErrorResponse(val error: String)

fun Application.installErrors() {
    val log = LoggerFactory.getLogger("app.klean.backend.Errors")
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorResponse(e.message)) }
        exception<BadRequestException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "bad request")) }
        exception<ContentTransformationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad body")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad body")) }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal error"))
        }
    }
}

/** The device behind the bearer token (only inside `authenticate("device")`). */
val ApplicationCall.device: DevicePrincipal
    get() = principal<DevicePrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "no device")

fun ApplicationCall.requireAdmin(): DevicePrincipal =
    device.also { if (!it.admin) throw ApiException(HttpStatusCode.Forbidden, "admin only") }
