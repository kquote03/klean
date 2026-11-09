package app.klean.backend.db

import app.klean.backend.testConfig
import app.klean.shared.sync.SyncRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordStoreTest {
    private val db = KleanDb.open(testConfig())
    private val store = RecordStore(db)

    @AfterTest fun close() = db.close()

    private fun rec(id: String, updatedAt: Long, device: String = "d1", table: String = "task", deleted: Boolean = false, title: String = "x") =
        SyncRecord(table, id, updatedAt, device, deleted, buildJsonObject { put("title", title) })

    @Test fun storesAndReadsBack() = runTest {
        val result = store.apply(listOf(rec("a", 10)))
        assertEquals(1, result.accepted)
        val stored = store.get("task", "a")!!
        assertEquals(rec("a", 10), stored.record)
        assertEquals(1, stored.serverSeq)
        assertEquals(64, stored.etag.length)
        assertNull(store.get("task", "missing"))
        assertNull(store.get("food_entry", "a"))
    }

    @Test fun olderVersionIsRejectedAndDoesNotAdvanceTheCursor() = runTest {
        store.apply(listOf(rec("a", 10, title = "new")))
        val result = store.apply(listOf(rec("a", 5, title = "old")))
        assertEquals(0, result.accepted)
        assertEquals(1, result.serverCursor)
        assertEquals("new", store.get("task", "a")!!.record.json["title"].toString().trim('"'))
    }

    @Test fun samePushTwiceIsANoOp() = runTest {
        store.apply(listOf(rec("a", 10)))
        assertEquals(0, store.apply(listOf(rec("a", 10))).accepted)
    }

    @Test fun newerVersionReplacesAndGetsANewSeqAndEtag() = runTest {
        store.apply(listOf(rec("a", 10), rec("b", 10)))
        val before = store.get("task", "a")!!
        store.apply(listOf(rec("a", 11, title = "y")))
        val after = store.get("task", "a")!!
        assertEquals(3, after.serverSeq)
        assertNotEquals(before.etag, after.etag)
    }

    @Test fun pullPagesInSeqOrder() = runTest {
        store.apply((1..5).map { rec("r$it", it.toLong()) })
        val first = store.pull(cursor = 0, limit = 2)
        assertEquals(listOf("r1", "r2"), first.records.map { it.id })
        assertTrue(first.hasMore)
        val rest = store.pull(cursor = first.cursor, limit = 10)
        assertEquals(listOf("r3", "r4", "r5"), rest.records.map { it.id })
        assertFalse(rest.hasMore)
        val empty = store.pull(cursor = rest.cursor, limit = 10)
        assertTrue(empty.records.isEmpty())
        assertEquals(rest.cursor, empty.cursor)
    }

    @Test fun tombstonesAreKeptAndListed() = runTest {
        store.apply(listOf(rec("a", 1, table = "contact")))
        store.apply(listOf(rec("a", 2, table = "contact", deleted = true)))
        assertTrue(store.get("contact", "a")!!.record.deleted)
        assertEquals(1, store.list("contact", includeDeleted = true).size)
        assertEquals(0, store.list("contact", includeDeleted = false).size)
        assertEquals(listOf("a"), store.changesSince("contact", 1).map { it.record.id })
        assertEquals(2, store.maxSeq("contact"))
        assertEquals(0, store.maxSeq("event"))
    }

    @Test fun invalidTableNamesAreRejected() = runTest {
        val result = store.apply(listOf(rec("a", 1, table = "Robert'); DROP TABLE records;--")))
        assertEquals(0, result.accepted)
        assertEquals(1, result.rejected)
    }
}
