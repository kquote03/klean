package app.klean.feature.sleep

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.sleep.data.DefaultSleepRepository
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepCaptureTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(nov10, 8))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val schedule = SleepScheduleRepository(db, stamper, SettingsRepository(db, stamper))
    private val repo = DefaultSleepRepository(db, stamper, schedule, DubaiZone, clock, emptySet())
    private val handler = SleepCaptureHandler(repo, schedule, DubaiZone)
    private val action = CandidateAction(ModuleId.SLEEP, RuleCaptureClassifier.LOG_SLEEP, 0.9f)
    private fun input(t: String, at: Long = clock.nowMs) = CaptureInput(t, CaptureSource.NOTIFICATION_REPLY, at)

    @After fun close() = db.close()

    @Test fun aWholeNightFromOnePhrase() = runBlocking {
        schedule.setWindow(QuietHours(23 * 60, 7 * 60))
        val id = handler.execute(input("slept at 11 woke at 7"), action)
        val s = repo.get(id)!!
        assertEquals(at(nov9, 23), s.bedAt)
        assertEquals(at(nov10, 7), s.wakeAt)
        handler.undo(id)
        assertNull(repo.get(id))
    }

    @Test fun goingToBedThenWokeUpCloseTheSameNight() = runBlocking {
        val bed = handler.execute(input("going to bed", at(nov9, 23, 40)), action)
        val up = handler.execute(input("woke up", at(nov10, 6, 50)), action)
        assertEquals(bed, up)
        assertEquals(at(nov10, 6, 50), repo.get(bed)!!.wakeAt)
    }
}
