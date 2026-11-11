package app.klean.feature.workout.ui

import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.feature.workout.data.SessionWithSets
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutWeekTest {
    private val sync = SyncColumns(0, 0, "d", false, 0)
    private val days = lastSevenDays(LocalDate(2026, 11, 10))
    private val day = 86_400_000L

    // Day n of November as epoch ms in a "zone" where each day is n * day: enough for the grouping.
    private fun s(id: String, d: Int, minutes: Int?, open: Boolean = false) = SessionWithSets(
        WorkoutSessionEntity(id, startedAt = d * day, endedAt = if (open) null else d * day + (minutes ?: 0) * 60_000L, sync = sync),
        emptyList(),
    )
    private val dateOf: (Long) -> LocalDate = { LocalDate(2026, 11, (it / day).toInt()) }

    @Test fun countsMinutesAndBarsForTheLastSevenDays() {
        val w = WorkoutWeek.of(listOf(s("a", 5, 47), s("b", 9, 28), s("old", 1, 60), s("run", 10, 0, open = true)), days, dateOf)
        assertEquals(LocalDate(2026, 11, 4), days.first())
        assertEquals(2, w.sessions)
        assertEquals(75, w.minutes)
        assertEquals(listOf(null, 1f, null, null, null, 28f / 47, null), w.bars)
        assertEquals(9 * day, w.lastAt)
    }

    @Test fun aSessionWithoutALengthStillShowsADay() {
        val w = WorkoutWeek.of(listOf(s("a", 6, null)), days, dateOf)
        assertEquals(1, w.sessions)
        assertEquals(0.5f, w.bars[2])
    }

    @Test fun anEmptyWeekHasNoBars() {
        val w = WorkoutWeek.of(emptyList(), days, dateOf)
        assertEquals(0, w.sessions)
        assertEquals(List(7) { null }, w.bars)
    }
}
