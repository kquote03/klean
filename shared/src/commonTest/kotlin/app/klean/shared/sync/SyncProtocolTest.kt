package app.klean.shared.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyncProtocolTest {
    private fun rec(updatedAt: Long, deviceId: String, deleted: Boolean = false, title: String = "t") = SyncRecord(
        table = "task", id = "0190-a", updatedAt = updatedAt, deviceId = deviceId, deleted = deleted,
        json = buildJsonObject { put("title", title) },
    )

    @Test fun remoteWinsWhenNoLocal() {
        val remote = rec(1, "b")
        assertSame(remote, lww(null, remote))
    }

    @Test fun newerUpdateWins() {
        val local = rec(10, "a")
        val remote = rec(11, "a")
        assertSame(remote, lww(local, remote))
        assertSame(local, lww(remote = rec(9, "z"), local = local))
    }

    @Test fun tieBrokenByDeviceIdLexically() {
        val a = rec(10, "device-a")
        val b = rec(10, "device-b")
        assertSame(b, lww(a, b))
        assertSame(b, lww(b, a))
    }

    @Test fun identicalVersionKeepsLocal() {
        val local = rec(10, "a")
        assertSame(local, lww(local, rec(10, "a")))
    }

    @Test fun lwwIsOrderIndependent() {
        val versions = listOf(rec(5, "c"), rec(7, "a"), rec(7, "b", deleted = true), rec(6, "z"))
        val forward = versions.fold(null as SyncRecord?) { acc, r -> lww(acc, r) }
        val backward = versions.reversed().fold(null as SyncRecord?) { acc, r -> lww(acc, r) }
        assertEquals(forward, backward)
        assertEquals(rec(7, "b", deleted = true), forward)
    }

    @Test fun tombstoneIsJustANewerVersion() {
        val live = rec(10, "a")
        val tomb = rec(11, "b", deleted = true)
        assertTrue(lww(live, tomb).deleted)
        // An older live edit never resurrects a tombstone; a newer one does (explicit restore).
        assertTrue(lww(tomb, rec(9, "c")).deleted)
        assertFalse(lww(tomb, rec(12, "c")).deleted)
    }

    @Test fun recordsSerialiseWithStableFieldNames() {
        val json = Json.encodeToString(SyncRecord.serializer(), rec(1, "d"))
        assertEquals("""{"table":"task","id":"0190-a","updatedAt":1,"deviceId":"d","deleted":false,"json":{"title":"t"}}""", json)
        val back = Json.decodeFromString(PushRequest.serializer(), """{"deviceId":"d","records":[$json]}""")
        assertEquals(JsonPrimitive("t"), back.records.single().json["title"])
    }

    @Test fun pullResponseRoundTrips() {
        val pull = PullResponse(records = listOf(rec(1, "d")), cursor = 42, hasMore = true)
        val text = Json.encodeToString(PullResponse.serializer(), pull)
        assertEquals(pull, Json.decodeFromString(PullResponse.serializer(), text))
        assertEquals(PushResponse(3, 9), Json.decodeFromString(PushResponse.serializer(), """{"accepted":3,"serverCursor":9}"""))
    }
}
