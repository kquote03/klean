package app.klean.shared.module

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ModuleId(val value: String) {
    companion object {
        val DIET = ModuleId("diet")
        /** Contributions owned by the app itself; never disabled. */
        val CORE = ModuleId("core")
        // Future: reserved so ids never change.
        val TASKS = ModuleId("tasks")
        val REMINDERS = ModuleId("reminders")
        val CALENDAR = ModuleId("calendar")
        val CONTACTS = ModuleId("contacts")
        val BIRTHDAYS = ModuleId("birthdays")
        val WORKOUT = ModuleId("workout")
        val SLEEP = ModuleId("sleep")
        /** AI delegation and Hermes (M23). */
        val DELEGATION = ModuleId("delegation")
    }
}
