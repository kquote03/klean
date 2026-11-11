package app.klean.core.ui.module

import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One line on Today's whiteboard. [at] is epoch millis (for ordering and the time column); null = no time. */
data class GlanceRow(
    val key: String,
    val module: ModuleId,
    val text: String,
    val at: Long?,
    /** "7 h 20 min", "3 sets", "with milk"; shown under or beside [text]. */
    val detail: String? = null,
    val icon: ImageVector? = null,
    /** Deep link (klean://…) opened on tap, or null. */
    val route: String? = null,
    /** A task-like row that can be ticked from Today. */
    val checkable: Boolean = false,
)

/** What a module's one-tap button on Today does. */
sealed interface QuickAction {
    val module: ModuleId
    val label: String
    val icon: ImageVector

    /** Open the capture sheet aimed at [module]. */
    data class Capture(override val module: ModuleId, override val label: String, override val icon: ImageVector) : QuickAction

    /** Do it now (Sleep's toggle); returns the snackbar text and an undo, or null on failure. */
    data class Instant(
        override val module: ModuleId,
        override val label: String,
        override val icon: ImageVector,
        val run: suspend () -> InstantResult?,
    ) : QuickAction

    /** Open a route (Workout's start sheet, New contact). */
    data class Open(override val module: ModuleId, override val label: String, override val icon: ImageVector, val route: Any) : QuickAction
}

data class InstantResult(val message: String, val undo: (suspend () -> Unit)?)

/** A module's part of Today. Bind with @IntoSet. Each list is today's only (logical day, user's zone). */
data class GlanceContribution(
    val attention: List<GlanceRow> = emptyList(),
    val upcoming: List<GlanceRow> = emptyList(),
    val done: List<GlanceRow> = emptyList(),
    val quickAction: QuickAction? = null,
)

/** A module's rows and one-tap action on Today's whiteboard. Bind with @IntoSet. */
interface TodayGlanceProvider {
    val moduleId: ModuleId
    fun glance(): Flow<GlanceContribution>
}

/**
 * Every enabled module's [GlanceContribution], keyed by module (a module with several providers gets their rows
 * merged and the first quick action). A provider that fails contributes nothing rather than blanking Today.
 * Providers must emit promptly (Today waits for every enabled one before it shows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TodayGlance @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TodayGlanceProvider>,
    private val gate: ModuleGate,
) {
    fun byModule(): Flow<Map<ModuleId, GlanceContribution>> = gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
        val on = providers.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
        if (on.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                on.map { p ->
                    p.glance()
                        .catch { e ->
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            emit(GlanceContribution())
                        }
                        .map { p.moduleId to it }
                },
            ) { pairs ->
                pairs.groupBy({ it.first }, { it.second }).mapValues { (_, parts) -> parts.merge() }
            }
        }
    }
}

private fun List<GlanceContribution>.merge(): GlanceContribution = if (size == 1) {
    single()
} else {
    GlanceContribution(
        attention = flatMap { it.attention },
        upcoming = flatMap { it.upcoming },
        done = flatMap { it.done },
        quickAction = firstNotNullOfOrNull { it.quickAction },
    )
}
