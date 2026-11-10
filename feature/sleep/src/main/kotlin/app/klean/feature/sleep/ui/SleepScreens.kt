package app.klean.feature.sleep.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.settings.quiet.SleepWindowPicker
import app.klean.feature.sleep.R
import app.klean.feature.sleep.data.Night
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

internal object SystemZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}

@Composable
internal fun rememberSleepFormatter(): SleepFormatter {
    val ctx = LocalContext.current
    return remember { SleepFormatter(ctx.applicationContext, SystemZone) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.sleep_back))
                }
            }
        },
        actions = { actions() },
    )
}

/** The one-tap button: "Going to bed" while awake, "I'm up" while a night is open. */
@Composable
fun SleepToggleButton(asleep: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Text(stringResource(if (asleep) R.string.sleep_im_up else R.string.sleep_go_to_bed))
    }
}

/** Sleep: one tap, then the last 14 nights as plain bars of their length. No targets, no labels on length. */
@Composable
fun SleepScreen(onEdit: (String?) -> Unit, onSettings: () -> Unit, vm: SleepViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberSleepFormatter()
    Scaffold(
        topBar = {
            SleepTopBar(stringResource(R.string.sleep_label), onBack = null) {
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.sleep_settings))
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    val open = s.open
                    val last = s.lastNight
                    Text(
                        when {
                            open != null -> stringResource(R.string.sleep_since, fmt.time(open.bedAt))
                            last != null -> stringResource(R.string.sleep_last_night, fmt.duration(last.wakeAt!! - last.bedAt))
                            else -> stringResource(R.string.sleep_card_empty)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (s.loaded) SleepToggleButton(open != null, vm::toggle, Modifier.padding(top = 12.dp))
                }
            }
            item {
                Text(
                    stringResource(R.string.sleep_nights), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            if (s.loaded && s.nights.all { it.session == null }) item { NeutralEmpty(stringResource(R.string.sleep_nights_empty)) }
            val longest = s.nights.maxOfOrNull { it.durationMs ?: 0 }?.coerceAtLeast(1) ?: 1
            items(s.nights, key = { it.date.toString() }) { n ->
                val today = s.today
                NightRow(n, if (today != null) fmt.day(n.date, today) else "", longest, fmt) { n.session?.let { onEdit(it.id) } }
            }
            item {
                TextButton(onClick = { onEdit(null) }, modifier = Modifier.padding(8.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.sleep_add_night), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun NightRow(n: Night, day: String, longest: Long, fmt: SleepFormatter, onClick: () -> Unit) {
    val s = n.session
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = s != null, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(20.dp)) {
            val ms = n.durationMs
            if (ms != null && ms > 0) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth((ms.toFloat() / longest).coerceIn(0.04f, 1f))
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
                )
            } else {
                // An empty night reads on one line where the bar would be, not squeezed into the duration column.
                Text(
                    stringResource(R.string.sleep_no_night),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        Text(
            n.durationMs?.takeIf { it > 0 }?.let { fmt.duration(it) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).width(72.dp),
        )
    }
}

/** Edit or add a night. */
@Composable
fun SleepEditScreen(onBack: () -> Unit, vm: SleepEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberSleepFormatter()
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = bed, false = wake
    Scaffold(
        topBar = {
            SleepTopBar(stringResource(if (s.isNew) R.string.sleep_edit_new else R.string.sleep_edit_title), onBack) {
                if (!s.isNew) {
                    IconButton(onClick = { vm.delete { onBack() } }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.sleep_delete))
                    }
                }
            }
        },
    ) { pad ->
        if (!s.loaded) return@Scaffold
        Column(Modifier.padding(pad)) {
            ListItem(
                modifier = Modifier.clickable { picking = true }.heightIn(min = 56.dp),
                headlineContent = { Text(stringResource(R.string.sleep_bed_time)) },
                trailingContent = { Text(fmt.time(s.bedAt)) },
            )
            ListItem(
                modifier = Modifier.clickable { picking = false }.heightIn(min = 56.dp),
                headlineContent = { Text(stringResource(R.string.sleep_wake_time)) },
                trailingContent = { Text(s.wakeAt?.let { fmt.time(it) } ?: stringResource(R.string.sleep_still_asleep)) },
                supportingContent = { s.wakeAt?.let { Text(fmt.duration(it - s.bedAt)) } },
            )
            Button(onClick = { vm.save(onBack) }, modifier = Modifier.padding(16.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sleep_save))
            }
        }
    }
    picking?.let { bed ->
        TimeDialog(
            initialMinute = if (bed) vm.bedMinute() else vm.wakeMinute() ?: (7 * 60),
            onDismiss = { picking = null },
            onPick = {
                if (bed) vm.setBed(it) else vm.setWake(it)
                picking = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onPick(picker.hour * 60 + picker.minute) }) { Text(stringResource(R.string.sleep_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sleep_cancel)) } },
    )
}

/** Sleep settings: the schedule (= quiet hours), the bedtime reminder, and the while-asleep notification. */
@Composable
fun SleepSettingsScreen(onBack: () -> Unit, vm: SleepSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Scaffold(topBar = { SleepTopBar(stringResource(R.string.sleep_settings_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.sleep_settings_schedule), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            Text(stringResource(R.string.sleep_settings_schedule_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            SleepWindowPicker(
                s.window,
                onStart = { vm.setWindow(s.window.copy(startMinute = it)) },
                onEnd = { vm.setWindow(s.window.copy(endMinute = it)) },
            )
            SwitchListItem(
                checked = s.bedtime, onCheckedChange = vm::setBedtime,
                headline = { Text(stringResource(R.string.sleep_settings_bedtime)) },
                supporting = { Text(stringResource(R.string.sleep_settings_bedtime_hint)) },
            )
            Box {
                ListItem(
                    modifier = Modifier.clickable(enabled = s.bedtime) { menu = true }.heightIn(min = 56.dp),
                    headlineContent = { Text(stringResource(R.string.sleep_settings_lead)) },
                    supportingContent = { Text(stringResource(R.string.sleep_minutes_before, s.lead)) },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.sleep_minutes_before, m)) }, onClick = {
                            vm.setLead(m)
                            menu = false
                        })
                    }
                }
            }
            SwitchListItem(
                checked = s.sessionNotification, onCheckedChange = vm::setSessionNotification,
                headline = { Text(stringResource(R.string.sleep_settings_session)) },
                supporting = { Text(stringResource(R.string.sleep_settings_session_hint)) },
            )
            app.klean.core.health.HealthConnectRow(needed = setOf(app.klean.core.health.HealthAccess.READ_SLEEP))
        }
    }
}
