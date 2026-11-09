package app.klean.feature.diet.unlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import app.klean.core.capture.CaptureActivity
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.RepeatChips
import app.klean.core.design.theme.KleanTheme
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * "Today so far" after a long break (plan §6.3). Opened by the unlock-sheet notification's
 * full-screen intent or its tap. Deliberately *not* `showWhenLocked`: it appears once the user has
 * unlocked, over whatever they open. One tap on Done (or outside the sheet) closes it.
 */
@AndroidEntryPoint
class UnlockSheetActivity : ComponentActivity() {
    private val vm: UnlockSheetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(false)
        setContent { KleanTheme { UnlockSheet(vm, onDone = ::finish) } }
    }

    override fun onResume() {
        super.onResume()
        // The sheet is showing; its notification has done its job.
        NotificationManagerCompat.from(this).cancel(NotificationIds.UNLOCK)
    }

    companion object {
        const val EXTRA_LOG_ID = "log_id"

        fun intent(ctx: Context, logId: String): Intent =
            Intent(ctx, UnlockSheetActivity::class.java)
                .putExtra(EXTRA_LOG_ID, logId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockSheet(vm: UnlockSheetViewModel, onDone: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        UnlockSheetContent(state, onLog = { vm.log(it) }, onDone = onDone, modifier = Modifier.navigationBarsPadding().imePadding())
    }
}

@Composable
internal fun UnlockSheetContent(state: UnlockSheetState, onLog: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.entries.isEmpty()) {
            Text(
                stringResource(R.string.unlock_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(state.entries, key = { it.id }) { e -> EntryRow(e.timeLabel, e.text, meta = null, onClick = {}) }
            }
        }
        CaptureField(
            value = text,
            onValueChange = { text = it },
            onSubmit = {
                onLog(text)
                text = ""
            },
            // Voice lives in the capture sheet (mic permission, Undo chip); hand over to it.
            onMic = {
                ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.UNLOCK_SHEET, ModuleId.DIET, startVoice = true))
                onDone()
            },
            onPhoto = null,
            placeholder = stringResource(R.string.diet_capture_placeholder),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        RepeatChips(state.suggestions, onRepeat = onLog, modifier = Modifier.padding(horizontal = 16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.unlock_done))
        }
    }
}
