package app.klean.feature.settings

import kotlinx.serialization.Serializable

@Serializable object ModulesSettingsRoute

@Serializable object QuietHoursSettingsRoute

// Stubs: each is replaced by a real screen in a later milestone.
@Serializable object AppearanceSettingsRoute
@Serializable object AiProvidersSettingsRoute
/** [provider] is a [app.klean.shared.ai.ProviderId] name. */
@Serializable data class AiProviderSettingsRoute(val provider: String)
@Serializable object OnDeviceSettingsRoute
@Serializable object BackupSettingsRoute
@Serializable object PermissionsSettingsRoute
@Serializable object AboutSettingsRoute
