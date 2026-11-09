package app.klean.feature.diet.ui

import app.klean.feature.diet.data.DietRepository
import app.klean.shared.time.DayWindows
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Entries per logical day (the day turns in the sleep window), so a 01:00 snack counts toward the evening before. */
fun countByLogicalDay(times: List<Long>, quietHours: QuietHours, tz: TimeZone): Map<LocalDate, Int> =
    times.groupingBy { DayWindows.logicalDate(it, quietHours, tz) }.eachCount()

/** Live entry counts per logical day for [from]..[to], both inclusive. Used by History and Today's week strip. */
@OptIn(ExperimentalCoroutinesApi::class)
fun DietRepository.observeCountsByLogicalDay(from: LocalDate, to: LocalDate, tz: () -> TimeZone): Flow<Map<LocalDate, Int>> =
    observeViewPrefs().flatMapLatest { prefs ->
        val zone = tz()
        val start = DayWindows.forDate(from, prefs.quietHours, zone).start
        val end = DayWindows.forDate(to, prefs.quietHours, zone).end
        observeEatenTimes(start, end).map { times -> countByLogicalDay(times, prefs.quietHours, zone) }
    }
