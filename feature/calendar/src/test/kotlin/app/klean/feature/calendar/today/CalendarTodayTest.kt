package app.klean.feature.calendar.today

import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.feature.calendar.CalendarDayRoute
import app.klean.feature.calendar.CalendarRoute
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeAgenda
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.FakeText
import app.klean.feature.calendar.OneTick
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.date
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.item
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTodayTest {
    private val now = dubai(2026, 11, 10, 12)
    private val clock = FakeClock(now)
    private val agenda = FakeAgenda()
    private val providers = CalendarDashboardProviders(agenda, clock, DubaiZone, FakeText, OneTick)
    private val status = CalendarStatusLine({ agenda }, clock, DubaiZone, FakeText, OneTick, { StatusRefresh { emptySet() } })

    private fun today() {
        agenda.all.value = listOf(
            item("past", "Breakfast", dubai(2026, 11, 10, 8)),
            item("hol", "National Day", dubai(2026, 11, 10), dubai(2026, 11, 11), allDay = true),
            item("task", "Pay rent", dubai(2026, 11, 10, 15), kind = AgendaKind.TASK),
            item("dent", "Dentist", dubai(2026, 11, 10, 14)),
            item("call", "Team call", dubai(2026, 11, 10, 16), kind = AgendaKind.DEVICE),
            item("tom", "Gym", dubai(2026, 11, 11, 18)),
        )
    }

    @Test fun theTileShowsTheNextTimedThingToday() = runBlocking {
        today()
        assertEquals(listOf(SummaryItem("calendar.next", "Next", "14:00 Dentist", route = CalendarRoute)), providers.summaries().first())
    }

    @Test fun theTileIsCalmWhenNothingIsLeft() = runBlocking {
        agenda.all.value = listOf(item("past", "Breakfast", dubai(2026, 11, 10, 8)))
        assertEquals(listOf(SummaryItem("calendar.next", "Today", "Nothing else today", route = CalendarRoute)), providers.summaries().first())
    }

    @Test fun activityCountsEventsPerDayAcrossSources() = runBlocking {
        today()
        val got = providers.activity(date(2026, 11, 9), date(2026, 11, 15)).first()
        assertEquals(mapOf(date(2026, 11, 10) to 4, date(2026, 11, 11) to 1), got)
        assertEquals(CalendarDayRoute("2026-11-12"), providers.dayRoute(date(2026, 11, 12)))
    }

    @Test fun todayItemsAreTheRemainingEventsWithDeepLinks() = runBlocking {
        today()
        val got = providers.items().first()
        assertEquals(
            listOf(
                TodayItem("calendar:hol", null, "National Day", dubai(2026, 11, 10), "klean://calendar/day/2026-11-10"),
                TodayItem("calendar:dent", "14:00", "Dentist", dubai(2026, 11, 10, 14), "klean://calendar/day/2026-11-10"),
                TodayItem("calendar:call", "16:00", "Team call", dubai(2026, 11, 10, 16), "klean://calendar/day/2026-11-10"),
            ),
            got,
        )
    }

    @Test fun theStatusLineNamesTheNextEvent() = runBlocking {
        today()
        assertEquals(listOf(StatusFragment("calendar.next", "Dentist 14:00", CalendarStatusLine.PRIORITY)), status.fragments().first())
        agenda.all.value = emptyList()
        assertEquals(emptyList<StatusFragment>(), status.build())
    }
}
