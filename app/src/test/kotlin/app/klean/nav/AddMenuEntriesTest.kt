package app.klean.nav

import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Test

class AddMenuEntriesTest {
    @Test fun everyLoggingModuleInMenuOrderWithTaskLast() {
        val all = setOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR, ModuleId.DIET, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.CONTACTS)
        assertEquals(
            listOf(ModuleId.SLEEP, ModuleId.WORKOUT, ModuleId.DIET, ModuleId.REMINDERS, ModuleId.CALENDAR, ModuleId.TASKS),
            addMenuEntries(all).map { it.module },
        )
    }

    @Test fun disabledModulesAreHidden() {
        assertEquals(listOf(ModuleId.CALENDAR), addMenuEntries(setOf(ModuleId.CALENDAR, ModuleId.CONTACTS)).map { it.module })
        assertEquals(emptyList<AddEntry>(), addMenuEntries(emptySet()))
    }
}
