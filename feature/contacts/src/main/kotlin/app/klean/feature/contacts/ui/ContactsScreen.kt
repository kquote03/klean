package app.klean.feature.contacts.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.core.design.theme.Space
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

/**
 * People › Contacts (Soft cards): the next birthday in a tertiary hero, then everyone as one list. Search opens
 * from the top bar; New contact, Birthdays and Contacts settings are in the overflow. Birthdays is a view of
 * the same screen ("All people" goes back), as the deep link `?tab=birthdays` opens it. A merge offer is one
 * row that opens its choices.
 */
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
    var searching by rememberSaveable { mutableStateOf(state.query.isNotBlank()) }
    var merging by rememberSaveable { mutableStateOf(false) }
    val shown = if (!state.birthdaysOn) ContactsTab.CONTACTS else tab
    val menu = buildList {
        // New contact is the Add menu's last line in People (plan 2026-11-11, D2), so it isn't here too.
        if (state.birthdaysOn && shown == ContactsTab.CONTACTS) add(MenuAction(stringResource(R.string.birthdays_label)) { tab = ContactsTab.BIRTHDAYS })
        add(MenuAction(stringResource(R.string.contacts_settings_title), onClick = onSettings))
    }
    KleanScreen(
        title = stringResource(R.string.contacts_label),
        menu = menu,
        actions = {
            if (shown == ContactsTab.CONTACTS) {
                IconButton(
                    onClick = {
                        if (searching) onQuery("")
                        searching = !searching
                    },
                    modifier = Modifier.size(Space.touchTarget),
                ) {
                    Icon(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(if (searching) R.string.contacts_search_close else R.string.contacts_search_open),
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
            when (shown) {
                ContactsTab.CONTACTS -> people(state, searching, onQuery, onOpen, onNew, onAllow, onBirthdays = { tab = ContactsTab.BIRTHDAYS }, onMergeOffer = { merging = true })
                ContactsTab.BIRTHDAYS -> birthdays(state, onOpen, onBack = { tab = ContactsTab.CONTACTS })
            }
        }
    }
    if (merging && state.mergePairs.isNotEmpty()) {
        MergeDialog(state.mergePairs, onMerge, onKeepApart, onNotNow = { merging = false; onNotNow() }, onDismiss = { merging = false })
    }
}

private fun LazyListScope.people(
    state: ContactsUiState,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onOpen: (String?, Long?) -> Unit,
    onNew: () -> Unit,
    onAllow: () -> Unit,
    onBirthdays: () -> Unit,
    onMergeOffer: () -> Unit,
) {
    if (searching) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.contacts_search)) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs),
            )
        }
    }
    val next = state.birthdays.comingUp.firstOrNull()
    if (state.birthdaysOn && next != null && !searching) {
        item(key = "hero") {
            ModuleCard(
                label = stringResource(R.string.birthdays_coming_up),
                fact = next.name,
                supporting = birthdayLine(next),
                tone = CardTone.TERTIARY,
                onClick = onBirthdays,
                onClickLabel = stringResource(R.string.birthdays_label),
            )
        }
    }
    if (state.mergePairs.isNotEmpty() && !searching) {
        item(key = "merge") {
            val p = state.mergePairs.first()
            Box(Modifier.fillMaxWidth().padding(top = Space.cardGap).groupSurface()) {
                ListRow(
                    title = stringResource(R.string.contacts_merge_title),
                    supporting = pairText(p) + if (state.mergePairs.size > 1) " " + pluralStringResource(R.plurals.contacts_merge_more, state.mergePairs.size - 1, state.mergePairs.size - 1) else "",
                    leading = { Icon(Icons.Rounded.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = onMergeOffer,
                )
            }
        }
    }
    val everyone = state.sections.flatMap { it.rows }
    if (state.loaded && everyone.isEmpty() && state.other.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                stringResource(if (state.query.isBlank()) R.string.contacts_empty else R.string.contacts_no_match),
                icon = Icons.Rounded.People,
                action = if (state.query.isBlank()) MenuAction(stringResource(R.string.contacts_new), onClick = onNew) else null,
            )
        }
    }
    if (everyone.isNotEmpty()) {
        item(key = "h_everyone") { SectionHeader(stringResource(R.string.contacts_everyone), first = searching) }
        listGroup("everyone", everyone, { it.key }) { PersonRow(it, onOpen) }
    }
    state.other.forEach { s ->
        item(key = "h_account:${s.header}") { SectionHeader(stringResource(R.string.contacts_in_account, s.header)) }
        listGroup("account:${s.header}", s.rows, { it.key }) { PersonRow(it, onOpen) }
    }
    if (state.loaded && !state.canReadPhone) {
        item(key = "access") {
            Box(Modifier.fillMaxWidth().padding(top = Space.sectionGap).groupSurface()) {
                ListRow(
                    title = stringResource(R.string.contacts_access_title),
                    supporting = stringResource(R.string.contacts_access_body),
                    onClick = onAllow,
                )
            }
        }
    }
}

private fun LazyListScope.birthdays(state: ContactsUiState, onOpen: (String?, Long?) -> Unit, onBack: () -> Unit) {
    val b = state.birthdays
    item(key = "h_birthdays") {
        SectionHeader(stringResource(R.string.birthdays_label), first = true, action = MenuAction(stringResource(R.string.contacts_all_people), onClick = onBack))
    }
    if (state.loaded && b.isEmpty) {
        item(key = "b_empty") { EmptyState(stringResource(R.string.birthdays_empty), icon = Icons.Rounded.Cake) }
    }
    if (b.comingUp.isNotEmpty()) {
        item(key = "h_soon") { SectionHeader(stringResource(R.string.birthdays_coming_up)) }
        listGroup("soon", b.comingUp, { it.contactId }) { BirthdayListRow(it, onOpen) }
    }
    b.byMonth.forEach { (month, rows) ->
        item(key = "h_m:$month") { SectionHeader(monthName(month)) }
        listGroup("m:$month", rows, { it.contactId }) { BirthdayListRow(it, onOpen) }
    }
}

@Composable
private fun birthdayLine(r: BirthdayRow): String {
    val whenText = whenLabel(r.date, r.daysUntil)
    return r.age?.takeIf { it > 0 }?.let { stringResource(R.string.birthdays_turns_when, it, whenText) } ?: whenText
}

@Composable
private fun pairText(p: DuplicatePair): String =
    if (p.b.accountName != null) stringResource(R.string.contacts_merge_pair_account, p.a.name, p.b.accountName)
    else stringResource(R.string.contacts_merge_pair, p.a.name, p.b.name)

@Composable
private fun Modifier.groupSurface(): Modifier = this.then(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large))

@Composable
private fun PersonRow(r: ContactRow, onOpen: (String?, Long?) -> Unit) {
    ListRow(
        title = r.name,
        supporting = r.detail,
        titleMaxLines = 1,
        leading = { Avatar(r.name) },
        onClick = { onOpen(r.contactId, r.rawId) },
    )
}

@Composable
private fun BirthdayListRow(r: BirthdayRow, onOpen: (String?, Long?) -> Unit) {
    ListRow(
        title = r.name,
        supporting = r.age?.takeIf { it > 0 }?.let { stringResource(R.string.birthdays_turns, it) },
        meta = whenLabel(r.date, r.daysUntil),
        titleMaxLines = 1,
        leading = { Avatar(r.name) },
        onClick = { onOpen(r.contactId, null) },
    )
}

/** The first letter on a tonal disc (People's accent). */
@Composable
private fun Avatar(name: String) {
    Box(
        Modifier.size(Space.xl + Space.xs).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(ContactLists.initial(name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

/** The merge offer's choices: each pair with Merge (the one tonal button) and Keep apart; Not now sets them aside. */
@Composable
private fun MergeDialog(
    pairs: List<DuplicatePair>,
    onMerge: (DuplicatePair) -> Unit,
    onKeepApart: (DuplicatePair) -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contacts_merge_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.contacts_merge_body), style = MaterialTheme.typography.bodyMedium)
                pairs.take(MERGE_SHOWN).forEach { p ->
                    Text(pairText(p), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        FilledTonalButton(onClick = { onMerge(p) }, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(stringResource(R.string.contacts_merge_one)) }
                        TextButton(onClick = { onKeepApart(p) }, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(stringResource(R.string.contacts_merge_keep)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.contacts_merge_not_now)) } },
    )
}

private const val MERGE_SHOWN = 3
