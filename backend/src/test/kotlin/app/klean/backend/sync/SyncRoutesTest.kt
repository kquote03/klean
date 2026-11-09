package app.klean.backend.sync

import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.shared.sync.PullResponse
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.PushResponse
import app.klean.shared.sync.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRoutesTest {
    private fun task(id: String, updatedAt: Long, device: String, title: String, deleted: Boolean = false) =
        SyncRecord("task", id, updatedAt, device, deleted, buildJsonObject {
            put("id", id); put("title", title); put("updated_at", updatedAt); put("device_id", device); put("deleted", deleted)
        })

    private suspend fun HttpClient.push(token: String, device: String, vararg records: SyncRecord) =
        post("/api/v1/sync/push") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(PushRequest(device, records.toList()))
        }

    private suspend fun HttpClient.pull(token: String, cursor: Long = 0, limit: Int = 100, tables: String? = null) =
        get("/api/v1/sync/pull?cursor=$cursor&limit=$limit" + (tables?.let { "&tables=$it" } ?: "")) { bearerAuth(token) }
            .body<PullResponse>()

    @Test fun pushThenPullRoundTrip() = kleanTest {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val r = c.push(phone.token, "phone", task("t1", 100, "phone", "Buy milk"), task("t2", 101, "phone", "Call mum"))
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(PushResponse(2, 2), r.body<PushResponse>())

        val pulled = c.pull(phone.token)
        assertEquals(listOf("t1", "t2"), pulled.records.map { it.id })
        assertEquals(JsonPrimitive("Buy milk"), pulled.records[0].json["title"])
        assertEquals(2, pulled.cursor)
        assertFalse(pulled.hasMore)
    }

    @Test fun olderUpdateIsRejectedByLww() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val a = c.claimDevice(web.token, "A")
        val b = c.claimDevice(web.token, "B")
        c.push(a.token, "a", task("t1", 200, "a", "newer"))
        val stale = c.push(b.token, "b", task("t1", 150, "b", "older")).body<PushResponse>()
        assertEquals(0, stale.accepted)
        assertEquals("newer", (c.pull(b.token).records.single().json["title"] as JsonPrimitive).content)
    }

    @Test fun twoDevicesConvergeOnTheSameWinner() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val a = c.claimDevice(web.token, "A")
        val b = c.claimDevice(web.token, "B")
        // Same timestamp: the lexically larger device id wins, whatever order the pushes arrive in.
        c.push(b.token, "device-b", task("t1", 300, "device-b", "from b"))
        c.push(a.token, "device-a", task("t1", 300, "device-a", "from a"))
        val seenByA = c.pull(a.token).records.single()
        val seenByB = c.pull(b.token).records.single()
        assertEquals(seenByA, seenByB)
        assertEquals("device-b", seenByA.deviceId)
    }

    @Test fun tombstonesPropagateAndAreNotResurrectedByOlderEdits() = kleanTest {
        val c = jsonClient()
        val web = c.login()
        val a = c.claimDevice(web.token, "A")
        val b = c.claimDevice(web.token, "B")
        c.push(a.token, "a", task("t1", 100, "a", "x"))
        val cursorB = c.pull(b.token).cursor
        c.push(a.token, "a", task("t1", 110, "a", "x", deleted = true))
        val delta = c.pull(b.token, cursor = cursorB)
        assertTrue(delta.records.single().deleted)

        assertEquals(0, c.push(b.token, "b", task("t1", 105, "b", "edit")).body<PushResponse>().accepted)
        assertTrue(c.pull(b.token).records.single().deleted)
    }

    @Test fun pullPagesAndFiltersByTable() = kleanTest {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        c.push(phone.token, "p", *(1..5).map { task("t$it", it.toLong(), "p", "n$it") }.toTypedArray())
        c.push(phone.token, "p", SyncRecord("food_entry", "f1", 9, "p", false, buildJsonObject { put("text", "toast") }))
        val page1 = c.pull(phone.token, limit = 3)
        assertEquals(3, page1.records.size)
        assertTrue(page1.hasMore)
        val page2 = c.pull(phone.token, cursor = page1.cursor, limit = 3)
        assertEquals(listOf("t4", "t5", "f1"), page2.records.map { it.id })
        assertFalse(page2.hasMore)
        assertEquals(listOf("f1"), c.pull(phone.token, tables = "food_entry").records.map { it.id })
    }

    @Test fun syncNeedsADeviceToken() = kleanTest {
        val c = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, c.push("bogus", "x", task("t1", 1, "x", "y")).status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/v1/sync/pull").status)
    }

    @Test fun badLimitIsRejected() = kleanTest {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        assertEquals(HttpStatusCode.BadRequest, c.get("/api/v1/sync/pull?cursor=-1") { bearerAuth(phone.token) }.status)
        assertEquals(HttpStatusCode.BadRequest, c.get("/api/v1/sync/pull?limit=0") { bearerAuth(phone.token) }.status)
    }
}
