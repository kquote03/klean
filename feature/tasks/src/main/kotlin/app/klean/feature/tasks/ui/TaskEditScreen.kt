package app.klean.feature.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.reminders.alarm.TimePickDialog
import app.klean.feature.reminders.ui.DatePickDialog
import app.klean.feature.reminders.ui.RepeatPicker
import app.klean.feature.tasks.R
import app.klean.shared.time.toLocal
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onCustomReminder: (taskId: String, title: String) -> Unit,
    vm: TaskEditViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val reminders by vm.taskReminders.collectAsStateWithLifecycle()
    LaunchedEffect(s.closed) { if (s.closed) onBack() }
    val ctx = LocalContext.current
    val fmt = remember { TaskFormatter(ctx.applicationContext, SystemZone) }
    val today = remember { Clock.System.now().toEpochMilliseconds().toLocal(TimeZone.currentSystemDefault()).date }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var newStep by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.tasks_new else R.string.tasks_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.tasks_back))
                    }
                },
                actions = {
                    if (!s.isNew) {
                        IconButton(onClick = vm::delete, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.tasks_delete))
                        }
                    }
                    TextButton(onClick = vm::save, enabled = s.canSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.tasks_save))
                    }
                },
            )
        },
    ) { pad ->
        if (!s.loaded) return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!s.isNew) CheckCircle(s.done, s.title, vm::setDone)
                OutlinedTextField(
                    value = s.title, onValueChange = vm::setTitle, label = { Text(stringResource(R.string.tasks_field_title)) },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = s.notes, onValueChange = vm::setNotes, label = { Text(stringResource(R.string.tasks_field_notes)) },
                minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            // List.
            if (s.parentId == null) {
                Row(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { listMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.tasks_field_list, lists.firstOrNull { it.id == s.listId }?.name ?: lists.firstOrNull()?.name.orEmpty()))
                    }
                    DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                        lists.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { vm.setList(l.id); listMenu = false }) }
                    }
                }
            }
            // Due.
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(s.dueDate?.let { fmt.day(it, today) } ?: stringResource(R.string.tasks_field_no_date))
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.padding(start = 8.dp).weight(1f).heightIn(min = 48.dp)) {
                    Text(s.dueMinutes?.let { m -> fmt.time(s.dueDate ?: today, m) } ?: stringResource(R.string.tasks_field_all_day))
                }
                if (s.dueDate != null) {
                    IconButton(onClick = { vm.setDue(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tasks_clear_date))
                    }
                }
            }
            if (s.someday) {
                Text(stringResource(R.string.tasks_someday_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            // Priority (neutral colours, no red).
            Text(stringResource(R.string.tasks_field_priority), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            val labels = listOf(R.string.tasks_priority_none, R.string.tasks_priority_low, R.string.tasks_priority_medium, R.string.tasks_priority_high)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(selected = s.priority == i, onClick = { vm.setPriority(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) {
                        Text(stringResource(l))
                    }
                }
            }
            RepeatPicker(s.repeat, onChange = vm::setRepeat, modifier = Modifier.padding(top = 12.dp))

            // Steps.
            if (s.parentId == null) {
                SectionTitle(R.string.tasks_section_steps)
                if (s.isNew) {
                    Hint(R.string.tasks_save_first_steps)
                } else {
                    subtasks.forEach { st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(st.completedAt != null, st.title) { vm.setSubtaskDone(st.id, it) }
                            TextButton(onClick = { onOpenTask(st.id) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Text(st.title, modifier = Modifier.fillMaxWidth())
                            }
                            IconButton(onClick = { vm.deleteSubtask(st.id) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tasks_delete_step, st.title))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newStep,
                        onValueChange = { v ->
                            if ('\n' in v) {
                                vm.addSubtask(v.replace("\n", ""))
                                newStep = ""
                            } else {
                                newStep = v
                            }
                        },
                        label = { Text(stringResource(R.string.tasks_add_step)) },
                        trailingIcon = {
                            TextButton(onClick = { vm.addSubtask(newStep); newStep = "" }, enabled = newStep.isNotBlank()) { Text(stringResource(R.string.tasks_add_step_button)) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Reminders.
            SectionTitle(R.string.tasks_section_reminders)
            if (s.isNew) {
                Hint(R.string.tasks_save_first_reminders)
            } else {
                reminders.forEach { r ->
                    ListItem(
                        leadingContent = {
                            Icon(if (r.isAlarm) Icons.Rounded.Alarm else Icons.Rounded.NotificationsNone, contentDescription = null)
                        },
                        headlineContent = { Text(fmtReminder(fmt, r.fireAt)) },
                        supportingContent = { Text(stringResource(R.string.tasks_reminder_ring_as_alarm)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = r.isAlarm, onCheckedChange = { vm.setReminderAlarm(r, it) })
                                IconButton(onClick = { vm.deleteReminder(r.id) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tasks_delete_reminder))
                                }
                            }
                        },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.dueDate != null) {
                        AssistChip(onClick = { vm.addReminder(ReminderPreset.AT_DUE) }, label = { Text(stringResource(R.string.tasks_reminder_at_due)) })
                        AssistChip(onClick = { vm.addReminder(ReminderPreset.HOUR_BEFORE) }, label = { Text(stringResource(R.string.tasks_reminder_hour_before)) })
                    }
                    AssistChip(onClick = { s.id?.let { onCustomReminder(it, s.title) } }, label = { Text(stringResource(R.string.tasks_reminder_custom)) })
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp), color = androidx.compose.ui.graphics.Color.Transparent)
        }
    }
    if (pickDate) DatePickDialog(s.dueDate ?: today, onDismiss = { pickDate = false }, onPicked = { vm.setDue(it); pickDate = false })
    if (pickTime) TimePickDialog(onDismiss = { pickTime = false }, onPicked = { vm.setTime(it); pickTime = false }, initialMinute = s.dueMinutes ?: 9 * 60)
}

private fun fmtReminder(fmt: TaskFormatter, at: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val local = at.toLocal(tz)
    val today = Clock.System.now().toEpochMilliseconds().toLocal(tz).date
    return fmt.day(local.date, today) + " " + fmt.time(local.date, local.hour * 60 + local.minute)
}

@Composable
private fun SectionTitle(res: Int) = Text(
    stringResource(res),
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
)

@Composable
private fun Hint(res: Int) = Text(
    stringResource(res),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
