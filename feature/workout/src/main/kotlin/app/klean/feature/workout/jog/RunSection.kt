package app.klean.feature.workout.jog

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.feature.workout.ui.WorkoutTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the run card shows. Times in seconds. */
data class RunUi(
    val running: Boolean = false,
    val distanceM: Double = 0.0,
    val elapsedS: Long = 0,
    val paceSecPerKm: Int? = null,
    val waitingForGps: Boolean = false,
)

@HiltViewModel
class RunViewModel @Inject constructor(private val tracker: JogTracker, ticker: WorkoutTicker) : ViewModel() {
    val state: StateFlow<RunUi> = combine(tracker.state, ticker.ticks(1_000)) { s, now ->
        if (!s.running) RunUi() else RunUi(
            running = true,
            distanceM = s.distanceM,
            elapsedS = ((now - s.startedAt) / 1000).coerceAtLeast(0),
            paceSecPerKm = s.paceSecPerKm(now),
            waitingForGps = s.lastFixAt == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunUi())

    fun start(ctx: Context) = JogTrackingService.start(ctx)
    fun finish(ctx: Context) = JogTrackingService.finish(ctx)
}

/** \"12:03\" or \"1:02:03\". */
fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Starts a run, asking for location just in time; [refused] is true once the user said no. */
class RunStarter(val start: () -> Unit, val refused: Boolean)

/**
 * "Run" on the Workout screen's saved row. Location is asked for just in time; without it the screen explains
 * calmly and nothing else changes.
 */
@Composable
fun rememberRunStarter(vm: RunViewModel): RunStarter {
    val ctx = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            refused = false
            vm.start(ctx)
        } else {
            refused = true
        }
    }
    return RunStarter(
        start = {
            if (JogTrackingService.hasLocation(ctx)) vm.start(ctx)
            else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        },
        refused = refused,
    )
}
