package app.klean.core.sync.ui

import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.sync.PairOutcome
import app.klean.core.sync.R
import app.klean.core.sync.SyncProblem
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.serialization.Serializable

@Serializable object SyncSettingsRoute

/** Settings › Sync: pair, unpair, last sync, sync now, and the devices in this sync group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onBack: () -> Unit, vm: SyncViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.sync_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SyncPanel(state, vm, showDevices = true)
        }
    }
    if (state.confirmUnpair) {
        AlertDialog(
            onDismissRequest = { vm.askUnpair(false) },
            title = { Text(stringResource(R.string.sync_unpair_title)) },
            text = { Text(stringResource(R.string.sync_unpair_body, state.status.url.orEmpty())) },
            confirmButton = { TextButton(onClick = vm::unpair) { Text(stringResource(R.string.sync_unpair)) } },
            dismissButton = { TextButton(onClick = { vm.askUnpair(false) }) { Text(stringResource(R.string.sync_cancel)) } },
        )
    }
}

/** Pairing (scan or paste) when not paired; status and actions when paired. Shared by Settings and setup. */
@Composable
fun SyncPanel(state: SyncUiState, vm: SyncViewModel, showDevices: Boolean) {
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.status.paired) Paired(state, vm, showDevices) else Unpaired(state, vm)
}

@Composable
private fun Unpaired(state: SyncUiState, vm: SyncViewModel) {
    val ctx = LocalContext.current
    val hasCamera = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    val prompt = stringResource(R.string.sync_scan_prompt)
    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(vm::scanned) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.sync_intro_web),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasCamera) {
            FilledTonalButton(
                onClick = {
                    scan.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(prompt)
                            .setBeepEnabled(false).setOrientationLocked(false),
                    )
                },
                enabled = !state.busy,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.sync_scan))
            }
        }
        OutlinedTextField(
            value = state.link,
            onValueChange = vm::setLink,
            label = { Text(stringResource(R.string.sync_link_label)) },
            placeholder = { Text("klean://pair?…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("sync_link"),
        )
        Button(onClick = vm::pair, enabled = state.link.isNotBlank() && !state.busy) { Text(stringResource(R.string.sync_pair)) }
        state.outcome?.takeIf { it != PairOutcome.PAIRED }?.let {
            Text(stringResource(it.message()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Paired(state: SyncUiState, vm: SyncViewModel, showDevices: Boolean) {
    val s = state.status
    val now = System.currentTimeMillis()
    val last = when {
        s.lastSyncAt == null -> stringResource(R.string.sync_last_never)
        now - s.lastSyncAt < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.sync_last_just_now)
        else -> DateUtils.getRelativeTimeSpanString(s.lastSyncAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val itemColors = ListItemDefaults.colors()
    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_paired_with)) },
        supportingContent = { Text(s.url.orEmpty()) },
        colors = itemColors,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_last)) },
        supportingContent = { Text(last) },
        colors = itemColors,
    )
    s.problem?.let {
        Text(
            stringResource(it.message()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = vm::syncNow, enabled = !state.busy) { Text(stringResource(R.string.sync_now)) }
        OutlinedButton(onClick = { vm.askUnpair(true) }, enabled = !state.busy) { Text(stringResource(R.string.sync_unpair)) }
    }
    if (showDevices && state.devices.isNotEmpty()) {
        Text(
            stringResource(R.string.sync_devices),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        state.devices.forEach { d ->
            val notes = buildList {
                if (d.id == state.thisDevice) add(stringResource(R.string.sync_device_this))
                if (d.isMainPhone) add(stringResource(R.string.sync_device_main))
            }.joinToString(" · ")
            ListItem(
                headlineContent = { Text(d.name) },
                supportingContent = notes.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                colors = itemColors,
            )
        }
    }
}

private fun PairOutcome.message(): Int = when (this) {
    PairOutcome.BAD_LINK -> R.string.sync_error_link
    PairOutcome.BAD_CODE -> R.string.sync_error_code
    PairOutcome.UNREACHABLE -> R.string.sync_error_unreachable
    PairOutcome.SERVER_ERROR, PairOutcome.PAIRED -> R.string.sync_error_server
}

private fun SyncProblem.message(): Int = when (this) {
    SyncProblem.OFFLINE -> R.string.sync_problem_offline
    SyncProblem.SERVER -> R.string.sync_problem_server
    SyncProblem.UNAUTHORIZED -> R.string.sync_problem_unauthorized
}
