package app.klean.feature.contacts.capture

import app.klean.core.common.ZoneProvider
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.toDraft
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.NaturalTime
import app.klean.shared.time.toLocal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalDateTime

/** What a birthday phrase says: whose, and when (no year unless one is given). */
data class BirthdayPhrase(val name: String, val birthday: Birthday?)

/**
 * "Sara's birthday is March 3", "Mum's bday 12/4", "birthday Omar 3 March 1990", "Lina's birthday is tomorrow".
 * Rules only, never AI. Day/month order for slashes (UAE). Pure.
 */
object BirthdayText {
    private val OPTS = setOf(RegexOption.IGNORE_CASE)
    private const val MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val MONTH_DAY = Regex("""\b($MONTHS)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""", OPTS)
    private val DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b\.?(?:,?\s+(\d{4}))?""", OPTS)
    private val SLASH = Regex("""\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{4}))?\b""")
    private val ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val MARKER = Regex("""(?:'s|’s|s'|s’)?\s*\b(?:birthday|b-?day)\b""", OPTS)
    private val FILLER = Regex("""\b(?:is|was|on|the|it's|its|at|of|for|add|save|remember|note)\b""", OPTS)

    fun parse(text: String, now: LocalDateTime): BirthdayPhrase {
        var work = text.trim()
        var birthday: Birthday? = null
        fun take(m: MatchResult) { work = work.replaceRange(m.range, " ") }

        ISO.find(work)?.let { m -> Birthday.of(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt())?.let { birthday = it; take(m) } }
        if (birthday == null) MONTH_DAY.find(work)?.let { m ->
            Birthday.of(month(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())?.let { birthday = it; take(m) }
        }
        if (birthday == null) DAY_MONTH.find(work)?.let { m ->
            Birthday.of(month(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())?.let { birthday = it; take(m) }
        }
        if (birthday == null) SLASH.find(work)?.let { m ->
            Birthday.of(m.groupValues[2].toInt(), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())?.let { birthday = it; take(m) }
        }
        if (birthday == null) {
            val p = NaturalTime.parse(work, now)
            val d = p.date
            if (d != null) {
                birthday = Birthday.of(d.month.ordinal + 1, d.day, null)
                work = p.rest
            }
        }

        val marker = MARKER.find(work)
        val rawName = if (marker != null) {
            val before = work.substring(0, marker.range.first)
            val after = work.substring(marker.range.last + 1)
            if (before.isNotBlank()) before else after
        } else {
            work
        }
        val name = rawName.replace(FILLER, " ").replace(Regex("[\\s,:;]+"), " ").trim().trim('-', '.', '!', '?')
            .let { if (it.equals("my", true) || it.equals("mine", true)) "Me" else it }
            .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        return BirthdayPhrase(name, birthday)
    }

    private fun month(s: String): Int = MONTHS.split('|').indexOfFirst { Regex("^(?:$it)$", OPTS).matches(s) } + 1
}

/**
 * `add_birthday`: "Sara's birthday is March 3" sets the birthday on the contact called Sara, or creates her. Saved at
 * once by rules alone: this handler has no AI dependency, and birthday phrases never reach an AI provider
 * ([app.klean.shared.capture.CompositeCaptureClassifier]). With Contacts switched off there is no handler for the
 * phrase, so the router's date rule turns it into a dated task instead.
 */
@Singleton
class ContactsCaptureHandler @Inject constructor(
    private val repo: ContactRepository,
    private val zone: ZoneProvider,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.CONTACTS
    override val actionSpecs = listOf(ActionSpec(RuleCaptureClassifier.ADD_BIRTHDAY, "Someone's birthday"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.ADD_BIRTHDAY, 1f)

    /** What each capture changed, so Undo puts it back: null = the contact was created by it. */
    private val previous = mutableMapOf<String, Birthday?>()
    private val created = mutableSetOf<String>()

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        val phrase = BirthdayText.parse(input.text, input.capturedAt.toLocal(zone.current()))
        val name = phrase.name.ifBlank { input.text.trim() }
        val existing = repo.findByName(name)
        val id = when {
            existing != null && phrase.birthday != null -> {
                synchronized(previous) { previous[existing.id] = existing.birthday }
                repo.setBirthday(name, phrase.birthday)
            }
            existing != null -> existing.id.also { synchronized(previous) { previous[it] = existing.birthday } }
            else -> repo.add(ContactDraft(displayName = name, birthday = phrase.birthday)).also { synchronized(previous) { created += it } }
        }
        return id
    }

    override suspend fun undo(recordId: String) {
        val wasCreated = synchronized(previous) { created.remove(recordId) }
        if (wasCreated) return repo.delete(recordId)
        val had = synchronized(previous) { if (recordId in previous) previous.remove(recordId) to true else null to false }
        if (!had.second) return
        val c = repo.get(recordId) ?: return
        val draft = c.toDraft().copy(birthday = had.first)
        repo.update(recordId, draft)
    }
}
