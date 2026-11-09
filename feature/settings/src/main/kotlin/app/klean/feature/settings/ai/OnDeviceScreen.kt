package app.klean.feature.settings.ai

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.ai.OnDeviceConfig
import app.klean.core.ai.ondevice.ModelDownloadState
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold

@Composable
fun OnDeviceScreen(onBack: () -> Unit, vm: OnDeviceViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.import(it) } }
    SettingsSubScaffold(R.string.ai_on_device_title, onBack) { modifier ->
        OnDeviceContent(
            s = s,
            modifier = modifier,
            onEnabled = { vm.setEnabled(it) },
            onUrl = vm::onUrlChange,
            onDownload = { vm.startDownload() },
            onCancel = vm::cancelDownload,
            onChoose = { pick.launch(arrayOf("*/*")) },
            onRemove = { vm.remove() },
            onTest = { vm.test() },
        )
    }
}

@Composable
fun OnDeviceContent(
    s: OnDeviceUiState,
    onEnabled: (Boolean) -> Unit,
    onUrl: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    fun size(b: Long) = Formatter.formatShortFileSize(ctx, b)
    val busy = s.importing || s.download is ModelDownloadState.Running || s.download is ModelDownloadState.Waiting
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ai_on_device_intro), style = MaterialTheme.typography.bodyMedium)
        if (s.model != null) {
            SwitchListItem(
                checked = s.enabled,
                onCheckedChange = onEnabled,
                headline = { Text(stringResource(R.string.ai_enable)) },
                supporting = { Text(stringResource(R.string.ai_on_device_enable_summary)) },
            )
        }
        SectionLabel(stringResource(R.string.ai_on_device_model))
        Text(
            s.model?.let { stringResource(R.string.ai_on_device_model_info, it.name, size(it.sizeBytes)) }
                ?: stringResource(R.string.ai_on_device_model_none),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (s.model != null) {
            OutlinedButton(onClick = onTest, enabled = s.test != TestResult.Running && s.enabled) { Text(stringResource(R.string.ai_on_device_test)) }
            when (val t = s.test) {
                null -> {}
                TestResult.Running -> Text(stringResource(R.string.ai_on_device_test_running), style = MaterialTheme.typography.bodyMedium)
                is TestResult.Ok -> Text(stringResource(R.string.ai_test_ok, t.summary), style = MaterialTheme.typography.bodyMedium)
                // A calm note, not an alarm: the rest of Klean works the same without the model.
                is TestResult.Error -> Text(stringResource(t.message), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.ai_on_device_remove)) }
        }
        SectionLabel(stringResource(R.string.ai_on_device_get))
        when (val d = s.download) {
            is ModelDownloadState.Running -> {
                if (d.total > 0) {
                    LinearProgressIndicator(progress = { (d.done.toFloat() / d.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ai_on_device_downloading, size(d.done), size(d.total)))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ai_on_device_downloading_unknown, size(d.done)))
                }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.ai_on_device_cancel)) }
            }
            ModelDownloadState.Waiting -> {
                Text(stringResource(R.string.ai_on_device_waiting))
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.ai_on_device_cancel)) }
            }
            else -> {
                OutlinedTextField(
                    value = s.url,
                    onValueChange = onUrl,
                    label = { Text(stringResource(R.string.ai_on_device_download_url)) },
                    supportingText = { Text(stringResource(R.string.ai_on_device_download_url_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onDownload, enabled = !busy) {
                    Text(stringResource(R.string.ai_on_device_download, OnDeviceConfig.DEFAULT_MODEL_MB))
                }
            }
        }
        Text(stringResource(R.string.ai_on_device_choose_summary), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onChoose, enabled = !busy) { Text(stringResource(R.string.ai_on_device_choose)) }
        if (s.importing) Text(stringResource(R.string.ai_on_device_importing))
        s.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
