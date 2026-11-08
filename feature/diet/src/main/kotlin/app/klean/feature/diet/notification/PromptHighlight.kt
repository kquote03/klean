package app.klean.feature.diet.notification

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import app.klean.shared.diet.prompt.PromptOutcome
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The wording of the check-in currently shown as a highlight (WIDGET_HIGHLIGHT format), if any. */
fun interface PromptHighlight {
    suspend fun phrasing(): Phrasing?
}

class DefaultPromptHighlight @Inject constructor(
    private val prefs: DevicePrefs,
    private val db: KleanDatabase,
) : PromptHighlight {
    override suspend fun phrasing(): Phrasing? {
        val id = prefs.highlightPromptId.first() ?: return null
        val row = db.promptLogDao().get(id)?.takeIf { it.outcome == PromptOutcome.PENDING } ?: return null
        return PromptPhrasings.byId(row.phrasingId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PromptHighlightModule {
    @Binds abstract fun highlight(impl: DefaultPromptHighlight): PromptHighlight
}
