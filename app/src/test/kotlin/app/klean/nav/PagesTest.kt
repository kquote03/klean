package app.klean.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swiping between pages: Today, Plan's tabs, Track's tabs, People's tabs, left to right. */
class PagesTest {
    private val pages = listOf("today", "tasks", "calendar", "week", "workout", "sleep", "diet", "contacts")

    @Test fun swipeLeftIsTheNextPage() {
        assertEquals("tasks", pageAfter(pages, "today", dx = -200f))
        assertEquals("week", pageAfter(pages, "calendar", dx = -200f))
    }

    @Test fun swipeRightIsThePreviousPage() {
        assertEquals("today", pageAfter(pages, "tasks", dx = 200f))
        assertEquals("diet", pageAfter(pages, "contacts", dx = 200f))
    }

    @Test fun theEndsStayPut() {
        assertNull(pageAfter(pages, "today", dx = 200f))
        assertNull(pageAfter(pages, "contacts", dx = -200f))
    }

    @Test fun aDetailScreenIsNoPage() = assertNull(pageAfter(pages, "task-detail", dx = -200f))

    @Test fun noMovementNoPage() = assertNull(pageAfter(pages, "today", dx = 0f))

    @Test fun aSwipeIsLongAndMostlySideways() {
        assertTrue(isPageSwipe(dx = -72f, dy = 0f, minPx = 72f))
        assertTrue(isPageSwipe(dx = 200f, dy = 99f, minPx = 72f))
        assertFalse("too short", isPageSwipe(dx = -71f, dy = 0f, minPx = 72f))
        assertFalse("too steep", isPageSwipe(dx = 200f, dy = 100f, minPx = 72f))
    }

    /** Page motion follows the order, for pops too: Back from Track to Today brings Today in from the left. */
    @Test fun directionFollowsThePageOrder() {
        assertEquals(true, pageForward(pages, "today", "tasks"))
        assertEquals(false, pageForward(pages, "sleep", "today"))
        assertEquals(false, pageForward(pages, "calendar", "tasks"))
        assertNull(pageForward(pages, "today", "settings"))
        assertNull(pageForward(pages, "today", "today"))
    }
}
