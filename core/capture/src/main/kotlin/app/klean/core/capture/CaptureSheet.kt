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
import app.klean.core.capture.voice.VoiceCaptureOverlay
import androidx.compose.runtime.saveable.rememberSaveable
import app.klean.shared.module.ModuleId
import app.klean.core.capture.photo.PhotoMenu
import app.klean.core.capture.photo.PhotoPicker
import app.klean.core.capture.photo.rememberPhotoPicker
import app.klean.core.design.theme.Space
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.PhotoThumbnail
import app.klean.core.design.component.RepeatChips
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    vm: CaptureViewModel,
    onDismiss: () -> Unit,
    /** Opens the voice screen in the host's navigation (in-app); null shows it over this sheet (CaptureActivity). */
    onVoice: ((target: ModuleId?) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    // The mic opens the dedicated voice screen (plan 2026-11-11 V3) instead of dictating into the field.
    var voiceOpen by rememberSaveable { mutableStateOf(false) }
    val onMic: () -> Unit = {
        if (onVoice != null) onVoice(vm.targetModule) else voiceOpen = true
    }
    val photoPicker = rememberPhotoPicker(vm::newCaptureUri, vm::onPhotoTaken, vm::onPhotoPicked)
    LaunchedEffect(Unit) { if (vm.consumeStartVoice()) onMic() }
    if (voiceOpen) {
        VoiceCaptureOverlay(vm.targetModule, onClose = { voiceOpen = false }, onSaved = { voiceOpen = false; onDismiss() })
    }
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
            photoPicker = photoPicker,
            onRemovePhoto = vm::removePhoto,
            onEatenTimePicked = vm::onEatenTimePicked,
            onMoveMenu = vm::holdOpen,
            onCaloriesChange = vm::onCaloriesChange,
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
    photoPicker: PhotoPicker?,
    onRemovePhoto: () -> Unit,
    onEatenTimePicked: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The "Move to…" menu opened (true) or closed without a choice (false). */
    onMoveMenu: (Boolean) -> Unit = {},
    onCaloriesChange: (String) -> Unit = {},
) {
    var pickTime by remember { mutableStateOf(false) }
    val text = state.text
    var photoMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().padding(start = Space.screenEdge, end = Space.screenEdge, bottom = Space.lg)) {
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
                    if (phase.more.isNotEmpty()) MoveToMenu(phase.more, onMove, onMoveMenu)
                }
            }
            CapturePhase.Editing, CapturePhase.Saving, CapturePhase.NoModule -> {
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Box {
                    CaptureField(
                        value = if (voice.listening && voice.partial.isNotBlank()) voice.partial else text,
                        onValueChange = onTextChange,
                        onSubmit = { onSubmit(text) },
                        onMic = onMic,
                        onPhoto = photoPicker?.let { { photoMenu = true } },
                        placeholder = stringResource(state.placeholderRes),
                        enabled = phase != CapturePhase.Saving,
                        listening = voice.listening,
                        modifier = Modifier.focusRequester(focus),
                    )
                    if (photoPicker != null) {
                        Box(Modifier.align(Alignment.TopEnd)) {
                            PhotoMenu(expanded = photoMenu, onDismiss = { photoMenu = false }, picker = photoPicker)
                        }
                    }
                }
                if (state.showCalorieSlot) {
                    KcalSlot(
                        state.calories,
                        onCaloriesChange,
                        enabled = phase != CapturePhase.Saving,
                        onDone = { onSubmit(text) },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                    )
                }
                if (state.eatenAt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xxs)) {
                        Text(
                            stringResource(R.string.capture_for_around, state.eatenAtLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(" · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.capture_change_time)) }
                    }
                }
                state.photoPath?.let { path -> AttachedPhoto(path, onRemovePhoto) }
                if (photoPicker?.noCamera == true) Note(stringResource(R.string.photo_no_camera))
                VoiceStatus(voice, onUndo = onUndoVoice)
                if (phase == CapturePhase.NoModule) {
                    Text(
                        stringResource(R.string.capture_no_module),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
                RepeatChips(
                    state.suggestions,
                    onRepeat = onSubmit,
                    label = stringResource(R.string.capture_repeats_label),
                    modifier = Modifier.padding(top = Space.xxs),
                )
            }
        }
    }
    if (pickTime) {
        EatenTimeDialog(state.eatenHour, state.eatenMinute, onPicked = onEatenTimePicked, onDismiss = { pickTime = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EatenTimeDialog(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPicked(picker.hour, picker.minute)
                onDismiss()
            }) { Text(stringResource(R.string.capture_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.capture_cancel)) } },
        text = { TimePicker(picker) },
    )
}

@Composable
private fun savedText(labelRes: Int?): String =
    labelRes?.let { stringResource(R.string.capture_saved_to, stringResource(it)) } ?: stringResource(R.string.capture_saved)

/** One "Move to…" chip; its menu lists every other module (bounded: one chip, however many modules). */
@Composable
private fun MoveToMenu(options: List<MoveOption>, onMove: (MoveOption) -> Unit, onMenu: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SuggestionChip(onClick = { open = true; onMenu(true) }, label = { Text(stringResource(R.string.capture_move_to_menu)) })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false; onMenu(false) }) {
            options.forEach { o ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(o.labelRes?.let { stringResource(it) } ?: o.action.moduleId.value) },
                    onClick = { open = false; onMove(o) },
                    modifier = Modifier.heightIn(min = Space.touchTarget),
                )
            }
        }
    }
}

@Composable
private fun SavedLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.sm))
    }
}

@Composable
private fun AttachedPhoto(path: String, onRemove: () -> Unit) {
    Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        PhotoThumbnail(path, contentDescription = stringResource(R.string.photo_attached), size = 56.dp)
        TextButton(onClick = onRemove, modifier = Modifier.padding(start = Space.xs)) { Text(stringResource(R.string.photo_remove)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) { content() }
}
