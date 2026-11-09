package app.klean.backend.auth

import app.klean.backend.TEST_ADMIN_PASSWORD
import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingTest {
    @Test fun wrongAdminPasswordIsRejected() = kleanTest {
        val c = jsonClient()
        val r = c.post("/api/v1/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("nope", "web")) }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test fun loginGivesAnAdminWebDevice() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val me = c.get("/api/v1/auth/me") { bearerAuth(web.token) }.body<DeviceDto>()
        assertEquals(web.deviceId, me.id)
        assertEquals("WEB", me.platform)
        assertTrue(me.admin)
    }

    @Test fun pairStartNeedsAdmin() = kleanTest {
        val c = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/v1/pair/start").status)
        val web = c.login()
        val phone = c.claimDevice(web.token, "Pixel")
        assertEquals(HttpStatusCode.Forbidden, c.post("/api/v1/pair/start") { bearerAuth(phone.token) }.status)
    }

    @Test fun startClaimListRevoke() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val start = c.post("/api/v1/pair/start") { bearerAuth(web.token) }.body<PairStartResponse>()
        assertTrue(start.qr.startsWith("klean://pair?url=http%3A%2F%2Fklean.test&code="), start.qr)
        assertTrue(start.qr.endsWith(start.code))

        val claim = c.post("/api/v1/pair/claim") {
            contentType(ContentType.Application.Json); setBody(PairClaimRequest(start.code, "Pixel 9a", "ANDROID"))
        }.body<PairClaimResponse>()
        assertNotEquals(web.token, claim.token)

        // The code is single use.
        val again = c.post("/api/v1/pair/claim") {
            contentType(ContentType.Application.Json); setBody(PairClaimRequest(start.code, "Other", "ANDROID"))
        }
        assertEquals(HttpStatusCode.Unauthorized, again.status)

        val devices = c.get("/api/v1/devices") { bearerAuth(claim.token) }.body<List<DeviceDto>>()
        assertEquals(setOf("Pixel 9a", "web"), devices.map { it.name }.toSet())
        val pixel = devices.single { it.name == "Pixel 9a" }
        assertFalse(pixel.admin)
        assertTrue(pixel.current)

        assertEquals(HttpStatusCode.OK, c.get("/api/v1/devices") { bearerAuth(claim.token) }.status)
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/devices/${claim.deviceId}") { bearerAuth(web.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/v1/devices") { bearerAuth(claim.token) }.status)
    }

    @Test fun aPhoneCannotRevokeAnotherDeviceButCanRevokeItself() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val a = c.claimDevice(web.token, "A")
        val b = c.claimDevice(web.token, "B")
        assertEquals(HttpStatusCode.Forbidden, c.delete("/api/v1/devices/${b.deviceId}") { bearerAuth(a.token) }.status)
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/devices/${a.deviceId}") { bearerAuth(a.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/v1/devices") { bearerAuth(a.token) }.status)
    }

    @Test fun unknownOrExpiredCodeIsRejected() = kleanTest {
        val c = jsonClient()
        val r = c.post("/api/v1/pair/claim") {
            contentType(ContentType.Application.Json); setBody(PairClaimRequest("ABCD-EFGH", "x", "ANDROID"))
        }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test fun appPasswordCreatesADavDevice() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val created = c.post("/api/v1/devices/app-password") {
            bearerAuth(web.token); contentType(ContentType.Application.Json); setBody(AppPasswordRequest("DAVx5"))
        }.body<AppPasswordResponse>()
        assertEquals("klean", created.username)
        val devices = c.get("/api/v1/devices") { bearerAuth(web.token) }.body<List<DeviceDto>>()
        assertEquals("DAV", devices.single { it.id == created.deviceId }.platform)
    }

    @Test fun passwordHashesVerifyAndAreSalted() {
        val a = Passwords.hash(TEST_ADMIN_PASSWORD)
        val b = Passwords.hash(TEST_ADMIN_PASSWORD)
        assertTrue(a.startsWith("\$argon2id\$v=19\$"))
        assertNotEquals(a, b)
        assertTrue(Passwords.verify(TEST_ADMIN_PASSWORD, a))
        assertFalse(Passwords.verify("wrong", a))
        assertFalse(Passwords.verify(TEST_ADMIN_PASSWORD, "garbage"))
    }
}
