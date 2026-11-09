package app.klean.feature.diet.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.dashboard.DashboardEntry
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UnlockSheetState(
    /** Today's entries, newest first. */
    val entries: List<DashboardEntry> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UnlockSheetViewModel @Inject constructor(
    private val repo: DietRepository,
    private val fmt: DietFormatter,
    ticker: MinuteTicker,
) : ViewModel() {
    // Repeats come from the same observed day, so they stay current after a log from here.
    val uiState: StateFlow<UnlockSheetState> = ticker.ticks()
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date -> repo.observeDay(date) }
        .map { entries ->
            UnlockSheetState(
                entries = entries.asReversed().map { DashboardEntry(it.id, fmt.time(it.eatenAt), it.rawText) },
                suggestions = repo.quickRepeats(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnlockSheetState())

    fun log(text: String): Job = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isNotEmpty()) repo.logEntry(clean, CaptureSource.UNLOCK_SHEET)
    }
}
