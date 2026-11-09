package app.klean.feature.diet.notification

import android.content.Context
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * Diet's part of the holistic "Today" status line: "3 food entries", plus "last: shawarma 1:15 PM"
 * when diet is the only module with a line. An active WIDGET_HIGHLIGHT check-in puts its wording first.
 *
 * Takes the repository and the other providers lazily: the repository's dispatcher holds this
 * listener, and the providers set holds this provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DietStatusLine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<DietRepository>,
    private val gate: ModuleGate,
    private val providers: Provider<Set<@JvmSuppressWildcards StatusLineProvider>>,
    private val fmt: DietFormatter,
    private val highlight: PromptHighlight,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, DietChangeListener {
    override val moduleId: ModuleId = ModuleId.DIET

    private val tick = MutableStateFlow(0L)

    private val alone: Flow<Boolean> = gate.disabledIds.map { off ->
        providers.get().none { it.moduleId != ModuleId.DIET && it.moduleId !in off }
    }.distinctUntilChanged()

    override fun fragments(): Flow<List<StatusFragment>> =
        combine(tick, alone) { _, a -> a }.mapLatest { a -> build(a) }

    internal suspend fun build(alone: Boolean): List<StatusFragment> {
        val summary = repo.get().todaySummary()
        val out = mutableListOf<StatusFragment>()
        highlight.phrasing()?.let { out += StatusFragment(KEY_HIGHLIGHT, ctx.getString(it.res), PRIORITY_HIGHLIGHT) }
        if (summary.count > 0) {
            out += StatusFragment(
                KEY_COUNT,
                ctx.resources.getQuantityString(R.plurals.diet_status_line_count, summary.count, summary.count),
                PRIORITY_COUNT,
            )
            if (alone && summary.lastText != null && summary.lastAt != null) {
                out += StatusFragment(KEY_LAST, ctx.getString(R.string.diet_status_line_last, summary.lastText, fmt.time(summary.lastAt)), PRIORITY_LAST)
            }
        }
        return out
    }

    override suspend fun onChange(change: DietChange) = refresh()

    /** Diet data or a check-in highlight changed: rebuild the line and re-post the holistic surfaces. */
    suspend fun refresh() {
        tick.value++
        statusRefresh.get().refresh()
    }

    companion object {
        const val KEY_COUNT = "diet.count"
        const val KEY_LAST = "diet.last"
        const val KEY_HIGHLIGHT = "diet.highlight"
        const val PRIORITY_HIGHLIGHT = 100
        const val PRIORITY_COUNT = 1
        const val PRIORITY_LAST = 0
    }
}
