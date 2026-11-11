package app.klean.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.settings.about.AboutSettingsScreen
import app.klean.feature.settings.ai.AiProviderScreen
import app.klean.feature.settings.ai.AiProvidersScreen
import app.klean.feature.settings.ai.OnDeviceScreen
import app.klean.feature.settings.appearance.AppearanceSettingsScreen
import app.klean.feature.settings.backup.BackupSettingsScreen
import app.klean.feature.settings.permissions.PermissionsSettingsScreen
import app.klean.feature.settings.quiet.QuietHoursSettingsScreen
import app.klean.shared.ai.ProviderId
import app.klean.core.sync.ui.SyncSettingsRoute
import app.klean.core.sync.ui.SyncSettingsScreen

/** One row of the hub: its label, icon and where it goes. */
private data class HubRow(val key: String, val label: String, val icon: ImageVector, val supporting: String? = null, val onClick: (() -> Unit)?)

/**
 * The settings hub, calm and grouped (docs/ui/design-system.md): Modules (each module's own settings, then turning
 * modules on or off), Klean (quiet hours, appearance, AI, backup, sync, permissions) and Help (about, setup again).
 * One list, on list-group surfaces with a leading icon per row.
 */
@Composable
fun SettingsScreen(modules: List<KleanModule>, onOpen: (Any) -> Unit, onRunSetup: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    val moduleRows = modules.map { m ->
        HubRow(m.id.value, stringResource(m.labelRes), m.icon, onClick = m.settingsRoute?.let { r -> { onOpen(r) } })
    } + HubRow("toggle", stringResource(R.string.settings_modules_toggle), Icons.Rounded.ToggleOn, onClick = { onOpen(ModulesSettingsRoute) })
    val general = listOf(
        HubRow("quiet", stringResource(R.string.settings_quiet_hours), Icons.Rounded.Bedtime, onClick = { onOpen(QuietHoursSettingsRoute) }),
        HubRow("appearance", stringResource(R.string.settings_appearance), Icons.Rounded.Palette, onClick = { onOpen(AppearanceSettingsRoute) }),
        HubRow("ai", stringResource(R.string.settings_ai_providers), Icons.Rounded.AutoAwesome, onClick = { onOpen(AiProvidersSettingsRoute) }),
        HubRow("backup", stringResource(R.string.settings_backup), Icons.Rounded.Backup, onClick = { onOpen(BackupSettingsRoute) }),
        HubRow("sync", stringResource(R.string.settings_sync), Icons.Rounded.Sync, onClick = { onOpen(SyncSettingsRoute) }),
        HubRow("permissions", stringResource(R.string.settings_permissions), Icons.Rounded.Security, onClick = { onOpen(PermissionsSettingsRoute) }),
    )
    val help = listOfNotNull(
        HubRow("about", stringResource(R.string.settings_about), Icons.Rounded.Info, onClick = { onOpen(AboutSettingsRoute) }),
        onRunSetup?.let { HubRow("setup", stringResource(R.string.settings_run_setup), Icons.AutoMirrored.Rounded.HelpOutline, stringResource(R.string.settings_run_setup_body), onClick = it) },
    )
    KleanScreen(title = stringResource(R.string.settings_title), onBack = onBack, showAdd = false) { pad ->
        LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
            item(key = "h_modules") { SectionHeader(stringResource(R.string.settings_section_modules), first = true) }
            listGroup("modules", moduleRows, { it.key }) { HubListRow(it) }
            item(key = "h_general") { SectionHeader(stringResource(R.string.settings_section_general)) }
            listGroup("general", general, { it.key }) { HubListRow(it) }
            item(key = "h_help") { SectionHeader(stringResource(R.string.settings_section_help)) }
            listGroup("help", help, { it.key }) { HubListRow(it) }
        }
    }
}

@Composable
private fun HubListRow(row: HubRow) {
    ListRow(
        title = row.label,
        supporting = row.supporting,
        leading = { Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = row.onClick,
    )
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
