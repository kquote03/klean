package app.klean.feature.diet.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.data.DietViewPrefs
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.GapDetector
import app.klean.shared.time.MINUTE_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

sealed interface DietRow {
    val key: String

    data class Entry(
        val id: String,
        val timeLabel: String,
        val text: String,
        val itemsSummary: String?,
        val providerName: String?,
        val hasPhoto: Boolean,
    ) : DietRow {
        override val key get() = id
    }

    /** A long stretch without entries. Shown neutrally; never a judgement. */
    data class GapRow(val label: String, val startMs: Long) : DietRow {
        override val key get() = "gap-$startMs"
    }
}

data class DietUiState(
    val date: LocalDate? = null,
    val rows: List<DietRow> = emptyList(),
    val repeats: List<String> = emptyList(),
    val showCalories: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietViewModel @Inject constructor(
    private val repo: DietRepository,
    private val clock: Clock,
    private val fmt: DietFormatter,
    private val ticker: MinuteTicker,
) : ViewModel() {
    private fun now() = clock.now().toEpochMilliseconds()

    private val nowFlow: Flow<Long> get() = ticker.ticks().map { now() }

    val uiState: StateFlow<DietUiState> = nowFlow
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            // Chips are re-ranked whenever today's entries change, from any surface (delete, undo, widget, tile).
            val dayWithRepeats = entriesWithItems(date).map { it to repo.quickRepeats() }
            combine(dayWithRepeats, repo.observeViewPrefs(), nowFlow) { (day, reps), prefs, now ->
                val (entries, items) = day
                DietUiState(date, buildRows(date, entries, items, prefs, now), reps, prefs.showCalories, loaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    fun onSubmit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.logEntry(clean, CaptureSource.MODULE_SCREEN) }
    }

    fun onRepeat(text: String) = onSubmit(text)

    private fun entriesWithItems(date: LocalDate): Flow<Pair<List<FoodEntryEntity>, List<FoodItemEntity>>> =
        repo.observeDay(date).flatMapLatest { entries ->
            repo.observeItems(entries.map { it.id }).map { items -> entries to items }
        }

    private fun buildRows(
        date: LocalDate,
        entries: List<FoodEntryEntity>,
        items: List<FoodItemEntity>,
        prefs: DietViewPrefs,
        now: Long,
    ): List<DietRow> {
        val window = repo.windowFor(date)
        val gaps = GapDetector.find(entries.map { it.eatenAt }, window, now, prefs.gapThresholdMinutes * MINUTE_MS)
        val byEntry = items.filter { it.state != FoodItemState.REJECTED }.groupBy { it.entryId }
        val entryRows = entries.map { e ->
            val its = byEntry[e.id].orEmpty()
            val summary = its.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
                ?.let { s ->
                    val kcal = its.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum()
                    if (prefs.showCalories && kcal != null) "$s · $kcal kcal" else s
                }
            e.eatenAt to DietRow.Entry(
                id = e.id,
                timeLabel = fmt.time(e.eatenAt),
                text = e.rawText,
                itemsSummary = summary,
                providerName = its.firstOrNull { it.providerId != "user" }?.providerId,
                hasPhoto = e.photoFile != null,
            )
        }
        val gapRows = gaps.map { g -> g.start to DietRow.GapRow(fmt.gap(g), g.start) }
        // At equal times the entry comes first: a gap starts after the entry that opens it.
        return (entryRows.map { Triple(it.first, 0, it.second) } + gapRows.map { Triple(it.first, 1, it.second) })
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
    }
}
