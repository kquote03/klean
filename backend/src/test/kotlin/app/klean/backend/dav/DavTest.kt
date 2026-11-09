package app.klean.backend.dav

import app.klean.backend.auth.AppPasswordRequest
import app.klean.backend.auth.AppPasswordResponse
import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.shared.sync.PullResponse
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DavTest {
    private val propfind = HttpMethod("PROPFIND")
    private val report = HttpMethod("REPORT")

    private val amira = """
        BEGIN:VCARD
        VERSION:3.0
        UID:amira-uid
        FN:Amira Haddad
        N:Haddad;Amira;;;
        TEL;TYPE=CELL:+971 50 123 4567
        EMAIL;TYPE=HOME:amira@example.com
        BDAY:1990-04-12
        X-SOCIALPROFILE:keep-me
        END:VCARD
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private val dentist = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//DAVx5//EN
        BEGIN:VEVENT
        UID:dentist-uid
        DTSTAMP:20261109T120000Z
        DTSTART:20261110T100000Z
        DTEND:20261110T110000Z
        SUMMARY:Dentist
        LOCATION:Clinic
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER:-PT30M
        END:VALARM
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private class Ctx(val c: HttpClient, val admin: String, val pass: String)

    private suspend fun ApplicationTestBuilder.setup(): Ctx {
        val c = jsonClient()
        val web = c.login()
        val pw = c.post("/api/v1/devices/app-password") {
            bearerAuth(web.token); contentType(ContentType.Application.Json); setBody(AppPasswordRequest("DAVx5"))
        }.body<AppPasswordResponse>()
        return Ctx(c, web.token, pw.token)
    }

    private suspend fun Ctx.dav(method: HttpMethod, path: String, body: String? = null, depth: String? = null, vararg headers: Pair<String, String>): HttpResponse =
        c.request(path) {
            this.method = method
            basicAuth("klean", pass)
            depth?.let { header("Depth", it) }
            headers.forEach { (k, v) -> header(k, v) }
            if (body != null) setBody(body)
        }

    @Test fun davNeedsBasicAuth() = kleanTest {
        val r = client.request("/dav/contacts/klean/") { method = propfind }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertTrue(r.headers[HttpHeaders.WWWAuthenticate]!!.startsWith("Basic"))
        val wrong = client.request("/dav/") { method = propfind; basicAuth("klean", "nope") }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
    }

    @Test fun wellKnownRedirects() = kleanTest {
        val c = createClient { followRedirects = false }
        val r = c.get("/.well-known/carddav")
        assertEquals(HttpStatusCode.MovedPermanently, r.status)
        assertEquals("/dav/", r.headers[HttpHeaders.Location])
    }

    @Test fun discoveryFromRootToCollections() = kleanTest {
        val ctx = setup()
        val root = ctx.dav(propfind, "/dav/", """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>""", "0")
        assertEquals(HttpStatusCode.MultiStatus, root.status)
        assertTrue("<d:current-user-principal><d:href>/dav/principal/</d:href></d:current-user-principal>" in root.bodyAsText(), root.bodyAsText())

        val principal = ctx.dav(propfind, "/dav/principal/", """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop><card:addressbook-home-set/><cal:calendar-home-set/><d:displayname/><d:unknown-thing/></d:prop>
            </d:propfind>""", "0").bodyAsText()
        assertTrue("<card:addressbook-home-set><d:href>/dav/contacts/</d:href></card:addressbook-home-set>" in principal, principal)
        assertTrue("<cal:calendar-home-set><d:href>/dav/calendars/</d:href></cal:calendar-home-set>" in principal, principal)
        assertTrue("HTTP/1.1 404 Not Found" in principal && "unknown-thing" in principal, principal)

        val home = ctx.dav(propfind, "/dav/contacts/", """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""", "1").bodyAsText()
        assertTrue("<d:href>/dav/contacts/klean/</d:href>" in home, home)
        assertTrue("<card:addressbook/>" in home, home)
    }

    @Test fun cardDavPutGetPropfindAndDelete() = kleanTest {
        val ctx = setup()
        val put = ctx.dav(HttpMethod.Put, "/dav/contacts/klean/amira.vcf", amira, null, "If-None-Match" to "*", HttpHeaders.ContentType to "text/vcard")
        assertEquals(HttpStatusCode.Created, put.status)
        // Creating again with If-None-Match: * fails.
        assertEquals(HttpStatusCode.PreconditionFailed, ctx.dav(HttpMethod.Put, "/dav/contacts/klean/amira.vcf", amira, null, "If-None-Match" to "*").status)

        val get = ctx.dav(HttpMethod.Get, "/dav/contacts/klean/amira.vcf")
        assertEquals(HttpStatusCode.OK, get.status)
        val etag = assertNotNull(get.headers[HttpHeaders.ETag])
        val card = get.bodyAsText()
        assertTrue("FN:Amira Haddad" in card, card)
        assertTrue("UID:amira-uid" in card, card)
        assertTrue("TEL;TYPE=cell:+971 50 123 4567" in card, card)
        assertTrue("BDAY:1990-04-12" in card, card)
        assertTrue("X-SOCIALPROFILE:keep-me" in card, card)

        val list = ctx.dav(propfind, "/dav/contacts/klean/", """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/"><d:prop><d:getetag/><cs:getctag/><d:sync-token/></d:prop></d:propfind>""", "1").bodyAsText()
        assertTrue("<d:href>/dav/contacts/klean/amira.vcf</d:href>" in list, list)
        assertTrue("<d:getetag>$etag</d:getetag>" in list, list)

        // Stale If-Match is refused; the current one works.
        assertEquals(HttpStatusCode.PreconditionFailed, ctx.dav(HttpMethod.Put, "/dav/contacts/klean/amira.vcf", amira, null, "If-Match" to "\"stale\"").status)
        val edited = amira.replace("FN:Amira Haddad", "FN:Amira H.")
        assertEquals(HttpStatusCode.NoContent, ctx.dav(HttpMethod.Put, "/dav/contacts/klean/amira.vcf", edited, null, "If-Match" to etag).status)

        // The contact is a normal synced record, visible to phones.
        val phone = ctx.c.claimDevice(ctx.admin, "Pixel")
        val pulled = ctx.c.get("/api/v1/sync/pull") { bearerAuth(phone.token) }.body<PullResponse>().records.single()
        assertEquals("contact", pulled.table)
        assertEquals("amira", pulled.id)
        assertEquals(JsonPrimitive("Amira H."), pulled.json["display_name"])
        assertEquals(JsonPrimitive(4), pulled.json["birthday_month"])
        assertTrue((pulled.json["phones"] as JsonPrimitive).content.contains("\"label\":\"mobile\""), pulled.json.toString())

        assertEquals(HttpStatusCode.NoContent, ctx.dav(HttpMethod.Delete, "/dav/contacts/klean/amira.vcf").status)
        assertEquals(HttpStatusCode.NotFound, ctx.dav(HttpMethod.Get, "/dav/contacts/klean/amira.vcf").status)
        val tomb = ctx.c.get("/api/v1/sync/pull") { bearerAuth(phone.token) }.body<PullResponse>().records.single()
        assertTrue(tomb.deleted)
    }

    @Test fun recordsPushedByAPhoneShowUpOverCardDav() = kleanTest {
        val ctx = setup()
        val phone = ctx.c.claimDevice(ctx.admin, "Pixel")
        val rec = SyncRecord("contact", "0190aaaa-bbbb-7ccc-8ddd-eeeeffff0000", 1000, "phone", false, buildJsonObject {
            put("id", "0190aaaa-bbbb-7ccc-8ddd-eeeeffff0000"); put("display_name", "Chidi Okafor")
            put("phones", """[{"label":"work","value":"+1 555 0100"}]"""); put("emails", "[]")
            put("birthday_month", 7); put("birthday_day", 3)
        })
        ctx.c.post("/api/v1/sync/push") { bearerAuth(phone.token); contentType(ContentType.Application.Json); setBody(PushRequest("phone", listOf(rec))) }
        val card = ctx.dav(HttpMethod.Get, "/dav/contacts/klean/${rec.id}.vcf").bodyAsText()
        assertTrue("FN:Chidi Okafor" in card, card)
        assertTrue("TEL;TYPE=work:+1 555 0100" in card, card)
        assertTrue("BDAY:--07-03" in card, card)
    }

    @Test fun calDavMultigetAndSyncCollection() = kleanTest {
        val ctx = setup()
        assertEquals(HttpStatusCode.Created, ctx.dav(HttpMethod.Put, "/dav/calendars/klean/dentist.ics", dentist).status)
        assertEquals(HttpStatusCode.Created, ctx.dav(HttpMethod.Put, "/dav/calendars/klean/other.ics", dentist.replace("dentist-uid", "other-uid").replace("Dentist", "Gym")).status)

        // Initial sync: every live member plus a token.
        val initial = ctx.dav(report, "/dav/calendars/klean/", """<?xml version="1.0"?>
            <d:sync-collection xmlns:d="DAV:"><d:sync-token/><d:sync-level>1</d:sync-level><d:prop><d:getetag/></d:prop></d:sync-collection>""", "1")
        assertEquals(HttpStatusCode.MultiStatus, initial.status)
        val body = initial.bodyAsText()
        assertTrue("/dav/calendars/klean/dentist.ics" in body && "/dav/calendars/klean/other.ics" in body, body)
        val token = Regex("<d:sync-token>([^<]+)</d:sync-token>").find(body)!!.groupValues[1]

        // Change one, delete the other: the delta has exactly those two, the deleted one as 404.
        ctx.dav(HttpMethod.Put, "/dav/calendars/klean/dentist.ics", dentist.replace("SUMMARY:Dentist", "SUMMARY:Dentist (moved)"))
        ctx.dav(HttpMethod.Delete, "/dav/calendars/klean/other.ics")
        val delta = ctx.dav(report, "/dav/calendars/klean/", """<?xml version="1.0"?>
            <d:sync-collection xmlns:d="DAV:"><d:sync-token>$token</d:sync-token><d:sync-level>1</d:sync-level><d:prop><d:getetag/></d:prop></d:sync-collection>""", "1").bodyAsText()
        val responses = Regex("<d:response>(.*?)</d:response>", RegexOption.DOT_MATCHES_ALL).findAll(delta).map { it.groupValues[1] }.toList()
        assertEquals(2, responses.size, delta)
        assertTrue(responses.single { "other.ics" in it }.contains("<d:status>HTTP/1.1 404 Not Found</d:status>"), delta)
        assertTrue(responses.single { "dentist.ics" in it }.contains("<d:getetag>"), delta)

        // A bogus token is a precondition failure.
        val bad = ctx.dav(report, "/dav/calendars/klean/", """<?xml version="1.0"?>
            <d:sync-collection xmlns:d="DAV:"><d:sync-token>http://other/1</d:sync-token><d:prop><d:getetag/></d:prop></d:sync-collection>""", "1")
        assertEquals(HttpStatusCode.Forbidden, bad.status)
        assertTrue("valid-sync-token" in bad.bodyAsText())

        // Multiget returns calendar data for known hrefs and 404 for unknown ones.
        val multiget = ctx.dav(report, "/dav/calendars/klean/", """<?xml version="1.0"?>
            <cal:calendar-multiget xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/><cal:calendar-data/></d:prop>
              <d:href>/dav/calendars/klean/dentist.ics</d:href>
              <d:href>/dav/calendars/klean/nope.ics</d:href>
            </cal:calendar-multiget>""", "1").bodyAsText()
        assertTrue("SUMMARY:Dentist (moved)" in multiget, multiget)
        assertTrue("TRIGGER:-PT30M" in multiget, multiget)
        val nope = Regex("<d:response>(.*?)</d:response>", RegexOption.DOT_MATCHES_ALL).findAll(multiget).map { it.groupValues[1] }.single { "nope.ics" in it }
        assertTrue("404" in nope)
    }

    @Test fun calendarQueryReturnsLiveEvents() = kleanTest {
        val ctx = setup()
        ctx.dav(HttpMethod.Put, "/dav/calendars/klean/dentist.ics", dentist)
        val r = ctx.dav(report, "/dav/calendars/klean/", """<?xml version="1.0"?>
            <cal:calendar-query xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/></d:prop>
              <cal:filter><cal:comp-filter name="VCALENDAR"><cal:comp-filter name="VEVENT"/></cal:comp-filter></cal:filter>
            </cal:calendar-query>""", "1")
        assertEquals(HttpStatusCode.MultiStatus, r.status)
        assertTrue("/dav/calendars/klean/dentist.ics" in r.bodyAsText())
    }

    @Test fun eventsBecomeSyncRecordsWithKleanColumns() = kleanTest {
        val ctx = setup()
        ctx.dav(HttpMethod.Put, "/dav/calendars/klean/dentist.ics", dentist)
        val phone = ctx.c.claimDevice(ctx.admin, "Pixel")
        val e = ctx.c.get("/api/v1/sync/pull") { bearerAuth(phone.token) }.body<PullResponse>().records.single()
        assertEquals("event", e.table)
        assertEquals(JsonPrimitive("Dentist"), e.json["title"])
        assertEquals(JsonPrimitive(1_794_304_800_000), e.json["start_at"])
        assertEquals(JsonPrimitive(1_794_308_400_000), e.json["end_at"])
        assertEquals(JsonPrimitive(30), e.json["reminder_minutes"])
        assertEquals(JsonPrimitive(false), e.json["all_day"])
        assertEquals(JsonPrimitive("dentist-uid"), e.json["uid"])
    }

    @Test fun badBodiesAreRejected() = kleanTest {
        val ctx = setup()
        assertEquals(HttpStatusCode.BadRequest, ctx.dav(HttpMethod.Put, "/dav/contacts/klean/x.vcf", "not a card").status)
        assertEquals(HttpStatusCode.BadRequest, ctx.dav(propfind, "/dav/", "<not-xml", "0").status)
        assertEquals(HttpStatusCode.NotFound, ctx.dav(HttpMethod.Get, "/dav/contacts/klean/missing.vcf").status)
        assertFalse(ctx.dav(HttpMethod.Options, "/dav/").headers["DAV"].isNullOrEmpty())
    }
}
