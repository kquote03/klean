package app.klean.feature.contacts.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.contacts.ContactsLinks
import app.klean.feature.contacts.R
import app.klean.feature.contacts.merge.DuplicatePair

@Composable
fun ContactsScreen(
    initialTab: String?,
    onOpen: (String?, Long?) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onAccessResult() }
    ContactsContent(
        state = state,
        initialTab = initialTab,
        onQuery = vm::setQuery,
        onOpen = onOpen,
        onNew = onNew,
        onSettings = onSettings,
        onAllow = { ask.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
        onMerge = vm::merge,
        onKeepApart = vm::keepApart,
        onNotNow = vm::notNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsContent(
    state: ContactsUiState,
    initialTab: String?,
    onQuery: (String) -> Unit,
    onOpen: (String?, Long?) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onAllow: () -> Unit,
    onMerge: (DuplicatePair) -> Unit,
    onKeepApart: (DuplicatePair) -> Unit,
    onNotNow: () -> Unit,
) {
    var tab by rememberSaveable {
        mutableStateOf(if (initialTab.equals(ContactsLinks.TAB_BIRTHDAYS, true)) ContactsTab.BIRTHDAYS else ContactsTab.CONTACTS)
    }
    val shown = if (!state.birthdaysOn) ContactsTab.CONTACTS else tab
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contacts_label)) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.contacts_settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.contacts_new)) }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.birthdaysOn) {
                PrimaryTabRow(selectedTabIndex = shown.ordinal) {
                    ContactsTab.entries.forEach { t ->
                        Tab(
                            selected = shown == t,
                            onClick = { tab = t },
                            text = { Text(stringResource(if (t == ContactsTab.CONTACTS) R.string.contacts_tab_people else R.string.birthdays_label)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            when (shown) {
                ContactsTab.CONTACTS -> PeopleTab(state, onQuery, onOpen, onAllow, onMerge, onKeepApart, onNotNow)
                ContactsTab.BIRTHDAYS -> BirthdaysTab(state, onOpen)
            }
        }
    }
}

@Composable
private fun PeopleTab(
    state: ContactsUiState,
    onQuery: (String) -> Unit,
    onOpen: (String?, Long?) -> Unit,
    onAllow: () -> Unit,
    onMerge: (DuplicatePair) -> Unit,
    onKeepApart: (DuplicatePair) -> Unit,
    onNotNow: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = app.klean.core.design.component.FabClearance) {
        item {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.contacts_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (state.mergePairs.isNotEmpty()) item { MergeCard(state.mergePairs, onMerge, onKeepApart, onNotNow) }
        if (state.loaded && state.sections.isEmpty() && state.other.isEmpty()) {
            item { NeutralEmpty(stringResource(if (state.query.isBlank()) R.string.contacts_empty else R.string.contacts_no_match)) }
        }
        sections(state.sections, onOpen)
        state.other.forEach { s ->
            item(key = "account:${s.header}") { Header(stringResource(R.string.contacts_in_account, s.header)) }
            items(s.rows, key = { it.key }) { Row(it, onOpen) }
        }
        if (state.loaded && !state.canReadPhone) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.contacts_access_title)) },
                    supportingContent = { Text(stringResource(R.string.contacts_access_body)) },
                    modifier = Modifier.clickable(onClick = onAllow).heightIn(min = 48.dp),
                )
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 88.dp)) }
    }
}

private fun LazyListScope.sections(sections: List<ContactSection>, onOpen: (String?, Long?) -> Unit) {
    sections.forEach { s ->
        item(key = "h:${s.header}") { Header(s.header) }
        items(s.rows, key = { it.key }) { Row(it, onOpen) }
    }
}

@Composable
private fun Row(r: ContactRow, onOpen: (String?, Long?) -> Unit) {
    ListItem(
        headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = r.detail?.let { d -> { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.clickable { onOpen(r.contactId, r.rawId) }.heightIn(min = 56.dp),
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MergeCard(pairs: List<DuplicatePair>, onMerge: (DuplicatePair) -> Unit, onKeepApart: (DuplicatePair) -> Unit, onNotNow: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.contacts_merge_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.contacts_merge_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pairs.take(5).forEach { p ->
                Text(
                    if (p.b.accountName != null) stringResource(R.string.contacts_merge_pair_account, p.a.name, p.b.accountName)
                    else stringResource(R.string.contacts_merge_pair, p.a.name, p.b.name),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onMerge(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_merge_one)) }
                    TextButton(onClick = { onKeepApart(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_merge_keep)) }
                }
            }
            if (pairs.size > 5) {
                Text(pluralStringResource(R.plurals.contacts_merge_more, pairs.size - 5, pairs.size - 5), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_merge_not_now)) }
        }
    }
}

@Composable
private fun BirthdaysTab(state: ContactsUiState, onOpen: (String?, Long?) -> Unit) {
    val b = state.birthdays
    LazyColumn(Modifier.fillMaxSize(), contentPadding = app.klean.core.design.component.FabClearance) {
        if (state.loaded && b.isEmpty) item { NeutralEmpty(stringResource(R.string.birthdays_empty)) }
        if (b.comingUp.isNotEmpty()) {
            item(key = "soon") { Header(stringResource(R.string.birthdays_coming_up)) }
            items(b.comingUp, key = { "soon:${it.contactId}" }) { BirthdayRowItem(it, onOpen) }
        }
        b.byMonth.forEach { (month, rows) ->
            item(key = "m:$month") { Header(monthName(month)) }
            items(rows, key = { "m:${it.contactId}" }) { BirthdayRowItem(it, onOpen) }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 88.dp)) }
    }
}

@Composable
private fun BirthdayRowItem(r: BirthdayRow, onOpen: (String?, Long?) -> Unit) {
    ListItem(
        headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = r.age?.takeIf { it > 0 }?.let { a -> { Text(stringResource(R.string.birthdays_turns, a)) } },
        trailingContent = { Text(whenLabel(r.date, r.daysUntil), style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.clickable { onOpen(r.contactId, null) }.heightIn(min = 56.dp),
    )
}
