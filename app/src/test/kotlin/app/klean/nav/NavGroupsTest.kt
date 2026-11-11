package app.klean.nav

import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGroupsTest {

    @Test fun allModulesOnGiveFourGroupsInOrder() {
        val groups = navGroups(
            listOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR, ModuleId.CONTACTS, ModuleId.BIRTHDAYS, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET),
        )
        assertEquals(listOf(NavGroup.TODAY, NavGroup.PLAN, NavGroup.TRACK, NavGroup.PEOPLE), groups.map { it.group })
        assertEquals(listOf(ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET), groups.single { it.group == NavGroup.TRACK }.modules)
        assertEquals(listOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR), groups.single { it.group == NavGroup.PLAN }.modules)
    }

    @Test fun aGroupWithNothingEnabledDisappears() {
        val groups = navGroups(listOf(ModuleId.TASKS, ModuleId.CALENDAR))
        assertEquals(listOf(NavGroup.TODAY, NavGroup.PLAN), groups.map { it.group })
    }

    @Test fun noMoreDestinationEverExists() {
        assertTrue(NavGroup.entries.none { it.name == "MORE" })
    }

    @Test fun enabledOrderDoesNotChangeGroupOrder() {
        val groups = navGroups(listOf(ModuleId.DIET, ModuleId.SLEEP, ModuleId.CONTACTS))
        assertEquals(listOf(NavGroup.TODAY, NavGroup.TRACK, NavGroup.PEOPLE), groups.map { it.group })
        assertEquals(listOf(ModuleId.SLEEP, ModuleId.DIET), groups.single { it.group == NavGroup.TRACK }.modules)
    }

    @Test fun groupOfAModule() {
        assertEquals(NavGroup.PLAN, NavGroup.of(ModuleId.REMINDERS))
        assertEquals(NavGroup.PEOPLE, NavGroup.of(ModuleId.BIRTHDAYS))
        assertEquals(null, NavGroup.of(ModuleId.DELEGATION))
    }

    @Test fun lastUsedModuleWinsAndFallsBackToTheFirst() {
        val plan = NavGroupEntry(NavGroup.PLAN, listOf(ModuleId.TASKS, ModuleId.CALENDAR))
        assertEquals(ModuleId.CALENDAR, plan.landing(mapOf("plan" to "calendar")))
        assertEquals(ModuleId.TASKS, plan.landing(emptyMap()))
        // A module turned off since: back to the first one still on.
        assertEquals(ModuleId.TASKS, plan.landing(mapOf("plan" to "reminders")))
    }

    @Test fun trackOpensOnWeekUntilAModuleIsPicked() {
        val track = listOf("workout", "sleep", "diet")
        assertEquals(WEEK_KEY, landingKey(NavGroup.TRACK, track, emptyMap()))
        assertEquals("sleep", landingKey(NavGroup.TRACK, track, mapOf("track" to "sleep")))
        assertEquals(WEEK_KEY, landingKey(NavGroup.TRACK, track, mapOf("track" to WEEK_KEY)))
        // A tracker turned off since: back to Week.
        assertEquals(WEEK_KEY, landingKey(NavGroup.TRACK, listOf("workout", "diet"), mapOf("track" to "sleep")))
    }

    @Test fun onlyTrackWithTwoOrMoreTrackersHasAWeek() {
        assertEquals("diet", landingKey(NavGroup.TRACK, listOf("diet"), emptyMap()))
        assertEquals("tasks", landingKey(NavGroup.PLAN, listOf("tasks", "calendar"), emptyMap()))
        assertEquals("tasks", landingKey(NavGroup.PLAN, listOf("tasks", "calendar"), mapOf("plan" to WEEK_KEY)))
    }
}
