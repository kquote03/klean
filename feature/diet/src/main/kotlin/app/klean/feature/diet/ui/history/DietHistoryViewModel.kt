package app.klean.feature.diet.ui.history

import androidx.lifecycle.ViewModel
import app.klean.core.common.ZoneProvider
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.observeCountsByLogicalDay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One day cell: a neutral count, nothing else. Zero shows nothing at all. */
data class HistoryDay(val date: LocalDate, val count: Int, val isToday: Boolean, val isFuture: Boolean)

data class HistoryMonth(
    /** First day of the month. */
    val month: LocalDate,
    val days: List<HistoryDay> = emptyList(),
    val canGoNext: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * Month grid data for diet history. Entries are counted by logical day (the day turns at the middle
 * of the sleep window, 04:00 by default), so a 01:00 snack counts toward the evening before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietHistoryViewModel @Inject constructor(
    private val repo: DietRepository,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val today: LocalDate = repo.todayDate()
    private val currentMonth = LocalDate(today.year, today.month, 1)

    /** Pages for the month pager; the last page is the current month. */
    val pageCount: Int = MONTHS_BACK + 1

    fun monthForPage(page: Int): LocalDate = currentMonth.minus(pageCount - 1 - page, DateTimeUnit.MONTH)

    fun month(first: LocalDate): Flow<HistoryMonth> {
        val start = LocalDate(first.year, first.month, 1)
        val last = start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return repo.observeCountsByLogicalDay(start, last) { zone.current() }.map { counts ->
            val days = generateSequence(start) { d -> d.plus(1, DateTimeUnit.DAY).takeIf { it <= last } }
                .map { d -> HistoryDay(d, counts[d] ?: 0, isToday = d == today, isFuture = d > today) }
                .toList()
            HistoryMonth(start, days, canGoNext = start < currentMonth, loaded = true)
        }
    }

    companion object {
        /** Ten years back is more than anyone scrolls; pages are cheap. */
        const val MONTHS_BACK = 120
    }
}
