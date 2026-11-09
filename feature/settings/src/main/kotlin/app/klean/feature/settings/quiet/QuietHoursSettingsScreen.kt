package app.klean.feature.settings.quiet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import app.klean.shared.time.QuietHours
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class QuietHoursSettingsViewModel @Inject constructor(private val store: QuietHoursStore) : ViewModel() {
    val quietHours: StateFlow<QuietHours> =
        store.quietHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuietHours.DEFAULT)

    fun setStart(minute: Int) = viewModelScope.launch { store.set(store.quietHours.first().copy(startMinute = minute)) }
    fun setEnd(minute: Int) = viewModelScope.launch { store.set(store.quietHours.first().copy(endMinute = minute)) }
}

@Composable
fun QuietHoursSettingsScreen(onBack: () -> Unit, vm: QuietHoursSettingsViewModel = hiltViewModel()) {
    val value by vm.quietHours.collectAsStateWithLifecycle()
    SettingsSubScaffold(R.string.settings_quiet_hours, onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.quiet_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SleepWindowPicker(value, onStart = { vm.setStart(it) }, onEnd = { vm.setEnd(it) })
        }
    }
}
