package app.klean.backend

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {
    val config = KleanConfig.fromEnv(System.getenv())
    val services = Services.create(config)
    embeddedServer(CIO, port = config.port, host = config.host) {
        kleanModule(services)
    }.start(wait = true)
}
