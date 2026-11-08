package app.klean.feature.diet.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavBackStackEntry
import app.klean.feature.diet.ui.RemovedEntrySnackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.capture.voice.VoiceStatus
import app.klean.core.capture.voice.VoiceUiState
import app.klean.core.capture.voice.rememberVoiceInput
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.RepeatChips
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.dietRows

@Composable
fun DietScreen(
    onOpenEntry: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    backStackEntry: NavBackStackEntry? = null,
    vm: DietViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    backStackEntry?.let { RemovedEntrySnackbar(it, snackbar, onUndo = vm::undoRemove) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val text by vm.field.collectAsStateWithLifecycle()
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    DietScreenContent(
        state = state,
        text = text,
        voice = voice,
        onTextChange = vm::onTextChange,
        onSubmit = vm::onSubmit,
        onRepeat = vm::onRepeat,
        onOpenEntry = onOpenEntry,
        onOpenHistory = onOpenHistory,
        onMic = rememberVoiceInput(vm.voice),
        onUndoVoice = vm.voice::undoLast,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreenContent(
    state: DietUiState,
    text: String,
    voice: VoiceUiState,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onMic: () -> Unit,
    onUndoVoice: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val focus = remember { FocusRequester() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.diet_today_title)) },
            actions = {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.diet_history_open))
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Pinned under the top bar: logging is always one action away.
            CaptureField(
                value = if (voice.listening && voice.partial.isNotBlank()) voice.partial else text,
                onValueChange = onTextChange,
                onSubmit = { onSubmit(text) },
                onMic = onMic,
                onPhoto = null,
                placeholder = stringResource(R.string.diet_capture_placeholder),
                listening = voice.listening,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            VoiceStatus(voice, onUndo = onUndoVoice, modifier = Modifier.padding(horizontal = 16.dp))
            RepeatChips(state.repeats, onRepeat = onRepeat, modifier = Modifier.padding(horizontal = 16.dp))
            if (state.loaded && state.rows.none { it is DietRow.Entry }) {
                NeutralEmpty(stringResource(R.string.diet_today_empty))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                dietRows(state.rows, onOpenEntry = onOpenEntry, onGapAdd = { focus.requestFocus() })
            }
        }
    }
}
