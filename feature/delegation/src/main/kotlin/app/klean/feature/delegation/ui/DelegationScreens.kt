package app.klean.feature.delegation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.delegation.R
import app.klean.feature.delegation.data.DelegationMode
import app.klean.feature.delegation.data.DelegationRole
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.delegation.hermes.HermesSettings
import app.klean.feature.delegation.notify.DelegationLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DelegationTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.delegation_back))
            }
        },
    )
}

@Composable
fun ApprovalScreen(onBack: () -> Unit, vm: ApprovalViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { DelegationTopBar(stringResource(R.string.delegation_approval_screen), onBack) }) { pad ->
        if (!s.loaded) return@Scaffold
        ApprovalContent(
            s, Modifier.padding(pad),
            onDraft = vm::setDraft, onRecipient = vm::setRecipient, onRemove = vm::removeRecipient, onAdd = vm::addRecipient,
            onApprove = vm::approve, onReject = vm::reject,
        )
    }
}

@Composable
fun ApprovalContent(
    s: ApprovalUi,
    modifier: Modifier = Modifier,
    onDraft: (String) -> Unit,
    onRecipient: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var newRecipient by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.taskTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.delegation_approval_action, s.action.replace('_', ' ')), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.done) {
            Text(
                stringResource(
                    when (s.decision) {
                        "REJECTED" -> R.string.delegation_decided_rejected
                        "EDITED" -> R.string.delegation_decided_edited
                        else -> R.string.delegation_decided_approved
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(stringResource(R.string.delegation_draft), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (editing) {
            OutlinedTextField(value = s.draft, onValueChange = onDraft, minLines = 4, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.delegation_draft)) })
        } else {
            Text(s.draft, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            pluralStringResource(R.plurals.delegation_recipients, s.recipients.size, s.recipients.size),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        )
        if (s.many) Text(stringResource(R.string.delegation_many_recipients), style = MaterialTheme.typography.bodyMedium)
        s.recipients.forEachIndexed { i, r ->
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = r, onValueChange = { onRecipient(i, it) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delegation_remove_recipient, r))
                    }
                }
            } else {
                Text(r, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newRecipient, onValueChange = { newRecipient = it }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.delegation_add_recipient)) },
                )
                TextButton(onClick = { onAdd(newRecipient); newRecipient = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delegation_add))
                }
            }
        }
        if (!s.done) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onReject, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_reject)) }
                if (!editing) OutlinedButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_edit)) }
                Button(onClick = onApprove, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (s.edited) R.string.delegation_approve_edited else R.string.delegation_approve))
                }
            }
        }
    }
}

@Composable
fun DelegationScreen(onBack: () -> Unit, onOpenApproval: (String) -> Unit, vm: DelegationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val accepted by vm.subtasksAccepted.collectAsStateWithLifecycle()
    var reply by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { DelegationTopBar(s.taskTitle.ifEmpty { stringResource(R.string.delegation_label) }, onBack) }) { pad ->
        val d = s.delegation ?: return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(statusText(d.status), style = MaterialTheme.typography.titleMedium)
            s.messages.forEach { m ->
                val mine = m.role == DelegationRole.USER
                ElevatedCard(Modifier.fillMaxWidth().padding(start = if (mine) 32.dp else 0.dp, end = if (mine) 0.dp else 32.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(if (mine) R.string.delegation_you else R.string.delegation_agent), style = MaterialTheme.typography.labelMedium)
                        Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            s.approvals.filter { it.decision == "PENDING" }.forEach { a ->
                FilledTonalButton(onClick = { onOpenApproval(a.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_review_approval)) }
            }
            if (s.needsInput) {
                OutlinedTextField(value = reply, onValueChange = { reply = it }, label = { Text(stringResource(R.string.delegation_reply_hint)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.answer(reply); reply = "" }, enabled = reply.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delegation_reply))
                }
            }
            d.result?.let { r ->
                Text(stringResource(R.string.delegation_result), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(r, style = MaterialTheme.typography.bodyLarge)
            }
            if (d.status == DelegationStatus.DONE) {
                val subs = s.proposedSubtasks
                if (subs.isNotEmpty() && !accepted) {
                    OutlinedButton(onClick = { vm.acceptSubtasks(subs) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(pluralStringResource(R.plurals.delegation_accept_subtasks, subs.size, subs.size))
                    }
                }
                if (!d.autoComplete) {
                    Button(onClick = { vm.confirmDone(onBack) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_mark_done)) }
                }
            }
        }
    }
}

@Composable
internal fun statusText(status: String): String = stringResource(
    when (status) {
        DelegationStatus.QUEUED, DelegationStatus.RUNNING -> R.string.delegation_status_running
        DelegationStatus.NEEDS_INPUT -> R.string.delegation_status_input
        DelegationStatus.NEEDS_APPROVAL -> R.string.delegation_status_approval
        DelegationStatus.DONE -> R.string.delegation_status_done
        else -> R.string.delegation_status_failed
    },
)

/** On the task screen: \"Delegate\" (choose how), or the latest delegation's status. */
@Composable
fun DelegateSection(taskId: String, modifier: Modifier = Modifier, vm: DelegateViewModel = hiltViewModel()) {
    LaunchedEffect(taskId) { vm.bind(taskId) }
    val latest by vm.latest.collectAsStateWithLifecycle()
    val hermesReady by vm.hermesReady.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var choose by rememberSaveable { mutableStateOf(false) }
    fun open(link: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(ctx.packageName)) }
    Column(modifier.fillMaxWidth()) {
        val d = latest
        if (d != null && d.status != DelegationStatus.FAILED) {
            ListItem(
                modifier = Modifier.clickable { open(DelegationLinks.delegation(d.id)) }.heightIn(min = 56.dp),
                headlineContent = { Text(stringResource(R.string.delegation_label)) },
                supportingContent = { Text(statusText(d.status)) },
            )
        } else {
            OutlinedButton(onClick = { choose = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_delegate)) }
        }
    }
    if (choose) {
        DelegateDialog(
            hermesReady = hermesReady,
            onDismiss = { choose = false },
            onStart = { mode, auto -> choose = false; vm.delegate(mode, auto) { id -> open(DelegationLinks.delegation(id)) } },
            onSetUp = { choose = false; open(DelegationLinks.SETTINGS) },
        )
    }
}

@Composable
fun DelegateDialog(hermesReady: Boolean, onDismiss: () -> Unit, onStart: (String, Boolean) -> Unit, onSetUp: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(DelegationMode.PARTIAL) }
    var auto by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delegation_delegate)) },
        text = {
            Column {
                listOf(
                    DelegationMode.PARTIAL to R.string.delegation_mode_partial,
                    DelegationMode.INTERACTIVE to R.string.delegation_mode_interactive,
                    DelegationMode.AGENTIC to R.string.delegation_mode_agentic,
                ).forEach { (m, label) ->
                    val enabled = m == DelegationMode.PARTIAL || hermesReady
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { mode = m },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m }, enabled = enabled)
                        Text(stringResource(label), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!hermesReady) {
                    TextButton(onClick = onSetUp, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_set_up_hermes)) }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.delegation_auto_complete), Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(mode, auto) }) { Text(stringResource(R.string.delegation_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.delegation_cancel)) } },
    )
}

/** Today's APPROVALS card: what's waiting for a decision. Hidden when nothing is. */
@Composable
fun ApprovalsCard(onOpenApproval: (String) -> Unit, modifier: Modifier = Modifier, vm: ApprovalsCardViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val list = pending.orEmpty()
    if (list.isEmpty()) return
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.delegation_card_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            list.take(5).forEach { p ->
                ListItem(
                    modifier = Modifier.clickable { onOpenApproval(p.approval.id) }.heightIn(min = 56.dp),
                    headlineContent = { Text(p.taskTitle) },
                    supportingContent = { Text(stringResource(R.string.delegation_approval_action, p.approval.action.replace('_', ' '))) },
                )
            }
        }
    }
}

@Composable
fun HermesSettingsScreen(onBack: () -> Unit, vm: HermesSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { DelegationTopBar(stringResource(R.string.delegation_settings_title), onBack) }) { pad ->
        if (!s.loaded) return@Scaffold
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.delegation_settings_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = s.url, onValueChange = vm::setUrl, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.delegation_settings_url)) })
            OutlinedTextField(
                value = s.token, onValueChange = vm::setToken, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), label = { Text(stringResource(R.string.delegation_settings_token)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::test, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_settings_test)) }
                Button(onClick = vm::save, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delegation_settings_save)) }
            }
            if (s.saved && s.test == null) Text(stringResource(R.string.delegation_settings_saved))
            s.test?.let { t ->
                Text(
                    stringResource(
                        when (t) {
                            HermesSettings.TestResult.OK -> R.string.delegation_test_ok
                            HermesSettings.TestResult.UNAUTHORIZED -> R.string.delegation_test_token
                            HermesSettings.TestResult.UNREACHABLE -> R.string.delegation_test_unreachable
                            HermesSettings.TestResult.NOT_SET -> R.string.delegation_test_not_set
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun DelegationsEmpty(modifier: Modifier = Modifier) = NeutralEmpty(stringResource(R.string.delegation_empty), modifier)
