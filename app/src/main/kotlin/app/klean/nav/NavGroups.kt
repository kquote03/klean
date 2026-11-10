package app.klean.nav

import app.klean.shared.module.ModuleId

/**
 * Bottom-bar destinations (UI plan, navigation decision A). Modules are grouped by how they're used; there is no
 * "More". Delegation is in no group: it lives in Today's approvals card.
 */
enum class NavGroup(val members: List<ModuleId>) {
    TODAY(emptyList()),
    PLAN(listOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR)),
    TRACK(listOf(ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET)),
    PEOPLE(listOf(ModuleId.CONTACTS, ModuleId.BIRTHDAYS)),
    ;

    /** The key stored for the group's last-used module. */
    val key: String get() = name.lowercase()

    companion object {
        fun of(module: ModuleId): NavGroup? = entries.firstOrNull { module in it.members }
    }
}

data class NavGroupEntry(val group: NavGroup, val modules: List<ModuleId>) {
    /** Where the group's tab lands: the module used last ([last], by group key) if it's still on, else the first. */
    fun landing(last: Map<String, String>): ModuleId? =
        last[group.key]?.let { k -> modules.firstOrNull { it.value == k } } ?: modules.firstOrNull()
}

/** Today always; every other group only when at least one of its modules is enabled, keeping members in group order. */
fun navGroups(enabled: List<ModuleId>): List<NavGroupEntry> = NavGroup.entries.mapNotNull { g ->
    if (g == NavGroup.TODAY) {
        NavGroupEntry(g, emptyList())
    } else {
        g.members.filter { it in enabled }.takeIf { it.isNotEmpty() }?.let { NavGroupEntry(g, it) }
    }
}
