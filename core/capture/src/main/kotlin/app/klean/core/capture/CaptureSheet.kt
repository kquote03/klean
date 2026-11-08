package app.klean.core.capture

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.capture.voice.VoiceStatus
import app.klean.core.capture.voice.VoiceUiState
import app.klean.core.capture.voice.rememberVoiceInput
import app.klean.core.design.component.CaptureField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(vm: CaptureViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    val onMic = rememberVoiceInput(vm.voice)
    LaunchedEffect(Unit) { if (vm.consumeStartVoice()) onMic() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.finished) {
        if (state.finished) scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CaptureSheetContent(
            state = state,
            voice = voice,
            onTextChange = vm::onTextChange,
            onSubmit = { vm.submit(it) },
            onMove = vm::moveTo,
            onMic = onMic,
            onUndoVoice = vm.voice::undoLast,
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }
}

@Composable
internal fun CaptureSheetContent(
    state: CaptureUiState,
    voice: VoiceUiState,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMove: (MoveOption) -> Unit,
    onMic: () -> Unit,
    onUndoVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = state.text
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        when (val phase = state.phase) {
            is CapturePhase.Saved -> {
                SavedLine(savedText(phase.moduleLabelRes))
                VoiceStatus(voice, onUndo = onUndoVoice)
            }
            is CapturePhase.SavedWithAlternatives -> {
                SavedLine(savedText(phase.moduleLabelRes))
                VoiceStatus(voice, onUndo = onUndoVoice)
                ChipRow {
                    phase.options.forEach { o ->
                        val label = o.labelRes?.let { stringResource(it) } ?: o.action.moduleId.value
                        SuggestionChip(onClick = { onMove(o) }, label = { Text(stringResource(R.string.capture_move_to, label)) })
                    }
                }
            }
            CapturePhase.Editing, CapturePhase.Saving, CapturePhase.NoModule -> {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                CaptureField(
                    value = if (voice.listening && voice.partial.isNotBlank()) voice.partial else text,
                    onValueChange = onTextChange,
                    onSubmit = { onSubmit(text) },
                    onMic = onMic,
                    onPhoto = null, // Photo attach arrives in 6.2.
                    placeholder = stringResource(R.string.capture_placeholder),
                    enabled = phase != CapturePhase.Saving,
                    listening = voice.listening,
                    modifier = Modifier.focusRequester(focus),
                )
                VoiceStatus(voice, onUndo = onUndoVoice)
                if (phase == CapturePhase.NoModule) {
                    Text(
                        stringResource(R.string.capture_no_module),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.suggestions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.capture_repeats_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    ChipRow { state.suggestions.forEach { s -> SuggestionChip(onClick = { onSubmit(s) }, label = { Text(s) }) } }
                }
            }
        }
    }
}

@Composable
private fun savedText(labelRes: Int?): String =
    labelRes?.let { stringResource(R.string.capture_saved_to, stringResource(it)) } ?: stringResource(R.string.capture_saved)

@Composable
private fun SavedLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
