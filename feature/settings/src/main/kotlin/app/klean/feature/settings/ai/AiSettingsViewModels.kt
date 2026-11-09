package app.klean.feature.settings.ai

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiService
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.provider.AiReasons
import app.klean.core.data.secret.SecretStore
import app.klean.feature.settings.R
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ENTRY_PARSE_SCHEMA
import app.klean.shared.ai.ENTRY_PARSE_SYSTEM
import app.klean.shared.ai.ParsedEntryDto
import app.klean.shared.ai.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Providers that exist in this version, in card order. The on-device model arrives later (M14). */
val SETTABLE_PROVIDERS = listOf(ProviderId.GEMINI, ProviderId.OPENAI_COMPAT)

data class AiProvidersUiState(
    val loaded: Boolean = false,
    /** Provider → On (enabled and configured). */
    val on: Map<ProviderId, Boolean> = emptyMap(),
    val assignments: Map<AiTask, ProviderId> = emptyMap(),
    /** What "Use for" can pick, besides None: providers that are on. */
    val options: List<ProviderId> = emptyList(),
) {
    fun statusOf(id: ProviderId) = on[id] == true
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val config: AiConfigStore,
    secrets: SecretStore,
    private val ai: AiService,
) : ViewModel() {
    val state: StateFlow<AiProvidersUiState> =
        combine(config.config, secrets.has(SecretStore.GEMINI_API_KEY), secrets.has(SecretStore.OPENAI_API_KEY)) { c, _, _ -> c }
            .mapLatest { c ->
                val on = SETTABLE_PROVIDERS.associateWith { ai.provider(it)?.isConfiguredAndEnabled() == true }
                AiProvidersUiState(true, on, c.assignments, SETTABLE_PROVIDERS.filter { on[it] == true })
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AiProvidersUiState())

    fun assign(task: AiTask, provider: ProviderId?) = viewModelScope.launch { config.assign(task, provider) }
}

sealed interface TestResult {
    data object Running : TestResult
    data class Ok(val summary: String) : TestResult
    data class Error(@StringRes val message: Int) : TestResult
}

data class AiProviderUiState(
    val loaded: Boolean = false,
    val provider: ProviderId = ProviderId.GEMINI,
    val enabled: Boolean = false,
    val on: Boolean = false,
    val hasKey: Boolean = false,
    /** What the user is typing into the key field; never pre-filled with the stored key. */
    val keyInput: String = "",
    val model: String = "",
    val baseUrl: String = "",
    /** Gemini: "Send photos too". */
    val sendPhotos: Boolean = false,
    /** OpenAI-compatible: "This model understands images". */
    val acceptsImages: Boolean = false,
    val test: TestResult? = null,
)

@HiltViewModel
class AiProviderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val config: AiConfigStore,
    private val secrets: SecretStore,
    private val ai: AiService,
) : ViewModel() {
    val provider: ProviderId = ProviderId.valueOf(checkNotNull(savedState.get<String>("provider")))
    private val keyName = if (provider == ProviderId.GEMINI) SecretStore.GEMINI_API_KEY else SecretStore.OPENAI_API_KEY
    private val _state = MutableStateFlow(AiProviderUiState(provider = provider))
    val state: StateFlow<AiProviderUiState> = _state.asStateFlow()
    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            val c = config.current().providers
            _state.update {
                it.copy(
                    model = if (provider == ProviderId.GEMINI) c.gemini.model else c.openAi.model,
                    baseUrl = c.openAi.baseUrl,
                    loaded = true,
                )
            }
            combine(config.config, secrets.has(keyName)) { cfg, has -> cfg to has }.collect { (cfg, has) ->
                val p = cfg.providers
                val on = ai.provider(provider)?.isConfiguredAndEnabled() == true
                _state.update {
                    it.copy(
                        enabled = if (provider == ProviderId.GEMINI) p.gemini.enabled else p.openAi.enabled,
                        sendPhotos = p.gemini.sendPhotos,
                        acceptsImages = p.openAi.acceptsImages,
                        hasKey = has,
                        on = on,
                    )
                }
            }
        }
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch {
        flush().join()
        config.updateProviders {
            if (provider == ProviderId.GEMINI) it.copy(gemini = it.gemini.copy(enabled = v)) else it.copy(openAi = it.openAi.copy(enabled = v))
        }
    }

    fun setSendPhotos(v: Boolean) = viewModelScope.launch { config.updateProviders { it.copy(gemini = it.gemini.copy(sendPhotos = v)) } }

    fun setAcceptsImages(v: Boolean) = viewModelScope.launch { config.updateProviders { it.copy(openAi = it.openAi.copy(acceptsImages = v)) } }

    fun onKeyChange(v: String) = _state.update { it.copy(keyInput = v) }

    fun saveKey(): Job = viewModelScope.launch {
        val k = _state.value.keyInput.trim()
        if (k.isEmpty()) return@launch
        secrets.put(keyName, k)
        _state.update { it.copy(keyInput = "", hasKey = true) }
    }

    fun removeKey(): Job = viewModelScope.launch {
        secrets.put(keyName, null)
        _state.update { it.copy(hasKey = false) }
    }

    fun onModelChange(v: String) {
        _state.update { it.copy(model = v) }
        scheduleSave()
    }

    fun onBaseUrlChange(v: String) {
        _state.update { it.copy(baseUrl = v) }
        scheduleSave()
    }

    /** Text fields save shortly after typing stops, so config listeners don't fire per keystroke. */
    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveFields()
        }
    }

    /** Saves typed fields now. */
    fun flush(): Job {
        pendingSave?.cancel()
        return viewModelScope.launch { saveFields() }
    }

    private suspend fun saveFields() {
        val s = _state.value
        config.updateProviders {
            if (provider == ProviderId.GEMINI) {
                it.copy(gemini = it.gemini.copy(model = s.model.trim().ifEmpty { it.gemini.model }))
            } else {
                it.copy(openAi = it.openAi.copy(model = s.model.trim(), baseUrl = s.baseUrl.trim()))
            }
        }
    }

    /** Sends "toast with butter" and shows what came back. Only while the provider is switched on (consent). */
    fun testConnection(): Job = viewModelScope.launch {
        flush().join()
        val p = ai.provider(provider)
        if (p == null || !p.isConfiguredAndEnabled()) {
            _state.update { it.copy(test = TestResult.Error(R.string.ai_test_turn_on)) }
            return@launch
        }
        _state.update { it.copy(test = TestResult.Running) }
        val out = ai.runWith(p, StructuredRequest(ENTRY_PARSE_SYSTEM, TEST_TEXT, ENTRY_PARSE_SCHEMA), sendImage = false)
        val result = when (out) {
            is AiOutcome.Success -> ParsedEntryDto.fromJson(out.json)?.sanitized()
                ?.let { d -> TestResult.Ok(d.items.joinToString(", ") { i -> i.portion?.let { "${i.name} · $it" } ?: i.name }) }
                ?: TestResult.Error(R.string.ai_error_bad_response)
            is AiOutcome.Unavailable -> TestResult.Error(messageFor(out.reason))
            is AiOutcome.Failed -> TestResult.Error(messageFor(out.reason))
            AiOutcome.NoProvider -> TestResult.Error(R.string.ai_test_turn_on)
        }
        _state.update { it.copy(test = result) }
    }

    override fun onCleared() {
        // Keep what was typed even if the user leaves within the save delay.
        if (pendingSave?.isActive == true) {
            pendingSave?.cancel()
            kotlinx.coroutines.runBlocking { saveFields() }
        }
    }

    companion object {
        const val TEST_TEXT = "toast with butter"
        private const val SAVE_DELAY_MS = 600L

        @StringRes fun messageFor(reason: String): Int = when (reason) {
            AiReasons.NETWORK -> R.string.ai_error_network
            AiReasons.TIMEOUT -> R.string.ai_error_timeout
            AiReasons.BUSY -> R.string.ai_error_busy
            AiReasons.GEMINI_REJECTED -> R.string.ai_error_gemini_rejected
            AiReasons.AUTH -> R.string.ai_error_auth
            AiReasons.NOT_FOUND -> R.string.ai_error_not_found
            AiReasons.NOT_CONFIGURED -> R.string.ai_error_not_configured
            AiReasons.BAD_RESPONSE -> R.string.ai_error_bad_response
            else -> R.string.ai_error_rejected
        }
    }
}
