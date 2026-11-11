package app.klean.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.R
import app.klean.shared.module.ModuleId

/** One module's line in the Add menu. [opensContactEditor]: "New contact" opens the editor, not the capture sheet. */
data class AddEntry(
    val module: ModuleId,
    @StringRes val label: Int,
    val icon: ImageVector,
    val opensContactEditor: Boolean = false,
)

/** Top to bottom, so the most common (Task) sits nearest the button when no screen's own line moves down. */
private val ALL = listOf(
    AddEntry(ModuleId.SLEEP, R.string.add_sleep, Icons.Rounded.Bedtime),
    AddEntry(ModuleId.WORKOUT, R.string.add_workout, Icons.Rounded.FitnessCenter),
    AddEntry(ModuleId.DIET, R.string.add_food, Icons.Rounded.EditNote),
    AddEntry(ModuleId.REMINDERS, R.string.add_reminder, Icons.Rounded.NotificationsNone),
    AddEntry(ModuleId.CALENDAR, R.string.add_event, Icons.Rounded.EventAvailable),
    AddEntry(ModuleId.TASKS, R.string.add_task, Icons.Rounded.CheckCircleOutline),
)

/** Contacts has no capture target of its own, so its line shows only in People. */
private val CONTACT = AddEntry(ModuleId.CONTACTS, R.string.add_contact, Icons.Rounded.PersonAdd, opensContactEditor = true)

/**
 * The Add menu's module lines for the [enabled] modules; a module that's off has none. The [current] screen's own
 * line is always last, nearest the finger (plan 2026-11-11, decision 8): Event on Calendar, New contact in People.
 * Birthdays is a tab of People, so it counts as Contacts.
 */
fun addMenuEntries(enabled: Set<ModuleId>, current: ModuleId? = null): List<AddEntry> {
    val here = if (current == ModuleId.BIRTHDAYS) ModuleId.CONTACTS else current
    val base = ALL.filter { it.module in enabled } +
        (if (here == ModuleId.CONTACTS && ModuleId.CONTACTS in enabled) listOf(CONTACT) else emptyList())
    val (mine, others) = base.partition { it.module == here }
    return others + mine
}
