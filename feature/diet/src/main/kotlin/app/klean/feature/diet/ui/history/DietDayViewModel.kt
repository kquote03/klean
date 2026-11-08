package app.klean.feature.diet.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.photo.PhotoStorage
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.DietRowBuilder
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.today.DietRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

data class DayPage(
    val date: LocalDate,
    val title: String,
    val rows: List<DietRow> = emptyList(),
    val loaded: Boolean = false,
) {
    val hasEntries: Boolean get() = rows.any { it is DietRow.Entry }
}

/** The day pager: one page per logical day, up to today, each with the same rows as Today. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietDayViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: DietRepository,
    private val clock: Clock,
    private val fmt: DietFormatter,
    private val ticker: MinuteTicker,
    photos: PhotoStorage,
) : ViewModel() {
    private val rowBuilder = DietRowBuilder(repo, fmt, photos)
    private val today: LocalDate = repo.todayDate()

    val pageCount: Int = DAYS_BACK + 1

    val initialPage: Int = run {
        val requested = savedState.get<String>("isoDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        (pageCount - 1 - requested.daysUntil(today).coerceAtLeast(0)).coerceIn(0, pageCount - 1)
    }

    fun dateForPage(page: Int): LocalDate = today.minus(pageCount - 1 - page, DateTimeUnit.DAY)

    fun day(date: LocalDate): Flow<DayPage> {
        val window = repo.windowFor(date)
        // Past days are complete: gaps end with the day and are never "ongoing".
        val nowFlow: Flow<Long> =
            if (date >= today) ticker.ticks().map { clock.now().toEpochMilliseconds() } else flowOf(window.end)
        val entriesWithItems = repo.observeDay(date).flatMapLatest { entries ->
            repo.observeItems(entries.map { it.id }).map { items -> entries to items }
        }
        return combine(entriesWithItems, repo.observeViewPrefs(), nowFlow) { (entries, items), prefs, now ->
            DayPage(date, fmt.date(date), rowBuilder.build(date, entries, items, prefs, now), loaded = true)
        }
    }

    fun undoRemove(id: String) {
        viewModelScope.launch { repo.restore(id) }
    }

    companion object {
        const val DAYS_BACK = 3_650
    }
}
