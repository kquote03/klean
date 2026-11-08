package app.klean.feature.diet.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.KleanCard
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardEntry(val id: String, val timeLabel: String, val text: String)
data class DietDashboardState(val count: Int = 0, val entries: List<DashboardEntry> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DietDashboardViewModel @Inject constructor(
    private val repo: DietRepository,
    private val fmt: DietFormatter,
    ticker: MinuteTicker,
) : ViewModel() {
    val uiState: StateFlow<DietDashboardState> = ticker.ticks()
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date -> repo.observeDay(date) }
        .map { entries ->
            DietDashboardState(
                count = entries.size,
                entries = entries.takeLast(LAST_N).map { DashboardEntry(it.id, fmt.time(it.eatenAt), it.rawText) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietDashboardState())

    fun onSubmit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.logEntry(clean, CaptureSource.DASHBOARD) }
    }

    private companion object {
        const val LAST_N = 5
    }
}

@Composable
fun DietDashboardCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: DietDashboardViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    KleanCard(title = stringResource(R.string.diet_card_title), onOpen = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (state.entries.isEmpty()) {
                Text(
                    stringResource(R.string.diet_card_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            state.entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        e.timeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(e.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CaptureField(
                value = text,
                onValueChange = { text = it },
                onSubmit = {
                    vm.onSubmit(text)
                    text = ""
                },
                onMic = {}, // Voice input arrives in M6.
                onPhoto = null,
                placeholder = stringResource(R.string.diet_capture_placeholder),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
