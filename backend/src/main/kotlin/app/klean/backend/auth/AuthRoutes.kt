package app.klean.backend.auth

import app.klean.backend.device
import app.klean.backend.requireAdmin
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Public routes: login and pair claim. */
fun Route.publicAuthRoutes(auth: AuthService) {
    post("/auth/login") { call.respond(auth.login(call.receive<LoginRequest>())) }
    post("/pair/claim") { call.respond(auth.claim(call.receive<PairClaimRequest>())) }
}

/** Routes behind a device token. */
fun Route.deviceAuthRoutes(auth: AuthService) {
    get("/auth/me") {
        val me = call.device
        call.respond(auth.devices(me.id).single { it.id == me.id })
    }
    post("/pair/start") {
        call.requireAdmin()
        call.respond(auth.startPairing())
    }
    get("/devices") { call.respond(auth.devices(call.device.id)) }
    post("/devices/app-password") {
        call.requireAdmin()
        call.respond(auth.createAppPassword(call.receive<AppPasswordRequest>()))
    }
    delete("/devices/{id}") {
        auth.revoke(call.device, call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}
