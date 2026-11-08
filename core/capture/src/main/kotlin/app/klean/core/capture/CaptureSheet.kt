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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CaptureField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(vm: CaptureViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.finished) {
        if (state.finished) scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CaptureSheetContent(
            state = state,
            onSubmit = vm::submit,
            onMove = vm::moveTo,
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }
}

@Composable
internal fun CaptureSheetContent(
    state: CaptureUiState,
    onSubmit: (String) -> Unit,
    onMove: (MoveOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        when (val phase = state.phase) {
            is CapturePhase.Saved -> SavedLine(savedText(phase.moduleLabelRes))
            is CapturePhase.SavedWithAlternatives -> {
                SavedLine(savedText(phase.moduleLabelRes))
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
                    value = text,
                    onValueChange = { text = it },
                    onSubmit = { onSubmit(text) },
                    onMic = {}, // Voice input arrives in M6.
                    onPhoto = null, // Photo attach arrives in M6.
                    placeholder = stringResource(R.string.capture_placeholder),
                    enabled = phase != CapturePhase.Saving,
                    modifier = Modifier.focusRequester(focus),
                )
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
