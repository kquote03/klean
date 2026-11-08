package app.klean.core.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.klean.core.design.R
import app.klean.core.design.theme.KleanTheme

/**
 * One-line capture input. Sized for one-handed use at the bottom of the screen:
 * 56 dp field, 48 dp touch targets, IME action sends.
 */
@Composable
fun CaptureField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMic: () -> Unit,
    onPhoto: (() -> Unit)?,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Voice is listening: the mic button becomes a stop button. */
    listening: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
            enabled = enabled,
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            trailingIcon = {
                Row {
                    if (onPhoto != null) {
                        IconButton(onClick = onPhoto, enabled = enabled, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.design_capture_photo))
                        }
                    }
                    IconButton(onClick = onMic, enabled = enabled, modifier = Modifier.size(48.dp)) {
                        if (listening) {
                            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.design_capture_stop_listening))
                        } else {
                            Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.design_capture_speak))
                        }
                    }
                }
            },
        )
        FilledIconButton(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp).size(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.design_capture_save))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptureFieldPreview() {
    KleanTheme {
        var text by remember { mutableStateOf("") }
        CaptureField(text, { text = it }, {}, {}, onPhoto = {}, placeholder = "What did you have?")
    }
}
