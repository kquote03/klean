package app.klean.feature.calendar.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.byDay
import app.klean.feature.calendar.date
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.item
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The calendar screen: views render, items open, and every tappable thing is labelled and ≥48 dp. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CalendarScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = date(2026, 11, 10)
    private val items = listOf(
        item("dent", "Dentist", dubai(2026, 11, 10, 14)),
        item("call", "Team call", dubai(2026, 11, 11, 10), kind = AgendaKind.DEVICE).copy(sourceName = "Work"),
        item("rent", "Pay rent", dubai(2026, 11, 11), dubai(2026, 11, 12), kind = AgendaKind.TASK, allDay = true),
    )

    private fun state(view: CalendarView, list: List<AgendaItem> = items): CalendarUiState {
        val (from, to) = CalendarRange.of(view, today)
        val days = generateSequence(from) { d -> d.plus(1, kotlinx.datetime.DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
        return CalendarUiState(view, today, today, days, list, byDay(list, from, to), canReadDevice = false, loaded = true)
    }

    private fun show(
        view: CalendarView,
        list: List<AgendaItem> = items,
        onItem: (AgendaItem) -> Unit = {},
        onView: (CalendarView) -> Unit = {},
        onAllow: () -> Unit = {},
    ) = compose.setContent {
        KleanTheme {
            CalendarScreenContent(
                state = state(view, list), showAccess = true, onAllow = onAllow, onLater = {}, onView = onView, onPrevious = {}, onNext = {},
                onToday = {}, onOpenDay = {}, onItem = onItem, onNew = {}, onOpenSettings = {},
            )
        }
    }

    @Test fun agendaGroupsItemsCalmlyAndOpensThem() {
        val opened = mutableListOf<String>()
        show(CalendarView.AGENDA, onItem = { opened += it.key })
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("This week").assertIsDisplayed()
        compose.onNodeWithText("From Work", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Task", substring = true).assertIsDisplayed()
        // No standing banner: the phone's calendars are offered from the menu.
        compose.onNodeWithText("Not now").assertDoesNotExist()
        compose.onNodeWithText("Dentist").performClick()
        assertEquals(listOf("dent"), opened)
    }

    @Test fun anEmptyAgendaOffersThePhonesCalendarsOnce() {
        var asked = 0
        show(CalendarView.AGENDA, list = emptyList(), onAllow = { asked++ })
        compose.onNodeWithText("Show phone calendars").performClick()
        assertEquals(1, asked)
    }

    @Test fun switchingViewsFromTheMenuReportsTheChoice() {
        val views = mutableListOf<CalendarView>()
        show(CalendarView.AGENDA, onView = { views += it })
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Month").performClick()
        assertEquals(listOf(CalendarView.MONTH), views)
    }

    @Test fun weekAndMonthRender() {
        show(CalendarView.WEEK)
        compose.onNodeWithText("Dentist").assertIsDisplayed()
    }

    @Test fun tappablesAreLabelledAndLargeEnough() {
        show(CalendarView.MONTH)
        val next = compose.onNodeWithContentDescription("Next").fetchSemanticsNode().touchBoundsInRoot
        with(compose.density) { assertTrue(next.width.toDp() >= 47.5.dp && next.height.toDp() >= 47.5.dp) }
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickables.size >= 30)
        clickables.forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue("unlabelled clickable: ${node.config}", !label.isNullOrBlank() || node.children.any { c ->
                c.config.getOrNull(SemanticsProperties.Text) != null || c.config.getOrNull(SemanticsProperties.ContentDescription) != null
            })
            with(compose.density) {
                val b = node.touchBoundsInRoot
                assertTrue("small target: $label ${b.height.toDp()}", b.width.toDp() >= 47.5.dp && b.height.toDp() >= 47.5.dp)
            }
        }
    }

    @Test fun agendaBuckets() {
        assertEquals(AgendaBucket.EARLIER, bucketOf(date(2026, 11, 9), today))
        assertEquals(AgendaBucket.TODAY, bucketOf(today, today))
        assertEquals(AgendaBucket.THIS_WEEK, bucketOf(date(2026, 11, 11), today))
        assertEquals(AgendaBucket.THIS_WEEK, bucketOf(date(2026, 11, 16), today))
        assertEquals(AgendaBucket.LATER, bucketOf(date(2026, 11, 17), today))
    }
}
