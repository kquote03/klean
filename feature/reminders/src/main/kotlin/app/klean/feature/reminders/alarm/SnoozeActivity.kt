package app.klean.feature.reminders.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.klean.core.common.ApplicationScope
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.ui.theme.KleanThemeFromPrefs
import app.klean.feature.reminders.R
import app.klean.feature.reminders.ReminderActions
import app.klean.feature.reminders.Snooze
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Later…" on a reminder notification: 1 hour, Tomorrow, or a time the user picks. A small dialog over whatever is open. */
@AndroidEntryPoint
class SnoozeActivity : ComponentActivity() {
    @Inject lateinit var actions: ReminderActions
    @Inject lateinit var prefs: DevicePrefs
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        setContent {
            KleanThemeFromPrefs(prefs) {
                SnoozeChoices(
                    onClose = ::finish,
                    onSnooze = { o -> act { actions.snooze(id, o) } },
                    onPick = { m -> act { actions.snoozeTo(id, m) } },
                )
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        appScope.launch { block() }
        finish()
    }

    companion object {
        const val EXTRA_ID = "reminderId"

        fun pendingIntent(ctx: Context, id: String): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, SnoozeActivity::class.java)
                .setData("klean-reminder://$id/later".toUri())
                .putExtra(EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

@Composable
private fun SnoozeChoices(onClose: () -> Unit, onSnooze: (Snooze) -> Unit, onPick: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        TimePickDialog(onDismiss = onClose, onPicked = onPick)
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.reminders_later_title)) },
        text = {
            Column {
                listOf(
                    R.string.reminders_action_snooze_hour to { onSnooze(Snooze.ONE_HOUR) },
                    R.string.reminders_action_tomorrow to { onSnooze(Snooze.TOMORROW) },
                    R.string.reminders_action_pick_time to { picking = true },
                ).forEach { (label, onClick) ->
                    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.reminders_cancel)) } },
    )
}

/** A time picker in a dialog; returns the minute of day. Follows the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(onDismiss: () -> Unit, onPicked: (Int) -> Unit, initialMinute: Int = 9 * 60) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60, initialMinute = initialMinute % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(ctx),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_pick_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.reminders_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminders_cancel)) } },
    )
}
