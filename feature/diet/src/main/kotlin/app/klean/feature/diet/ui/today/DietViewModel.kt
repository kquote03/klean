package app.klean.feature.diet.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.core.capture.voice.VoiceSession
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.photo.PhotoStorage
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.DietRowBuilder
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.caloriesSoFar
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

sealed interface DietRow {
    val key: String

    data class Entry(
        val id: String,
        val timeLabel: String,
        val text: String,
        /** AI items under the raw text: "shawarma · 1 wrap". Empty when there are none. */
        val itemLines: List<String>,
        /** Wire id of the provider that produced the items, for the chip. */
        val providerId: String?,
        /** Absolute path of the photo, for the row thumbnail. */
        val photoPath: String?,
    ) : DietRow {
        override val key get() = id
        val hasPhoto: Boolean get() = photoPath != null
    }

    /** A long stretch without entries. Shown neutrally; never a judgement. */
    data class GapRow(val label: String, val startMs: Long, val midpointMs: Long) : DietRow {
        override val key get() = "gap-$startMs"
    }
}

/** One of the week's earlier entries on the Diet screen: "Lentil soup, bread", "Monday", "19:40". */
data class EarlierEntry(val id: String, val text: String, val day: String, val time: String)

data class DietUiState(
    val date: LocalDate? = null,
    val rows: List<DietRow> = emptyList(),
    val repeats: List<String> = emptyList(),
    val showCalories: Boolean = false,
    /** "~1,850 kcal so far", only when calories are on and known. */
    val caloriesSoFar: String? = null,
    val loaded: Boolean = false,
    /** The last few entries of the six days before today, newest first (Track › Diet's "Earlier this week"). */
    val earlier: List<EarlierEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietViewModel @Inject constructor(
    private val repo: DietRepository,
    private val clock: Clock,
    private val fmt: DietFormatter,
    private val ticker: MinuteTicker,
    recognizer: VoiceRecognizer,
    private val photos: PhotoStorage,
) : ViewModel() {
    private val rows = DietRowBuilder(repo, fmt, photos)
    private val _field = MutableStateFlow("")

    /** The capture field's text. Here so voice Undo can put the words back. */
    val field: StateFlow<String> = _field.asStateFlow()

    val voice = VoiceSession(
        scope = viewModelScope,
        recognizer = recognizer,
        save = { text -> repo.logEntry(text, CaptureSource.MODULE_SCREEN, viaVoice = true) },
        undo = { u ->
            repo.delete(u.recordId)
            if (_field.value.isBlank()) _field.value = u.text
        },
    )

    private fun now() = clock.now().toEpochMilliseconds()

    private val nowFlow: Flow<Long> get() = ticker.ticks().map { now() }

    val uiState: StateFlow<DietUiState> = nowFlow
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            // Chips are re-ranked whenever today's entries change, from any surface (delete, undo, widget, tile).
            val dayWithRepeats = entriesWithItems(date).map { it to repo.quickRepeats() }
            combine(dayWithRepeats, repo.observeViewPrefs(), nowFlow, earlierThisWeek(date)) { (day, reps), prefs, now, earlier ->
                val (entries, items) = day
                DietUiState(
                    date, rows.build(date, entries, items, prefs, now), reps, prefs.showCalories,
                    caloriesSoFar(items, prefs.showCalories, fmt), loaded = true, earlier = earlier,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietUiState())

    fun onTextChange(text: String) {
        _field.value = text
    }

    fun onSubmit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        _field.value = ""
        viewModelScope.launch { repo.logEntry(clean, CaptureSource.MODULE_SCREEN) }
    }

    /** A quick-repeat chip: logs at once and leaves the field alone. */
    fun onRepeat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.logEntry(clean, CaptureSource.MODULE_SCREEN) }
    }

    /** Undo from the "Entry removed" snackbar shown after deleting on the entry screen. */
    fun undoRemove(id: String) {
        viewModelScope.launch { repo.restore(id) }
    }

    override fun onCleared() {
        voice.close()
    }

    private fun earlierThisWeek(today: LocalDate): Flow<List<EarlierEntry>> =
        combine((1..EARLIER_DAYS).map { back -> today.minus(DatePeriod(days = back)).let { d -> repo.observeDay(d).map { l -> l.map { d to it } } } }) { days ->
            days.flatMap { it }.sortedByDescending { it.second.eatenAt }.take(EARLIER_MAX).map { (d, e) ->
                EarlierEntry(e.id, e.rawText, java.time.DayOfWeek.of(d.dayOfWeek.isoDayNumber).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()), fmt.time(e.eatenAt))
            }
        }

    private fun entriesWithItems(date: LocalDate): Flow<Pair<List<FoodEntryEntity>, List<FoodItemEntity>>> =
        repo.observeDay(date).flatMapLatest { entries ->
            repo.observeItems(entries.map { it.id }).map { items -> entries to items }
        }

    private companion object {
        const val EARLIER_DAYS = 6
        const val EARLIER_MAX = 5
    }
}
