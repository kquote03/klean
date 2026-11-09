package app.klean.backend.google

import app.klean.backend.device
import app.klean.backend.requireAdmin
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.json.JsonObject

/** Public: Google redirects the browser here (no bearer token); the `state` check authenticates it. */
fun Route.googlePublicRoutes(google: GoogleService) {
    get("/google/oauth/callback") {
        google.callback(call.request.queryParameters["code"], call.request.queryParameters["state"])
        call.respondRedirect("/?google=connected")
    }
}

/**
 * Device-token routes. Deliberately GET and PATCH only under `/google/tasks`: no route can create a task.
 */
fun Route.googleRoutes(google: GoogleService) {
    get("/google/status") {
        call.device
        call.respond(google.status())
    }
    get("/google/oauth/start") {
        call.requireAdmin()
        call.respond(google.startUrl())
    }
    delete("/google/oauth") {
        call.requireAdmin()
        google.disconnect()
        call.respond(HttpStatusCode.NoContent)
    }
    get("/google/calendar/events") {
        call.device
        val q = call.request.queryParameters
        call.upstream(google.calendarEvents(q["calendarId"] ?: "primary", q["timeMin"], q["timeMax"], q["pageToken"]))
    }
    get("/google/tasks/lists") {
        call.device
        call.upstream(google.taskLists())
    }
    get("/google/tasks/lists/{list}/tasks") {
        call.device
        call.upstream(google.tasks(call.parameters["list"]!!, call.request.queryParameters["pageToken"]))
    }
    patch("/google/tasks/lists/{list}/tasks/{task}") {
        call.device
        call.upstream(google.patchTask(call.parameters["list"]!!, call.parameters["task"]!!, call.receive<JsonObject>()))
    }
}

private suspend fun RoutingCall.upstream(u: Upstream) = respondText(u.body, ContentType.Application.Json, u.status)
