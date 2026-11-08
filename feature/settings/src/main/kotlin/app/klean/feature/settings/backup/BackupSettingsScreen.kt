package app.klean.feature.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupSettingsScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val resolver = ctx.contentResolver
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        if (uri != null) vm.export { resolver.openOutputStream(uri, "w") }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickRestore { resolver.openInputStream(uri) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.setAutoFolder(uri.toString())
        }
    }

    val messageText = state.message?.let { stringResource(it.res()) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.consumeMessage()
        }
    }

    SettingsSubScaffold(R.string.settings_backup, onBack) { modifier ->
      Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { Header(R.string.backup_section_manual) }
            item {
                ActionRow(R.string.backup_export, R.string.backup_export_summary, enabled = !state.busy) {
                    exportLauncher.launch(vm.exportFileName())
                }
            }
            item {
                ActionRow(R.string.backup_restore, R.string.backup_restore_summary, enabled = !state.busy) {
                    restoreLauncher.launch(arrayOf("*/*"))
                }
            }
            item {
                ListItem(
                    supportingContent = { Text(stringResource(R.string.backup_include_keys_warning)) },
                    trailingContent = { Switch(checked = state.includeSecrets, onCheckedChange = vm::setIncludeSecrets) },
                ) { Text(stringResource(R.string.backup_include_keys)) }
            }
            item { Header(R.string.backup_section_auto) }
            item {
                ListItem(
                    supportingContent = { Text(stringResource(R.string.backup_auto_daily_summary)) },
                    trailingContent = {
                        Switch(
                            checked = state.autoEnabled,
                            onCheckedChange = { on -> if (on) folderLauncher.launch(null) else vm.disableAuto() },
                        )
                    },
                ) { Text(stringResource(R.string.backup_auto_daily)) }
            }
            if (state.autoEnabled) {
                item {
                    val folder = state.autoFolder?.let { folderName(ctx, it) }.orEmpty()
                    ListItem(
                        trailingContent = {
                            TextButton(onClick = { folderLauncher.launch(state.autoFolder?.let(Uri::parse)) }) {
                                Text(stringResource(R.string.backup_auto_folder_change))
                            }
                        },
                    ) { Text(stringResource(R.string.backup_auto_folder, folder)) }
                }
                item {
                    ListItem(
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.setKeep(state.keep - 1) }, enabled = state.keep > 1) {
                                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.backup_auto_keep_fewer))
                                }
                                IconButton(onClick = { vm.setKeep(state.keep + 1) }, enabled = state.keep < BackupViewModel.MAX_KEEP) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.backup_auto_keep_more))
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.backup_auto_keep, state.keep)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
      }
    }

    state.confirm?.let { c ->
        val date = remember(c.backup.exportedAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.backup.exportedAt)) }
        AlertDialog(
            onDismissRequest = vm::dismissRestore,
            text = {
                Text(stringResource(R.string.backup_confirm_text, c.currentEntries, date, c.backup.entryCount, c.backup.photoCount))
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text(stringResource(R.string.backup_confirm_replace)) } },
            dismissButton = { TextButton(onClick = vm::dismissRestore) { Text(stringResource(R.string.backup_confirm_cancel)) } },
        )
    }
}

@Composable
private fun Header(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ActionRow(title: Int, summary: Int, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

private fun folderName(ctx: Context, treeUri: String): String =
    runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))?.name }.getOrNull() ?: treeUri

private fun BackupMessage.res() = when (this) {
    BackupMessage.EXPORTED -> R.string.backup_msg_exported
    BackupMessage.EXPORT_FAILED -> R.string.backup_msg_export_failed
    BackupMessage.NOT_A_BACKUP -> R.string.backup_msg_not_a_backup
    BackupMessage.NEWER_VERSION -> R.string.backup_msg_newer_version
    BackupMessage.DAMAGED -> R.string.backup_msg_damaged
}
