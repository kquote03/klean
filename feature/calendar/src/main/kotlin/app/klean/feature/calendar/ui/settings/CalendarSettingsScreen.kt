package app.klean.feature.calendar.ui.settings

import app.klean.core.design.theme.Space
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.calendar.CalendarKeys
import app.klean.feature.calendar.R
import app.klean.feature.calendar.ui.edit.reminderLabel

@Composable
fun CalendarSettingsScreen(onBack: () -> Unit, vm: CalendarSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onAccessResult() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it.toString()) } }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let { vm.export(it.toString()) }
    }
    val exportName = stringResource(R.string.calendar_export_file_name)
    CalendarSettingsContent(
        state = state,
        onBack = onBack,
        onAllow = { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        onSelected = vm::setSelected,
        onShowTasks = vm::setShowTasks,
        onShowReminders = vm::setShowReminders,
        onDefaultReminder = vm::setDefaultReminder,
        onImport = { open.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/octet-stream", "text/plain")) },
        onExport = { create.launch(exportName) },
        onMessageShown = vm::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CalendarSettingsContent(
    state: CalendarSettingsUiState,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSelected: (String, Boolean) -> Unit,
    onShowTasks: (Boolean) -> Unit,
    onShowReminders: (Boolean) -> Unit,
    onDefaultReminder: (Int) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val res = LocalContext.current.resources
    val text = when (val m = state.message) {
        is SettingsMessage.Imported -> res.getQuantityString(R.plurals.calendar_imported, m.added, m.added, m.updated)
        is SettingsMessage.Exported -> res.getQuantityString(R.plurals.calendar_exported, m.count, m.count)
        SettingsMessage.ImportFailed -> stringResource(R.string.calendar_import_failed)
        SettingsMessage.ExportFailed -> stringResource(R.string.calendar_export_failed)
        null -> null
    }
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.calendar_settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(stringResource(R.string.calendar_settings_calendars))
            state.calendars.forEach { c ->
                SwitchListItem(
                    checked = c.selected,
                    onCheckedChange = { onSelected(c.id, it) },
                    headline = { Text(if (c.klean) stringResource(R.string.calendar_settings_klean) else c.name) },
                    supporting = {
                        Text(stringResource(if (c.klean) R.string.calendar_settings_klean_summary else R.string.calendar_settings_phone_summary))
                    },
                )
            }
            if (!state.canReadDevice) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.calendar_settings_access)) },
                    supportingContent = { Text(stringResource(R.string.calendar_access_body)) },
                    modifier = Modifier.clickable(onClick = onAllow).heightIn(min = 48.dp),
                )
            }
            Header(stringResource(R.string.calendar_settings_also))
            SwitchListItem(checked = state.showTasks, onCheckedChange = onShowTasks, headline = { Text(stringResource(R.string.calendar_settings_tasks)) })
            SwitchListItem(
                checked = state.showReminders, onCheckedChange = onShowReminders,
                headline = { Text(stringResource(R.string.calendar_settings_reminders)) },
            )
            Header(stringResource(R.string.calendar_settings_new_events))
            Text(
                stringResource(R.string.calendar_settings_default_reminder),
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Space.md),
            )
            FlowRow(Modifier.padding(horizontal = Space.md), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarKeys.REMINDER_CHOICES.forEach { m ->
                    FilterChip(
                        selected = state.defaultReminder == m, onClick = { onDefaultReminder(m) },
                        label = { Text(reminderLabel(m)) }, modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Header(stringResource(R.string.calendar_settings_files))
            ListItem(
                headlineContent = { Text(stringResource(R.string.calendar_settings_import)) },
                supportingContent = { Text(stringResource(R.string.calendar_settings_import_summary)) },
                modifier = Modifier.clickable(onClick = onImport).heightIn(min = 48.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.calendar_settings_export)) },
                supportingContent = { Text(stringResource(R.string.calendar_settings_export_summary)) },
                modifier = Modifier.clickable(onClick = onExport).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.md, top = Space.lg, bottom = Space.xxs).semantics { heading() },
    )
}
