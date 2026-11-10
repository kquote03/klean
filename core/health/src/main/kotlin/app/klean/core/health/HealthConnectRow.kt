package app.klean.core.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Health Connect settings row shows. */
data class HealthRowState(val availability: HealthAvailability, val granted: Set<HealthAccess>) {
    fun connected(needed: Set<HealthAccess>) = availability == HealthAvailability.AVAILABLE && granted.containsAll(needed)
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(private val gateway: HealthConnectGateway) : ViewModel() {
    private val availability = MutableStateFlow(gateway.availability())
    val state: StateFlow<HealthRowState> = combine(availability, gateway.granted) { a, g -> HealthRowState(a, g) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HealthRowState(availability.value, gateway.granted.value))

    fun permissionsFor(access: Set<HealthAccess>) = gateway.permissionsFor(access)

    fun refresh() = viewModelScope.launch {
        availability.value = gateway.availability()
        gateway.refresh()
    }
}

/**
 * The Health Connect row for a module's settings (Sleep, Workout). Never blocks anything: without Health
 * Connect it says so calmly, and Klean keeps its own log.
 */
@Composable
fun HealthConnectRow(needed: Set<HealthAccess>, modifier: Modifier = Modifier, vm: HealthConnectViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    HealthConnectRowContent(
        state = state,
        needed = needed,
        onConnect = { ask.launch(vm.permissionsFor(HealthConnectGateway.ALL)) },
        onUpdate = { openStore(ctx) },
        onManage = { openSettings(ctx) },
        modifier = modifier,
    )
}

@Composable
fun HealthConnectRowContent(
    state: HealthRowState,
    needed: Set<HealthAccess>,
    onConnect: () -> Unit,
    onUpdate: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val (body, action) = when {
            state.availability == HealthAvailability.UNAVAILABLE -> R.string.health_unavailable to null
            state.availability == HealthAvailability.NEEDS_UPDATE -> R.string.health_needs_update to (R.string.health_update to onUpdate)
            state.connected(needed) -> R.string.health_connected to (R.string.health_manage to onManage)
            else -> R.string.health_offer to (R.string.health_connect to onConnect)
        }
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { (label, onClick) ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (label == R.string.health_manage) {
                    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
                } else {
                    FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
                }
            }
        }
    }
}

private const val PROVIDER = "com.google.android.apps.healthdata"

private fun openStore(ctx: Context) {
    val uri = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun openSettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
