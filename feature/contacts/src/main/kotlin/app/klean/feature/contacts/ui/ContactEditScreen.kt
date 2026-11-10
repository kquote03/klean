package app.klean.feature.contacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.contacts.R
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.PostalAddress
import app.klean.feature.contacts.data.ReminderDays

@Composable
fun ContactEditScreen(onDone: () -> Unit, vm: ContactEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ContactEditContent(state, vm, onBack = onDone)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ContactEditContent(state: ContactEditUiState, vm: ContactEditViewModel, onBack: () -> Unit) {
    val f = state.form
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.contacts_new else R.string.contacts_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.contacts_back))
                    }
                },
                actions = { TextButton(onClick = vm::save, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_save)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = f.name, onValueChange = { v -> vm.edit { it.copy(name = v) } }, singleLine = true,
                label = { Text(stringResource(R.string.contacts_field_name)) },
                isError = state.problem == FormProblem.NAME,
                supportingText = if (state.problem == FormProblem.NAME) ({ Text(stringResource(R.string.contacts_name_needed)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.org, onValueChange = { v -> vm.edit { it.copy(org = v) } }, singleLine = true,
                label = { Text(stringResource(R.string.contacts_field_org)) }, modifier = Modifier.fillMaxWidth(),
            )

            Section(stringResource(R.string.contacts_field_phones))
            f.phones.forEachIndexed { i, p ->
                ValueRow(p, PHONE_LABELS, KeyboardType.Phone, stringResource(R.string.contacts_field_phone), { vm.setPhone(i, it) }, { vm.removePhone(i) })
            }
            OutlinedButton(onClick = vm::addPhone, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_add_phone)) }

            Section(stringResource(R.string.contacts_field_emails))
            f.emails.forEachIndexed { i, e ->
                ValueRow(e, EMAIL_LABELS, KeyboardType.Email, stringResource(R.string.contacts_field_email), { vm.setEmail(i, it) }, { vm.removeEmail(i) })
            }
            OutlinedButton(onClick = vm::addEmail, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_add_email)) }

            if (!state.isOther) {
                Section(stringResource(R.string.contacts_field_addresses))
                f.addresses.forEachIndexed { i, a -> AddressFields(a, { vm.setAddress(i, it) }, { vm.removeAddress(i) }) }
                OutlinedButton(onClick = vm::addAddress, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_add_address)) }
            }

            Section(stringResource(R.string.contacts_field_birthday))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Picker(
                    text = f.month?.let { monthName(it) } ?: stringResource(R.string.contacts_no_birthday),
                    description = stringResource(R.string.contacts_birthday_month),
                    options = listOf<Pair<Int?, String>>(null to stringResource(R.string.contacts_no_birthday)) + (1..12).map { it to monthName(it) },
                    onPick = vm::setBirthdayMonth,
                )
                if (f.month != null) {
                    Picker(
                        text = (f.day ?: 1).toString(), description = stringResource(R.string.contacts_birthday_day),
                        options = (1..daysIn(f.month)).map { it to it.toString() }, onPick = { it?.let(vm::setBirthdayDay) },
                    )
                    OutlinedTextField(
                        value = f.year, onValueChange = vm::setBirthdayYear, singleLine = true,
                        label = { Text(stringResource(R.string.contacts_birthday_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.problem == FormProblem.BIRTHDAY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.problem == FormProblem.BIRTHDAY) {
                Text(stringResource(R.string.contacts_birthday_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (f.month != null && !state.isOther) {
                Text(stringResource(R.string.birthdays_reminders), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderDays.CHOICES.forEach { n ->
                        FilterChip(
                            selected = n in f.reminderDays, onClick = { vm.toggleReminder(n) },
                            label = { Text(reminderLabel(n)) }, modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                if (f.reminderDays.isEmpty()) {
                    Text(stringResource(R.string.birthdays_no_reminders), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = f.notes, onValueChange = { v -> vm.edit { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.contacts_field_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            if (!state.isNew && !state.isOther) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.contacts_delete)) }
            }
            Box(Modifier.heightIn(min = 24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contacts_delete_title, f.name)) },
            text = { Text(stringResource(R.string.contacts_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.contacts_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.contacts_keep)) } },
        )
    }
}

private fun daysIn(month: Int) = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]

@Composable
private fun Section(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun ValueRow(
    v: LabeledValue,
    labels: List<String>,
    type: KeyboardType,
    hint: String,
    onChange: (LabeledValue) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Picker(
            text = labelText(v.label), description = stringResource(R.string.contacts_label_kind),
            options = (labels + listOfNotNull(v.label.takeIf { it !in labels })).map { it to labelText(it) },
            onPick = { onChange(v.copy(label = it.orEmpty())) },
        )
        OutlinedTextField(
            value = v.value, onValueChange = { onChange(v.copy(value = it)) }, singleLine = true,
            label = { Text(hint) }, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.contacts_remove_value, hint))
        }
    }
}

@Composable
private fun AddressFields(a: PostalAddress, onChange: (PostalAddress) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Picker(
                text = labelText(a.label), description = stringResource(R.string.contacts_label_kind),
                options = (EMAIL_LABELS + listOfNotNull(a.label.takeIf { it !in EMAIL_LABELS })).map { it to labelText(it) },
                onPick = { onChange(a.copy(label = it.orEmpty())) },
            )
            Box(Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.contacts_remove_value, stringResource(R.string.contacts_field_address)))
            }
        }
        Field(a.street, R.string.contacts_address_street) { onChange(a.copy(street = it)) }
        Field(a.locality, R.string.contacts_address_city) { onChange(a.copy(locality = it)) }
        Field(a.region, R.string.contacts_address_region) { onChange(a.copy(region = it)) }
        Field(a.postcode, R.string.contacts_address_postcode) { onChange(a.copy(postcode = it)) }
        Field(a.country, R.string.contacts_address_country) { onChange(a.copy(country = it)) }
    }
}

@Composable
private fun Field(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
}

/** A button that opens a menu of [options] (DropdownMenu: ExposedDropdownMenu doesn't resolve with material3 1.5.0-rc01). */
@Composable
private fun <T> Picker(text: String, description: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$description, $text" },
        ) { Text(text) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(v) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}
