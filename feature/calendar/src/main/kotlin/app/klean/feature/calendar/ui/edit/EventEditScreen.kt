package app.klean.feature.calendar.ui.edit

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.calendar.CalendarKeys
import app.klean.feature.calendar.R
import app.klean.feature.calendar.data.Repeat
import app.klean.feature.calendar.ui.rememberCalendarFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private enum class Picker { START_DATE, START_TIME, END_DATE, END_TIME }

@Composable
fun EventEditScreen(onDone: () -> Unit, vm: EventEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    EventEditContent(
        state = state,
        onBack = onDone,
        onSave = vm::save,
        onTitle = vm::setTitle,
        onAllDay = vm::setAllDay,
        onStartDate = vm::setStartDate,
        onStartTime = vm::setStartTime,
        onEndDate = vm::setEndDate,
        onEndTime = vm::setEndTime,
        onLocation = vm::setLocation,
        onNotes = vm::setNotes,
        onRepeat = vm::setRepeat,
        onReminder = vm::setReminder,
        onDeleteAll = vm::deleteAll,
        onDeleteThis = vm::deleteThis,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EventEditContent(
    state: EventEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTitle: (String) -> Unit,
    onAllDay: (Boolean) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onStartTime: (Int) -> Unit,
    onEndDate: (LocalDate) -> Unit,
    onEndTime: (Int) -> Unit,
    onLocation: (String) -> Unit,
    onNotes: (String) -> Unit,
    onRepeat: (Repeat) -> Unit,
    onReminder: (Int) -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteThis: () -> Unit,
) {
    val f = state.form
    val fmt = rememberCalendarFormat()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.calendar_edit_new else R.string.calendar_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.calendar_edit_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.calendar_edit_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = f.title, onValueChange = onTitle, label = { Text(stringResource(R.string.calendar_edit_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            SwitchListItem(checked = f.allDay, onCheckedChange = onAllDay, headline = { Text(stringResource(R.string.calendar_edit_all_day)) })
            WhenRow(
                label = stringResource(R.string.calendar_edit_starts),
                date = fmt.shortDay(f.startDate), time = if (f.allDay) null else fmt.minutes(f.startMinutes),
                onDate = { picker = Picker.START_DATE }, onTime = { picker = Picker.START_TIME },
            )
            WhenRow(
                label = stringResource(R.string.calendar_edit_ends),
                date = fmt.shortDay(f.endDate), time = if (f.allDay) null else fmt.minutes(f.endMinutes),
                onDate = { picker = Picker.END_DATE }, onTime = { picker = Picker.END_TIME },
            )
            Text(stringResource(R.string.calendar_edit_repeat), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = Repeat.entries.filter { it != Repeat.CUSTOM || f.repeat == Repeat.CUSTOM }
                choices.forEach { r ->
                    FilterChip(
                        selected = f.repeat == r, onClick = { onRepeat(r) },
                        label = { Text(repeatLabel(r, f.rrule)) }, modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            if (!f.allDay) {
                Text(stringResource(R.string.calendar_edit_reminder), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (CalendarKeys.REMINDER_CHOICES + f.reminder).distinct().forEach { m ->
                        FilterChip(
                            selected = f.reminder == m, onClick = { onReminder(m) },
                            label = { Text(reminderLabel(m)) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = f.location, onValueChange = onLocation, label = { Text(stringResource(R.string.calendar_edit_location)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.notes, onValueChange = onNotes, label = { Text(stringResource(R.string.calendar_edit_notes)) },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.calendar_edit_delete))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        Picker.START_DATE -> DateDialog(f.startDate, onDismiss = { picker = null }) { onStartDate(it); picker = null }
        Picker.END_DATE -> DateDialog(f.endDate, onDismiss = { picker = null }) { onEndDate(it); picker = null }
        Picker.START_TIME -> TimeDialog(f.startMinutes, onDismiss = { picker = null }) { onStartTime(it); picker = null }
        Picker.END_TIME -> TimeDialog(f.endMinutes, onDismiss = { picker = null }) { onEndTime(it); picker = null }
        null -> Unit
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(if (state.recurring) R.string.calendar_delete_recurring_title else R.string.calendar_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteAll() }) {
                    Text(stringResource(if (state.recurring) R.string.calendar_delete_all else R.string.calendar_edit_delete))
                }
            },
            dismissButton = {
                Row {
                    if (state.recurring) {
                        TextButton(onClick = { confirmDelete = false; onDeleteThis() }) { Text(stringResource(R.string.calendar_delete_this)) }
                    }
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.calendar_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun WhenRow(label: String, date: String, time: String?, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onDate, modifier = Modifier.heightIn(min = 48.dp)) { Text(date) }
        if (time != null) OutlinedButton(onClick = onTime, modifier = Modifier.heightIn(min = 48.dp)) { Text(time) }
    }
}

@Composable
private fun repeatLabel(r: Repeat, rrule: String?): String = when (r) {
    Repeat.NONE -> stringResource(R.string.calendar_repeat_none)
    Repeat.DAILY -> stringResource(R.string.calendar_repeat_daily)
    Repeat.WEEKLY -> stringResource(R.string.calendar_repeat_weekly)
    Repeat.MONTHLY -> stringResource(R.string.calendar_repeat_monthly)
    Repeat.YEARLY -> stringResource(R.string.calendar_repeat_yearly)
    Repeat.CUSTOM -> stringResource(R.string.calendar_repeat_custom, rrule.orEmpty())
}

@Composable
internal fun reminderLabel(m: Int): String = when (m) {
    -1 -> stringResource(R.string.calendar_reminder_none)
    0 -> stringResource(R.string.calendar_reminder_at_start)
    10 -> stringResource(R.string.calendar_reminder_10)
    60 -> stringResource(R.string.calendar_reminder_60)
    1440 -> stringResource(R.string.calendar_reminder_1440)
    else -> stringResource(R.string.calendar_reminder_minutes, m)
}

/** The M3 date picker speaks UTC midnights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) } ?: onDismiss()
            }) { Text(stringResource(R.string.calendar_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_cancel)) } },
    ) { DatePicker(state = state, title = { Text(stringResource(R.string.calendar_pick_date), Modifier.padding(16.dp)) }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60, DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.calendar_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_cancel)) } },
    )
}
