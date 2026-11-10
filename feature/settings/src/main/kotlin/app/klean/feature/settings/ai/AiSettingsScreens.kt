package app.klean.feature.settings.ai

import app.klean.core.design.component.SwitchListItem
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedCard
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId

@StringRes fun providerName(id: ProviderId): Int = when (id) {
    ProviderId.GEMINI -> R.string.ai_provider_gemini
    ProviderId.OPENAI_COMPAT -> R.string.ai_provider_openai
    ProviderId.ON_DEVICE -> R.string.ai_provider_on_device
}

@StringRes private fun taskLabel(t: AiTask): Int = when (t) {
    AiTask.ENTRY_PARSE -> R.string.ai_task_entry_parse
    AiTask.CALORIE_ESTIMATE -> R.string.ai_task_calories
    AiTask.CAPTURE_ROUTING -> R.string.ai_task_capture_routing
    AiTask.DELEGATION -> R.string.ai_task_delegation
}

@Composable
fun AiProvidersScreen(onBack: () -> Unit, onOpen: (ProviderId) -> Unit, vm: AiProvidersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    SettingsSubScaffold(R.string.settings_ai_providers, onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ai_intro), style = MaterialTheme.typography.bodyMedium)
            SETTABLE_PROVIDERS.forEach { id ->
                ProviderCard(
                    title = stringResource(providerName(id)),
                    summary = stringResource(
                        when (id) {
                            ProviderId.GEMINI -> R.string.ai_gemini_summary
                            ProviderId.OPENAI_COMPAT -> R.string.ai_openai_summary
                            ProviderId.ON_DEVICE -> R.string.ai_on_device_summary
                        },
                    ),
                    status = stringResource(if (s.statusOf(id)) R.string.ai_status_on else R.string.ai_status_off),
                    onClick = { onOpen(id) },
                )
            }
            Text(
                stringResource(R.string.ai_use_for),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            AiTask.entries.forEach { task ->
                TaskPicker(
                    label = stringResource(taskLabel(task)),
                    selected = s.assignments[task],
                    options = s.options,
                    onPick = { vm.assign(task, it) },
                )
                if (task == AiTask.CAPTURE_ROUTING) {
                    Text(
                        stringResource(R.string.ai_task_capture_routing_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.options.isEmpty()) {
                Text(
                    stringResource(R.string.ai_use_for_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(title: String, summary: String, status: String, onClick: (() -> Unit)?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = { Text(status, style = MaterialTheme.typography.labelLarge) },
        )
    }
}

@Composable
private fun TaskPicker(label: String, selected: ProviderId?, options: List<ProviderId>, onPick: (ProviderId?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = selected?.let { stringResource(providerName(it)) } ?: stringResource(R.string.ai_none)
    Box {
        ListItem(
            modifier = Modifier.clickable(role = Role.DropdownList) { open = true },
            headlineContent = { Text(label) },
            supportingContent = { Text(current) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.ai_none)) }, onClick = { onPick(null); open = false })
            options.forEach { id ->
                DropdownMenuItem(text = { Text(stringResource(providerName(id))) }, onClick = { onPick(id); open = false })
            }
        }
    }
}

@Composable
fun AiProviderScreen(onBack: () -> Unit, vm: AiProviderViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val gemini = s.provider == ProviderId.GEMINI
    val leave = {
        vm.flush()
        onBack()
    }
    androidx.activity.compose.BackHandler(onBack = leave)
    SettingsSubScaffold(providerName(s.provider), leave) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.ai_enable),
                summary = stringResource(if (gemini) R.string.ai_gemini_consent else R.string.ai_openai_consent),
                checked = s.enabled,
                onChange = { vm.setEnabled(it) },
            )
            if (!gemini) {
                OutlinedTextField(
                    value = s.baseUrl,
                    onValueChange = vm::onBaseUrlChange,
                    label = { Text(stringResource(R.string.ai_base_url)) },
                    supportingText = { Text(stringResource(R.string.ai_base_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = s.model,
                onValueChange = vm::onModelChange,
                label = { Text(stringResource(R.string.ai_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KeyField(
                label = stringResource(if (gemini) R.string.ai_api_key else R.string.ai_api_key_optional),
                input = s.keyInput,
                hasKey = s.hasKey,
                onChange = vm::onKeyChange,
                onSave = { vm.saveKey() },
                onRemove = { vm.removeKey() },
            )
            if (gemini) {
                SwitchRow(stringResource(R.string.ai_send_photos), stringResource(R.string.ai_send_photos_summary), s.sendPhotos) { vm.setSendPhotos(it) }
            } else {
                SwitchRow(stringResource(R.string.ai_accepts_images), stringResource(R.string.ai_accepts_images_summary), s.acceptsImages) {
                    vm.setAcceptsImages(it)
                }
            }
            OutlinedButton(onClick = { vm.testConnection() }, enabled = s.test != TestResult.Running) {
                Text(stringResource(R.string.ai_test))
            }
            when (val t = s.test) {
                null -> {}
                TestResult.Running -> Text(stringResource(R.string.ai_test_running), style = MaterialTheme.typography.bodyMedium)
                is TestResult.Ok -> Text(stringResource(R.string.ai_test_ok, t.summary), style = MaterialTheme.typography.bodyMedium)
                is TestResult.Error -> Text(
                    stringResource(t.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun KeyField(label: String, input: String, hasKey: Boolean, onChange: (String) -> Unit, onSave: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { if (hasKey) Text(stringResource(R.string.ai_key_saved_placeholder)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = input.isNotBlank()) { Text(stringResource(R.string.ai_key_save)) }
            if (hasKey) TextButton(onClick = onRemove) { Text(stringResource(R.string.ai_key_remove)) }
        }
        Text(
            stringResource(if (hasKey) R.string.ai_key_stored else R.string.ai_key_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchListItem(checked = checked, onCheckedChange = onChange, headline = { Text(title) }, supporting = { Text(summary) })
}
