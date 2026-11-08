package app.klean.shared.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MINUTES = 1440

fun Long.toLocal(zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
fun minuteOfDay(epochMs: Long, zone: TimeZone): Int = epochMs.toLocal(zone).let { it.hour * 60 + it.minute }
fun atMinute(date: LocalDate, minute: Int, zone: TimeZone): Long =
    LocalDateTime(date, LocalTime(minute / 60, minute % 60)).toInstant(zone).toEpochMilliseconds()
