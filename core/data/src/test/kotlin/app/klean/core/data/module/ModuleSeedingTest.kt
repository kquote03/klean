package app.klean.core.data.module

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleSeedingTest {
    @Test fun firstRunSwitchesNewModulesOffButNotDiet() {
        val (known, disabled) = seedNewModules(registered = setOf("diet", "tasks"), known = emptySet(), disabled = emptySet())
        assertEquals(setOf("diet", "tasks"), known)
        assertEquals(setOf("tasks"), disabled)
    }

    @Test fun runningAgainWithNothingNewChangesNothing() {
        val first = seedNewModules(setOf("diet", "tasks"), emptySet(), emptySet())
        val second = seedNewModules(setOf("diet", "tasks"), first.first, first.second)
        assertEquals(first, second)
    }

    @Test fun aModuleTheUserTurnedOnStaysOn() {
        val (known, disabled) = seedNewModules(setOf("diet", "tasks"), emptySet(), emptySet())
        val userTurnedTasksOn = disabled - "tasks"
        val next = seedNewModules(setOf("diet", "tasks", "sleep"), known, userTurnedTasksOn)
        assertEquals(setOf("diet", "tasks", "sleep"), next.first)
        assertEquals(setOf("sleep"), next.second)
    }

    @Test fun dietTheUserSwitchedOffStaysOff() {
        val (_, disabled) = seedNewModules(setOf("diet", "core", "tasks"), emptySet(), setOf("diet"))
        assertEquals(setOf("diet", "tasks"), disabled)
    }
}
