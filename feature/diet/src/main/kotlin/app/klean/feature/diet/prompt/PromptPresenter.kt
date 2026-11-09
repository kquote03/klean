package app.klean.feature.diet.prompt

import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptTrigger

/** Shows a check-in in the given format. Implemented by PromptNotifier; faked in engine tests. */
interface PromptPresenter {
    /** [placeLabel]: the place being left, for a GEOFENCE prompt ("Anything from %1$s?"). */
    suspend fun present(logId: String, format: PromptFormat, phrasingId: String, trigger: PromptTrigger, placeLabel: String? = null)

    /** Removes the active check-in notification and any widget/status highlight. */
    suspend fun cancelActive()
}
