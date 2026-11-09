package app.klean.core.data.device

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultDeviceRepositoryTest {
    private val clock = FakeClock(1_762_700_000_000L)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val repo = DefaultDeviceRepository(db, SyncStamper(db, prefs, clock), prefs, clock, deviceName = "Pixel 9")

    @After fun close() = db.close()

    @Test fun registerUpsertsThisDeviceAsMainPhoneByDefault() = runBlocking {
        repo.registerThisDevice()
        val row = db.deviceDao().get(prefs.deviceId())!!
        assertEquals("Pixel 9", row.name)
        assertEquals("ANDROID", row.platform)
        assertTrue(row.isMainPhone)
        assertEquals(clock.nowMs, row.lastSeenAt)
        assertEquals(prefs.deviceId(), row.sync.deviceId)
        assertFalse(row.sync.deleted)
    }

    @Test fun registeringAgainKeepsOneRowAndRefreshesLastSeen() = runBlocking {
        repo.registerThisDevice()
        val first = db.deviceDao().get(prefs.deviceId())!!
        clock.nowMs += 60_000
        repo.registerThisDevice()
        val rows = db.deviceDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(clock.nowMs, rows.single().lastSeenAt)
        assertEquals(first.sync.createdAt, rows.single().sync.createdAt)
        assertTrue(rows.single().sync.changeSeq > first.sync.changeSeq)
    }

    @Test fun setMainPhoneUpdatesPrefAndRow() = runBlocking {
        repo.registerThisDevice()
        repo.setMainPhone(false)
        assertFalse(prefs.isMainPhone.first())
        assertFalse(db.deviceDao().get(prefs.deviceId())!!.isMainPhone)
        repo.setMainPhone(true)
        assertEquals(prefs.deviceId(), db.deviceDao().mainPhone()?.id)
    }
}
