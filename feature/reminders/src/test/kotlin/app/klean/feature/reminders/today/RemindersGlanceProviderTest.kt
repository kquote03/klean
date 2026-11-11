package app.klean.feature.reminders.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.QuickAction
import app.klean.feature.reminders.Dubai
import app.klean.feature.reminders.DubaiZone
import app.klean.feature.reminders.FakeClock
import app.klean.feature.reminders.RINGING
import app.klean.feature.reminders.inMemoryDb
import app.klean.feature.reminders.tempDevicePrefs
import app.klean.shared.module.ModuleId
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nov9 = LocalDate(2026, 11, 9)
    private fun at(h: Int, m: Int = 0, d: LocalDate = nov9) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val repo = DefaultReminderRepository(db, stamper, clock, emptySet())
    private val provider = RemindersGlanceProvider(ctx, { db }, clock, DubaiZone)

    @After fun close() = db.close()

    @Test fun todaysRemindersAcrossTheThreeSections() = runBlocking {
        repo.add("Call Ahmed", at(17), "Asia/Dubai", isAlarm = false)
        repo.add("Tomorrow thing", at(9, d = LocalDate(2026, 11, 10)), "Asia/Dubai", isAlarm = false)
        val rang = repo.add("Take vitamins", at(11), "Asia/Dubai", isAlarm = false)
        repo.update(db.reminderDao().get(rang)!!.copy(state = RINGING, lastFiredAt = at(11)))
        val done = repo.add("Water plants", at(8), "Asia/Dubai", isAlarm = false)
        repo.markDone(done)
        val g = provider.glance().first()
        assertEquals(listOf("Take vitamins"), g.attention.map { it.text })
        assertEquals(listOf("Call Ahmed"), g.upcoming.map { it.text })
        assertEquals(at(17), g.upcoming.single().at)
        assertEquals(listOf("Water plants"), g.done.map { it.text })
        assertTrue((g.attention + g.upcoming + g.done).all { it.module == ModuleId.REMINDERS && it.route == "klean://tasks/reminders" })
    }

    @Test fun yesterdaysAreGoneJustAfterMidnight() = runBlocking {
        val late = repo.add("Late call", at(23, 0, LocalDate(2026, 11, 8)), "Asia/Dubai", isAlarm = false)
        repo.markDone(late)
        repo.add("Stretch", at(9), "Asia/Dubai", isAlarm = false)
        clock.nowMs = at(0, 30)
        val g = provider.glance().first()
        assertTrue(g.done.isEmpty())
        assertEquals(listOf("Stretch"), g.upcoming.map { it.text })
    }

    @Test fun reminderIsOneTapCaptureAndCalm() = runBlocking {
        val a = provider.glance().first().quickAction!!
        assertEquals(QuickAction.Capture(ModuleId.REMINDERS, "Reminder", a.icon), a)
        assertFalse("!" in a.label)
    }
}
