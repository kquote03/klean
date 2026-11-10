package app.klean.backend.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Serves the web client (M26) at `/`: the files of `web/dist`, which Gradle's `:backend:processResources`
 * copies into the jar under [base]. Any other GET outside `/api` and `/dav` falls back to `index.html`, so the
 * client's own routes (`/tasks`, `/calendar`, …) survive a reload. Hashed build assets are cached for a year;
 * `index.html` and the service worker never are, so a new build shows at once.
 *
 * With no web build on the classpath (the Android CI job, a backend-only build) these paths answer 404 and
 * nothing else changes. Existing routes are untouched: Ktor prefers their constant segments over this tailcard.
 */
fun Route.webRoutes(base: String = "web", loader: ClassLoader = WebClient::class.java.classLoader) {
    val web = WebClient(base, loader)
    get("/") { web.serve(call, "") }
    get("/{path...}") { web.serve(call, call.request.path().removePrefix("/")) }
}

class WebClient(private val base: String, private val loader: ClassLoader) {
    private fun bytes(path: String): ByteArray? {
        if (path.isEmpty() || path.endsWith("/") || path.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        val url = loader.getResource("$base/$path") ?: return null
        if (url.protocol == "file" && java.io.File(url.toURI()).isDirectory) return null
        return runCatching { url.readBytes() }.getOrNull()
    }

    suspend fun serve(call: ApplicationCall, path: String) {
        // The API and DAV keep their own 404s; never answer them with the app.
        val first = path.substringBefore('/')
        if (first == "api" || first == "dav" || first == ".well-known") {
            call.respondText("""{"error":"not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            return
        }
        val asset = bytes(path)
        val name = if (asset != null) path else "index.html"
        val body = asset ?: bytes("index.html") ?: run {
            call.respondText("""{"error":"the web client is not part of this build"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
            return
        }
        call.response.header(HttpHeaders.CacheControl, if (name.startsWith("assets/")) "public, max-age=31536000, immutable" else "no-cache")
        if (name == "sw.js") call.response.header("Service-Worker-Allowed", "/")
        val type = if (name.endsWith(".webmanifest")) ContentType("application", "manifest+json") else ContentType.defaultForFilePath(name)
        call.respondBytes(body, type, HttpStatusCode.OK)
    }
}
