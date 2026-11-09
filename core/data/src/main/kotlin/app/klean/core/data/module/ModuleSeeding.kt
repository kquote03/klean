package app.klean.core.data.module

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Ids the user has been offered at least once. Synced so every device agrees. */
val KnownModules = SettingKey("core.modules.known", emptySet<String>(), SetSerializer(String.serializer()))

/** [known] and [disabled] to store; [pending]: new modules an existing install should hear about once. */
data class SeedResult(val known: Set<String>, val disabled: Set<String>, val pending: Set<String>)

/**
 * Klean is holistic, so modules start switched on (holistic plan H6), on a fresh install and when an
 * update adds one. The difference is what the user is told: a fresh install sees every module in the
 * setup wizard; an existing install ([known] was non-empty) lists the new ones once ("New in Klean").
 *
 * Ids already known are left alone, so a module the user switched off stays off.
 */
fun seedNewModules(
    registered: Set<String>,
    known: Set<String>,
    disabled: Set<String>,
    neverListed: Set<String> = setOf("core"),
): SeedResult {
    val fresh = registered - known
    val pending = if (known.isEmpty()) emptySet() else fresh - neverListed
    return SeedResult(known + fresh, disabled, pending)
}
