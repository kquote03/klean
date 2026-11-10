package app.klean.core.capture.status

import app.klean.core.capture.UniversalCaptureService
import javax.inject.Inject

/** Quick-reply chips for the status line's "Add something" on a watch: the enabled modules' quick repeats. */
fun interface StatusReplyChoices {
    suspend fun choices(): List<String>

    companion object {
        val NONE = StatusReplyChoices { emptyList() }
    }
}

/** The capture sheet's own suggestions (every enabled module's quick repeats), capped for the watch. */
class CaptureReplyChoices @Inject constructor(
    private val capture: dagger.Lazy<UniversalCaptureService>,
) : StatusReplyChoices {
    override suspend fun choices(): List<String> = capture.get().suggestions(limit = KleanStatusNotifier.MAX_CHIPS)
}
