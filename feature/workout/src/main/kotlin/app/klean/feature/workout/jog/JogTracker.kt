package app.klean.feature.workout.jog

import android.content.Context
import app.klean.feature.workout.HealthConnectBridge
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.shared.health.GeoPoint
import app.klean.shared.health.RouteMath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A run in progress. [distanceM] counts only the fixes [RouteMath.clean] keeps. */
data class JogState(
    val running: Boolean = false,
    val startedAt: Long = 0,
    val points: List<GeoPoint> = emptyList(),
    val distanceM: Double = 0.0,
    /** When the last fix arrived (null = still waiting for GPS). */
    val lastFixAt: Long? = null,
) {
    fun paceSecPerKm(now: Long): Int? = RouteMath.paceSecPerKm(distanceM, now - startedAt)
}

/** GPS routes as files under `filesDir/routes/` (the sync blob dir `routes`). */
open class RouteStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    open val dir: File get() = File(ctx.filesDir, DIR)

    /** Writes [points] and returns the file name stored in `workout_session.route_blob`. */
    fun write(name: String, points: List<GeoPoint>): String {
        dir.mkdirs()
        File(dir, name).writeText(RouteMath.encodeRoute(points))
        return name
    }

    fun read(name: String): List<GeoPoint> = File(dir, name).takeIf { it.isFile }?.let { RouteMath.decodeRoute(it.readText()) }.orEmpty()

    companion object {
        const val DIR = "routes"
    }
}

/**
 * Tracks one GPS run (M22). [JogTrackingService] feeds it fixes while the run is on; [finish] saves the run as
 * a workout session with its route and distance, then hands it to Health Connect. The distance is computed
 * incrementally with the same rule as [RouteMath.clean], so the live number and the saved one agree.
 */
@Singleton
class JogTracker @Inject constructor(
    private val clock: Clock,
    private val repo: WorkoutRepository,
    private val routes: RouteStore,
    private val health: HealthConnectBridge,
) {
    private val _state = MutableStateFlow(JogState())
    val state: StateFlow<JogState> = _state.asStateFlow()
    private var filter = app.klean.shared.health.RouteFilter()

    private fun now() = clock.now().toEpochMilliseconds()

    fun start() {
        if (_state.value.running) return
        filter = app.klean.shared.health.RouteFilter()
        _state.value = JogState(running = true, startedAt = now())
    }

    fun onFix(p: GeoPoint) {
        val s = _state.value
        if (!s.running) return
        val step = filter.accept(p)
        if (step == null) {
            // Dropped, unless it became the new anchor after a bad one (kept on the route, not counted).
            _state.value = if (filter.last === p) s.copy(points = s.points + p, lastFixAt = p.timeMs) else s.copy(lastFixAt = p.timeMs)
            return
        }
        _state.value = s.copy(points = s.points + p, distanceM = s.distanceM + step, lastFixAt = p.timeMs)
    }

    /** Saves the run (even a short one; it can be deleted like any session) and returns its session id. */
    suspend fun finish(): String? {
        val s = _state.value
        if (!s.running) return null
        _state.value = JogState()
        filter = app.klean.shared.health.RouteFilter()
        val end = now()
        val route = if (s.points.size >= 2) routes.write("run-${s.startedAt}.route", s.points) else null
        val id = repo.logRun(s.startedAt, end, s.distanceM.roundToInt(), route)
        runCatching { health.writeSession(id) }
        return id
    }

    /** Stops without saving. */
    fun discard() {
        _state.value = JogState()
        filter = app.klean.shared.health.RouteFilter()
    }
}
