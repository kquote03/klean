package app.klean.feature.settings.quiet

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.klean.feature.settings.R
import app.klean.shared.time.QuietHours
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "Sleep from" and "Wake up at" rows; each opens a time picker. Shared by setup and Settings. */
@Composable
fun SleepWindowPicker(
    value: QuietHours,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    Column(modifier) {
        TimeRow(stringResource(R.string.quiet_sleep_from), value.startMinute) { editing = true }
        TimeRow(stringResource(R.string.quiet_wake_at), value.endMinute) { editing = false }
    }
    editing?.let { isStart ->
        TimeDialog(
            initialMinute = if (isStart) value.startMinute else value.endMinute,
            onDismiss = { editing = null },
            onPick = { m ->
                if (isStart) onStart(m) else onEnd(m)
                editing = null
            },
        )
    }
}

@Composable
private fun TimeRow(label: String, minute: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = { Text(formatMinute(minute)) },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(picker.hour * 60 + picker.minute) }) { Text(stringResource(R.string.quiet_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.quiet_cancel)) } },
        text = { TimePicker(picker) },
    )
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

internal fun formatMinute(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(timeFormat)
