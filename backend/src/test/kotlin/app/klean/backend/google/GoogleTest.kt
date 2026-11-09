package app.klean.backend.google

import app.klean.backend.KleanConfig
import app.klean.backend.Services
import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.backend.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var accessCalls = 0
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** A fake of Google's OAuth, Calendar and Tasks endpoints. Real Google is never contacted. */
    private val engine = MockEngine { req ->
        requests += req
        val body = when (val b = req.body) {
            is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
            else -> ""
        }
        bodies += body
        val path = req.url.encodedPath
        when {
            req.url.host == "oauth2.googleapis.com" && path == "/token" -> {
                val form = parseQueryString(body)
                accessCalls++
                if (form["grant_type"] == "authorization_code" && form["code"] == "good-code")
                    respond("""{"access_token":"at-1","expires_in":3600,"refresh_token":"rt-1","scope":"https://www.googleapis.com/auth/tasks","token_type":"Bearer"}""", HttpStatusCode.OK, json)
                else if (form["grant_type"] == "refresh_token" && form["refresh_token"] == "rt-1")
                    respond("""{"access_token":"at-2","expires_in":3600,"token_type":"Bearer"}""", HttpStatusCode.OK, json)
                else respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, json)
            }
            path == "/calendar/v3/calendars/primary/events" ->
                respond("""{"items":[{"id":"e1","summary":"Dentist","start":{"dateTime":"2026-11-10T14:00:00+04:00"}}]}""", HttpStatusCode.OK, json)
            path == "/tasks/v1/users/@me/lists" ->
                respond("""{"items":[{"id":"L1","title":"My Tasks"}]}""", HttpStatusCode.OK, json)
            path == "/tasks/v1/lists/L1/tasks" && req.method == HttpMethod.Get ->
                respond("""{"items":[{"id":"T1","title":"Pay rent","status":"needsAction"}]}""", HttpStatusCode.OK, json)
            path == "/tasks/v1/lists/L1/tasks/T1" && req.method == HttpMethod.Patch ->
                respond("""{"id":"T1","title":"Pay rent","status":"completed"}""", HttpStatusCode.OK, json)
            else -> respond("""{"error":"unexpected $path"}""", HttpStatusCode.NotFound, json)
        }
    }

    private val config: KleanConfig = testConfig().copy(googleClientId = "cid.apps.googleusercontent.com", googleClientSecret = "secret")
    private val services: (KleanConfig) -> Services = { Services.create(it, httpClient = HttpClient(engine)) }

    @AfterTest fun neverCreatesGoogleTasks() {
        // The overlay must never create a Google task: no POST to any Tasks API URL, in any test of this class.
        val creates = requests.filter { it.method == HttpMethod.Post && "/tasks/" in it.url.encodedPath }
        assertEquals(emptyList(), creates.map { it.url.toString() })
        assertTrue(requests.filter { it.method == HttpMethod.Post }.all { it.url.host == "oauth2.googleapis.com" })
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.connected(): Pair<HttpClient, String> {
        val c = jsonClient()
        val web = c.login()
        val start = c.get("/api/v1/google/oauth/start") { bearerAuth(web.token) }.body<GoogleStartResponse>()
        val state = parseQueryString(start.url.substringAfter('?'))["state"]!!
        val noRedirect = createClient { followRedirects = false }
        val cb = noRedirect.get("/api/v1/google/oauth/callback?code=good-code&state=$state")
        assertEquals(HttpStatusCode.Found, cb.status)
        return c to web.token
    }

    @Test fun startBuildsAnOfflineConsentUrl() = kleanTest(config, services) {
        val c = jsonClient()
        val web = c.login()
        val start = c.get("/api/v1/google/oauth/start") { bearerAuth(web.token) }.body<GoogleStartResponse>()
        assertTrue(start.url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"), start.url)
        val q: Parameters = parseQueryString(start.url.substringAfter('?'))
        assertEquals("cid.apps.googleusercontent.com", q["client_id"])
        assertEquals("http://klean.test/api/v1/google/oauth/callback", q["redirect_uri"])
        assertEquals("offline", q["access_type"])
        assertEquals("code", q["response_type"])
        assertTrue("https://www.googleapis.com/auth/tasks" in q["scope"]!!.split(' '))
        assertTrue("https://www.googleapis.com/auth/calendar.readonly" in q["scope"]!!.split(' '))
        val phone = c.claimDevice(web.token, "Pixel")
        assertEquals(HttpStatusCode.Forbidden, c.get("/api/v1/google/oauth/start") { bearerAuth(phone.token) }.status)
    }

    @Test fun callbackRejectsAWrongState() = kleanTest(config, services) {
        val c = jsonClient()
        val web = c.login()
        c.get("/api/v1/google/oauth/start") { bearerAuth(web.token) }
        val r = createClient { followRedirects = false }.get("/api/v1/google/oauth/callback?code=good-code&state=forged")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertFalse(c.get("/api/v1/google/status") { bearerAuth(web.token) }.body<GoogleStatus>().connected)
    }

    @Test fun connectThenReadCalendarAndTasks() = kleanTest(config, services) {
        val (c, token) = connected()
        assertEquals(GoogleStatus(configured = true, connected = true), c.get("/api/v1/google/status") { bearerAuth(token) }.body<GoogleStatus>())

        val events = Json.parseToJsonElement(c.get("/api/v1/google/calendar/events?timeMin=2026-11-10T00:00:00Z") { bearerAuth(token) }.bodyAsText()).jsonObject
        assertEquals("Dentist", events["items"]!!.jsonArray[0].jsonObject["summary"]!!.jsonPrimitive.content)
        val calReq = requests.last { "/calendar/" in it.url.encodedPath }
        assertEquals("Bearer at-1", calReq.headers[HttpHeaders.Authorization])
        assertEquals("2026-11-10T00:00:00Z", calReq.url.parameters["timeMin"])
        assertEquals("true", calReq.url.parameters["singleEvents"])

        val lists = Json.parseToJsonElement(c.get("/api/v1/google/tasks/lists") { bearerAuth(token) }.bodyAsText()).jsonObject
        assertEquals("L1", lists["items"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        val tasks = Json.parseToJsonElement(c.get("/api/v1/google/tasks/lists/L1/tasks") { bearerAuth(token) }.bodyAsText()).jsonObject
        assertEquals("Pay rent", tasks["items"]!!.jsonArray[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test fun patchSendsOnlyAllowedFields() = kleanTest(config, services) {
        val (c, token) = connected()
        val r = c.patch("/api/v1/google/tasks/lists/L1/tasks/T1") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody("""{"status":"completed","title":"Pay rent","parent":"X","id":"other","due":"2026-11-12"}""")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        val sent = Json.parseToJsonElement(bodies[requests.indexOfLast { it.method == HttpMethod.Patch }]).jsonObject
        assertEquals(setOf("status", "title", "due"), sent.keys)
        assertEquals("2026-11-12T00:00:00.000Z", sent["due"]!!.jsonPrimitive.content)

        // Un-completing clears `completed`, as the Tasks API requires.
        c.patch("/api/v1/google/tasks/lists/L1/tasks/T1") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"status":"needsAction"}""") }
        val reopen = Json.parseToJsonElement(bodies[requests.indexOfLast { it.method == HttpMethod.Patch }]).jsonObject
        assertEquals(JsonNull, reopen["completed"])

        val bad = c.patch("/api/v1/google/tasks/lists/L1/tasks/T1") { bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"status":"deleted"}""") }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test fun thereIsNoRouteToCreateATask() = kleanTest(config, services) {
        val (c, token) = connected()
        for (path in listOf("/api/v1/google/tasks/lists/L1/tasks", "/api/v1/google/tasks/lists", "/api/v1/google/tasks/lists/L1/tasks/T1")) {
            val post = c.post(path) { bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"title":"new"}""") }
            assertTrue(post.status == HttpStatusCode.NotFound || post.status == HttpStatusCode.MethodNotAllowed, "$path → ${post.status}")
            val put = c.put(path) { bearerAuth(token); contentType(ContentType.Application.Json); setBody("""{"title":"new"}""") }
            assertTrue(put.status == HttpStatusCode.NotFound || put.status == HttpStatusCode.MethodNotAllowed, "$path → ${put.status}")
        }
    }

    @Test fun expiredAccessTokenIsRefreshed() = kleanTest(config, services) { s ->
        val (c, token) = connected()
        s.google.expireAccessTokenForTest()
        c.get("/api/v1/google/tasks/lists") { bearerAuth(token) }
        assertEquals("Bearer at-2", requests.last { "/tasks/" in it.url.encodedPath }.headers[HttpHeaders.Authorization])
        assertEquals(2, accessCalls)
    }

    @Test fun notConfiguredOrNotConnected() = kleanTest(services = services) {
        val c = jsonClient()
        val web = c.login()
        assertEquals(GoogleStatus(configured = false, connected = false), c.get("/api/v1/google/status") { bearerAuth(web.token) }.body<GoogleStatus>())
        assertEquals(HttpStatusCode.ServiceUnavailable, c.get("/api/v1/google/oauth/start") { bearerAuth(web.token) }.status)
        assertEquals(HttpStatusCode.Conflict, c.get("/api/v1/google/tasks/lists") { bearerAuth(web.token) }.status)
    }

    @Test fun disconnectForgetsTheRefreshToken() = kleanTest(config, services) {
        val (c, token) = connected()
        assertEquals(HttpStatusCode.NoContent, c.delete("/api/v1/google/oauth") { bearerAuth(token) }.status)
        assertFalse(c.get("/api/v1/google/status") { bearerAuth(token) }.body<GoogleStatus>().connected)
    }
}
