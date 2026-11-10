package app.klean.feature.workout.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Stored by name in `workout_set.kind` and `exercise_definition.kind`. */
enum class WorkoutKind { STRENGTH, TIMED, CARDIO }

/**
 * One line of a workout: "3×15 pushups", "1 min plank", "5 km run". [sets] is how many sets are planned
 * (strength); reps, weight, duration and distance are what each set is. A template stores a list of these
 * as JSON in `workout_template.items`.
 */
@Serializable
data class WorkoutItem(
    val exercise: String,
    val kind: WorkoutKind,
    val sets: Int = 1,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationS: Int? = null,
    val distanceM: Int? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(serializer())

        fun encode(items: List<WorkoutItem>): String = json.encodeToString(listSerializer, items)

        /** A row written by a newer version may not parse; an unreadable template is simply empty. */
        fun decode(raw: String): List<WorkoutItem> = runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }
}

/**
 * The template language: items separated by commas, semicolons or new lines.
 * - strength: `3x15 pushups`, `3×15 pushups`, `pushups 3x15`, `30 pushups`, `5x5 squats @ 60 kg`
 * - timed: `1 min plank`, `45 s plank`, `90 sec wall sit`
 * - cardio: `5 km run`, `5k run`, `800 m row`, `30 min walk` (a cardio word with a duration)
 * - anything else is a bare exercise name (`yoga`)
 *
 * [format] is the inverse of [parse].
 */
object TemplateText {
    private val OPTS = setOf(RegexOption.IGNORE_CASE)
    private const val NUM = """(\d+(?:[.,]\d+)?)"""
    private val SETS_FIRST = Regex("""^(\d+)\s*[x×]\s*(\d+)\s+(.+)$""", OPTS)
    private val SETS_LAST = Regex("""^(.+?)\s+(\d+)\s*[x×]\s*(\d+)$""", OPTS)
    private val WEIGHT = Regex("""^(.+?)\s*(?:@|at)?\s*$NUM\s*kg$""", OPTS)
    private val TIME_UNITS = """(h|hr|hrs|hours?|min|mins|minutes?|s|sec|secs|seconds?)"""
    private val TIMED = Regex("""^$NUM\s*$TIME_UNITS\s+(?:of\s+)?(.+)$""", OPTS)
    private val DISTANCE = Regex("""^$NUM\s*(km|k|m|mi)\s+(.+)$""", OPTS)
    private val DISTANCE_LAST = Regex("""^(.+?)\s+$NUM\s*(km|k|m|mi)$""", OPTS)
    private val DISTANCE_ALONE = Regex("""^$NUM\s*(km|k|m|mi)$""", OPTS)
    private val REPS = Regex("""^(\d+)\s+(.+)$""", OPTS)
    private val SEPARATORS = Regex("""[,;\n]""")
    private val CARDIO_WORDS = Regex("""\b(run|running|jog|jogging|walk|walking|hike|cycle|cycling|bike|ride|swim|swimming|row|rowing|elliptical)\b""", OPTS)

    /** Leading log verbs and trailing filler that capture strips ("did 30 pushups today"). */
    private val LOG_PREFIX = Regex("""^(?:i\s+)?(?:did|done|finished|completed|just did|went for an?|had an?|logged)\s+""", OPTS)
    private val LOG_SUFFIX = Regex("""\s+(?:today|this morning|this evening|tonight|just now)$""", OPTS)
    private val RAN = Regex("""^(?:i\s+)?(ran|jogged|walked|swam|cycled|rode|rowed|hiked)\s+(.+)$""", OPTS)
    private val PAST_TO_NOUN = mapOf("ran" to "run", "jogged" to "jog", "walked" to "walk", "swam" to "swim", "cycled" to "cycle", "rode" to "ride", "rowed" to "row", "hiked" to "hike")

    fun parse(text: String): List<WorkoutItem> = text.split(SEPARATORS).mapNotNull { parseItem(it) }

    /** A log line ("did 30 pushups", "ran 5k"): verbs stripped, then [parse]. A bare word like "gym" gives nothing. */
    fun parseLog(text: String): List<WorkoutItem> = text.split(SEPARATORS).mapNotNull { part ->
        var t = part.trim().replace(LOG_SUFFIX, "").replace(LOG_PREFIX, "").trim()
        RAN.matchEntire(t)?.let { m ->
            val noun = PAST_TO_NOUN.getValue(m.groupValues[1].lowercase())
            val rest = m.groupValues[2].trim()
            t = if (DISTANCE_ALONE.matches(rest) || Regex("""^$NUM\s*$TIME_UNITS$""", OPTS).matches(rest)) "$rest $noun" else rest
        }
        parseItem(t)?.takeIf { it.reps != null || it.durationS != null || it.distanceM != null || it.weightKg != null }
    }

    private fun parseItem(raw: String): WorkoutItem? {
        val t = raw.trim().replace(Regex("""\s+"""), " ")
        if (t.isEmpty()) return null
        val (body, kg) = WEIGHT.matchEntire(t)?.let { m -> m.groupValues[1].trim() to num(m.groupValues[2]) } ?: (t to null)
        SETS_FIRST.matchEntire(body)?.let { m ->
            return WorkoutItem(name(m.groupValues[3]), WorkoutKind.STRENGTH, m.groupValues[1].toInt(), m.groupValues[2].toInt(), weightKg = kg)
        }
        SETS_LAST.matchEntire(body)?.let { m ->
            return WorkoutItem(name(m.groupValues[1]), WorkoutKind.STRENGTH, m.groupValues[2].toInt(), m.groupValues[3].toInt(), weightKg = kg)
        }
        TIMED.matchEntire(body)?.let { m ->
            val name = name(m.groupValues[3])
            val seconds = (num(m.groupValues[1]) * unitSeconds(m.groupValues[2])).toInt()
            val kind = if (CARDIO_WORDS.containsMatchIn(name)) WorkoutKind.CARDIO else WorkoutKind.TIMED
            return WorkoutItem(name, kind, durationS = seconds)
        }
        DISTANCE.matchEntire(body)?.let { m -> return cardio(m.groupValues[3], m.groupValues[1], m.groupValues[2]) }
        DISTANCE_LAST.matchEntire(body)?.let { m -> return cardio(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
        REPS.matchEntire(body)?.let { m ->
            return WorkoutItem(name(m.groupValues[2]), WorkoutKind.STRENGTH, 1, m.groupValues[1].toInt(), weightKg = kg)
        }
        val name = name(body)
        if (name.isEmpty() || name.first().isDigit()) return null
        return WorkoutItem(name, if (CARDIO_WORDS.containsMatchIn(name)) WorkoutKind.CARDIO else WorkoutKind.STRENGTH, weightKg = kg)
    }

    private fun cardio(name: String, value: String, unit: String): WorkoutItem {
        val metres = num(value) * when (unit.lowercase()) {
            "km", "k" -> 1000.0
            "mi" -> 1609.344
            else -> 1.0
        }
        return WorkoutItem(name(name), WorkoutKind.CARDIO, distanceM = kotlin.math.round(metres).toInt())
    }

    private fun unitSeconds(u: String): Double = when (u.lowercase()) {
        "h", "hr", "hrs", "hour", "hours" -> 3600.0
        "min", "mins", "minute", "minutes" -> 60.0
        else -> 1.0
    }

    private fun num(s: String): Double = s.replace(',', '.').toDouble()

    private fun name(s: String): String = s.trim().trimEnd('.', '!', '?').trim()

    fun format(items: List<WorkoutItem>): String = items.joinToString(", ") { formatItem(it) }

    fun formatItem(i: WorkoutItem): String {
        val weight = i.weightKg?.let { " @ ${number(it)} kg" }.orEmpty()
        return when {
            i.distanceM != null -> "${distance(i.distanceM)} ${i.exercise}"
            i.durationS != null -> "${duration(i.durationS)} ${i.exercise}"
            i.reps != null && i.sets > 1 -> "${i.sets}×${i.reps} ${i.exercise}$weight"
            i.reps != null -> "${i.reps} ${i.exercise}$weight"
            else -> "${i.exercise}$weight"
        }
    }

    /** "5 km", "2.5 km", "800 m". */
    fun distance(m: Int): String = if (m >= 1000) "${number(m / 1000.0)} km" else "$m m"

    /** "1 min", "45 s", "1 h". */
    fun duration(s: Int): String = when {
        s >= 3600 && s % 3600 == 0 -> "${s / 3600} h"
        s >= 60 && s % 60 == 0 -> "${s / 60} min"
        else -> "$s s"
    }

    private fun number(d: Double): String = if (d == kotlin.math.floor(d)) d.toLong().toString() else d.toString().trimEnd('0').trimEnd('.')
}
