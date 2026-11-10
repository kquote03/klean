package app.klean.core.google.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.google.AuthStep
import app.klean.core.google.GoogleAuth
import app.klean.core.google.GoogleLocalState
import app.klean.core.google.GoogleSyncResult
import app.klean.core.google.GoogleTasksScheduler
import app.klean.core.google.GoogleTasksSync
import app.klean.core.google.R
import app.klean.core.google.TaskOrigin
import android.content.Intent
import android.content.IntentSender
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoogleTasksUi(
    val available: Boolean = true,
    val connected: Boolean = false,
    val lists: List<TaskListEntity> = emptyList(),
    val lastSyncAt: Long? = null,
    val busy: Boolean = false,
    /** A calm one-line note after an action (string res), or null. */
    val note: Int? = null,
    /** Consent screen to launch once. */
    val consent: IntentSender? = null,
)

@HiltViewModel
class GoogleTasksViewModel @Inject constructor(
    private val auth: GoogleAuth,
    private val sync: GoogleTasksSync,
    private val scheduler: GoogleTasksScheduler,
    state: GoogleLocalState,
    db: KleanDatabase,
) : ViewModel() {
    private val local = MutableStateFlow(GoogleTasksUi(available = auth.available()))

    val ui: StateFlow<GoogleTasksUi> = combine(
        local, auth.connected, state.lastSyncAt,
        db.taskListDao().observeAll().map { l -> l.filter { it.source == TaskOrigin.GOOGLE } },
    ) { l, connected, last, lists -> l.copy(connected = connected, lastSyncAt = last, lists = lists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun connect() = viewModelScope.launch {
        local.update { it.copy(busy = true, note = null) }
        handle(auth.connect())
    }

    fun onConsentResult(data: Intent?) = viewModelScope.launch {
        local.update { it.copy(consent = null) }
        handle(auth.finishConsent(data))
    }

    private suspend fun handle(step: AuthStep) {
        when (step) {
            AuthStep.Granted -> {
                scheduler.schedulePeriodic()
                runSync()
            }
            is AuthStep.NeedsConsent -> local.update { it.copy(consent = step.intentSender) }
            AuthStep.Declined -> local.update { it.copy(busy = false, note = R.string.google_declined) }
            is AuthStep.Unavailable -> local.update { it.copy(busy = false, available = auth.available(), note = R.string.google_unavailable_now) }
        }
    }

    fun syncNow() = viewModelScope.launch { runSync() }

    private suspend fun runSync() {
        local.update { it.copy(busy = true, note = null) }
        val note = when (val r = sync.run()) {
            is GoogleSyncResult.Done -> null
            GoogleSyncResult.NotConnected -> R.string.google_reconnect
            is GoogleSyncResult.Failed -> R.string.google_sync_later
        }
        local.update { it.copy(busy = false, note = note) }
    }

    fun setSelected(listId: String, selected: Boolean) = viewModelScope.launch {
        sync.setSelected(listId, selected)
        if (selected) scheduler.syncNow()
    }

    fun disconnect() = viewModelScope.launch {
        scheduler.cancel()
        auth.disconnect()
        sync.forget()
        local.update { it.copy(note = R.string.google_disconnected) }
    }
}

/** Settings › Tasks: connect Google Tasks, pick lists, sync, disconnect. */
@Composable
fun GoogleTasksSection(vm: GoogleTasksViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r -> vm.onConsentResult(r.data) }
    LaunchedEffect(s.consent) { s.consent?.let { launcher.launch(IntentSenderRequest.Builder(it).build()) } }
    GoogleTasksContent(s, onConnect = vm::connect, onSync = vm::syncNow, onSelect = vm::setSelected, onDisconnect = vm::disconnect)
}

@Composable
fun GoogleTasksContent(
    s: GoogleTasksUi,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.google_tasks_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        when {
            !s.available && !s.connected -> ListItem(
                headlineContent = { Text(stringResource(R.string.google_tasks_title)) },
                supportingContent = { Text(stringResource(R.string.google_unavailable)) },
            )
            !s.connected -> ListItem(
                headlineContent = { Text(stringResource(R.string.google_connect_headline)) },
                supportingContent = { Text(stringResource(R.string.google_connect_body)) },
                trailingContent = {
                    TextButton(onClick = onConnect, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.google_connect))
                    }
                },
            )
            else -> {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_connected)) },
                    supportingContent = { Text(stringResource(R.string.google_connected_body)) },
                    trailingContent = {
                        TextButton(onClick = onSync, enabled = !s.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.google_sync_now))
                        }
                    },
                )
                s.lists.forEach { l ->
                    Row(
                        Modifier
                            .toggleable(value = l.selected, role = Role.Checkbox, onValueChange = { onSelect(l.id, it) })
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = l.selected, onCheckedChange = null)
                        Text(l.name, modifier = Modifier.padding(start = 16.dp))
                    }
                }
                TextButton(onClick = onDisconnect, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.google_disconnect))
                }
            }
        }
        s.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}
