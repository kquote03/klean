package app.klean.feature.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.component.SwitchListItem
import app.klean.core.ui.module.WidgetRefresher
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppearanceUiState(
    val loaded: Boolean = false,
    val wallpaperColours: Boolean = false,
    /** Material You needs Android 12; below that the switch is hidden. */
    val wallpaperColoursSupported: Boolean = false,
)

@HiltViewModel
class AppearanceViewModel(
    private val prefs: DevicePrefs,
    private val widgets: WidgetRefresher,
    private val wallpaperColoursSupported: Boolean,
) : ViewModel() {
    @Inject constructor(prefs: DevicePrefs, widgets: WidgetRefresher) :
        this(prefs, widgets, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val state: StateFlow<AppearanceUiState> = prefs.dynamicColor
        .map { AppearanceUiState(loaded = true, wallpaperColours = it, wallpaperColoursSupported = wallpaperColoursSupported) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState(wallpaperColoursSupported = wallpaperColoursSupported))

    fun setWallpaperColours(on: Boolean) {
        viewModelScope.launch {
            prefs.setDynamicColor(on)
            // The app re-themes itself from the flow; widgets on the home screen need a nudge.
            widgets.refreshAll()
        }
    }
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    SettingsSubScaffold(R.string.settings_appearance, onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (s.wallpaperColoursSupported) {
                SwitchListItem(
                    checked = s.wallpaperColours,
                    onCheckedChange = vm::setWallpaperColours,
                    headline = { Text(stringResource(R.string.appearance_wallpaper_colours)) },
                    supporting = { Text(stringResource(R.string.appearance_wallpaper_colours_summary)) },
                )
            }
            Text(
                stringResource(R.string.appearance_dark_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
