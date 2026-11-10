package app.klean.shared.capture

import app.klean.shared.module.ModuleId
import app.klean.shared.time.NaturalTime
import kotlinx.datetime.LocalDateTime

/**
 * Offline, keyword- and time-expression-based routing for universal capture. Runs before any AI, so routing
 * works with no provider and no network. Every rule only answers for modules present in `options`; a rule
 * whose modules are all absent falls through to the next one. Returns null when nothing matches, and the
 * router then falls back to its default module (diet).
 *
 * Action names are the contract with the module capture handlers: [ADD_BIRTHDAY], [ADD_REMINDER], [LOG_SLEEP],
 * [LOG_WORKOUT], [ADD_EVENT], [ADD_TASK].
 *
 * @param now current local date-time, used for [NaturalTime].
 */
class RuleCaptureClassifier(private val now: () -> LocalDateTime) : CaptureClassifier {

    override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? {
        val present = options.mapTo(HashSet()) { it.moduleId }
        fun pick(vararg c: CandidateAction): List<CandidateAction>? =
            c.filter { it.moduleId in present }.ifEmpty { null }

        val t = text.trim()
        if (DELEGATE_RX.containsMatchIn(t)) {
            pick(CandidateAction(ModuleId.DELEGATION, DELEGATE, HIT), CandidateAction(ModuleId.TASKS, ADD_TASK, 0.8f))?.let { return it }
        }
        if (isBirthday(t)) pick(
            CandidateAction(ModuleId.CONTACTS, ADD_BIRTHDAY, HIT),
            CandidateAction(ModuleId.BIRTHDAYS, ADD_BIRTHDAY, HIT - 0.05f),
        )?.let { return it }
        if (REMINDER.containsMatchIn(t)) pick(CandidateAction(ModuleId.REMINDERS, ADD_REMINDER, HIT))?.let { return it }
        if (SLEEP.containsMatchIn(t)) pick(CandidateAction(ModuleId.SLEEP, LOG_SLEEP, HIT))?.let { return it }

        val parsed = NaturalTime.parse(t, now())
        if (WORKOUT.containsMatchIn(t)) {
            val planned = parsed.date != null && !PAST_TENSE.containsMatchIn(t)
            val hit = if (planned) pick(
                CandidateAction(ModuleId.CALENDAR, ADD_EVENT, 0.8f),
                CandidateAction(ModuleId.WORKOUT, LOG_WORKOUT, 0.5f),
            ) else pick(CandidateAction(ModuleId.WORKOUT, LOG_WORKOUT, HIT))
            hit?.let { return it }
        }
        if (parsed.date != null && !FOOD.containsMatchIn(t)) {
            val c = if (parsed.minutes != null) CandidateAction(ModuleId.CALENDAR, ADD_EVENT, 0.7f)
            else CandidateAction(ModuleId.TASKS, ADD_TASK, 0.7f)
            pick(c)?.let { return it }
        }
        if (TASK.containsMatchIn(t)) pick(CandidateAction(ModuleId.TASKS, ADD_TASK, 0.8f))?.let { return it }
        return null
    }

    companion object {
        const val ADD_BIRTHDAY = "add_birthday"
        const val ADD_REMINDER = "add_reminder"
        const val LOG_SLEEP = "log_sleep"
        const val LOG_WORKOUT = "log_workout"
        const val ADD_EVENT = "add_event"
        const val ADD_TASK = "add_task"
        /** Hand a task to an agent (M23): "ask Hermes to…", "Hermes, …", "delegate: …". */
        const val DELEGATE = "delegate"
        val DELEGATE_RX = Regex("""^(?:ask\s+hermes\s+to\b|hermes\s*[,:]|delegate\s*[:,]|delegate\s+to\s+hermes\b)""", setOf(RegexOption.IGNORE_CASE))

        private const val HIT = 0.9f
        private val OPTS = setOf(RegexOption.IGNORE_CASE)
        private val BIRTHDAY = Regex("""birthday|\bb-?day\b""", OPTS)
        private val REMINDER = Regex("""^remind|\balarm\b|\bwake me\b""", OPTS)
        private val SLEEP = Regex("""\bslept\b|\bwent to bed\b|\bwoke up\b|\bgoing to bed\b|\bbedtime\b""", OPTS)
        private val WORKOUT =
            Regex("""\bgym\b|\bworkout\b|\bpush-?ups?\b|\bsquats?\b|\bplank\b|\bran \d|\brun \d|\bkm run\b|\blifted\b""", OPTS)
        private val PAST_TENSE = Regex("""\b(?:did|done|ran|lifted|finished|completed)\b""", OPTS)
        private val FOOD = Regex(
            """\b(?:ate|eat|eating|breakfast|lunch|dinner|snack|brunch|coffee|tea|drank|drink|meal)\b""", OPTS,
        )
        private val TASK = Regex("""^todo\b|^to do\b|\bneed to\b|\bhave to\b|\bbuy |\bcall |\bemail |\bpay """, OPTS)

        /** True for birthday phrases; these must be resolved by rules alone and never reach an AI provider. */
        fun isBirthday(text: String): Boolean = BIRTHDAY.containsMatchIn(text)
    }
}

/**
 * Rules first, AI second (decision 6). Returns [rules]' answer when non-null; otherwise asks [ai], except for
 * birthday phrases, which never leave the device.
 */
class CompositeCaptureClassifier(
    private val rules: CaptureClassifier,
    private val ai: CaptureClassifier?,
) : CaptureClassifier {
    override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? {
        rules.classify(text, options)?.takeIf { it.isNotEmpty() }?.let { return it }
        if (RuleCaptureClassifier.isBirthday(text)) return null
        return ai?.classify(text, options)
    }
}
