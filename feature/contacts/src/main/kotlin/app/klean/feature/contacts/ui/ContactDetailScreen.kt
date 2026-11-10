package app.klean.feature.contacts.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.contacts.R

@Composable
fun ContactDetailScreen(onBack: () -> Unit, onEdit: (String?, Long?) -> Unit, vm: ContactDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // A DAVx5 contact is read once; read it again when coming back from its editor.
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }
    val ctx = LocalContext.current
    ContactDetailContent(
        state = state, onBack = onBack, onEdit = { onEdit(state.contactId, state.rawId) },
        onCall = { n -> runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", n, null))) } },
        onEmail = { e -> runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e, null))) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailContent(
    state: ContactDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    val c = state.contact
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.contacts_back))
                    }
                },
                actions = {
                    if (c != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.contacts_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (c == null) {
                if (state.loaded) NeutralEmpty(stringResource(R.string.contacts_gone))
                return@Column
            }
            c.account?.let { Line(stringResource(R.string.contacts_in_account, it), null) }
            c.org?.let { Line(it, stringResource(R.string.contacts_field_org)) }
            if (c.phones.isNotEmpty()) Header(stringResource(R.string.contacts_field_phones))
            c.phones.forEach { p -> Line(p.value, labelText(p.label), onClick = { onCall(p.value) }) }
            if (c.emails.isNotEmpty()) Header(stringResource(R.string.contacts_field_emails))
            c.emails.forEach { e -> Line(e.value, labelText(e.label), onClick = { onEmail(e.value) }) }
            if (c.addresses.isNotEmpty()) Header(stringResource(R.string.contacts_field_addresses))
            c.addresses.forEach { a -> Line(a.oneLine(), labelText(a.label)) }
            c.birthday?.let { b ->
                Header(stringResource(R.string.contacts_field_birthday))
                Line(birthdayText(b.month, b.day, b.year), c.turns?.takeIf { it > 0 }?.let { stringResource(R.string.birthdays_turns_on, it, c.nextBirthday?.let(::shortDate).orEmpty()) })
                if (c.account == null) {
                    Line(
                        if (c.reminderDays.isEmpty()) stringResource(R.string.birthdays_no_reminders)
                        else c.reminderDays.map { reminderLabel(it) }.joinToString(", "),
                        stringResource(R.string.birthdays_reminders),
                    )
                }
            }
            c.anniversary?.let { a ->
                app.klean.feature.contacts.data.ContactVcf.parseIso(a)?.let { b ->
                    Line(birthdayText(b.month, b.day, b.year), stringResource(R.string.contacts_field_anniversary))
                }
            }
            c.notes?.let { Header(stringResource(R.string.contacts_field_notes)); Line(it, null) }
            if (c.more.isNotEmpty()) Header(stringResource(R.string.contacts_field_more))
            c.more.forEach { (k, v) -> Line(v, k) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Line(text: String, label: String?, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(text) },
        supportingContent = label?.takeIf { it.isNotBlank() }?.let { l -> { Text(l) } },
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 48.dp),
    )
}
