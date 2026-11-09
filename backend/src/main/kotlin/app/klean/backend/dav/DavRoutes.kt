package app.klean.backend.dav

import app.klean.backend.ApiException
import app.klean.backend.AUTH_DAV
import app.klean.backend.auth.DevicePrincipal
import app.klean.backend.db.RecordStore
import app.klean.backend.db.StoredRecord
import app.klean.shared.ical.ICalCodec
import app.klean.shared.sync.SyncRecord
import app.klean.shared.vcard.VCardCodec
import app.klean.shared.vcard.VCardVersion
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.net.URI
import java.net.URLDecoder
import kotlin.time.Clock

/**
 * Minimal CardDAV (RFC 6352) + CalDAV (RFC 4791) over the generic records of `contact` and `event`:
 *
 * ```
 * /dav/                       root            → current-user-principal
 * /dav/principal/             principal       → addressbook-home-set, calendar-home-set
 * /dav/contacts/              addressbook home
 * /dav/contacts/klean/        the address book  (members <id>.vcf)
 * /dav/calendars/             calendar home
 * /dav/calendars/klean/       the calendar      (members <id>.ics)
 * ```
 * Methods: OPTIONS, PROPFIND (Depth 0/1), REPORT (sync-collection, *-multiget, *-query), GET/HEAD, PUT, DELETE.
 * ETags are the record's sha256; sync tokens are the table's highest `server_seq`. A DAV write is an ordinary
 * synced record version (LWW clock = server time), so phones pull it like any other change.
 *
 * Simplifications (listed in TASKS.md): query REPORT filters are ignored (every live member is returned), one
 * address book and one calendar, no MKCOL/PROPPATCH/ACL, RECURRENCE-ID overrides are dropped.
 */
class Dav(private val records: RecordStore, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(Dav::class.java)

    enum class Kind(val table: String, val home: String, val ext: String, val contentType: String) {
        CONTACTS("contact", "contacts", "vcf", "text/vcard; charset=utf-8"),
        CALENDAR("event", "calendars", "ics", "text/calendar; charset=utf-8; component=vevent");

        val homePath get() = "/dav/$home/"
        val collectionPath get() = "/dav/$home/$COLLECTION/"
        fun memberPath(id: String) = "$collectionPath$id.$ext"
    }

    sealed interface Res {
        data object Root : Res
        data object Principal : Res
        data class Home(val kind: Kind) : Res
        data class Collection(val kind: Kind) : Res
        data class Member(val kind: Kind, val id: String) : Res
    }

    fun resolve(path: String): Res? {
        val segs = path.removePrefix("/dav").split('/').filter { it.isNotEmpty() }.map { URLDecoder.decode(it, Charsets.UTF_8) }
        val kind = { s: String -> Kind.entries.firstOrNull { it.home == s } }
        return when {
            segs.isEmpty() -> Res.Root
            segs == listOf("principal") -> Res.Principal
            segs.size == 1 -> kind(segs[0])?.let { Res.Home(it) }
            segs.size == 2 && segs[1] == COLLECTION -> kind(segs[0])?.let { Res.Collection(it) }
            segs.size == 3 && segs[1] == COLLECTION -> kind(segs[0])?.let { k ->
                val stem = segs[2].removeSuffix(".${k.ext}")
                if (MEMBER_ID.matches(stem)) Res.Member(k, stem) else null
            }
            else -> null
        }
    }

    fun hrefOf(res: Res): String = when (res) {
        Res.Root -> "/dav/"
        Res.Principal -> PRINCIPAL
        is Res.Home -> res.kind.homePath
        is Res.Collection -> res.kind.collectionPath
        is Res.Member -> res.kind.memberPath(res.id)
    }

    // ---- properties ----

    private val commonProps = listOf(dav("resourcetype"), dav("displayname"), dav("current-user-principal"), dav("current-user-privilege-set"))

    private fun allPropNames(res: Res): List<QName> = commonProps + when (res) {
        Res.Root -> emptyList()
        Res.Principal -> listOf(dav("principal-URL"), card("addressbook-home-set"), cal("calendar-home-set"))
        is Res.Home -> emptyList()
        is Res.Collection -> listOf(dav("sync-token"), cs("getctag"), dav("supported-report-set")) + when (res.kind) {
            Kind.CONTACTS -> listOf(card("supported-address-data"), card("addressbook-description"))
            Kind.CALENDAR -> listOf(cal("supported-calendar-component-set"), cal("calendar-description"))
        }
        is Res.Member -> listOf(dav("getetag"), dav("getcontenttype"))
    }

    /** Writes the value of [name] for [res], or returns false if it has none. */
    private fun XmlOut.prop(res: Res, name: QName, stored: StoredRecord?, seq: Long?, dataRequest: Element?): Boolean {
        when (name) {
            dav("resourcetype") -> el(name) {
                when (res) {
                    Res.Root, is Res.Home -> empty(dav("collection"))
                    Res.Principal -> { empty(dav("collection")); empty(dav("principal")) }
                    is Res.Collection -> {
                        empty(dav("collection"))
                        empty(if (res.kind == Kind.CONTACTS) card("addressbook") else cal("calendar"))
                    }
                    is Res.Member -> {}
                }
            }
            dav("displayname") -> text(name, when (res) {
                is Res.Collection -> if (res.kind == Kind.CONTACTS) "Klean contacts" else "Klean"
                is Res.Member -> res.id
                Res.Principal -> "Klean"
                else -> "Klean"
            })
            dav("current-user-principal") -> el(name) { href(PRINCIPAL) }
            dav("principal-URL") -> if (res == Res.Principal) el(name) { href(PRINCIPAL) } else return false
            dav("current-user-privilege-set") -> el(name) {
                listOf("read", "write", "write-properties", "write-content", "bind", "unbind").forEach { p ->
                    el(dav("privilege")) { empty(dav(p)) }
                }
            }
            card("addressbook-home-set") -> if (res == Res.Principal) el(name) { href(Kind.CONTACTS.homePath) } else return false
            cal("calendar-home-set") -> if (res == Res.Principal) el(name) { href(Kind.CALENDAR.homePath) } else return false
            dav("sync-token") -> if (res is Res.Collection && seq != null) text(name, syncToken(seq)) else return false
            cs("getctag") -> if (res is Res.Collection && seq != null) text(name, seq.toString()) else return false
            dav("supported-report-set") -> if (res is Res.Collection) el(name) {
                val reports = if (res.kind == Kind.CONTACTS) listOf(card("addressbook-multiget"), card("addressbook-query"))
                else listOf(cal("calendar-multiget"), cal("calendar-query"))
                (reports + dav("sync-collection")).forEach { r -> el(dav("supported-report")) { el(dav("report")) { empty(r) } } }
            } else return false
            card("supported-address-data") -> if (res is Res.Collection && res.kind == Kind.CONTACTS) el(name) {
                empty(card("address-data-type"), mapOf("content-type" to "text/vcard", "version" to "3.0"))
                empty(card("address-data-type"), mapOf("content-type" to "text/vcard", "version" to "4.0"))
            } else return false
            card("addressbook-description") -> if (res is Res.Collection && res.kind == Kind.CONTACTS) text(name, "Contacts synced with Klean") else return false
            cal("supported-calendar-component-set") -> if (res is Res.Collection && res.kind == Kind.CALENDAR) el(name) {
                empty(cal("comp"), mapOf("name" to "VEVENT"))
            } else return false
            cal("calendar-description") -> if (res is Res.Collection && res.kind == Kind.CALENDAR) text(name, "Events synced with Klean") else return false
            dav("getetag") -> if (stored != null) text(name, quoted(stored.etag)) else return false
            dav("getcontenttype") -> if (res is Res.Member) text(name, res.kind.contentType) else return false
            card("address-data") -> if (stored != null && res is Res.Member && res.kind == Kind.CONTACTS) {
                val version = if (dataRequest?.getAttribute("version") == "4.0") VCardVersion.V4 else VCardVersion.V3
                text(name, DavMapping.vcardText(stored.record, version))
            } else return false
            cal("calendar-data") -> if (stored != null && res is Res.Member && res.kind == Kind.CALENDAR) {
                text(name, DavMapping.icsText(stored.record))
            } else return false
            else -> return false
        }
        return true
    }

    private fun XmlOut.response(res: Res, requested: List<Pair<QName, Element?>>, stored: StoredRecord?, seq: Long?) {
        el(dav("response")) {
            href(hrefOf(res))
            val missing = ArrayList<QName>()
            val found = XmlOut()
            // Render found props into a scratch builder first so we know whether a 200 propstat is needed.
            val ok = requested.filter { (name, el) -> found.prop(res, name, stored, seq, el).also { if (!it) missing += name } }
            if (ok.isNotEmpty()) el(dav("propstat")) {
                el(dav("prop")) { ok.forEach { (name, el) -> prop(res, name, stored, seq, el) } }
                text(dav("status"), "HTTP/1.1 200 OK")
            }
            if (missing.isNotEmpty()) el(dav("propstat")) {
                el(dav("prop")) { missing.forEach { empty(it) } }
                text(dav("status"), "HTTP/1.1 404 Not Found")
            }
        }
    }

    private fun XmlOut.notFound(href: String) {
        el(dav("response")) {
            href(href)
            text(dav("status"), "HTTP/1.1 404 Not Found")
        }
    }

    private fun requestedProps(root: Element?, res: Res): List<Pair<QName, Element?>> {
        val prop = root?.child(dav("prop"))
        return prop?.children()?.map { it.qname to it } ?: allPropNames(res).map { it to null }
    }

    // ---- handlers ----

    suspend fun handle(call: ApplicationCall) {
        val path = call.request.local.uri.substringBefore('?')
        val res = resolve(path) ?: throw ApiException(HttpStatusCode.NotFound, "not found")
        when (call.request.httpMethod) {
            HttpMethod.Options -> options(call)
            PROPFIND -> propfind(call, res)
            REPORT -> report(call, res)
            HttpMethod.Get, HttpMethod.Head -> get(call, res)
            HttpMethod.Put -> put(call, res)
            HttpMethod.Delete -> delete(call, res)
            else -> {
                call.response.header(HttpHeaders.Allow, ALLOW)
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }

    private suspend fun options(call: ApplicationCall) {
        call.response.header("DAV", "1, 3, addressbook, calendar-access")
        call.response.header(HttpHeaders.Allow, ALLOW)
        call.respond(HttpStatusCode.OK)
    }

    private suspend fun propfind(call: ApplicationCall, res: Res) {
        val body = call.receiveText()
        val root = body.takeIf { it.isNotBlank() }?.let { parseXml(it) }
        if (root != null && root.qname != dav("propfind")) throw ApiException(HttpStatusCode.BadRequest, "expected propfind")
        val depth = call.request.headers["Depth"]?.trim() ?: "1"
        val out = XmlOut()
        out.open(dav("multistatus"), root = true)
        when (res) {
            is Res.Member -> {
                val stored = live(res) ?: throw ApiException(HttpStatusCode.NotFound, "not found")
                out.response(res, requestedProps(root, res), stored, null)
            }
            is Res.Collection -> {
                val seq = records.maxSeq(res.kind.table)
                out.response(res, requestedProps(root, res), null, seq)
                if (depth != "0") records.list(res.kind.table, includeDeleted = false).forEach { s ->
                    val m = Res.Member(res.kind, s.record.id)
                    out.response(m, requestedProps(root, m), s, null)
                }
            }
            is Res.Home -> {
                out.response(res, requestedProps(root, res), null, null)
                if (depth != "0") {
                    val c = Res.Collection(res.kind)
                    out.response(c, requestedProps(root, c), null, records.maxSeq(res.kind.table))
                }
            }
            Res.Root -> {
                out.response(res, requestedProps(root, res), null, null)
                if (depth != "0") {
                    out.response(Res.Principal, requestedProps(root, Res.Principal), null, null)
                    Kind.entries.forEach { out.response(Res.Home(it), requestedProps(root, Res.Home(it)), null, null) }
                }
            }
            Res.Principal -> out.response(res, requestedProps(root, res), null, null)
        }
        out.close(dav("multistatus"))
        call.respondText(out.toString(), XML, HttpStatusCode.MultiStatus)
    }

    private suspend fun report(call: ApplicationCall, res: Res) {
        val collection = res as? Res.Collection ?: throw ApiException(HttpStatusCode.BadRequest, "REPORT needs a collection")
        val kind = collection.kind
        val root = parseXml(call.receiveText())
        val props = root.child(dav("prop"))?.children()?.map { it.qname to it }
            ?: listOf(dav("getetag") to null)
        val out = XmlOut()
        out.open(dav("multistatus"), root = true)
        when (root.qname) {
            dav("sync-collection") -> {
                val token = root.child(dav("sync-token"))?.textContent?.trim().orEmpty()
                val current = records.maxSeq(kind.table)
                if (token.isEmpty()) {
                    records.list(kind.table, includeDeleted = false).forEach { s -> out.response(Res.Member(kind, s.record.id), props, s, null) }
                } else {
                    val since = parseSyncToken(token)
                    if (since == null || since > current) {
                        val err = XmlOut()
                        err.open(dav("error"), root = true); err.empty(dav("valid-sync-token")); err.close(dav("error"))
                        call.respondText(err.toString(), XML, HttpStatusCode.Forbidden)
                        return
                    }
                    records.changesSince(kind.table, since).forEach { s ->
                        if (s.record.deleted) out.notFound(kind.memberPath(s.record.id))
                        else out.response(Res.Member(kind, s.record.id), props, s, null)
                    }
                }
                out.text(dav("sync-token"), syncToken(current))
            }
            card("addressbook-multiget"), cal("calendar-multiget") -> {
                root.children().filter { it.qname == dav("href") }.forEach { h ->
                    val href = h.textContent.trim()
                    val member = resolve(pathOf(href)) as? Res.Member
                    val stored = member?.takeIf { it.kind == kind }?.let { live(it) }
                    if (member == null || stored == null) out.notFound(href) else out.response(member, props, stored, null)
                }
            }
            card("addressbook-query"), cal("calendar-query") -> {
                records.list(kind.table, includeDeleted = false).forEach { s -> out.response(Res.Member(kind, s.record.id), props, s, null) }
            }
            else -> throw ApiException(HttpStatusCode.BadRequest, "unsupported report ${root.qname}")
        }
        out.close(dav("multistatus"))
        call.respondText(out.toString(), XML, HttpStatusCode.MultiStatus)
    }

    private suspend fun get(call: ApplicationCall, res: Res) {
        val member = res as? Res.Member ?: run {
            call.respondText("Klean DAV\n", ContentType.Text.Plain)
            return
        }
        val stored = live(member) ?: throw ApiException(HttpStatusCode.NotFound, "not found")
        call.response.header(HttpHeaders.ETag, quoted(stored.etag))
        val body = if (member.kind == Kind.CONTACTS) DavMapping.vcardText(stored.record) else DavMapping.icsText(stored.record)
        call.respondText(body, ContentType.parse(member.kind.contentType))
    }

    private suspend fun put(call: ApplicationCall, res: Res) {
        val member = res as? Res.Member ?: throw ApiException(HttpStatusCode.MethodNotAllowed, "PUT needs a member")
        val existing = records.get(member.kind.table, member.id)
        val live = existing?.takeUnless { it.record.deleted }
        checkPreconditions(call, live)
        val body = call.receiveText()
        val json = when (member.kind) {
            Kind.CONTACTS -> {
                val card = VCardCodec.parse(body).singleOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "expected one vCard")
                DavMapping.contactJson(card, member.id, existing?.record?.json)
            }
            Kind.CALENDAR -> {
                val event = ICalCodec.parse(body).singleOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "expected one VEVENT")
                DavMapping.eventJson(event, member.id, existing?.record?.json)
            }
        }
        val stored = write(call, member, existing, json, deleted = false)
        call.response.header(HttpHeaders.ETag, quoted(stored.etag))
        call.respond(if (live == null) HttpStatusCode.Created else HttpStatusCode.NoContent)
    }

    private suspend fun delete(call: ApplicationCall, res: Res) {
        val member = res as? Res.Member ?: throw ApiException(HttpStatusCode.MethodNotAllowed, "DELETE needs a member")
        val existing = live(member) ?: throw ApiException(HttpStatusCode.NotFound, "not found")
        checkPreconditions(call, existing)
        write(call, member, existing, existing.record.json, deleted = true)
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun write(call: ApplicationCall, member: Res.Member, existing: StoredRecord?, json: kotlinx.serialization.json.JsonObject, deleted: Boolean): StoredRecord {
        val device = call.principal<DevicePrincipal>()!!
        // Server time is the LWW clock; never go backwards relative to what's stored.
        val now = maxOf(clock.now().toEpochMilliseconds(), (existing?.record?.updatedAt ?: 0L) + 1)
        val full = DavMapping.withSyncColumns(json, existing?.record?.json, now, device.id, deleted)
        val stored = records.put(SyncRecord(member.kind.table, member.id, now, device.id, deleted, full))
        log.info("dav {} {} {} by {}", if (deleted) "delete" else "put", member.kind.table, member.id, device.id)
        return stored
    }

    private fun checkPreconditions(call: ApplicationCall, live: StoredRecord?) {
        val ifMatch = call.request.headers[HttpHeaders.IfMatch]
        val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
        if (ifNoneMatch?.trim() == "*" && live != null) throw ApiException(HttpStatusCode.PreconditionFailed, "exists")
        if (ifMatch != null) {
            val tags = ifMatch.split(',').map { it.trim() }
            val ok = live != null && ("*" in tags || quoted(live.etag) in tags || tags.any { it.removePrefix("W/") == quoted(live.etag) })
            if (!ok) throw ApiException(HttpStatusCode.PreconditionFailed, "etag mismatch")
        }
    }

    private suspend fun live(member: Res.Member): StoredRecord? =
        records.get(member.kind.table, member.id)?.takeUnless { it.record.deleted }

    companion object {
        const val COLLECTION = "klean"
        const val PRINCIPAL = "/dav/principal/"
        const val SYNC_TOKEN_PREFIX = "http://klean.app/ns/sync/"
        val PROPFIND = HttpMethod("PROPFIND")
        val REPORT = HttpMethod("REPORT")
        const val ALLOW = "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, REPORT"
        val XML = ContentType.parse("application/xml; charset=utf-8")
        private val MEMBER_ID = Regex("^[A-Za-z0-9._@-]{1,64}$")

        fun quoted(etag: String) = "\"$etag\""
        fun syncToken(seq: Long) = "$SYNC_TOKEN_PREFIX$seq"
        fun parseSyncToken(token: String): Long? = token.takeIf { it.startsWith(SYNC_TOKEN_PREFIX) }?.removePrefix(SYNC_TOKEN_PREFIX)?.toLongOrNull()?.takeIf { it >= 0 }

        /** Hrefs may be absolute URLs; only the path matters. */
        fun pathOf(href: String): String = runCatching { URI(href).rawPath }.getOrNull() ?: href
    }
}

fun Route.davRoutes(dav: Dav) {
    // DAVx5 and others start discovery here (RFC 6764).
    get("/.well-known/carddav") { call.respondRedirect("/dav/", permanent = true) }
    get("/.well-known/caldav") { call.respondRedirect("/dav/", permanent = true) }
    authenticate(AUTH_DAV) {
        route("/dav") { handle { dav.handle(call) } }
        route("/dav/{path...}") { handle { dav.handle(call) } }
    }
}
