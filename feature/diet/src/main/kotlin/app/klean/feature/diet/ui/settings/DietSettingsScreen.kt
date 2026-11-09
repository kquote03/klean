package app.klean.feature.diet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.diet.R

@Composable
fun DietSettingsScreen(onBack: () -> Unit, onOpenDeliveryApps: () -> Unit = {}, vm: DietSettingsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DietSettingsContent(
        state = state,
        onBack = onBack,
        onPrompts = { vm.setPromptsEnabled(it) },
        onLearn = { vm.setLearnMealTimes(it) },
        onCalories = { vm.setShowCalories(it) },
        onStatusLine = { vm.setStatusNotification(it) },
        onUnlockSheet = { vm.setUnlockSheet(it) },
        onPretendLongBreak = { vm.pretendLongBreak() },
        onOpenDeliveryApps = onOpenDeliveryApps,
        onFireNow = { vm.fireNow() },
        onShowNext = { vm.showNext() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietSettingsContent(
    state: DietSettingsState,
    onBack: () -> Unit,
    onPrompts: (Boolean) -> Unit,
    onLearn: (Boolean) -> Unit,
    onCalories: (Boolean) -> Unit,
    onFireNow: () -> Unit,
    onStatusLine: (Boolean) -> Unit = {},
    onShowNext: () -> Unit,
    onUnlockSheet: (Boolean) -> Unit = {},
    onPretendLongBreak: () -> Unit = {},
    onOpenDeliveryApps: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diet_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ToggleRow(
                title = stringResource(R.string.diet_settings_prompts),
                summary = state.nextCheckIn?.let { stringResource(R.string.diet_settings_next_check_in, it) }
                    ?: stringResource(if (state.promptsEnabled) R.string.diet_settings_prompts_summary else R.string.diet_settings_prompts_off),
                checked = state.promptsEnabled,
                onChange = onPrompts,
            )
            ToggleRow(
                title = stringResource(R.string.diet_settings_learn),
                summary = stringResource(R.string.diet_settings_learn_summary),
                checked = state.learnMealTimes,
                enabled = state.promptsEnabled,
                onChange = onLearn,
            )
            ToggleRow(
                title = stringResource(R.string.diet_settings_status_line),
                summary = stringResource(R.string.diet_settings_status_line_summary),
                checked = state.statusNotification,
                onChange = onStatusLine,
            )
            ToggleRow(
                title = stringResource(R.string.diet_settings_unlock_sheet),
                summary = stringResource(R.string.diet_settings_unlock_sheet_summary),
                checked = state.unlockSheet,
                onChange = onUnlockSheet,
            )
            ToggleRow(
                title = stringResource(R.string.diet_settings_calories),
                summary = stringResource(R.string.diet_settings_calories_summary),
                checked = state.showCalories,
                onChange = onCalories,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.delivery_apps_title)) },
                supportingContent = { Text(stringResource(R.string.delivery_apps_summary)) },
                modifier = Modifier.clickable(onClick = onOpenDeliveryApps),
            )
            if (state.debug) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.diet_debug_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OutlinedButton(onClick = onFireNow, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.diet_debug_fire))
                }
                OutlinedButton(onClick = onShowNext, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.diet_debug_show_next))
                }
                OutlinedButton(onClick = onPretendLongBreak, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.diet_debug_pretend_long_break))
                }
                if (state.pretendArmed) {
                    Text(
                        stringResource(R.string.diet_debug_pretend_long_break_done),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                state.debugInfo?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun ToggleRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}
