package app.klean.core.capture.voice

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.capture.R
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.theme.Space
import app.klean.shared.module.ModuleId

private val MIC_SIZE = 96.dp
private const val RING_GROWTH = 0.25f

/**
 * The dedicated voice screen (plan 2026-11-11 V3): a large mic with a live level ring and the live transcript,
 * then the text (editable, the only input), where it goes, and Save. Opened from Today's Speak, the capture
 * sheet's mic, the `voice` shortcut and `klean://voice`. Starts listening at once.
 */
@Composable
fun VoiceCaptureScreen(vm: VoiceCaptureViewModel, onClose: () -> Unit, onSaved: () -> Unit = onClose) {
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    val review by vm.state.collectAsStateWithLifecycle()
    val onMic = rememberVoiceInput(vm.voice)
    val ctx = LocalContext.current
    val reduced = remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(Unit) { if (!review.reviewing && review.saved == null && !voice.listening) onMic() }
    LaunchedEffect(review.finished) { if (review.finished) onSaved() }
    VoiceCaptureContent(
        voice = voice,
        review = review,
        reducedMotion = reduced,
        onClose = {
            vm.close()
            onClose()
        },
        onDone = vm.voice::stop,
        onMic = onMic,
        onSpeakAgain = {
            vm.resetReview()
            onMic()
        },
        onTextChange = vm::onTextChange,
        onSelect = vm::select,
        onSave = vm::save,
        onUndo = vm::undo,
        onDownload = vm.voice::downloadPack,
        onUseSystemVoice = vm.voice::useSystemDialog,
    )
}

/**
 * The voice screen shown over a host that has no navigation of its own (the capture sheet in [CaptureActivity]
 * for the tile, widget and notification): the same screen in a full-screen dialog.
 */
@Composable
fun VoiceCaptureOverlay(target: ModuleId?, onClose: () -> Unit, onSaved: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // A fresh view model per opening: the host activity's store outlives this overlay.
        val key = remember { "voice-${System.nanoTime()}" }
        val vm: VoiceCaptureViewModel = hiltViewModel(key = key)
        remember(vm) { vm.setTarget(target) }
        VoiceCaptureScreen(vm, onClose = onClose, onSaved = onSaved)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCaptureContent(
    voice: VoiceUiState,
    review: VoiceReviewState,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onMic: () -> Unit,
    onSpeakAgain: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelect: (ModuleId) -> Unit,
    onSave: (String, ModuleId) -> Unit,
    onUndo: () -> Unit,
    onDownload: () -> Unit,
    onUseSystemVoice: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = Space.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(Space.touchTarget)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.voice_close))
                }
                Spacer(Modifier.weight(1f))
                if (voice.listening) TextButton(onClick = onDone) { Text(stringResource(R.string.voice_done)) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = Space.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val saved = review.saved
                when {
                    saved != null -> Saved(saved, onUndo)
                    review.reviewing -> Review(review, onTextChange, onSelect, onSave)
                    voice.listening -> Listening(voice.partial)
                    voice.askingPermission -> Calm(stringResource(R.string.voice_permission_rationale))
                    voice.messageRes != null -> Problem(voice, onDownload, onUseSystemVoice)
                    else -> Calm(stringResource(R.string.voice_screen_hint))
                }
            }
            if (review.saved == null) {
                val (desc, action) = when {
                    voice.listening -> stringResource(R.string.voice_screen_listening) to null
                    review.reviewing -> stringResource(R.string.voice_speak_again) to onSpeakAgain
                    voice.messageRes != null -> stringResource(R.string.voice_try_again_action) to onMic
                    else -> stringResource(R.string.voice_speak) to onMic
                }
                MicButton(
                    listening = voice.listening,
                    level = voice.level,
                    reducedMotion = reducedMotion,
                    // Save is the one filled button while reviewing.
                    prominent = !review.reviewing,
                    label = desc,
                    onClick = action,
                )
                Text(
                    // "Listening" is already the screen's title while listening.
                    if (voice.listening) "" else desc,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The mic already says it.
                    modifier = Modifier.padding(top = Space.xs, bottom = Space.lg).clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun Listening(partial: String) {
    Text(
        stringResource(R.string.voice_screen_listening),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        partial.ifBlank { stringResource(R.string.voice_screen_hint) },
        style = MaterialTheme.typography.headlineSmall,
        color = if (partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Space.md).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Review(
    review: VoiceReviewState,
    onTextChange: (String) -> Unit,
    onSelect: (ModuleId) -> Unit,
    onSave: (String, ModuleId) -> Unit,
) {
    OutlinedTextField(
        value = review.text,
        onValueChange = onTextChange,
        label = { Text(stringResource(R.string.voice_text_label)) },
        textStyle = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    if (review.destinations.isNotEmpty()) {
        Text(
            stringResource(R.string.voice_goes_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.xs),
        )
        // One bounded single-choice group: it counts once for the clutter budget, like a list.
        FlowRow(
            Modifier.fillMaxWidth().testTag(CLUTTER_EXEMPT),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            maxLines = 2,
        ) {
            review.destinations.forEach { d ->
                val selected = d.module == review.selected
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(d.module) },
                    label = { Text(d.labelRes?.let { stringResource(it) } ?: d.module.value) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier.heightIn(min = Space.touchTarget),
                )
            }
        }
    }
    if (review.noModule) Calm(stringResource(R.string.capture_no_module))
    val module = review.selected
    Button(
        onClick = { if (module != null) onSave(review.text.trim(), module) },
        enabled = module != null && review.text.isNotBlank() && !review.saving,
        modifier = Modifier.fillMaxWidth().padding(top = Space.lg).heightIn(min = Space.touchTarget),
    ) { Text(stringResource(R.string.voice_save)) }
}

@Composable
private fun Saved(to: VoiceDestination, onUndo: () -> Unit) {
    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Space.touchTarget))
    val label = to.labelRes?.let { stringResource(it) } ?: to.module.value
    Text(
        stringResource(R.string.capture_saved_to, label),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = Space.sm).semantics { liveRegion = LiveRegionMode.Polite },
    )
    TextButton(onClick = onUndo, modifier = Modifier.padding(top = Space.xs)) { Text(stringResource(R.string.voice_undo)) }
}

@Composable
private fun Problem(voice: VoiceUiState, onDownload: () -> Unit, onUseSystemVoice: () -> Unit) {
    Calm(voiceMessage(voice).orEmpty())
    val pack = voice.error as? VoiceError.NeedsLanguagePack
    when {
        pack?.canDownload == true -> FilledTonalButton(onClick = onDownload, modifier = Modifier.padding(top = Space.md)) {
            Text(stringResource(R.string.voice_download))
        }
        voice.systemDialogAvailable -> FilledTonalButton(onClick = onUseSystemVoice, modifier = Modifier.padding(top = Space.md)) {
            Text(stringResource(R.string.voice_use_system))
        }
    }
}

@Composable
private fun Calm(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Space.xs).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 96 dp mic with a level ring behind it (1.0–1.25 with the voice; still with reduced motion). */
@Composable
private fun MicButton(listening: Boolean, level: Float, reducedMotion: Boolean, prominent: Boolean, label: String, onClick: (() -> Unit)?) {
    val target = if (listening && !reducedMotion) 1f + RING_GROWTH * level.coerceIn(0f, 1f) else 1f
    val scale by animateFloatAsState(target, animationSpec = tween(durationMillis = 100), label = "level")
    val container = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(Modifier.size(MIC_SIZE * (1f + RING_GROWTH)), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier.size(MIC_SIZE)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
        }
        val mic: @Composable () -> Unit = {
            Box(Modifier.size(MIC_SIZE), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(MIC_SIZE / 2.4f))
            }
        }
        if (onClick != null) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = container,
                contentColor = content,
                modifier = Modifier.size(MIC_SIZE).semantics { contentDescription = label },
                content = mic,
            )
        } else {
            Surface(
                shape = CircleShape,
                color = container,
                contentColor = content,
                modifier = Modifier.size(MIC_SIZE).semantics { contentDescription = label },
                content = mic,
            )
        }
    }
}
