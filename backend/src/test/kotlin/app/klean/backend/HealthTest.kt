package app.klean.backend

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthTest {
    @Test fun healthIsOkWithoutAuth() = kleanTest {
        val response = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test fun requestIdIsEchoedOrGenerated() = kleanTest {
        assertEquals("abc123", client.get("/api/v1/health") { header(HttpHeaders.XRequestId, "abc123") }.headers[HttpHeaders.XRequestId])
        assertNotNull(client.get("/api/v1/health").headers[HttpHeaders.XRequestId])
    }

    @Test fun unknownRoutesAre404() = kleanTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/nope").status)
    }
}
