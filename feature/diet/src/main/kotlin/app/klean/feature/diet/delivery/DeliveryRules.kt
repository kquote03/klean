package app.klean.feature.diet.delivery

import android.app.Notification
import app.klean.core.common.ApplicationScope
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.feature.diet.ui.settings.DietDebug
import app.klean.shared.diet.DeliveryMatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Which notifications count as "your food arrived" (plan §6.2). [isDelivery] takes the text as a
 * lambda so a notification from any other package is never read at all.
 */
data class DeliveryRules(val packages: Set<String>, val words: List<String>) {
    fun isDelivery(pkg: String, text: () -> String): Boolean {
        if (pkg !in packages) return false
        val t = text()
        return DeliveryMatcher.isDelivered(t) || words.any { w -> w.trim().let { it.isNotEmpty() && t.contains(it, ignoreCase = true) } }
    }

    companion object {
        fun of(apps: List<String>, words: List<String>, extraPackages: Set<String>) = DeliveryRules(
            packages = apps.map(String::trim).filter(String::isNotEmpty).toSet() + extraPackages,
            words = words.map(String::trim).filter(String::isNotEmpty),
        )
    }
}

/** The current rules, read synchronously from the listener's callback (no disk access there). */
fun interface DeliveryRulesSource {
    fun current(): DeliveryRules
}

/** Settings `diet.deliveryApps` + `diet.deliveryMatchWords`, plus debug-only packages, cached as a StateFlow. */
@Singleton
class SettingsDeliveryRules @Inject constructor(
    settings: SettingsRepository,
    debug: DietDebug,
    @ApplicationScope scope: CoroutineScope,
) : DeliveryRulesSource {
    private val extra = debug.extraDeliveryPackages
    val rules: StateFlow<DeliveryRules> = combine(
        settings.observe(SyncedKeys.DietDeliveryApps),
        settings.observe(SyncedKeys.DietDeliveryMatchWords),
    ) { apps, words -> DeliveryRules.of(apps, words, extra) }
        .stateIn(scope, SharingStarted.Eagerly, DeliveryRules.of(DeliveryMatcher.DEFAULT_APPS, emptyList(), extra))

    override fun current(): DeliveryRules = rules.value
}

/** Joins what a notification says, for matching only. Never stored or logged. */
object DeliveryText {
    fun of(n: Notification): String {
        val e = n.extras ?: return ""
        return listOfNotNull(
            e.getCharSequence(Notification.EXTRA_TITLE),
            e.getCharSequence(Notification.EXTRA_TEXT),
            e.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString("\n")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryRulesModule {
    @Binds abstract fun source(impl: SettingsDeliveryRules): DeliveryRulesSource
    @Binds abstract fun prompts(impl: DeliveryPrompter): DeliveryPrompts
}
