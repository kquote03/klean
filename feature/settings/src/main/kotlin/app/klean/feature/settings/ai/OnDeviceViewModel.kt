package app.klean.feature.settings.ai

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiService
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.ondevice.ModelDownloadState
import app.klean.core.ai.ondevice.ModelDownloadWorker
import app.klean.core.ai.ondevice.ModelDownloads
import app.klean.core.ai.ondevice.ModelInfo
import app.klean.core.ai.ondevice.ModelInstallResult
import app.klean.core.ai.ondevice.OnDeviceModelStore
import app.klean.feature.settings.R
import app.klean.shared.ai.ENTRY_PARSE_SCHEMA
import app.klean.shared.ai.ENTRY_PARSE_SYSTEM
import app.klean.shared.ai.ParsedEntryDto
import app.klean.shared.ai.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnDeviceUiState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val model: ModelInfo? = null,
    val url: String = "",
    val download: ModelDownloadState = ModelDownloadState.Idle,
    val importing: Boolean = false,
    /** A one-line note after an import or download didn't work out. */
    @param:StringRes val note: Int? = null,
    val test: TestResult? = null,
)

/**
 * The "On this phone" settings (Task 14.2): get a model (download or pick a file), switch it on or off,
 * try it, remove it. Nothing here touches the network except the download the user starts.
 */
@HiltViewModel
class OnDeviceViewModel @Inject constructor(
    private val config: AiConfigStore,
    private val models: OnDeviceModelStore,
    private val downloads: ModelDownloads,
    private val ai: AiService,
) : ViewModel() {
    private val _state = MutableStateFlow(OnDeviceUiState())
    val state: StateFlow<OnDeviceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(url = config.current().providers.onDevice.downloadUrl) }
            combine(config.config, models.current, downloads.state) { c, m, d -> Triple(c, m, d) }.collect { (c, m, d) ->
                _state.update { s ->
                    val failed = (d as? ModelDownloadState.Failed)?.let { noteFor(it.error) }
                    s.copy(loaded = true, enabled = c.providers.onDevice.enabled, model = m, download = d, note = if (failed != null && d != s.download) failed else s.note)
                }
            }
        }
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch {
        config.updateProviders { it.copy(onDevice = it.onDevice.copy(enabled = v)) }
    }

    fun onUrlChange(v: String) = _state.update { it.copy(url = v) }

    fun startDownload(): Job = viewModelScope.launch {
        val url = _state.value.url.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(note = R.string.ai_on_device_note_bad_url) }
            return@launch
        }
        config.updateProviders { it.copy(onDevice = it.onDevice.copy(downloadUrl = url)) }
        _state.update { it.copy(note = null, test = null) }
        downloads.start(url)
    }

    fun cancelDownload() = downloads.cancel()

    fun import(uri: Uri): Job = viewModelScope.launch {
        _state.update { it.copy(importing = true, note = null, test = null) }
        val r = models.importFrom(uri)
        _state.update {
            it.copy(
                importing = false,
                note = when (r) {
                    is ModelInstallResult.Installed -> null
                    ModelInstallResult.NotAModel -> R.string.ai_on_device_note_not_a_model
                    ModelInstallResult.NoSpace -> R.string.ai_on_device_note_space
                    ModelInstallResult.Failed -> R.string.ai_on_device_note_import_failed
                },
            )
        }
    }

    fun remove(): Job = viewModelScope.launch {
        downloads.cancel()
        models.remove()
        _state.update { it.copy(note = null, test = null) }
    }

    /** Asks the model about "toast with butter". The first answer includes loading the model, so it can take a while. */
    fun test(): Job = viewModelScope.launch {
        val p = ai.provider(ProviderId.ON_DEVICE)
        if (p == null || !p.isConfiguredAndEnabled()) {
            _state.update { it.copy(test = TestResult.Error(R.string.ai_on_device_test_needs_model)) }
            return@launch
        }
        _state.update { it.copy(test = TestResult.Running) }
        val out = ai.runWith(p, StructuredRequest(ENTRY_PARSE_SYSTEM, AiProviderViewModel.TEST_TEXT, ENTRY_PARSE_SCHEMA), sendImage = false)
        val result = when (out) {
            is AiOutcome.Success -> ParsedEntryDto.fromJson(out.json)?.sanitized()
                ?.let { d -> TestResult.Ok(d.items.joinToString(", ") { i -> i.portion?.let { "${i.name} · $it" } ?: i.name }) }
                ?: TestResult.Error(R.string.ai_error_bad_response)
            is AiOutcome.Unavailable -> TestResult.Error(AiProviderViewModel.messageFor(out.reason))
            is AiOutcome.Failed -> TestResult.Error(AiProviderViewModel.messageFor(out.reason))
            AiOutcome.NoProvider -> TestResult.Error(R.string.ai_on_device_test_needs_model)
        }
        _state.update { it.copy(test = result) }
    }

    companion object {
        @StringRes fun noteFor(error: String): Int = when (error) {
            ModelDownloadWorker.ERROR_REFUSED -> R.string.ai_on_device_note_refused
            ModelDownloadWorker.ERROR_SPACE -> R.string.ai_on_device_note_space
            ModelDownloadWorker.ERROR_NOT_A_MODEL -> R.string.ai_on_device_note_not_a_model
            else -> R.string.ai_on_device_note_network
        }
    }
}
