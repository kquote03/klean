package app.klean.feature.calendar.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.byDay
import app.klean.feature.calendar.date
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.item
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Plan › Calendar in light and dark: agenda (empty, typical, long), week and month, each within the clutter budget. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarShotsTest {
    @get:Rule val compose = createComposeRule()

    private val today = date(2026, 11, 10)
    private val typical = listOf(
        item("sync", "Team sync", dubai(2026, 11, 10, 15), dubai(2026, 11, 10, 15, 30)),
        item("lina", "Dinner with Lina", dubai(2026, 11, 10, 19, 30)),
        item("call", "Quarterly call", dubai(2026, 11, 11, 10), kind = AgendaKind.DEVICE).copy(sourceName = "Work"),
        item("rent", "Pay rent", dubai(2026, 11, 13), dubai(2026, 11, 14), kind = AgendaKind.TASK, allDay = true),
        item("sara", "Sara's birthday", dubai(2026, 11, 20), dubai(2026, 11, 21), kind = AgendaKind.BIRTHDAY, allDay = true),
    )

    private fun state(view: CalendarView, items: List<app.klean.feature.calendar.agenda.AgendaItem>): CalendarUiState {
        val (from, to) = CalendarRange.of(view, today)
        val days = generateSequence(from) { d -> d.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
        return CalendarUiState(view, today, today, days, items, byDay(items, from, to), canReadDevice = false, loaded = true)
    }

    private fun render(name: String, view: CalendarView, items: List<app.klean.feature.calendar.agenda.AgendaItem> = typical, showAccess: Boolean = false) =
        compose.shot(name, screen = true) {
            CalendarScreenContent(
                state = state(view, items), showAccess = showAccess, onAllow = {}, onLater = {}, onView = {}, onPrevious = {}, onNext = {},
                onToday = {}, onOpenDay = {}, onItem = {}, onNew = {}, onOpenSettings = {},
            )
        }

    @Test fun agenda() {
        render("calendar-agenda", CalendarView.AGENDA)
        compose.onNodeWithText("This week").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun agendaEmptyOffersThePhonesCalendarsOnce() {
        render("calendar-empty", CalendarView.AGENDA, emptyList(), showAccess = true)
        compose.onNodeWithText("Show phone calendars").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun agendaLong() {
        val many = (0 until 14).flatMap { d ->
            (0 until 3).map { h -> item("e$d$h", "Event $d.$h with a long title that runs on", dubai(2026, 11, 10 + d, 9 + h * 3)) }
        }
        render("calendar-long", CalendarView.AGENDA, many)
        compose.assertClutterBudget()
    }

    @Test fun week() {
        render("calendar-week", CalendarView.WEEK)
        compose.assertClutterBudget()
    }

    @Test fun month() {
        render("calendar-month", CalendarView.MONTH)
        compose.assertClutterBudget()
    }

    @Test fun viewsLiveInTheOverflow() {
        val shot = render("calendar-menu", CalendarView.AGENDA)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Month").assertExists()
        shot.capture("calendar-menu-open")
    }
}
