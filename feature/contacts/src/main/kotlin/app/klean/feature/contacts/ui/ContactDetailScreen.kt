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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.platform.testTag
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.core.design.theme.Space
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

/**
 * A contact: its details in calm groups (numbers, emails, addresses, birthday, notes), each on one surface. A tap
 * on a number calls, on an email writes. Edit is the top bar's one action.
 */
@Composable
fun ContactDetailContent(
    state: ContactDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
) {
    val c = state.contact
    KleanScreen(
        title = c?.name.orEmpty(),
        onBack = onBack,
        showAdd = false,
        actions = {
            if (c != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.contacts_edit))
                }
            }
        },
    ) { pad ->
        if (c == null) {
            if (state.loaded) EmptyState(stringResource(R.string.contacts_gone), Modifier.padding(pad))
            return@KleanScreen
        }
        val about = listOfNotNull(
            c.account?.let { Line(stringResource(R.string.contacts_in_account, it), null) },
            c.org?.let { Line(it, stringResource(R.string.contacts_field_org)) },
        )
        val groups = buildList {
            add(Group("about", null, about))
            add(Group("phones", stringResource(R.string.contacts_field_phones), c.phones.map { p -> Line(p.value, labelText(p.label), stringResource(R.string.contacts_call, p.value)) { onCall(p.value) } }))
            add(Group("emails", stringResource(R.string.contacts_field_emails), c.emails.map { e -> Line(e.value, labelText(e.label), stringResource(R.string.contacts_write, e.value)) { onEmail(e.value) } }))
            add(Group("addresses", stringResource(R.string.contacts_field_addresses), c.addresses.map { a -> Line(a.oneLine(), labelText(a.label)) }))
            c.birthday?.let { b ->
                val lines = buildList {
                    add(Line(birthdayText(b.month, b.day, b.year), c.turns?.takeIf { it > 0 }?.let { stringResource(R.string.birthdays_turns_on, it, c.nextBirthday?.let(::shortDate).orEmpty()) }))
                    if (c.account == null) {
                        add(
                            Line(
                                if (c.reminderDays.isEmpty()) stringResource(R.string.birthdays_no_reminders) else c.reminderDays.map { reminderLabel(it) }.joinToString(", "),
                                stringResource(R.string.birthdays_reminders),
                            ),
                        )
                    }
                }
                add(Group("birthday", stringResource(R.string.contacts_field_birthday), lines))
            }
            c.anniversary?.let { a -> app.klean.feature.contacts.data.ContactVcf.parseIso(a) }?.let { b ->
                add(Group("anniversary", stringResource(R.string.contacts_field_anniversary), listOf(Line(birthdayText(b.month, b.day, b.year), null))))
            }
            c.notes?.let { add(Group("notes", stringResource(R.string.contacts_field_notes), listOf(Line(it, null, maxLines = Int.MAX_VALUE)))) }
            add(Group("more", stringResource(R.string.contacts_field_more), c.more.map { (k, v) -> Line(v, k) }))
        }.filter { it.lines.isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
            groups.forEachIndexed { i, g -> group(g, first = i == 0) }
        }
    }
}

private data class Group(val key: String, val title: String?, val lines: List<Line>)

/** One detail: the value, what kind it is, and what a tap does ([action], read as [actionLabel]). */
private data class Line(val text: String, val label: String?, val actionLabel: String? = null, val maxLines: Int = 2, val action: (() -> Unit)? = null)

private fun LazyListScope.group(g: Group, first: Boolean) {
    if (g.title != null) item(key = "h_${g.key}") { SectionHeader(g.title, first = first) }
    listGroup(g.key, g.lines.withIndex().toList(), { it.index }) { (_, l) ->
        ListRow(
            title = l.text,
            supporting = l.label?.takeIf { it.isNotBlank() },
            titleMaxLines = l.maxLines,
            onClick = l.action,
            onClickLabel = l.actionLabel,
        )
    }
}
