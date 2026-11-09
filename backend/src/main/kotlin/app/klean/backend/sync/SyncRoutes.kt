package app.klean.backend.sync

import app.klean.backend.ApiException
import app.klean.backend.db.RecordStore
import app.klean.backend.device
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.PushResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

const val MAX_PUSH_RECORDS = 1000
const val DEFAULT_PULL_LIMIT = 500
const val MAX_PULL_LIMIT = 1000

/**
 * `POST /sync/push`: LWW-merges each record into the generic store. `GET /sync/pull?cursor=&limit=&tables=`:
 * records with a server sequence above `cursor`, oldest first. A client pulls its own writes back too; LWW makes
 * that a no-op. Tombstones are ordinary records with `deleted=true`.
 */
fun Route.syncRoutes(records: RecordStore) {
    val log = LoggerFactory.getLogger("app.klean.backend.sync")
    post("/sync/push") {
        val caller = call.device
        val request = call.receive<PushRequest>()
        if (request.records.size > MAX_PUSH_RECORDS) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, "at most $MAX_PUSH_RECORDS records per push")
        }
        val result = records.apply(request.records)
        log.info("push device={} records={} accepted={} rejected={} cursor={}",
            caller.id, request.records.size, result.accepted, result.rejected, result.serverCursor)
        call.respond(PushResponse(result.accepted, result.serverCursor))
    }
    get("/sync/pull") {
        call.device
        val cursor = call.request.queryParameters["cursor"]?.toLongOrNull() ?: 0L
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PULL_LIMIT
        if (cursor < 0) throw ApiException(HttpStatusCode.BadRequest, "cursor must be >= 0")
        if (limit !in 1..MAX_PULL_LIMIT) throw ApiException(HttpStatusCode.BadRequest, "limit must be 1..$MAX_PULL_LIMIT")
        val tables = call.request.queryParameters["tables"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        call.respond(records.pull(cursor, limit, tables))
    }
}
