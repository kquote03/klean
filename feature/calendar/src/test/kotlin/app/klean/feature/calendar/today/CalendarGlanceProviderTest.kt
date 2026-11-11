package app.klean.feature.calendar.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ui.module.QuickAction
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeAgenda
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.FakeText
import app.klean.feature.calendar.OneTick
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.item
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val agenda = FakeAgenda()
    private val provider = CalendarGlanceProvider(ctx, agenda, clock, DubaiZone, FakeText, OneTick)

    private fun day() {
        agenda.all.value = listOf(
            item("yday", "Yesterday's dinner", dubai(2026, 11, 9, 20)),
            item("past", "Breakfast", dubai(2026, 11, 10, 8)),
            item("now", "Design review", dubai(2026, 11, 10, 11, 30), dubai(2026, 11, 10, 12, 30)),
            item("soon", "Lunch with Lina", dubai(2026, 11, 10, 12, 20)),
            item("hol", "National Day", dubai(2026, 11, 10), dubai(2026, 11, 11), allDay = true),
            item("dent", "Dentist", dubai(2026, 11, 10, 14), dubai(2026, 11, 10, 15)),
            item("task", "Pay rent", dubai(2026, 11, 10, 15), kind = AgendaKind.TASK),
            item("tom", "Gym", dubai(2026, 11, 11, 18)),
        )
    }

    @Test fun nowAndSoonNeedAttention() = runBlocking {
        day()
        val g = provider.glance().first()
        assertEquals(listOf("Design review", "Lunch with Lina"), g.attention.map { it.text })
        assertEquals("Until 12:30", g.attention[0].detail)
        assertEquals("Starts in 20 min", g.attention[1].detail)
    }

    @Test fun restOfTodayIsComingUpWithStartAndEnd() = runBlocking {
        day()
        val g = provider.glance().first()
        assertEquals(listOf("National Day", "Dentist"), g.upcoming.map { it.text })
        assertEquals("All day", g.upcoming[0].detail)
        assertEquals(null, g.upcoming[0].at)
        assertEquals("14:00 – 15:00", g.upcoming[1].detail)
        assertTrue(g.upcoming.all { it.route == "klean://calendar/day/2026-11-10" && it.module == ModuleId.CALENDAR })
    }

    @Test fun endedEventsAreSoFarTodayAndYesterdayIsGone() = runBlocking {
        day()
        assertEquals(listOf("Breakfast"), provider.glance().first().done.map { it.text })
        clock.nowMs = dubai(2026, 11, 10, 0, 30)
        val g = provider.glance().first()
        assertTrue(g.done.isEmpty())
        assertFalse((g.attention + g.upcoming).any { it.text == "Yesterday's dinner" })
    }

    @Test fun eventIsOneTapCaptureAndCalm() = runBlocking {
        day()
        val g = provider.glance().first()
        assertEquals(QuickAction.Capture(ModuleId.CALENDAR, "Event", g.quickAction!!.icon), g.quickAction)
        assertFalse((g.attention + g.upcoming + g.done).flatMap { listOfNotNull(it.text, it.detail) }.any { "!" in it })
    }
}
