package app.klean.feature.diet.delivery

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.notifications.QuietHoursService
import app.klean.feature.diet.notification.PromptPhrasings
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.prompt.PromptPresenter
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import app.klean.shared.time.MINUTE_MS
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A delivery from [pkg] just arrived. Returns whether the user was asked. */
fun interface DeliveryPrompts {
    suspend fun onDelivered(pkg: String): Boolean
}

/** `deliveryOffer` values in DevicePrefs (plan §3). */
object DeliveryOffer {
    const val NOT_OFFERED = "NOT_OFFERED"
    const val ACCEPTED = "ACCEPTED"
    const val DECLINED = "DECLINED"
    const val SNOOZED_PREFIX = "SNOOZED_UNTIL:"

    fun snoozedUntil(ms: Long) = "$SNOOZED_PREFIX$ms"
}

/**
 * Asks "What did you order?" after a delivery (plan §6.2, Task 12.1). Stores only that it happened:
 * a `prompt_log` row with trigger DELIVERY and the package name. Never the notification's text.
 */
@Singleton
class DeliveryPrompter @Inject constructor(
    private val db: KleanDatabase,
    private val writer: PromptLogWriter,
    private val gate: ModuleGate,
    private val quiet: QuietHoursService,
    private val prefs: DevicePrefs,
    private val clock: Clock,
    private val presenter: PromptPresenter,
) : DeliveryPrompts {
    private val mutex = Mutex()

    override suspend fun onDelivered(pkg: String): Boolean = mutex.withLock {
        val now = clock.now().toEpochMilliseconds()
        if (!gate.isEnabled(ModuleId.DIET)) return@withLock false
        if (prefs.deliveryOffer.first() != DeliveryOffer.ACCEPTED) return@withLock false
        if (quiet.isQuiet(now)) return@withLock false
        val last = db.promptLogDao().lastForTrigger(PromptTrigger.DELIVERY, pkg)
        if (last != null && now - last.sentAt < DEBOUNCE_MS) return@withLock false
        val id = UuidV7.generate(now)
        writer.insert { sync ->
            PromptLogEntity(
                id = id, moduleId = ModuleId.DIET.value, trigger = PromptTrigger.DELIVERY, triggerDetail = pkg,
                format = PromptFormat.HEADS_UP, phrasingId = PromptPhrasings.DELIVERY_ID, scheduledFor = now, sentAt = now,
                outcome = PromptOutcome.PENDING, outcomeAt = null, sync = sync,
            )
        }
        presenter.present(id, PromptFormat.HEADS_UP, PromptPhrasings.DELIVERY_ID, PromptTrigger.DELIVERY)
        true
    }

    companion object {
        /** One question per app per 45 min: apps often post "delivered" more than once. */
        const val DEBOUNCE_MS = 45 * MINUTE_MS
    }
}
