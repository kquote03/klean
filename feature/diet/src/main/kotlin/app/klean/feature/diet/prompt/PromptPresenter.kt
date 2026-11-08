package app.klean.feature.diet.prompt

import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptTrigger

/** Shows a check-in in the given format. Implemented by PromptNotifier; faked in engine tests. */
interface PromptPresenter {
    suspend fun present(logId: String, format: PromptFormat, phrasingId: String, trigger: PromptTrigger)

    /** Removes the active check-in notification and any widget/status highlight. */
    suspend fun cancelActive()
}
