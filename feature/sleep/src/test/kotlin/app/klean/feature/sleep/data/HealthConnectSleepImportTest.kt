package app.klean.feature.sleep.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.core.health.FakeHealthConnectGateway
import app.klean.core.health.HcSleep
import app.klean.core.health.HealthAvailability
import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.FakeClock
import app.klean.feature.sleep.inMemoryDb
import app.klean.feature.sleep.tempDevicePrefs
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectSleepImportTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private val nov8 = LocalDate(2026, 11, 8)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(nov10, 12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val hc = FakeHealthConnectGateway()
    private var changes = 0
    private val listener = SleepChangeListener { changes++ }
    private val import = HealthConnectSleepImport(hc, db, stamper, clock, setOf(listener))

    @After fun close() = db.close()

    private suspend fun insertManual(id: String, bed: Long, wake: Long) = db.sleepDao().upsert(
        app.klean.core.data.db.entity.SleepSessionEntity(id, bed, wake, sync = stamper.stamp()),
    )

    @Test fun importsEachNightOnceAndReplacesTheOverlappingManualOne() = runBlocking {
        insertManual("manual-same", at(nov9, 23), at(nov10, 7))      // overlaps the watch night by ~90%
        insertManual("manual-other", at(nov8, 0, 30), at(nov8, 7))    // a night the watch didn't record
        hc.sleep += HcSleep("w1", at(nov9, 23, 30), at(nov10, 7, 10), "com.watch")
        hc.sleep += HcSleep("w2", at(nov9 , 14), at(nov9, 15), "com.watch")

        assertEquals(2, import.importRecent(30))
        val live = db.sleepDao().observeAll().first()
        assertEquals(setOf("w1", "w2", null), live.map { it.hcRecordId }.toSet())
        assertTrue(live.none { it.id == "manual-same" })
        val replaced = db.sleepDao().get("manual-same")!!
        assertTrue(replaced.sync.deleted)
        assertEquals(HealthConnectSleepImport.REPLACED_NOTE, replaced.notes)
        assertEquals(1, changes)

        // A second run brings nothing new.
        assertEquals(0, import.importRecent(30))
        assertEquals(3, db.sleepDao().observeAll().first().size)
    }

    @Test fun withoutHealthConnectNothingHappens() = runBlocking {
        hc.available = HealthAvailability.UNAVAILABLE
        hc.sleep += HcSleep("w1", at(nov9, 23), at(nov10, 7), "com.watch")
        assertEquals(0, import.importRecent(30))
        assertEquals(0, db.sleepDao().observeAll().first().size)
        assertEquals(0, changes)
    }

    @Test fun withoutTheSleepPermissionNothingIsRead() = runBlocking {
        hc.granted.value = emptySet()
        hc.sleep += HcSleep("w1", at(nov9, 23), at(nov10, 7), "com.watch")
        assertEquals(0, import.importRecent(30))
    }
}
