package app.klean.backend.blobs

import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.backend.sha256Hex
import app.klean.backend.testConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlobRoutesTest {
    private val photo = ByteArray(10_000) { (it % 251).toByte() }
    private val sha = sha256Hex(photo)

    @Test fun uploadThenDownload() = kleanTest { s ->
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val put = c.put("/api/v1/blobs/$sha") { bearerAuth(phone.token); contentType(ContentType.Image.JPEG); setBody(photo) }
        assertEquals(HttpStatusCode.Created, put.status)

        val get = c.get("/api/v1/blobs/$sha") { bearerAuth(phone.token) }
        assertEquals(HttpStatusCode.OK, get.status)
        assertEquals("image/jpeg", get.headers[HttpHeaders.ContentType])
        assertContentEquals(photo, get.readRawBytes())
        assertEquals(HttpStatusCode.OK, c.head("/api/v1/blobs/$sha") { bearerAuth(phone.token) }.status)
        assertTrue(File(s.config.blobDir, "${sha.take(2)}/$sha").isFile)
    }

    @Test fun reUploadIsIdempotent() = kleanTest {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        c.put("/api/v1/blobs/$sha") { bearerAuth(phone.token); setBody(photo) }
        assertEquals(HttpStatusCode.OK, c.put("/api/v1/blobs/$sha") { bearerAuth(phone.token); setBody(photo) }.status)
    }

    @Test fun hashMismatchIsRejectedAndNothingIsStored() = kleanTest { s ->
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val wrong = sha256Hex("something else".toByteArray())
        val put = c.put("/api/v1/blobs/$wrong") { bearerAuth(phone.token); setBody(photo) }
        assertEquals(HttpStatusCode.BadRequest, put.status)
        assertEquals(HttpStatusCode.NotFound, c.get("/api/v1/blobs/$wrong") { bearerAuth(phone.token) }.status)
        assertEquals(emptyList(), s.config.blobDir.walkTopDown().filter { it.isFile }.toList())
    }

    @Test fun badHashFormatIsRejected() = kleanTest {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        assertEquals(HttpStatusCode.BadRequest, c.put("/api/v1/blobs/..%2F..%2Fetc") { bearerAuth(phone.token); setBody(photo) }.status)
        assertEquals(HttpStatusCode.BadRequest, c.get("/api/v1/blobs/ABC") { bearerAuth(phone.token) }.status)
    }

    @Test fun tooLargeIsRejected() = kleanTest(config = testConfig().copy(blobMaxBytes = 1000)) {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        assertEquals(HttpStatusCode.PayloadTooLarge, c.put("/api/v1/blobs/$sha") { bearerAuth(phone.token); setBody(photo) }.status)
    }

    @Test fun blobsNeedAToken() = kleanTest {
        val c = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/v1/blobs/$sha").status)
    }
}
