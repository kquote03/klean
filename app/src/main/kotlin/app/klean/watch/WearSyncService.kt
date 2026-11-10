package app.klean.watch

import android.util.Log
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WearCodec
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** The workout actions the watch can take, over the Workout module's repository. */
interface WatchWorkouts {
    /** Starts (or returns to) a session from a saved workout. False for an unknown template. */
    suspend fun start(templateId: String): Boolean

    /** Adds one strength set to the open session. False when that session isn't open any more. */
    suspend fun logSet(sessionId: String, exercise: String, reps: Int, weightKg: Double?): Boolean
}

class RepoWatchWorkouts @Inject constructor(private val repo: dagger.Lazy<WorkoutRepository>) : WatchWorkouts {
    override suspend fun start(templateId: String): Boolean {
        repo.get().template(templateId) ?: return false
        repo.get().start(templateId) // the Workout screen's own call: reuses an open session
        return true
    }

    override suspend fun logSet(sessionId: String, exercise: String, reps: Int, weightKg: Double?): Boolean {
        val s = repo.get().session(sessionId)?.takeIf { it.isOpen && !it.session.sync.deleted } ?: return false
        if (exercise.isBlank() || reps <= 0) return false
        repo.get().addSet(s.session.id, WorkoutItem(exercise.trim(), WorkoutKind.STRENGTH, sets = 1, reps = reps, weightKg = weightKg))
        return true
    }
}

/**
 * What the phone does with a message from the watch (M27). Captures go through universal capture with
 * source WATCH, exactly like every other surface; workout messages drive the open session. Garbage,
 * unknown paths and switched-off modules are ignored, and nothing ever throws back into the service.
 */
class PhoneWearInbox @Inject constructor(
    private val capture: UniversalCaptureService,
    private val workouts: WatchWorkouts,
    private val gate: ModuleGate,
    private val refresh: StatusRefresh,
    private val clock: Clock,
) {
    /** True when the message was understood and saved. */
    suspend fun onMessage(path: String, bytes: ByteArray): Boolean {
        val m = WearCodec.decode(path, bytes) ?: return false
        return try {
            withTimeout(TIMEOUT_MS) { handle(m) }.also { if (it) runCatching { refresh.refresh() } }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w("Klean", "watch message timed out", e)
            false
        } catch (e: Exception) {
            Log.w("Klean", "watch message failed", e)
            false
        }
    }

    private suspend fun handle(m: WatchMessage): Boolean = when (m) {
        is WatchMessage.Capture -> submit(m.text, m.module?.let(::ModuleId), m.viaVoice)
        is WatchMessage.LogFood -> submit(m.text, ModuleId.DIET, viaVoice = false)
        is WatchMessage.StartWorkout -> gate.isEnabled(ModuleId.WORKOUT) && workouts.start(m.templateId)
        is WatchMessage.LogSet -> gate.isEnabled(ModuleId.WORKOUT) && workouts.logSet(m.sessionId, m.exercise, m.reps, m.weightKg)
        is WatchMessage.Alarm -> false // phone → watch only
    }

    private suspend fun submit(text: String, module: ModuleId?, viaVoice: Boolean): Boolean {
        val input = CaptureInput(text, CaptureSource.WATCH, clock.now().toEpochMilliseconds(), viaVoice = viaVoice, targetModule = module)
        return capture.submit(input) is CaptureResult.Saved
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}

/**
 * The Data Layer's entry point on the phone (manifest: MESSAGE_RECEIVED under `/klean`). Play services
 * binds it only when it exists, so a phone without them never runs this. Messages arrive on a
 * background thread, which may block until the save is done.
 */
@AndroidEntryPoint
class WearSyncService : WearableListenerService() {
    @Inject lateinit var inbox: PhoneWearInbox

    override fun onMessageReceived(event: MessageEvent) {
        runCatching { runBlocking { inbox.onMessage(event.path, event.data) } }
            .onFailure { Log.w("Klean", "watch message dropped", it) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchWorkoutsModule {
    @Binds abstract fun workouts(impl: RepoWatchWorkouts): WatchWorkouts
}
