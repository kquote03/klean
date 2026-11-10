package app.klean.wear.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The phone ↔ watch Data Layer protocol (M27). The watch never talks to the backend: the phone sends a
 * [WatchState] snapshot as a DataItem at [WearPaths.STATE], and the watch sends [WatchMessage]s back with
 * `MessageClient`. Pure Kotlin with no Android types, so the phone side can share it (it moves to `:shared`
 * when the phone's `WearSyncService` lands).
 */
object WearPaths {
    const val PREFIX = "/klean"
    /** DataItem, phone → watch: the latest [WatchState]. */
    const val STATE = "/klean/state"
    /** Messages, watch → phone. */
    const val CAPTURE = "/klean/capture"
    const val LOG_FOOD = "/klean/food/log"
    const val START_WORKOUT = "/klean/workout/start"
    const val LOG_SET = "/klean/workout/set"
    /** Message, phone → watch: an alarm is ringing on the phone; vibrate. */
    const val ALARM = "/klean/alarm"
    /** Capability the phone app declares (res/values/wear.xml on the phone). */
    const val PHONE_CAPABILITY = "klean_phone"
}

@Serializable
data class FoodLine(val at: Long, val text: String)

@Serializable
data class NextUp(
    /** "TASK" or "REMINDER". */
    val kind: String,
    val title: String,
    /** UTC ms; null = no time (a task for today). */
    val at: Long? = null,
)

@Serializable
data class WorkoutRef(val id: String, val name: String)

@Serializable
data class ActiveWorkout(val sessionId: String, val name: String, val sets: Int)

/** What the watch shows. Sent whole by the phone after any relevant change. */
@Serializable
data class WatchState(
    val v: Int = WearCodec.VERSION,
    val generatedAt: Long = 0,
    /** Today's food entries, oldest first. */
    val todayFood: List<FoodLine> = emptyList(),
    /** Recent items for one-tap logging, most common first. */
    val recentFoods: List<String> = emptyList(),
    val next: NextUp? = null,
    val workouts: List<WorkoutRef> = emptyList(),
    val active: ActiveWorkout? = null,
    /** Diet module switched off on the phone: food tiles say so instead of offering to log. */
    val dietOn: Boolean = true,
)

/** Watch → phone (and the phone's alarm ping back). */
@Serializable
sealed interface WatchMessage {
    /** Universal capture from the watch's voice or keyboard. [module] null = let the phone route it. */
    @Serializable @SerialName("capture")
    data class Capture(val text: String, val module: String? = null, val viaVoice: Boolean = false) : WatchMessage

    /** One-tap log of a recent food. */
    @Serializable @SerialName("log_food")
    data class LogFood(val text: String) : WatchMessage

    @Serializable @SerialName("start_workout")
    data class StartWorkout(val templateId: String) : WatchMessage

    @Serializable @SerialName("log_set")
    data class LogSet(val sessionId: String, val exercise: String, val reps: Int, val weightKg: Double? = null) : WatchMessage

    @Serializable @SerialName("alarm")
    data class Alarm(val reminderId: String, val title: String, val ringing: Boolean = true) : WatchMessage
}

object WearCodec {
    const val VERSION = 1
    /** The Data Layer's limit for a message or DataItem is 100 KB; stay well under it. */
    const val MAX_BYTES = 90 * 1024
    private const val MAX_TEXT = 500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; classDiscriminator = "t" }

    fun pathOf(m: WatchMessage): String = when (m) {
        is WatchMessage.Capture -> WearPaths.CAPTURE
        is WatchMessage.LogFood -> WearPaths.LOG_FOOD
        is WatchMessage.StartWorkout -> WearPaths.START_WORKOUT
        is WatchMessage.LogSet -> WearPaths.LOG_SET
        is WatchMessage.Alarm -> WearPaths.ALARM
    }

    fun encode(m: WatchMessage): Pair<String, ByteArray> {
        val clean = when (m) {
            is WatchMessage.Capture -> m.copy(text = m.text.trim().take(MAX_TEXT))
            is WatchMessage.LogFood -> m.copy(text = m.text.trim().take(MAX_TEXT))
            else -> m
        }
        return pathOf(clean) to json.encodeToString(WatchMessage.serializer(), clean).encodeToByteArray()
    }

    /** Null for an unknown path, a mismatched type or bytes that don't parse (an older or newer app). */
    fun decode(path: String, bytes: ByteArray): WatchMessage? {
        val m = runCatching { json.decodeFromString(WatchMessage.serializer(), bytes.decodeToString()) }.getOrNull() ?: return null
        if (pathOf(m) != path) return null
        if (m is WatchMessage.Capture && m.text.isBlank()) return null
        if (m is WatchMessage.LogFood && m.text.isBlank()) return null
        return m
    }

    /** Encodes [s], dropping the oldest food lines and recent items until it fits [MAX_BYTES]. */
    fun encodeState(s: WatchState): ByteArray {
        var cur = s
        while (true) {
            val bytes = json.encodeToString(WatchState.serializer(), cur).encodeToByteArray()
            if (bytes.size <= MAX_BYTES) return bytes
            cur = when {
                cur.todayFood.isNotEmpty() -> cur.copy(todayFood = cur.todayFood.drop(maxOf(1, cur.todayFood.size / 4)))
                cur.recentFoods.isNotEmpty() -> cur.copy(recentFoods = cur.recentFoods.dropLast(maxOf(1, cur.recentFoods.size / 4)))
                cur.workouts.isNotEmpty() -> cur.copy(workouts = cur.workouts.dropLast(maxOf(1, cur.workouts.size / 4)))
                else -> return bytes
            }
        }
    }

    /** Null for bytes that don't parse or a newer protocol version this watch app doesn't know. */
    fun decodeState(bytes: ByteArray): WatchState? =
        runCatching { json.decodeFromString(WatchState.serializer(), bytes.decodeToString()) }.getOrNull()?.takeIf { it.v <= VERSION }
}
