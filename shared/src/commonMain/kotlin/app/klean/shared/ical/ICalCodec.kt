/*
 * iCalendar (RFC 5545) VEVENT codec: DTSTART/DTEND/DURATION, RRULE, EXDATE, one VALARM, plus unmodelled VEVENT
 * properties kept verbatim. Used by the backend's CalDAV and by the Android calendar module (ICS import/export).
 *
 * Times: parsed values keep their original form ([ICalTime.value] + TZID), so a parse → write round trip is exact.
 * Klean-made times are written in UTC (`…Z`) or as dates, so no VTIMEZONE is ever needed. TZIDs are resolved as
 * IANA names; a VTIMEZONE block with a non-IANA TZID falls back to the caller's zone.
 * Not modelled: RECURRENCE-ID overrides (extra VEVENTs with the same UID are dropped), VTODO/VJOURNAL.
 */
package app.klean.shared.ical

import app.klean.shared.vcard.ContentLine
import app.klean.shared.vcard.ContentLines
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A DATE or DATE-TIME value as written: `20261110`, `20261110T100000Z`, or local time with/without [tzid]. */
data class ICalTime(val value: String, val tzid: String? = null, val isDate: Boolean = false) {

    /** Epoch ms. Dates are the start of the day in [zone]; floating times are read in [zone]. */
    fun toEpochMs(zone: TimeZone): Long {
        if (isDate) return parseDate(value).atStartOfDayIn(zone).toEpochMilliseconds()
        val utc = value.endsWith("Z")
        val ldt = parseDateTime(value.removeSuffix("Z"))
        val tz = when {
            utc -> TimeZone.UTC
            tzid != null -> runCatching { TimeZone.of(tzid!!) }.getOrDefault(zone)
            else -> zone
        }
        return ldt.toInstant(tz).toEpochMilliseconds()
    }

    companion object {
        fun utc(epochMs: Long): ICalTime {
            val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)
            return ICalTime(formatDate(t.date) + "T" + two(t.hour) + two(t.minute) + two(t.second) + "Z")
        }

        fun date(date: LocalDate): ICalTime = ICalTime(formatDate(date), isDate = true)

        fun dateOf(epochMs: Long, zone: TimeZone): ICalTime = date(Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date)

        private fun two(n: Int) = n.toString().padStart(2, '0')
        private fun formatDate(d: LocalDate) = d.year.toString().padStart(4, '0') + two(d.month.number) + two(d.day)

        internal fun parseDate(s: String) = LocalDate(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())

        internal fun parseDateTime(s: String): LocalDateTime {
            val d = parseDate(s)
            val t = s.substringAfter('T', "000000").padEnd(6, '0')
            return LocalDateTime(d.year, d.month.number, d.day, t.substring(0, 2).toInt(), t.substring(2, 4).toInt(), t.substring(4, 6).toInt())
        }

        internal fun from(cl: ContentLine): ICalTime? {
            val v = cl.value.trim()
            if (!Regex("^\\d{8}(T\\d{6}Z?)?$").matches(v)) return null
            val isDate = cl.param("VALUE").equals("DATE", ignoreCase = true) || v.length == 8
            return ICalTime(v, cl.param("TZID"), isDate)
        }
    }
}

data class ICalEvent(
    val uid: String,
    val summary: String = "",
    val description: String? = null,
    val location: String? = null,
    val start: ICalTime,
    /** DTEND; null when the event used DURATION (see [durationMinutes]) or had neither. */
    val end: ICalTime? = null,
    val durationMinutes: Int? = null,
    val rrule: String? = null,
    val exdates: List<ICalTime> = emptyList(),
    /** Minutes before start of the first VALARM, or null. */
    val alarmMinutesBefore: Int? = null,
    val sequence: Int = 0,
    val dtstamp: String? = null,
    val extras: List<ContentLine> = emptyList(),
) {
    val allDay: Boolean get() = start.isDate

    /** DTEND, or DTSTART + DURATION, or DTSTART (+1 day for all-day). */
    fun endEpochMs(zone: TimeZone): Long {
        end?.let { return it.toEpochMs(zone) }
        val s = start.toEpochMs(zone)
        durationMinutes?.let { return s + it * 60_000L }
        return if (allDay) s + 86_400_000L else s
    }
}

object ICalCodec {
    private val MODELLED = setOf(
        "UID", "SUMMARY", "DESCRIPTION", "LOCATION", "DTSTART", "DTEND", "DURATION", "RRULE", "EXDATE",
        "SEQUENCE", "DTSTAMP", "BEGIN", "END",
    )

    /** Every master VEVENT in the text (one per UID; the first wins). */
    fun parse(text: String): List<ICalEvent> {
        val events = LinkedHashMap<String, ICalEvent>()
        val stack = ArrayList<String>()
        var props = ArrayList<ContentLine>()
        var alarm: MutableList<ContentLine>? = null
        var alarmMinutes: Int? = null
        for (line in ContentLines.unfold(text)) {
            val cl = ContentLines.parse(line) ?: continue
            if (cl.name == "BEGIN") {
                val comp = cl.value.uppercase()
                stack += comp
                when (comp) {
                    "VEVENT" -> { props = ArrayList(); alarmMinutes = null }
                    "VALARM" -> alarm = ArrayList()
                }
                continue
            }
            if (cl.name == "END") {
                when (stack.removeLastOrNull()) {
                    "VALARM" -> {
                        if (alarmMinutes == null) {
                            alarm?.firstOrNull { it.name == "TRIGGER" && it.param("VALUE") == null }
                                ?.let { durationMinutes(it.value) }?.let { alarmMinutes = -it }
                        }
                        alarm = null
                    }
                    "VEVENT" -> build(props, alarmMinutes)?.let { e -> if (e.uid !in events) events[e.uid] = e }
                }
                continue
            }
            when (stack.lastOrNull()) {
                "VEVENT" -> props += cl
                "VALARM" -> alarm?.add(cl)
            }
        }
        return events.values.toList()
    }

    private fun build(props: List<ContentLine>, alarmMinutes: Int?): ICalEvent? {
        val uid = props.firstOrNull { it.name == "UID" }?.value ?: return null
        val start = props.firstOrNull { it.name == "DTSTART" }?.let { ICalTime.from(it) } ?: return null
        fun text(name: String) = props.firstOrNull { it.name == name }?.let { ContentLines.unescapeText(it.value) }
        return ICalEvent(
            uid = uid,
            summary = text("SUMMARY").orEmpty(),
            description = text("DESCRIPTION"),
            location = text("LOCATION"),
            start = start,
            end = props.firstOrNull { it.name == "DTEND" }?.let { ICalTime.from(it) },
            durationMinutes = props.firstOrNull { it.name == "DURATION" }?.let { durationMinutes(it.value) },
            rrule = props.firstOrNull { it.name == "RRULE" }?.value,
            exdates = props.filter { it.name == "EXDATE" }.flatMap { cl ->
                cl.value.split(',').mapNotNull { v -> ICalTime.from(cl.copy(value = v)) }
            },
            alarmMinutesBefore = alarmMinutes,
            sequence = props.firstOrNull { it.name == "SEQUENCE" }?.value?.trim()?.toIntOrNull() ?: 0,
            dtstamp = props.firstOrNull { it.name == "DTSTAMP" }?.value,
            extras = props.filter { it.name !in MODELLED },
        )
    }

    /** RFC 5545 DURATION to signed minutes: `-PT15M` → -15, `P1DT2H` → 1560, `-P1W` → -10080. */
    fun durationMinutes(raw: String): Int? {
        val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").find(raw.trim()) ?: return null
        val g = m.groupValues
        if (g.drop(2).all { it.isEmpty() }) return null
        fun n(i: Int) = g[i].toIntOrNull() ?: 0
        val minutes = n(2) * 10080 + n(3) * 1440 + n(4) * 60 + n(5) + n(6) / 60
        return if (g[1] == "-") -minutes else minutes
    }

    private fun formatDuration(minutes: Int): String {
        val sign = if (minutes < 0) "-" else ""
        val m = kotlin.math.abs(minutes)
        return when {
            m == 0 -> "PT0M"
            m % 1440 == 0 -> "${sign}P${m / 1440}D"
            else -> "${sign}PT" + (if (m >= 60) "${m / 60}H" else "") + (if (m % 60 != 0) "${m % 60}M" else "")
        }
    }

    fun write(events: List<ICalEvent>, prodId: String = "-//Klean//Klean//EN"): String = buildString {
        fun line(name: String, value: String, params: List<Pair<String, List<String>>> = emptyList()) {
            append(ContentLines.fold(ContentLines.format(ContentLine(null, name, params, value))))
        }
        fun time(name: String, t: ICalTime) {
            val params = buildList {
                if (t.isDate) add("VALUE" to listOf("DATE"))
                t.tzid?.let { add("TZID" to listOf(it)) }
            }
            line(name, t.value, params)
        }
        fun esc(s: String) = ContentLines.escapeText(s)

        line("BEGIN", "VCALENDAR")
        line("VERSION", "2.0")
        line("PRODID", prodId)
        line("CALSCALE", "GREGORIAN")
        for (e in events) {
            line("BEGIN", "VEVENT")
            line("UID", e.uid)
            line("DTSTAMP", e.dtstamp ?: "19700101T000000Z")
            time("DTSTART", e.start)
            e.end?.let { time("DTEND", it) }
            e.durationMinutes?.let { line("DURATION", formatDuration(it)) }
            if (e.summary.isNotEmpty()) line("SUMMARY", esc(e.summary))
            e.description?.let { line("DESCRIPTION", esc(it)) }
            e.location?.let { line("LOCATION", esc(it)) }
            e.rrule?.let { line("RRULE", it) }
            // One EXDATE line per value keeps mixed TZIDs/date kinds exact.
            e.exdates.forEach { time("EXDATE", it) }
            if (e.sequence != 0) line("SEQUENCE", e.sequence.toString())
            e.extras.forEach { append(ContentLines.fold(ContentLines.format(it))) }
            e.alarmMinutesBefore?.let {
                line("BEGIN", "VALARM")
                line("ACTION", "DISPLAY")
                line("DESCRIPTION", "Reminder")
                line("TRIGGER", formatDuration(-it))
                line("END", "VALARM")
            }
            line("END", "VEVENT")
        }
        line("END", "VCALENDAR")
    }
}
