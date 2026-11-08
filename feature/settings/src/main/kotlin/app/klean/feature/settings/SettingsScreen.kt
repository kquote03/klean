package app.klean.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry

private data class GeneralRow(@StringRes val label: Int, val route: Any)

private val generalRows = listOf(
    GeneralRow(R.string.settings_quiet_hours, QuietHoursSettingsRoute),
    GeneralRow(R.string.settings_appearance, AppearanceSettingsRoute),
    GeneralRow(R.string.settings_ai_providers, AiProvidersSettingsRoute),
    GeneralRow(R.string.settings_backup, BackupSettingsRoute),
    GeneralRow(R.string.settings_permissions, PermissionsSettingsRoute),
    GeneralRow(R.string.settings_about, AboutSettingsRoute),
)

/** Top-level settings list. Modules section is driven by the registry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modules: List<KleanModule>, onOpen: (Any) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(R.string.settings_section_modules) }
            items(modules, key = { it.id.value }) { module ->
                val route = module.settingsRoute
                SettingsRow(
                    label = stringResource(module.labelRes),
                    onClick = route?.let { r -> { onOpen(r) } },
                )
            }
            item {
                SettingsRow(stringResource(R.string.settings_modules_toggle), onClick = { onOpen(ModulesSettingsRoute) })
            }
            item { SectionHeader(R.string.settings_section_general) }
            items(generalRows, key = { it.label }) { row ->
                SettingsRow(stringResource(row.label), onClick = { onOpen(row.route) })
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(label: String, onClick: (() -> Unit)?) {
    ListItem(modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) { Text(label) }
}

/** Registers the settings sub-screens under the app's NavHost. */
fun NavGraphBuilder.settingsGraph(nav: NavController, registry: ModuleRegistry) {
    val back: () -> Unit = { nav.popBackStack() }
    composable<ModulesSettingsRoute> { ModulesSettingsScreen(registry = registry, onBack = back) }
    composable<QuietHoursSettingsRoute> { StubSettingsScreen(R.string.settings_quiet_hours, back) }
    composable<AppearanceSettingsRoute> { StubSettingsScreen(R.string.settings_appearance, back) }
    composable<AiProvidersSettingsRoute> { StubSettingsScreen(R.string.settings_ai_providers, back) }
    composable<BackupSettingsRoute> { StubSettingsScreen(R.string.settings_backup, back) }
    composable<PermissionsSettingsRoute> { StubSettingsScreen(R.string.settings_permissions, back) }
    composable<AboutSettingsRoute> { StubSettingsScreen(R.string.settings_about, back) }
}
