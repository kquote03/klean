package app.klean.core.capture.voice

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.klean.core.capture.R

/**
 * Returns the mic button's click handler. Asks for RECORD_AUDIO just in time: the first tap shows
 * the system dialog while [VoiceStatus] shows a one-line rationale; once granted it starts listening.
 * Also launches the system voice dialog whenever the session asks for it ([SystemDialogLauncher]).
 */
@Composable
fun rememberVoiceInput(session: VoiceSession): () -> Unit {
    val ctx = LocalContext.current
    val s by rememberUpdatedState(session)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        s.onPermissionResult(granted)
    }
    SystemDialogLauncher(session)
    return {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            s.onMic()
        } else {
            s.onPermissionRequested()
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/**
 * The last step of the voice chain: the system voice dialog (`ACTION_RECOGNIZE_SPEECH`), started for a result
 * when the session sets [VoiceUiState.dialog]; its first `EXTRA_RESULTS` entry goes back to the session.
 */
@Composable
fun SystemDialogLauncher(session: VoiceSession) {
    val state by session.state.collectAsState()
    val s by rememberUpdatedState(session)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        s.onDialogResult(text.takeIf { r.resultCode == Activity.RESULT_OK })
    }
    val intent = state.dialog
    LaunchedEffect(intent) {
        if (intent == null) return@LaunchedEffect
        s.onDialogLaunched()
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            s.onDialogResult(null)
        }
    }
}

/** The calm line for [state]'s message, with its language name filled in. */
@Composable
fun voiceMessage(state: VoiceUiState): String? = state.messageRes?.let { res ->
    if (state.messageArg != null) stringResource(res, state.messageArg) else stringResource(res)
}

/** Listening line, calm notes and the 5 s Undo chip under a capture field. Shows nothing when idle. */
@Composable
fun VoiceStatus(state: VoiceUiState, onUndo: () -> Unit, modifier: Modifier = Modifier, onDownload: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth()) {
        val line = when {
            state.askingPermission -> stringResource(R.string.voice_permission_rationale)
            state.listening -> if (state.partial.isBlank()) {
                stringResource(R.string.voice_listening)
            } else {
                stringResource(R.string.voice_listening_heard, state.partial)
            }
            else -> voiceMessage(state)
        }
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val pack = state.error as? VoiceError.NeedsLanguagePack
        if (pack?.canDownload == true && onDownload != null) {
            TextButton(onClick = onDownload) { Text(stringResource(R.string.voice_download)) }
        }
        state.undo?.let { u ->
            val desc = stringResource(R.string.voice_undo_description, u.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onUndo,
                    label = { Text(stringResource(R.string.voice_saved_undo)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(),
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp).semantics { contentDescription = desc },
                )
            }
        }
    }
}
