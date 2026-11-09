package app.klean.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class NavLayoutTest {
    @Test fun noModules() = assertEquals(NavLayout(emptyList<Int>(), emptyList()), navLayout(emptyList<Int>()))

    @Test fun threeModulesAllFitAndSettingsStays() =
        assertEquals(NavLayout(listOf(1, 2, 3), emptyList()), navLayout(listOf(1, 2, 3)))

    @Test fun fourthModuleMovesUnderMore() =
        assertEquals(NavLayout(listOf(1, 2, 3), listOf(4)), navLayout(listOf(1, 2, 3, 4)))

    @Test fun sevenModulesKeepTheBarAtFive() {
        val layout = navLayout((1..7).toList())
        assertEquals(listOf(1, 2, 3), layout.primary)
        assertEquals(listOf(4, 5, 6, 7), layout.overflow)
    }
}
