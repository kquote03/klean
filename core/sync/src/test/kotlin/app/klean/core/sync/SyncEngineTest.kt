package app.klean.core.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.shared.sync.PullResponse
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.PushResponse
import app.klean.shared.sync.SyncRecord
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The engine against the real backend (`:backend`, in-process): two phones, LWW, tombstones, blobs, faults. */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val a = Phone("phone-a")
    private val b = Phone("phone-b")

    @After fun close() {
        a.close()
        b.close()
    }

    @Test fun twoPhonesConverge() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        a.putTask("t1", "Buy milk", at = 1_000)
        a.putFood("f1", "toast", at = 1_100)
        b.putTask("t2", "Call mum", at = 1_200)

        val first = a.engine.sync(apiA)
        assertEquals(2, first.pushed)
        b.engine.sync(apiB)
        a.engine.sync(apiA)

        for (p in listOf(a, b)) {
            assertEquals("Buy milk", p.task("t1")!!.title)
            assertEquals("Call mum", p.task("t2")!!.title)
            assertEquals("toast", p.db.foodEntryDao().get("f1")!!.rawText)
        }
        // Same version everywhere, and pulled rows aren't queued to push again.
        assertEquals(a.task("t2")!!.sync.updatedAt, b.task("t2")!!.sync.updatedAt)
        assertEquals("phone-b", a.task("t2")!!.sync.deviceId)
        assertEquals(0, a.engine.sync(apiA).pushed)
        assertEquals(0, b.engine.sync(apiB).pushed)
    }

    @Test fun foodEntryCaloriesRoundTrip() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        a.putFood("f-kcal", "chips", at = 1_100, kcal = 300, kcalSource = "USER", grams = 200)
        a.putFood("f-none", "salad", at = 1_200)
        a.engine.sync(apiA)
        b.engine.sync(apiB)

        val chips = b.db.foodEntryDao().get("f-kcal")!!
        assertEquals(300, chips.kcal)
        assertEquals("USER", chips.kcalSource)
        assertEquals(200, chips.grams)
        val salad = b.db.foodEntryDao().get("f-none")!!
        assertEquals(null, salad.kcal)
        assertEquals(null, salad.grams)
    }

    @Test fun tombstonesPropagateAndAnOlderEditDoesNotResurrect() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        val c = Phone("phone-c")
        try {
            val apiC = pair(c)
            a.putTask("t1", "Old idea", at = 1_000)
            a.engine.sync(apiA)
            b.engine.sync(apiB)
            c.engine.sync(apiC)
            assertFalse(b.task("t1")!!.sync.deleted)

            // C edits offline at 2 000; A deletes at 3 000 and syncs first.
            c.putTask("t1", "Edited offline", at = 2_000)
            a.putTask("t1", "Old idea", at = 3_000, deleted = true)
            a.engine.sync(apiA)
            b.engine.sync(apiB)
            assertTrue(b.task("t1")!!.sync.deleted)

            // C's older live edit reaches the server late: the tombstone stays, and C takes it.
            c.engine.sync(apiC)
            assertTrue(c.task("t1")!!.sync.deleted)
            b.engine.sync(apiB)
            assertTrue(b.task("t1")!!.sync.deleted)
            assertEquals("Old idea", b.task("t1")!!.title)
        } finally {
            c.close()
        }
    }

    @Test fun lastWriteWinsAndTiesGoToTheLargerDeviceId() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        a.putTask("t1", "Start", at = 1_000)
        a.engine.sync(apiA)
        b.engine.sync(apiB)

        a.putTask("t1", "A's edit", at = 5_000)
        b.putTask("t1", "B's later edit", at = 6_000)
        a.engine.sync(apiA)
        b.engine.sync(apiB)
        a.engine.sync(apiA)
        assertEquals("B's later edit", a.task("t1")!!.title)
        assertEquals("B's later edit", b.task("t1")!!.title)

        // Same clock on both: "phone-b" > "phone-a" wins on every side.
        a.putTask("t1", "tie A", at = 9_000)
        b.putTask("t1", "tie B", at = 9_000)
        a.engine.sync(apiA)
        b.engine.sync(apiB)
        a.engine.sync(apiA)
        assertEquals("tie B", a.task("t1")!!.title)
        assertEquals("tie B", b.task("t1")!!.title)
    }

    @Test fun anUnpushedLocalEditWinsUntilItIsPushed() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        a.putTask("t1", "Start", at = 1_000)
        a.engine.sync(apiA)
        b.engine.sync(apiB)
        a.putTask("t1", "A's edit", at = 5_000)
        a.engine.sync(apiA)

        // B edits while its run is between push and pull (older clock than A's edit).
        val racing = object : SyncApi by apiB {
            var edited = false
            override suspend fun pull(cursor: Long, limit: Int): PullResponse {
                if (!edited) {
                    edited = true
                    b.putTask("t1", "B's unsent edit", at = 4_000)
                }
                return apiB.pull(cursor, limit)
            }
        }
        val run = b.engine.sync(racing)
        assertFalse(run.complete)
        assertEquals("B's unsent edit", b.task("t1")!!.title)

        // The next run pushes it; the server keeps A's newer version and B settles on it.
        assertTrue(b.engine.sync(apiB).complete)
        assertEquals("A's edit", b.task("t1")!!.title)
    }

    @Test fun blobsUploadOnceAndArriveOnTheOtherPhone() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        val bytes = ByteArray(2048) { (it % 251).toByte() }
        File(a.files, "photos").apply { mkdirs() }.resolve("p1.jpg").writeBytes(bytes)
        a.putFood("f1", "salad", at = 1_000, photo = "p1.jpg")
        a.putFood("f2", "same photo", at = 1_100, photo = "p1.jpg")

        val counting = CountingApi(apiA)
        a.engine.sync(counting)
        assertEquals(1, counting.puts) // the second record found it with HEAD
        assertTrue(apiA.hasBlob(BlobFiles.sha256(bytes)))

        val pulled = apiB.pull(0, 100).records.single { it.id == "f1" }
        assertEquals(BlobFiles.sha256(bytes), pulled.json[SyncTables.BLOB_KEY]!!.jsonObject["photo_file"]!!.jsonPrimitive.content)

        b.engine.sync(apiB)
        assertArrayEquals(bytes, File(b.files, "photos/p1.jpg").readBytes())
        assertEquals("p1.jpg", b.db.foodEntryDao().get("f1")!!.photoFile)
    }

    @Test fun serverOnlyKeysAndUnknownTablesAreIgnored() = withServer {
        val apiA = pair(a)
        val apiB = pair(b)
        a.putTask("t1", "From A", at = 1_000)
        a.engine.sync(apiA)
        val row = apiA.pull(0, 10).records.single()
        val web = PushRequest(
            "web",
            listOf(
                row.copy(updatedAt = 2_000, deviceId = "web", json = JsonObject(row.json + mapOf("title" to JsonPrimitive("From the web"), "dav_extras" to JsonPrimitive("X-FOO:1")))),
                SyncRecord("future_table", "x1", 2_000, "web", false, buildJsonObject { put("id", "x1") }),
            ),
        )
        apiA.push(web)
        val report = b.engine.sync(apiB)
        assertTrue(report.complete)
        assertEquals("From the web", b.task("t1")!!.title)
        assertEquals("web", b.task("t1")!!.sync.deviceId)
    }

    @Test fun whenTheServerIsDownLocalUseGoesOnAndTheNextRunCatchesUp() = withServer {
        val apiA = pair(a)
        a.putTask("t1", "Offline task", at = 1_000)
        val down = object : SyncApi by apiA {
            override suspend fun push(request: PushRequest): PushResponse = throw IOException("connection refused")
        }
        val failing500 = object : SyncApi by apiA {
            override suspend fun pull(cursor: Long, limit: Int): PullResponse = throw SyncHttpException(500, "boom")
        }
        try {
            a.engine.sync(down)
            fail("expected a network error")
        } catch (e: IOException) {
            // retried by WorkManager
        }
        // Local writes keep working and nothing was marked as sent.
        a.putTask("t2", "Still works", at = 1_100)
        assertEquals(0L, RoomLocalStore(a.db).cursor().lastPushSeq)

        try {
            a.engine.sync(failing500)
            fail("expected a server error")
        } catch (e: SyncHttpException) {
            assertEquals(500, e.status)
        }
        // The push half went through; the pull cursor didn't move.
        assertEquals(0L, RoomLocalStore(a.db).cursor().cursor)
        assertNotNull(a.task("t2"))

        val ok = a.engine.sync(apiA)
        assertTrue(ok.complete)
        assertEquals(setOf("t1", "t2"), apiA.pull(0, 10).records.map { it.id }.toSet())
        assertNull(SyncOutcome.Failed(SyncProblem.UNAUTHORIZED).takeIf { it.retry })
        assertTrue(SyncOutcome.Failed(SyncProblem.OFFLINE).retry && SyncOutcome.Failed(SyncProblem.SERVER).retry)
    }

    private class CountingApi(private val inner: SyncApi) : SyncApi by inner {
        var puts = 0
        override suspend fun putBlob(sha256: String, bytes: ByteArray, contentType: String) {
            puts++
            inner.putBlob(sha256, bytes, contentType)
        }
    }
}
