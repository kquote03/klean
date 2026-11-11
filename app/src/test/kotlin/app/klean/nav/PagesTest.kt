package app.klean.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pager's pages: Today, Plan's tabs, Track's tabs, People's tabs, without the modules that are off. */
class PagesTest {
    private object Tasks
    private object Calendar
    private object Workout
    private object Sleep
    private object Diet
    private object Contacts
    private object Detail

    private val all = pageList(
        listOf(
            NavGroup.TODAY to emptyList(),
            NavGroup.PLAN to listOf(Tasks, Calendar),
            NavGroup.TRACK to listOf(TrackWeekRoute, Workout, Sleep, Diet),
            NavGroup.PEOPLE to listOf(Contacts),
        ),
    )

    @Test fun pagesRunTodayThenEachGroupsTabs() {
        assertEquals(listOf(TodayRoute, Tasks, Calendar, TrackWeekRoute, Workout, Sleep, Diet, Contacts), all.map { it.route })
        assertEquals(listOf(0, 0, 1, 0, 1, 2, 3, 0), all.map { it.tab })
        assertEquals(NavGroup.TRACK, all[5].group)
    }

    @Test fun aRouteFindsItsPage() {
        assertEquals(0, all.indexOfRoute(TodayRoute::class))
        assertEquals(2, all.indexOfRoute(Calendar::class))
        assertEquals(7, all.indexOfRoute(Contacts::class))
        assertEquals("a detail is no page", -1, all.indexOfRoute(Detail::class))
    }

    @Test fun aPageFindsItsRouteAndTab() {
        assertEquals(Sleep, all[all.indexOfTab(NavGroup.TRACK, 2)].route)
        assertEquals(-1, all.indexOfTab(NavGroup.PEOPLE, 1))
        assertEquals(4, all.tabCount(NavGroup.TRACK))
    }

    @Test fun modulesThatAreOffHaveNoPage() {
        val some = pageList(listOf(NavGroup.TRACK to listOf(Diet), NavGroup.PEOPLE to listOf(Contacts)))
        assertEquals(listOf(TodayRoute, Diet, Contacts), some.map { it.route })
        assertEquals(-1, some.indexOfRoute(Tasks::class))
        assertEquals(1, some.indexOfTab(NavGroup.TRACK, 0))
    }

    @Test fun keysAreStableAcrossModulesTurningOff() {
        val some = pageList(listOf(NavGroup.TRACK to listOf(Diet)))
        assertEquals(all[6].key, some[1].key)
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test fun neighboursSlideFartherPagesJump() {
        assertTrue(slidesTo(0, 1))
        assertTrue(slidesTo(3, 2))
        assertFalse(slidesTo(0, 5))
    }
}
