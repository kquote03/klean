package app.klean.feature.reminders.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.klean.core.common.ApplicationScope
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.ui.theme.KleanThemeFromPrefs
import app.klean.feature.reminders.R
import app.klean.feature.reminders.RINGING
import app.klean.feature.reminders.ReminderActions
import app.klean.feature.reminders.ReminderFormatter
import app.klean.feature.reminders.Snooze
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The ringing alarm, full screen and over the lock screen. Big buttons: Dismiss, Snooze 10 min, 1 hour,
 * Tomorrow, Pick a time. Dismiss stops it and nothing else (never completes a task). Closes itself when the
 * alarm is handled elsewhere (notification) or goes quiet after 10 minutes.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var ringState: AlarmRingState
    @Inject lateinit var fmt: ReminderFormatter
    @Inject lateinit var prefs: DevicePrefs
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val title = MutableStateFlow("")
    private val time = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        setContent {
            KleanThemeFromPrefs(prefs) {
                val t by title.collectAsStateWithLifecycle()
                val tm by time.collectAsStateWithLifecycle()
                AlarmScreen(
                    title = t, time = tm,
                    onDismiss = { act { actions.dismiss(id) } },
                    onSnooze = { o -> act { actions.snooze(id, o) } },
                    onPick = { m -> act { actions.snoozeTo(id, m) } },
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val r = db.reminderDao().get(id)
                if (r == null || r.sync.deleted || r.state != RINGING) {
                    finish()
                    return@repeatOnLifecycle
                }
                title.value = r.title
                time.value = fmt.time(r.fireAt)
                var seen = false
                ringState.current.collect { ringing ->
                    if (ringing == id) seen = true else if (seen) finish()
                }
            }
        }
    }

    /** Runs the action beyond this screen's life (it closes at once). */
    private fun act(block: suspend () -> Unit) {
        appScope.launch { block() }
        finish()
    }

    companion object {
        const val EXTRA_ID = "reminderId"

        fun intent(ctx: Context, id: String): Intent = Intent(ctx, AlarmActivity::class.java)
            .setData("klean-reminder://$id/alarm".toUri())
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun pendingIntent(ctx: Context, id: String): PendingIntent = PendingIntent.getActivity(
            ctx, 0, intent(ctx, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmScreen(
    title: String,
    time: String,
    onDismiss: () -> Unit,
    onSnooze: (Snooze) -> Unit,
    onPick: (Int) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.reminders_action_dismiss), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onSnooze(Snooze.TEN_MINUTES) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.reminders_action_snooze_10))
                }
                FilledTonalButton(onClick = { onSnooze(Snooze.ONE_HOUR) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.reminders_action_snooze_hour))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSnooze(Snooze.TOMORROW) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.reminders_action_tomorrow))
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.reminders_action_pick_time))
                }
            }
        }
    }
    if (picking) TimePickDialog(onDismiss = { picking = false }, onPicked = { picking = false; onPick(it) })
}
