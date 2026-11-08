package app.klean.feature.settings.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.core.notifications.permissions.PermissionAction
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.notifications.permissions.PermissionIntents
import app.klean.core.notifications.permissions.PermissionKind
import app.klean.core.notifications.permissions.PermissionModel
import app.klean.core.notifications.permissions.PermissionRowModel
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.klean.core.notifications.R as NR

/**
 * Live permission rows. [refresh] runs on every resume, because changes happen in system screens.
 * [includeReadOnly]: the Settings screen lists status-only rows too; setup leaves them out.
 */
class PermissionsViewModel(
    private val checker: PermissionChecker,
    private val includeReadOnly: Boolean,
) : ViewModel() {
    private var notificationsAsked = false
    private val _rows = MutableStateFlow(build())
    val rows: StateFlow<List<PermissionRowModel>> = _rows.asStateFlow()

    fun refresh() { _rows.value = build() }

    /** Result of the `POST_NOTIFICATIONS` dialog. A second ask would be ignored, so later taps open settings. */
    fun onNotificationsResult(granted: Boolean) {
        notificationsAsked = true
        refresh()
    }

    private fun build() = PermissionModel.rows(checker.current(), checker.sdkInt, includeReadOnly, notificationsAsked)
}

/** The permission rows plus everything their buttons do. Shared by Settings and setup. */
@Composable
fun PermissionRows(vm: PermissionsViewModel, modifier: Modifier = Modifier) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onNotificationsResult(it) }
    PermissionRowsContent(
        rows = rows,
        modifier = modifier,
        onAction = { row ->
            when (row.action) {
                PermissionAction.REQUEST_RUNTIME -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                PermissionAction.OPEN_SETTINGS -> PermissionIntents.settingsFor(ctx, row.kind)?.let { ctx.startSafely(it) }
                PermissionAction.NONE -> Unit
            }
        },
        onHelp = { ctx.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(PermissionIntents.manufacturerHelpUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
    )
}

/** Some phones remove a specific settings screen; fall back to the app's info page. */
private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(PermissionIntents.appDetails(this)) }
    }
}

@Composable
fun PermissionRowsContent(
    rows: List<PermissionRowModel>,
    onAction: (PermissionRowModel) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        rows.forEach { row -> PermissionRow(row, onAction = { onAction(row) }, onHelp = onHelp) }
    }
}

@Composable
private fun PermissionRow(row: PermissionRowModel, onAction: () -> Unit, onHelp: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(row.titleRes)) },
        supportingContent = {
            Column {
                Text(stringResource(row.whyRes))
                if (row.action != PermissionAction.NONE || row.kind == PermissionKind.BATTERY) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.action != PermissionAction.NONE) {
                            FilledTonalButton(onClick = onAction) { Text(stringResource(NR.string.permission_turn_on)) }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (row.kind == PermissionKind.BATTERY) {
                            TextButton(onClick = onHelp) { Text(stringResource(NR.string.permission_battery_help)) }
                        }
                    }
                }
            }
        },
        trailingContent = {
            // Neutral either way: "Off" is a state, not a problem.
            Text(
                stringResource(if (row.granted) NR.string.permission_on else NR.string.permission_off),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
fun PermissionsSettingsScreen(
    checker: PermissionChecker,
    onBack: () -> Unit,
    vm: PermissionsViewModel = viewModel { PermissionsViewModel(checker, includeReadOnly = true) },
) {
    SettingsSubScaffold(R.string.settings_permissions, onBack) { modifier ->
        PermissionRows(vm, modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp))
    }
}
