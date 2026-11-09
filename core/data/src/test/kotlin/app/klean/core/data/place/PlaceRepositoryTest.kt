package app.klean.core.data.place

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.FakeClock
import app.klean.core.data.inMemoryDb
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceRepositoryTest {
    private val clock = FakeClock(1_700_000_000_000L)
    private val db = inMemoryDb()
    private val repo = DefaultPlaceRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock), clock)

    @After fun close() = db.close()

    private val mall = PlaceDraft(label = "Dubai Mall food court", latitude = 25.1972, longitude = 55.2796, radiusM = 150)

    @Test fun addStoresAStampedUuidV7Row() = runBlocking {
        val id = repo.save(null, mall)
        val row = db.geofenceDao().get(id)!!
        assertEquals(36, id.length)
        assertEquals('7', id[14]) // UUIDv7
        assertEquals("Dubai Mall food court", row.label)
        assertEquals(150, row.radiusM)
        assertTrue(row.enabled)
        assertEquals(clock.nowMs, row.sync.createdAt)
        assertTrue(row.sync.changeSeq > 0)
        assertEquals(listOf("Dubai Mall food court"), repo.observeAll().first().map { it.label })
    }

    @Test fun editKeepsCreatedAtAndBumpsChangeSeq() = runBlocking {
        val id = repo.save(null, mall)
        val before = db.geofenceDao().get(id)!!
        clock.nowMs += 60_000
        repo.save(id, mall.copy(label = "Mall", radiusM = 300, enabled = false))
        val after = db.geofenceDao().get(id)!!
        assertEquals("Mall", after.label)
        assertEquals(300, after.radiusM)
        assertFalse(after.enabled)
        assertEquals(before.sync.createdAt, after.sync.createdAt)
        assertEquals(clock.nowMs, after.sync.updatedAt)
        assertTrue(after.sync.changeSeq > before.sync.changeSeq)
        assertEquals(emptyList<Place>(), repo.enabled())
    }

    @Test fun deleteTombstonesAndHidesTheRow() = runBlocking {
        val id = repo.save(null, mall)
        val seq = db.geofenceDao().get(id)!!.sync.changeSeq
        repo.delete(id)
        val row = db.geofenceDao().get(id)!!
        assertTrue("tombstone, never a hard delete", row.sync.deleted)
        assertNotEquals(seq, row.sync.changeSeq)
        assertTrue(repo.observeAll().first().isEmpty())
        assertNull(repo.get(id))
        assertEquals(listOf(id), repo.allIdsIncludingRemoved())
    }

    @Test fun valuesAreKeptInRange() = runBlocking {
        val id = repo.save(null, mall.copy(label = "  Office  ", radiusM = 20))
        val p = repo.get(id)!!
        assertEquals("Office", p.label)
        assertEquals(PlaceDraft.MIN_RADIUS_M, p.radiusM)
        val big = repo.get(repo.save(null, mall.copy(radiusM = 5_000)))!!
        assertEquals(PlaceDraft.MAX_RADIUS_M, big.radiusM)
    }

    @Test fun enabledListsOnlyActivePlaces() = runBlocking {
        val a = repo.save(null, mall)
        val b = repo.save(null, mall.copy(label = "Gym"))
        repo.setEnabled(b, false)
        val c = repo.save(null, mall.copy(label = "Office"))
        repo.delete(c)
        assertEquals(listOf(a), repo.enabled().map { it.id })
    }
}
