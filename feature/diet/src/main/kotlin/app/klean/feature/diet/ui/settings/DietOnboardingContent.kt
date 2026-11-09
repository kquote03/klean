package app.klean.feature.diet.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.diet.R

/** The diet page of the setup wizard: the main Diet settings switches, written as they change. */
@Composable
fun DietOnboardingContent(modifier: Modifier = Modifier, vm: DietSettingsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Column(modifier) {
        Text(
            stringResource(R.string.diet_onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ToggleRow(
            title = stringResource(R.string.diet_settings_prompts),
            summary = stringResource(R.string.diet_settings_prompts_summary),
            checked = state.promptsEnabled,
            onChange = { vm.setPromptsEnabled(it) },
        )
        ToggleRow(
            title = stringResource(R.string.diet_settings_learn),
            summary = stringResource(R.string.diet_settings_learn_summary),
            checked = state.learnMealTimes,
            enabled = state.promptsEnabled,
            onChange = { vm.setLearnMealTimes(it) },
        )
        ToggleRow(
            title = stringResource(R.string.diet_settings_status_line),
            summary = stringResource(R.string.diet_settings_status_line_summary),
            checked = state.statusNotification,
            onChange = { vm.setStatusNotification(it) },
        )
        ToggleRow(
            title = stringResource(R.string.diet_settings_unlock_sheet),
            summary = stringResource(R.string.diet_settings_unlock_sheet_summary),
            checked = state.unlockSheet,
            onChange = { vm.setUnlockSheet(it) },
        )
        ToggleRow(
            title = stringResource(R.string.diet_settings_calories),
            summary = stringResource(R.string.diet_settings_calories_summary),
            checked = state.showCalories,
            onChange = { vm.setShowCalories(it) },
        )
    }
}
