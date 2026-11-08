package app.klean.feature.diet.prompt

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules diet check-ins. Placeholder until the prompt engine lands (M5); the module toggle
 * already calls it so enable/disable needs no further wiring then.
 */
@Singleton
open class DietPromptEngine @Inject constructor() {
    /** Plans and arms the next check-in alarm. */
    open suspend fun reschedule() = Unit

    /** Cancels the pending check-in alarm. */
    open suspend fun cancel() = Unit
}
