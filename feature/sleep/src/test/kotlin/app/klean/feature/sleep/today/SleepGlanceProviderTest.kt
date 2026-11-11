package app.klean.feature.sleep.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.QuickAction
import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.DubaiZone
import app.klean.feature.sleep.FakeClock
import app.klean.feature.sleep.data.DefaultSleepRepository
import app.klean.feature.sleep.inMemoryDb
import app.klean.feature.sleep.tempDevicePrefs
import app.klean.feature.sleep.ui.SleepFormatter
import app.klean.feature.sleep.ui.SleepTicker
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(nov10, 9))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val schedule = SleepScheduleRepository(db, stamper, SettingsRepository(db, stamper))
    private val repo = DefaultSleepRepository(db, stamper, schedule, DubaiZone, clock, emptySet())
    private val ticker = object : SleepTicker(clock) { override fun ticks(): Flow<Long> = flowOf(clock.nowMs) }
    private val provider = SleepGlanceProvider(ctx, { repo }, { schedule }, SleepFormatter(ctx, DubaiZone), ticker, clock, DubaiZone)

    @After fun close() = db.close()

    @Test fun lastNightIsSoFarToday() = runBlocking {
        repo.log(at(nov9, 23, 40), at(nov10, 7))
        repo.log(at(LocalDate(2026, 11, 8), 23), at(nov9, 6))
        val g = provider.glance().first()
        val row = g.done.single()
        assertEquals("Slept 7 h 20 m", row.text)
        assertEquals(at(nov10, 7), row.at)
        assertTrue(row.detail!!.contains("–"))
        assertEquals(ModuleId.SLEEP, row.module)
    }

    @Test fun whileAsleepNothingIsDoneAndTheButtonSaysImUp() = runBlocking {
        clock.nowMs = at(nov9, 23, 40)
        assertEquals("Bedtime", provider.glance().first().quickAction!!.label)
        repo.goToBed(clock.nowMs)
        clock.nowMs = at(nov10, 0, 30)
        val g = provider.glance().first()
        assertTrue(g.done.isEmpty())
        assertEquals("I'm up", g.quickAction!!.label)
    }

    @Test fun theButtonActsAtOnceWithUndo() = runBlocking {
        clock.nowMs = at(nov9, 23, 40)
        val bed = provider.glance().first().quickAction as QuickAction.Instant
        val night = bed.run()!!
        assertEquals("Good night", night.message)
        assertTrue(repo.open() != null)
        night.undo!!()
        assertNull(repo.open())

        repo.goToBed(at(nov9, 23, 40))
        clock.nowMs = at(nov10, 7)
        val up = provider.glance().first().quickAction as QuickAction.Instant
        val morning = up.run()!!
        assertEquals("Morning · slept 7 h 20 m", morning.message)
        assertNull(repo.open())
        morning.undo!!()
        assertTrue(repo.open() != null)
        assertFalse(morning.message.contains("!"))
    }
}
