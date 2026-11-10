package app.klean.feature.settings

import app.klean.feature.settings.ai.AiProviderScreen
import app.klean.feature.settings.ai.AiProvidersScreen
import app.klean.feature.settings.ai.OnDeviceScreen
import app.klean.shared.ai.ProviderId

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
import androidx.navigation.navDeepLink
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.feature.settings.about.AboutSettingsScreen
import app.klean.feature.settings.appearance.AppearanceSettingsScreen
import app.klean.feature.settings.backup.BackupSettingsScreen
import app.klean.feature.settings.permissions.PermissionsSettingsScreen
import app.klean.feature.settings.quiet.QuietHoursSettingsScreen
import app.klean.core.sync.ui.SyncSettingsRoute
import app.klean.core.sync.ui.SyncSettingsScreen

private data class GeneralRow(@StringRes val label: Int, val route: Any)

private val generalRows = listOf(
    GeneralRow(R.string.settings_quiet_hours, QuietHoursSettingsRoute),
    GeneralRow(R.string.settings_appearance, AppearanceSettingsRoute),
    GeneralRow(R.string.settings_ai_providers, AiProvidersSettingsRoute),
    GeneralRow(R.string.settings_backup, BackupSettingsRoute),
    GeneralRow(R.string.settings_sync, SyncSettingsRoute),
    GeneralRow(R.string.settings_permissions, PermissionsSettingsRoute),
    GeneralRow(R.string.settings_about, AboutSettingsRoute),
)

/** Top-level settings list. Modules section is driven by the registry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modules: List<KleanModule>, onOpen: (Any) -> Unit, onRunSetup: (() -> Unit)? = null) {
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
            if (onRunSetup != null) {
                item { SettingsRow(stringResource(R.string.settings_run_setup), onClick = onRunSetup) }
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
fun NavGraphBuilder.settingsGraph(nav: NavController, registry: ModuleRegistry, permissions: PermissionChecker) {
    val back: () -> Unit = { nav.popBackStack() }
    composable<ModulesSettingsRoute> { ModulesSettingsScreen(registry = registry, onBack = back) }
    composable<QuietHoursSettingsRoute> { QuietHoursSettingsScreen(onBack = back) }
    composable<AppearanceSettingsRoute> { AppearanceSettingsScreen(onBack = back) }
    composable<AiProvidersSettingsRoute> {
        AiProvidersScreen(
            onBack = back,
            onOpen = { if (it == ProviderId.ON_DEVICE) nav.navigate(OnDeviceSettingsRoute) else nav.navigate(AiProviderSettingsRoute(it.name)) },
        )
    }
    composable<OnDeviceSettingsRoute> { OnDeviceScreen(onBack = back) }
    composable<AiProviderSettingsRoute> { AiProviderScreen(onBack = back) }
    composable<BackupSettingsRoute> { BackupSettingsScreen(onBack = back) }
    composable<SyncSettingsRoute>(deepLinks = listOf(navDeepLink { uriPattern = "klean://settings/sync" })) { SyncSettingsScreen(onBack = back) }
    composable<PermissionsSettingsRoute>(deepLinks = listOf(navDeepLink { uriPattern = "klean://settings/permissions" })) {
        PermissionsSettingsScreen(checker = permissions, onBack = back)
    }
    composable<AboutSettingsRoute> { AboutSettingsScreen(onBack = back) }
}
