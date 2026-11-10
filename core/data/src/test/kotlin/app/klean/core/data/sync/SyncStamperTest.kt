package app.klean.core.data.sync

import app.klean.core.data.db.entity.SyncColumns

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.FakeClock
import app.klean.core.data.db.transact
import app.klean.core.data.inMemoryDb
import app.klean.core.data.tempDevicePrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStamperTest {
    private val clock = FakeClock(1_000_000L)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)

    @After fun close() = db.close()

    @Test fun changeSeqIncrementsPerStamp() = runBlocking {
        val a = db.transact { stamper.stamp() }
        val b = db.transact { stamper.stamp() }
        assertEquals(1L, a.changeSeq)
        assertEquals(2L, b.changeSeq)
        assertEquals(prefs.deviceId(), a.deviceId)
        assertFalse(a.deleted)
    }

    @Test fun updateKeepsCreatedAtAndAdvancesUpdatedAt() = runBlocking {
        val first = db.transact { stamper.stamp() }
        clock.nowMs += 5_000
        val second = db.transact { stamper.stamp(first) }
        assertEquals(1_000_000L, second.createdAt)
        assertEquals(1_005_000L, second.updatedAt)
        assertTrue(second.changeSeq > first.changeSeq)
    }

    @Test fun deletedSetsTombstoneAndIsKeptOnLaterStamps() = runBlocking {
        val first = db.transact { stamper.stamp() }
        val gone = db.transact { stamper.stamp(first, deleted = true) }
        assertTrue(gone.deleted)
        assertTrue(db.transact { stamper.stamp(gone) }.deleted)
        assertFalse(db.transact { stamper.stamp(gone, deleted = false) }.deleted)
    }

    @Test fun anEditOnAnotherDevicesNewerVersionStillWinsLww() = runBlocking {
        val theirs = SyncColumns(createdAt = 1L, updatedAt = 2_000_000L, deviceId = "other-phone", changeSeq = 0)
        val mine = db.transact { stamper.stamp(theirs) }
        assertEquals(2_000_001L, mine.updatedAt) // this phone's clock (1_000_000) is behind
        assertEquals(prefs.deviceId(), mine.deviceId)
        clock.nowMs = 3_000_000L
        assertEquals(3_000_000L, db.transact { stamper.stamp(theirs) }.updatedAt)
    }
}
