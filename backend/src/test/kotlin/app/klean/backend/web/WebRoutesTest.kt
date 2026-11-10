package app.klean.backend.web

import app.klean.backend.kleanTest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The web client is served at / with an SPA fallback; test files live in src/test/resources/web-test/. */
class WebRoutesTest {
    private fun web(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        routing { webRoutes(base = "web-test") }
        block()
    }

    @Test fun servesIndexAtRoot() = web {
        val r = client.get("/")
        assertEquals(HttpStatusCode.OK, r.status)
        assertContains(r.bodyAsText(), "klean-web-test-index")
        assertEquals("no-cache", r.headers[HttpHeaders.CacheControl])
        assertTrue(r.contentType()!!.match(ContentType.Text.Html))
    }

    @Test fun servesAssetsWithLongCaching() = web {
        val r = client.get("/assets/app-abc123.js")
        assertEquals(HttpStatusCode.OK, r.status)
        assertContains(r.bodyAsText(), "klean-web-test-asset")
        assertEquals("public, max-age=31536000, immutable", r.headers[HttpHeaders.CacheControl])
        assertTrue(r.contentType()!!.match(ContentType.Application.JavaScript) || r.contentType()!!.match(ContentType.Text.JavaScript))
    }

    @Test fun manifestAndServiceWorker() = web {
        assertEquals("application/manifest+json", client.get("/manifest.webmanifest").contentType()?.withoutParameters()?.toString())
        val sw = client.get("/sw.js")
        assertEquals("/", sw.headers["Service-Worker-Allowed"])
        assertEquals("no-cache", sw.headers[HttpHeaders.CacheControl])
    }

    @Test fun clientRoutesFallBackToIndex() = web {
        for (p in listOf("/tasks", "/calendar", "/settings/devices", "/approvals?x=1")) {
            val r = client.get(p)
            assertEquals(HttpStatusCode.OK, r.status, p)
            assertContains(r.bodyAsText(), "klean-web-test-index", message = p)
        }
    }

    @Test fun apiAndDavPathsAreNeverTheApp() = web {
        for (p in listOf("/api", "/api/v1/nope", "/dav/x", "/.well-known/x")) {
            val r = client.get(p)
            assertEquals(HttpStatusCode.NotFound, r.status, p)
            assertTrue("klean-web-test-index" !in r.bodyAsText(), p)
        }
    }

    @Test fun refusesTraversal() = web {
        val r = client.get("/assets/../../logback.xml")
        assertTrue("configuration" !in r.bodyAsText())
    }

    @Test fun withoutABuildTheRootIs404() = testApplication {
        routing { webRoutes(base = "no-such-web-build") }
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/tasks").status)
    }

    /** Wired into the real module: existing routes keep answering as before. */
    @Test fun existingRoutesAreUntouched() = kleanTest {
        assertEquals("""{"status":"ok"}""", client.get("/api/v1/health").bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/devices").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/nope").status)
        // POST to an unknown path is not swallowed by the GET fallback.
        assertTrue(client.post("/tasks").status.value in 400..499)
    }
}
