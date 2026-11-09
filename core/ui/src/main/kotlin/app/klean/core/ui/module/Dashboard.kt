package app.klean.core.ui.module

import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** One tile on Today ("Today · 3 entries"). Values are already formatted for display. */
data class SummaryItem(
    val key: String,
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    /** Where a tap goes, or null for a tile that only informs. */
    val route: Any? = null,
)

/** A module's tiles on Today. Bind with @IntoSet. Emit an empty list to show nothing. */
interface DashboardSummaryProvider {
    val moduleId: ModuleId
    fun summaries(): Flow<List<SummaryItem>>
}

/**
 * A module's per-day activity for the week overview on Today. Counts are neutral facts: a day
 * without activity is simply blank. Future calendar/tasks/workout modules plug in here.
 */
interface DayActivityProvider {
    val moduleId: ModuleId
    /** Activity per logical day in [from]..[to], both inclusive. Days with nothing may be absent. */
    fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    /** The screen that shows this module's [date]. */
    fun dayRoute(date: LocalDate): Any
}

data class DayActivity(val count: Int, val route: Any?)

/** One short fragment of the persistent "Today" status line, e.g. "2 tasks" or "Dentist 14:00". */
data class StatusFragment(val key: String, val text: String, val priority: Int)

/** A module's contribution to the persistent status notification. Bind with @IntoSet. */
interface StatusLineProvider {
    val moduleId: ModuleId
    fun fragments(): Flow<List<StatusFragment>>
}

/**
 * Everything the enabled modules contribute to Today, filtered by the module gate.
 * Providers are ordered by module id, so app-owned "core" tiles (quiet hours) come first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DashboardContributions @Inject constructor(
    private val summaryProviders: Set<@JvmSuppressWildcards DashboardSummaryProvider>,
    private val activityProviders: Set<@JvmSuppressWildcards DayActivityProvider>,
    private val gate: ModuleGate,
    private val statusProviders: Set<@JvmSuppressWildcards StatusLineProvider> = emptySet(),
) {
    /** Gate-filtered status fragments, highest priority first, at most [max]. */
    fun statusLine(max: Int = 3): Flow<List<StatusFragment>> = gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
        val on = statusProviders.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
        if (on.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(on.map { it.fragments() }) { lists ->
                lists.flatMap { it }.sortedByDescending { it.priority }.take(max)
            }
        }
    }

    /** True when any enabled module contributes to the status line. */
    fun hasStatusProviders(): Flow<Boolean> =
        gate.disabledIds.map { off -> statusProviders.any { it.moduleId !in off } }.distinctUntilChanged()

    /** True when any enabled module other than [except] contributes to the status line. */
    fun hasOtherStatusProviders(except: ModuleId): Flow<Boolean> =
        gate.disabledIds.map { off -> statusProviders.any { it.moduleId != except && it.moduleId !in off } }
            .distinctUntilChanged()

    fun summaries(): Flow<List<SummaryItem>> = gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
        val on = summaryProviders.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
        if (on.isEmpty()) flowOf(emptyList()) else combine(on.map { it.summaries() }) { lists -> lists.flatMap { it } }
    }

    fun hasActivityProviders(): Flow<Boolean> =
        gate.disabledIds.map { off -> activityProviders.any { it.moduleId !in off } }.distinctUntilChanged()

    fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayActivity>> =
        gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
            val on = activityProviders.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
            if (on.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(on.map { p -> p.activity(from, to) }) { maps ->
                    val days = maps.flatMap { it.keys }.toSet()
                    days.associateWith { d ->
                        val total = maps.sumOf { it[d] ?: 0 }
                        val first = on.indices.firstOrNull { (maps[it][d] ?: 0) > 0 }
                        DayActivity(total, first?.let { on[it].dayRoute(d) })
                    }.filterValues { it.count > 0 }
                }
            }
        }
}

/** A holistic surface that shows the status line or Today items (the notification, the Today widget). */
interface StatusSurface {
    suspend fun refresh()
}

/**
 * The core hook every module calls after a write, so the holistic surfaces follow at once instead of
 * waiting for their flows. Surfaces are looked up lazily: they depend on the providers that call this.
 */
@Singleton
class StatusRefresh @Inject constructor(
    private val surfaces: javax.inject.Provider<Set<@JvmSuppressWildcards StatusSurface>>,
) {
    suspend fun refresh() {
        for (s in surfaces.get()) {
            try {
                s.refresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Klean", "status surface refresh failed", e)
            }
        }
    }
}

/** Declares both sets so the app builds even when nothing contributes to Today. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardMultibindings {
    @Multibinds abstract fun summaries(): Set<DashboardSummaryProvider>
    @Multibinds abstract fun activity(): Set<DayActivityProvider>
    @Multibinds abstract fun statusLine(): Set<StatusLineProvider>
    @Multibinds abstract fun statusSurfaces(): Set<StatusSurface>
}
