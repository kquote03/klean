package app.klean.feature.reminders.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.reminders.R
import app.klean.feature.reminders.ReminderFormatter
import app.klean.feature.reminders.Snooze
import app.klean.feature.reminders.alarm.TimePickDialog
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The Reminders tab inside Tasks (plan H5), also shown on its own when Tasks is off. Earlier (went off,
 * not handled), Today, Tomorrow, Later. A quick field at the top; tap a row to edit.
 */
@Composable
fun RemindersTab(onEdit: (String?) -> Unit, modifier: Modifier = Modifier, vm: RemindersViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fmt = remember { ReminderFormatter(ctx.applicationContext, SystemZone, kotlin.time.Clock.System) }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            CaptureField(
                value = draft,
                onValueChange = { vm.draft.value = it },
                onSubmit = vm::submit,
                // Voice runs in the capture sheet (mic permission, Undo), aimed at Reminders.
                onMic = { ctx.startActivity(app.klean.core.capture.CaptureActivity.intent(ctx, app.klean.shared.diet.CaptureSource.MODULE_SCREEN, app.klean.shared.module.ModuleId.REMINDERS, startVoice = true)) },
                onPhoto = null,
                placeholder = stringResource(R.string.reminders_add_placeholder),
                modifier = Modifier.padding(16.dp),
            )
        }
        if (groups.isEmpty) item { NeutralEmpty(stringResource(R.string.reminders_empty)) }
        section(R.string.reminders_group_earlier, groups.earlier, fmt, onEdit, vm, fired = true)
        section(R.string.reminders_group_today, groups.today, fmt, onEdit, vm)
        section(R.string.reminders_group_tomorrow, groups.tomorrow, fmt, onEdit, vm)
        section(R.string.reminders_group_later, groups.later, fmt, onEdit, vm)
    }
}

private object SystemZone : app.klean.core.common.ZoneProvider {
    override fun current() = TimeZone.currentSystemDefault()
}

private fun LazyListScope.section(
    title: Int,
    rows: List<ReminderRow>,
    fmt: ReminderFormatter,
    onEdit: (String?) -> Unit,
    vm: RemindersViewModel,
    fired: Boolean = false,
) {
    if (rows.isEmpty()) return
    item(key = "h$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(rows, key = { it.id }) { r -> ReminderListRow(r, fmt, fired, onEdit, vm) }
}

@Composable
private fun ReminderListRow(r: ReminderRow, fmt: ReminderFormatter, fired: Boolean, onEdit: (String?) -> Unit, vm: RemindersViewModel) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable { onEdit(r.id) },
        leadingContent = {
            Icon(
                if (r.isAlarm) Icons.Rounded.Alarm else Icons.Rounded.NotificationsNone,
                contentDescription = stringResource(if (r.isAlarm) R.string.reminders_kind_alarm else R.string.reminders_kind_ping),
            )
        },
        headlineContent = { Text(r.title) },
        supportingContent = {
            Row {
                Text(
                    if (r.snoozed) stringResource(R.string.reminders_snoozed_until, fmt.dayTime(r.at)) else fmt.dayTime(r.at),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (r.recurring) {
                    Icon(
                        Icons.Rounded.Repeat, contentDescription = stringResource(R.string.reminders_repeats),
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        },
        trailingContent = {
            Row {
                if (fired) {
                    IconButton(onClick = { vm.done(r.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.reminders_action_done))
                    }
                }
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.reminders_more_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (fired) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.reminders_action_dismiss)) }, onClick = { menu = false; vm.dismiss(r.id) })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.reminders_action_done)) }, onClick = { menu = false; vm.done(r.id) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.reminders_snooze_10_long)) }, onClick = { menu = false; vm.snooze(r.id, Snooze.TEN_MINUTES) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.reminders_snooze_hour_long)) }, onClick = { menu = false; vm.snooze(r.id, Snooze.ONE_HOUR) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.reminders_action_tomorrow)) }, onClick = { menu = false; vm.snooze(r.id, Snooze.TOMORROW) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.reminders_delete)) }, onClick = { menu = false; vm.delete(r.id) })
                }
            }
        },
    )
}

/** Reminders on their own (reached when Tasks is off, or from Reminders settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.reminders_back))
                    }
                },
            )
        },
    ) { pad -> RemindersTab(onEdit = onEdit, modifier = Modifier.padding(pad)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(onBack: () -> Unit, vm: ReminderEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onBack() }
    val ctx = LocalContext.current
    val fmt = remember { ReminderFormatter(ctx.applicationContext, SystemZone, kotlin.time.Clock.System) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.reminders_new else R.string.reminders_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.reminders_back))
                    }
                },
                actions = {
                    if (!s.isNew) {
                        IconButton(onClick = vm::delete, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.reminders_delete))
                        }
                    }
                    TextButton(onClick = vm::save, enabled = s.canSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.reminders_save))
                    }
                },
            )
        },
    ) { pad ->
        if (!s.loaded) return@Scaffold
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = s.title, onValueChange = vm::setTitle,
                label = { Text(stringResource(R.string.reminders_field_title)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val tz = TimeZone.currentSystemDefault()
            val date = s.date
            Row(Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(date?.let { fmt.day(atMinute(it, s.minute, tz)) } ?: stringResource(R.string.reminders_pick_day))
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.padding(start = 8.dp).weight(1f).heightIn(min = 48.dp)) {
                    Text(date?.let { fmt.time(atMinute(it, s.minute, tz)) } ?: "")
                }
            }
            RepeatPicker(s.repeat, onChange = vm::setRepeat, modifier = Modifier.padding(top = 12.dp))
            SwitchListItem(
                checked = s.isAlarm, onCheckedChange = vm::setAlarm,
                headline = { Text(stringResource(R.string.reminders_field_alarm)) },
                supporting = { Text(stringResource(R.string.reminders_field_alarm_hint)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (pickDate) {
        DatePickDialog(s.date, onDismiss = { pickDate = false }, onPicked = { vm.setDate(it); pickDate = false })
    }
    if (pickTime) {
        TimePickDialog(onDismiss = { pickTime = false }, onPicked = { vm.setMinute(it); pickTime = false }, initialMinute = s.minute)
    }
}

/** A date picker in a dialog. The picker works in UTC midnights; the result is a plain date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { atMinute(it, 0, TimeZone.UTC) })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(it.toLocal(TimeZone.UTC).date) } ?: onDismiss() }) {
                Text(stringResource(R.string.reminders_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminders_cancel)) } },
    ) { DatePicker(state = state) }
}

/** None / Daily / Weekdays / Weekly / Monthly / Yearly / Every N days / Every N weeks / Custom (RRULE text). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatPicker(choice: RepeatChoice, onChange: (RepeatChoice) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val labels = mapOf(
        RepeatKind.NONE to R.string.repeat_none, RepeatKind.DAILY to R.string.repeat_daily,
        RepeatKind.WEEKDAYS to R.string.repeat_weekdays, RepeatKind.WEEKLY to R.string.repeat_weekly,
        RepeatKind.MONTHLY to R.string.repeat_monthly, RepeatKind.YEARLY to R.string.repeat_yearly,
        RepeatKind.EVERY_N_DAYS to R.string.repeat_every_n_days, RepeatKind.EVERY_N_WEEKS to R.string.repeat_every_n_weeks,
        RepeatKind.CUSTOM to R.string.repeat_custom,
    )
    Column(modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            OutlinedTextField(
                value = stringResource(labels.getValue(choice.kind)),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.repeat_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                RepeatKind.entries.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labels.getValue(k))) },
                        onClick = {
                            open = false
                            onChange(choice.copy(kind = k))
                        },
                    )
                }
            }
        }
        if (choice.kind == RepeatKind.EVERY_N_DAYS || choice.kind == RepeatKind.EVERY_N_WEEKS) {
            OutlinedTextField(
                value = choice.n.toString(),
                onValueChange = { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let { onChange(choice.copy(n = it.coerceAtLeast(1))) } },
                label = { Text(stringResource(R.string.repeat_n_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
        if (choice.kind == RepeatKind.CUSTOM) {
            val valid = Repeats.isValidCustom(choice.custom)
            OutlinedTextField(
                value = choice.custom,
                onValueChange = { onChange(choice.copy(custom = it)) },
                label = { Text(stringResource(R.string.repeat_custom_label)) },
                supportingText = { Text(stringResource(if (valid) R.string.repeat_custom_ok else R.string.repeat_custom_hint)) },
                isError = choice.custom.isNotEmpty() && !valid,
                singleLine = true,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}

/** Reminders settings: this phone rings alarms, and the system permissions alarms rely on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSettingsScreen(onBack: () -> Unit, onOpenReminders: () -> Unit, vm: RemindersSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.reminders_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SwitchListItem(
                checked = s.mainPhone, onCheckedChange = vm::setMainPhone,
                headline = { Text(stringResource(R.string.reminders_main_phone)) },
                supporting = { Text(stringResource(R.string.reminders_main_phone_hint)) },
            )
            if (!s.exactAlarms && Build.VERSION.SDK_INT >= 31) {
                SettingLink(R.string.reminders_exact_title, R.string.reminders_exact_hint) {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${ctx.packageName}".toUri()))
                }
            }
            if (!s.fullScreen && Build.VERSION.SDK_INT >= 34) {
                SettingLink(R.string.reminders_full_screen_title, R.string.reminders_full_screen_hint) {
                    open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${ctx.packageName}".toUri()))
                }
            }
            SettingLink(
                R.string.reminders_dnd_title,
                if (s.dndAccess) R.string.reminders_dnd_on else R.string.reminders_dnd_hint,
            ) { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            SettingLink(R.string.reminders_sound_title, R.string.reminders_sound_hint) {
                open(Intent(Settings.ACTION_SOUND_SETTINGS))
            }
            SettingLink(R.string.reminders_open_title, R.string.reminders_open_hint, onOpenReminders)
        }
    }
}

@Composable
private fun SettingLink(title: Int, hint: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = 56.dp),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
    )
}
