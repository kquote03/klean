package app.klean.core.capture.voice

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.klean.core.capture.R

/**
 * Returns the mic button's click handler. Asks for RECORD_AUDIO just in time: the first tap shows
 * the system dialog while [VoiceStatus] shows a one-line rationale; once granted it starts listening.
 */
@Composable
fun rememberVoiceInput(session: VoiceSession): () -> Unit {
    val ctx = LocalContext.current
    val s by rememberUpdatedState(session)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        s.onPermissionResult(granted)
    }
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

/** Listening line, calm notes and the 5 s Undo chip under a capture field. Shows nothing when idle. */
@Composable
fun VoiceStatus(state: VoiceUiState, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        val line = when {
            state.askingPermission -> stringResource(R.string.voice_permission_rationale)
            state.listening -> if (state.partial.isBlank()) {
                stringResource(R.string.voice_listening)
            } else {
                stringResource(R.string.voice_listening_heard, state.partial)
            }
            state.messageRes != null -> stringResource(state.messageRes)
            else -> null
        }
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
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
