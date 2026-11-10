package app.klean.feature.sleep.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.DubaiZone
import app.klean.feature.sleep.FakeClock
import app.klean.feature.sleep.inMemoryDb
import app.klean.feature.sleep.tempDevicePrefs
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepRepositoryTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(nov9, 23, 40))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val schedule = SleepScheduleRepository(db, stamper, SettingsRepository(db, stamper))
    private var changes = 0
    private val repo = DefaultSleepRepository(db, stamper, schedule, DubaiZone, clock, setOf(SleepChangeListener { changes++ }))

    @After fun close() = db.close()

    @Test fun goToBedThenWakeUp() = runBlocking {
        val id = repo.goToBed(clock.nowMs)
        assertEquals(id, repo.observeOpen().first()?.id)
        // A second "going to bed" while asleep is a no-op.
        assertEquals(id, repo.goToBed(at(nov10, 0, 10)))
        assertEquals(1, repo.observeSessions().first().size)
        repo.wakeUp(at(nov10, 7))
        val s = repo.get(id)!!
        assertEquals(at(nov10, 7), s.wakeAt)
        assertNull(repo.open())
        assertEquals(7 * 60 + 20, (SleepMath.duration(s)!! / 60_000).toInt())
        assertTrue(changes >= 2)
    }

    @Test fun aStaleOpenSessionIsClosedBeforeANewOne() = runBlocking {
        val old = repo.goToBed(at(nov9, 1))
        val fresh = repo.goToBed(at(nov10, 0))
        assertNotEquals(old, fresh)
        assertEquals(at(nov9, 13), repo.get(old)!!.wakeAt)
        assertEquals(fresh, repo.open()!!.id)
    }

    @Test fun toggleAlternates() = runBlocking {
        val bed = repo.toggle(clock.nowMs)
        assertTrue(bed.asleep)
        val up = repo.toggle(at(nov10, 6, 50))
        assertFalse(up.asleep)
        assertEquals(bed.id, up.id)
        assertEquals(at(nov10, 6, 50), repo.get(bed.id)!!.wakeAt)
    }

    @Test fun wakingWithNothingOpenUsesTheScheduledBedtime() = runBlocking {
        schedule.setWindow(QuietHours(23 * 60, 7 * 60))
        val id = repo.wakeUp(at(nov10, 6, 30))
        val s = repo.get(id)!!
        assertEquals(at(nov9, 23), s.bedAt)
        assertEquals(at(nov10, 6, 30), s.wakeAt)
    }

    @Test fun logEditDeleteRestore() = runBlocking {
        val id = repo.log(at(nov10, 7), at(nov9, 23), SleepSource.CAPTURE)
        assertEquals(at(nov9, 23), repo.get(id)!!.bedAt)
        assertEquals(SleepSource.CAPTURE, repo.get(id)!!.source)
        repo.update(id, at(nov9, 22, 30), at(nov10, 6))
        assertEquals(at(nov9, 22, 30), repo.get(id)!!.bedAt)
        repo.delete(id)
        assertNull(repo.get(id))
        assertTrue(repo.observeSessions().first().isEmpty())
        repo.restore(id)
        assertEquals(at(nov10, 6), repo.get(id)!!.wakeAt)
    }
}
