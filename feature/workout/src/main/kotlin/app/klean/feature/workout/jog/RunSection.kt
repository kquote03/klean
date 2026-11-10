package app.klean.feature.workout.jog

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.feature.workout.R
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

/**
 * \"Start a run\" on the Workout screen, or the run in progress with Finish. Location is asked for just in time;
 * without it the button explains calmly and nothing else changes.
 */
@Composable
fun RunSection(vm: RunViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsStateWithLifecycle()
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
    RunSectionContent(
        state = s,
        refused = refused,
        onStart = {
            if (JogTrackingService.hasLocation(ctx)) vm.start(ctx)
            else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        },
        onFinish = { vm.finish(ctx) },
        modifier = modifier,
    )
}

@Composable
fun RunSectionContent(state: RunUi, refused: Boolean, onStart: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.running) {
        Column(modifier) {
            OutlinedButton(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsRun, contentDescription = null)
                Text(stringResource(R.string.workout_run_start), Modifier.padding(start = 8.dp))
            }
            if (refused) {
                Text(
                    stringResource(R.string.workout_run_needs_location), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }
    val ctx = LocalContext.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.workout_run_notification_title), style = MaterialTheme.typography.titleMedium)
            Text(JogTrackingService.formatKm(ctx, state.distanceM), style = MaterialTheme.typography.displaySmall)
            val pace = state.paceSecPerKm?.let { stringResource(R.string.workout_run_pace, clock(it.toLong())) }
            Text(
                listOfNotNull(clock(state.elapsedS), pace).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.waitingForGps) {
                Text(stringResource(R.string.workout_run_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.workout_run_finish)) }
            }
        }
    }
}
