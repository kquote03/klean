package app.klean.feature.contacts.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.contacts.R
import app.klean.feature.contacts.data.ReminderDays
import app.klean.shared.vcard.VCardVersion

@Composable
fun ContactsSettingsScreen(onBack: () -> Unit, vm: ContactsSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onAccessResult() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it.toString()) } }
    var version by remember { mutableStateOf(VCardVersion.V3) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vcard")) { uri -> uri?.let { vm.export(it.toString(), version) } }
    val fileName = stringResource(R.string.contacts_export_file_name)
    ContactsSettingsContent(
        state = state, onBack = onBack,
        onAllow = { ask.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
        onImport = { open.launch(arrayOf("text/vcard", "text/x-vcard", "text/directory", "application/octet-stream", "text/plain")) },
        onExport = { v -> version = v; create.launch(fileName) },
        onMessageShown = vm::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSettingsContent(
    state: ContactsSettingsUiState,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onImport: () -> Unit,
    onExport: (VCardVersion) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val res = LocalContext.current.resources
    val text = when (val m = state.message) {
        is ContactsMessage.Imported -> res.getQuantityString(R.plurals.contacts_imported, m.added, m.added, m.updated)
        is ContactsMessage.Exported -> res.getQuantityString(R.plurals.contacts_exported, m.count, m.count)
        ContactsMessage.ImportFailed -> stringResource(R.string.contacts_import_failed)
        ContactsMessage.ExportFailed -> stringResource(R.string.contacts_export_failed)
        null -> null
    }
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onMessageShown()
        }
    }
    SettingsScaffold(stringResource(R.string.contacts_settings_title), onBack, snackbar) {
        Header(stringResource(R.string.contacts_settings_phone))
        if (state.canReadPhone) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.contacts_settings_phone_on)) },
                supportingContent = { Text(stringResource(R.string.contacts_settings_phone_on_body)) },
            )
        } else {
            ListItem(
                headlineContent = { Text(stringResource(R.string.contacts_access_title)) },
                supportingContent = { Text(stringResource(R.string.contacts_access_body)) },
                modifier = Modifier.clickable(onClick = onAllow).heightIn(min = 48.dp),
            )
        }
        Header(stringResource(R.string.contacts_settings_dav))
        ListItem(
            headlineContent = {
                Text(
                    if (state.davAccounts.isEmpty()) stringResource(R.string.contacts_settings_dav_none)
                    else state.davAccounts.joinToString(", "),
                )
            },
            supportingContent = { Text(stringResource(R.string.contacts_settings_dav_body)) },
        )
        Header(stringResource(R.string.contacts_settings_files))
        ListItem(
            headlineContent = { Text(stringResource(R.string.contacts_settings_import)) },
            supportingContent = { Text(stringResource(R.string.contacts_settings_import_body)) },
            modifier = Modifier.clickable(onClick = onImport).heightIn(min = 48.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.contacts_settings_export_v3)) },
            supportingContent = { Text(stringResource(R.string.contacts_settings_export_v3_body)) },
            modifier = Modifier.clickable { onExport(VCardVersion.V3) }.heightIn(min = 48.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.contacts_settings_export_v4)) },
            supportingContent = { Text(stringResource(R.string.contacts_settings_export_v4_body)) },
            modifier = Modifier.clickable { onExport(VCardVersion.V4) }.heightIn(min = 48.dp),
        )
    }
}

@Composable
fun BirthdaysSettingsScreen(onBack: () -> Unit, vm: BirthdaysSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BirthdaysSettingsContent(state, onBack, vm::setShowAge, vm::toggleDefault, vm::setHour)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BirthdaysSettingsContent(
    state: BirthdaysSettingsUiState,
    onBack: () -> Unit,
    onShowAge: (Boolean) -> Unit,
    onToggleDefault: (Int) -> Unit,
    onHour: (Int) -> Unit,
) {
    SettingsScaffold(stringResource(R.string.birthdays_settings_title), onBack, remember { SnackbarHostState() }) {
        SwitchListItem(
            checked = state.showAge, onCheckedChange = onShowAge,
            headline = { Text(stringResource(R.string.birthdays_settings_age)) },
            supporting = { Text(stringResource(R.string.birthdays_settings_age_body)) },
        )
        Header(stringResource(R.string.birthdays_settings_defaults))
        Text(
            stringResource(R.string.birthdays_settings_defaults_body), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderDays.CHOICES.forEach { n ->
                FilterChip(
                    selected = n in state.defaultDays, onClick = { onToggleDefault(n) },
                    label = { Text(reminderLabel(n)) }, modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Header(stringResource(R.string.birthdays_settings_time))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BirthdaysSettingsViewModel.HOURS.forEach { h ->
                FilterChip(
                    selected = state.hour == h, onClick = { onHour(h) },
                    label = { Text(hourLabel(h)) }, modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

private fun hourLabel(h: Int): String =
    java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.contacts_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) { content() }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
}
