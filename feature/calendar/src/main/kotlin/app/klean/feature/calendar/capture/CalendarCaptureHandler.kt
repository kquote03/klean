package app.klean.feature.calendar.capture

import app.klean.core.common.ZoneProvider
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.calendar.CalendarKeys
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventDraft
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.NaturalTime
import app.klean.shared.time.toLocal
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** Phrase → event, offline: "dentist friday 9:30" is a one-hour event, "conference 3 December" an all-day one. */
object CaptureEventParser {
    private const val HOUR = 3_600_000L

    fun parse(text: String, nowMs: Long, zone: TimeZone, defaultReminder: Int?): EventDraft {
        val now = nowMs.toLocal(zone)
        val parsed = NaturalTime.parse(text, now)
        val title = parsed.rest.trim().ifEmpty { text.trim() }
        val date = parsed.date
        return when {
            date != null && parsed.minutes != null -> {
                val start = parsed.dateTime!!.toInstant(zone).toEpochMilliseconds()
                EventDraft(title, start, start + HOUR, zone = zone.id, reminderMinutes = defaultReminder)
            }
            date != null -> EventDraft(
                title,
                date.atStartOfDayIn(zone).toEpochMilliseconds(),
                date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds(),
                allDay = true,
                zone = zone.id,
            )
            else -> {
                val nextHour = LocalDateTime(now.date, LocalTime(now.hour, 0)).toInstant(zone).toEpochMilliseconds() + HOUR
                EventDraft(title, nextHour, nextHour + HOUR, zone = zone.id, reminderMinutes = defaultReminder)
            }
        }
    }
}

/** Calendar's part of universal capture. Saves at once, no AI; undo tombstones. */
class CalendarCaptureHandler @Inject constructor(
    private val repo: CalendarRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.CALENDAR
    override val actionSpecs: List<ActionSpec> =
        listOf(ActionSpec(RuleCaptureClassifier.ADD_EVENT, "Something happening at a date or time: an appointment, a meeting, a plan"))

    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.ADD_EVENT, 1f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        val reminder = settings.get(CalendarKeys.DefaultReminderMinutes).takeIf { it >= 0 }
        return repo.add(CaptureEventParser.parse(input.text, clock.now().toEpochMilliseconds(), zone.current(), reminder))
    }

    override suspend fun undo(recordId: String) = repo.delete(recordId)
}
