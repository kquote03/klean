package app.klean.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.R
import app.klean.shared.module.ModuleId

/** One module's line in the Add menu. */
data class AddEntry(val module: ModuleId, @StringRes val label: Int, val icon: ImageVector)

/** Top to bottom, so the most common (Task) sits nearest the button. */
private val ALL = listOf(
    AddEntry(ModuleId.SLEEP, R.string.add_sleep, Icons.Rounded.Bedtime),
    AddEntry(ModuleId.WORKOUT, R.string.add_workout, Icons.Rounded.FitnessCenter),
    AddEntry(ModuleId.DIET, R.string.add_food, Icons.Rounded.EditNote),
    AddEntry(ModuleId.REMINDERS, R.string.add_reminder, Icons.Rounded.NotificationsNone),
    AddEntry(ModuleId.CALENDAR, R.string.add_event, Icons.Rounded.EventAvailable),
    AddEntry(ModuleId.TASKS, R.string.add_task, Icons.Rounded.CheckCircleOutline),
)

/** The Add menu's module lines for the [enabled] modules; a module that's off has none. */
fun addMenuEntries(enabled: Set<ModuleId>): List<AddEntry> = ALL.filter { it.module in enabled }
