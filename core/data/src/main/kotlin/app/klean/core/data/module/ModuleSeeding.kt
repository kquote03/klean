package app.klean.core.data.module

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Ids the user has been offered at least once. Synced so every device agrees. */
val KnownModules = SettingKey("core.modules.known", emptySet<String>(), SetSerializer(String.serializer()))

/**
 * Modules added after an install start switched off, so an update never fills the app with things
 * the user didn't ask for. Diet is the original module and is never seeded off.
 *
 * Returns the new (known, disabled) sets. Ids already known are left alone, so a module the user
 * turned on stays on.
 */
fun seedNewModules(
    registered: Set<String>,
    known: Set<String>,
    disabled: Set<String>,
    alwaysOn: Set<String> = setOf("diet", "core"),
): Pair<Set<String>, Set<String>> {
    val fresh = registered - known
    return (known + fresh) to (disabled + (fresh - alwaysOn))
}
