package app.klean.nav

import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // The screen's own item sits last, nearest the finger (plan 2026-11-11, decision 8).
    @Test fun currentModuleIsLast() {
        val e = addMenuEntries(setOf(ModuleId.TASKS, ModuleId.CALENDAR, ModuleId.DIET), current = ModuleId.CALENDAR)
        assertEquals(ModuleId.CALENDAR, e.last().module)
        assertEquals(listOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.CALENDAR), e.map { it.module })
    }

    @Test fun contactsAddsNewContactLastOnlyInPeople() {
        val on = setOf(ModuleId.TASKS, ModuleId.CONTACTS)
        val people = addMenuEntries(on, current = ModuleId.CONTACTS)
        assertEquals(ModuleId.CONTACTS, people.last().module)
        assertTrue(people.last().opensContactEditor)
        assertTrue(addMenuEntries(on, current = ModuleId.TASKS).none { it.module == ModuleId.CONTACTS })
    }

    @Test fun birthdaysCountAsPeople() {
        assertEquals(ModuleId.CONTACTS, addMenuEntries(setOf(ModuleId.CONTACTS, ModuleId.BIRTHDAYS), current = ModuleId.BIRTHDAYS).last().module)
    }

    @Test fun noNewContactWhenContactsIsOff() {
        assertTrue(addMenuEntries(setOf(ModuleId.TASKS), current = ModuleId.CONTACTS).none { it.module == ModuleId.CONTACTS })
    }
}
