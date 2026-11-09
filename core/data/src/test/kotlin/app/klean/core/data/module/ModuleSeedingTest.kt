package app.klean.core.data.module

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleSeedingTest {
    @Test fun freshInstallTurnsEverythingOnAndListsNothing() {
        val r = seedNewModules(registered = setOf("diet", "tasks", "calendar"), known = emptySet(), disabled = emptySet())
        assertEquals(setOf("diet", "tasks", "calendar"), r.known)
        assertEquals(emptySet<String>(), r.disabled)
        assertEquals(emptySet<String>(), r.pending)
    }

    @Test fun existingInstallGetsTheNewOnesOnAndListedOnce() {
        val r = seedNewModules(setOf("diet", "tasks", "calendar"), known = setOf("diet"), disabled = emptySet())
        assertEquals(setOf("diet", "tasks", "calendar"), r.known)
        assertEquals(emptySet<String>(), r.disabled)
        assertEquals(setOf("tasks", "calendar"), r.pending)
    }

    @Test fun secondRunIsANoOp() {
        val first = seedNewModules(setOf("diet", "tasks"), setOf("diet"), emptySet())
        val second = seedNewModules(setOf("diet", "tasks"), first.known, first.disabled)
        assertEquals(SeedResult(first.known, first.disabled, emptySet()), second)
    }

    @Test fun aModuleTheUserSwitchedOffStaysOff() {
        val r = seedNewModules(setOf("diet", "tasks", "sleep"), setOf("diet", "tasks"), setOf("tasks", "diet"))
        assertEquals(setOf("tasks", "diet"), r.disabled)
        assertEquals(setOf("sleep"), r.pending)
    }

    @Test fun coreIsNeverListed() {
        assertEquals(setOf("tasks"), seedNewModules(setOf("core", "tasks"), setOf("diet"), emptySet()).pending)
    }
}
