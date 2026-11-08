package app.klean.core.capture

import app.klean.core.data.module.ModuleGate
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureRouter
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.module.ModuleId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

sealed interface CaptureResult {
    /** Saved to [moduleId]. [alternatives] are other plausible targets the user can move it to. */
    data class Saved(
        val moduleId: ModuleId,
        val recordId: String,
        val alternatives: List<CandidateAction>,
        val input: CaptureInput,
    ) : CaptureResult

    /** Every module that could take the text is switched off. Nothing was saved. */
    data object NoModule : CaptureResult
}

/**
 * One entry point for "write something down" from anywhere: Today, the capture sheet, the tile, the
 * widget. Routes to the enabled modules' [CaptureHandler]s and saves at once; never waits on AI
 * beyond the router's own short, offline-only budget.
 */
@Singleton
class UniversalCaptureService @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards CaptureHandler>,
    private val gate: ModuleGate,
    private val router: CaptureRouter,
) {
    /** Handlers of enabled modules, updated when modules are switched on or off. */
    val availableHandlers: Flow<List<CaptureHandler>> =
        gate.disabledIds.map { off -> handlers.filter { it.moduleId !in off }.sortedBy { it.moduleId.value } }

    suspend fun enabledHandlers(): List<CaptureHandler> = availableHandlers.first()

    suspend fun submit(input: CaptureInput): CaptureResult {
        val hs = enabledHandlers()
        val ranked = router.route(input, hs)
        val top = ranked.firstOrNull() ?: return CaptureResult.NoModule
        val handler = hs.firstOrNull { it.moduleId == top.moduleId } ?: return CaptureResult.NoModule
        val id = handler.execute(input, top)
        return CaptureResult.Saved(top.moduleId, id, ranked.drop(1).filter { it.moduleId != top.moduleId }, input)
    }

    /**
     * Moves a saved capture to another module. Saves the new record first and only then undoes the
     * old one, so a failure never loses what the user wrote.
     */
    suspend fun correct(saved: CaptureResult.Saved, to: CandidateAction): CaptureResult.Saved {
        val target = enabledHandlers().firstOrNull { it.moduleId == to.moduleId } ?: return saved
        val previous = handlers.firstOrNull { it.moduleId == saved.moduleId }
        val newId = target.execute(saved.input, to)
        previous?.undo(saved.recordId)
        val back = previous?.defaultAction(saved.input)
        val alternatives = listOfNotNull(back) + saved.alternatives.filter { it.moduleId != to.moduleId && it.moduleId != back?.moduleId }
        return CaptureResult.Saved(to.moduleId, newId, alternatives, saved.input)
    }

    /** Quick-repeat chips from enabled handlers, or only [target]'s when given. */
    suspend fun suggestions(target: ModuleId? = null, limit: Int = 8): List<String> =
        enabledHandlers()
            .filter { target == null || it.moduleId == target }
            .flatMap { runCatching { it.suggestions() }.getOrDefault(emptyList()) }
            .distinct()
            .take(limit)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureHandlersModule {
    /** Legal even when no feature contributes a handler. */
    @Multibinds abstract fun handlers(): Set<CaptureHandler>
}

@Module
@InstallIn(SingletonComponent::class)
object CaptureRouterModule {
    /** No classifier in Prototype 1: with one module, routing never needs AI. */
    @Provides fun router(): CaptureRouter = DefaultCaptureRouter(classifier = null)
}
