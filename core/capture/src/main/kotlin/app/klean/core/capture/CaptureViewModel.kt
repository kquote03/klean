package app.klean.core.capture

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.core.capture.voice.VoiceSession
import app.klean.core.capture.voice.VoiceUndo
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoveOption(val action: CandidateAction, @StringRes val labelRes: Int?)

sealed interface CapturePhase {
    data object Editing : CapturePhase
    data object Saving : CapturePhase
    data class Saved(@StringRes val moduleLabelRes: Int?) : CapturePhase
    /** Saved, and other modules could also fit: offer to move it. Stays open longer. */
    data class SavedWithAlternatives(@StringRes val moduleLabelRes: Int?, val options: List<MoveOption>) : CapturePhase
    data object NoModule : CapturePhase
}

data class CaptureUiState(
    /** What is in the field. Lives here so voice can fill it and Undo can put it back. */
    val text: String = "",
    val suggestions: List<String> = emptyList(),
    val phase: CapturePhase = CapturePhase.Editing,
    val startVoice: Boolean = false,
    /** The sheet should close. */
    val finished: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val service: UniversalCaptureService,
    private val registry: ModuleRegistry,
    private val clock: Clock,
    recognizer: VoiceRecognizer,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val target: ModuleId? = savedState.get<String>(CaptureActivity.EXTRA_TARGET_MODULE)?.let(::ModuleId)
    private val source: CaptureSource = savedState.get<String>(CaptureActivity.EXTRA_SOURCE)
        ?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
        ?: CaptureActivity.DEFAULT_SOURCE

    private val _state = MutableStateFlow(CaptureUiState(startVoice = savedState.get<Boolean>(CaptureActivity.EXTRA_START_VOICE) == true))
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var lastSaved: CaptureResult.Saved? = null
    private var closeJob: Job? = null
    private var startVoicePending = _state.value.startVoice

    val voice = VoiceSession(
        scope = viewModelScope,
        recognizer = recognizer,
        save = { text -> save(text, viaVoice = true)?.recordId },
        undo = { u -> undoVoice(u) },
    )

    init {
        viewModelScope.launch {
            val s = service.suggestions(target)
            _state.update { it.copy(suggestions = s) }
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }

    /** True once if the sheet was opened from a mic button (widget); the sheet then starts listening. */
    fun consumeStartVoice(): Boolean = startVoicePending.also { startVoicePending = false }

    fun submit(text: String, viaVoice: Boolean = false) {
        val clean = begin(text) ?: return
        viewModelScope.launch { finish(clean, viaVoice) }
    }

    /** Moves to Saving synchronously, so a double tap saves once. Null when there is nothing to save. */
    private fun begin(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.phase != CapturePhase.Editing) return null
        _state.update { it.copy(phase = CapturePhase.Saving, text = clean) }
        return clean
    }

    private suspend fun save(text: String, viaVoice: Boolean): CaptureResult.Saved? =
        begin(text)?.let { finish(it, viaVoice) }

    private suspend fun finish(clean: String, viaVoice: Boolean): CaptureResult.Saved? {
        val input = CaptureInput(clean, source, clock.now().toEpochMilliseconds(), viaVoice = viaVoice, targetModule = target)
        return when (val r = service.submit(input)) {
            CaptureResult.NoModule -> {
                _state.update { it.copy(phase = CapturePhase.NoModule) }
                null
            }
            is CaptureResult.Saved -> r.also { onSaved(it) }
        }
    }

    private suspend fun undoVoice(u: VoiceUndo) {
        val saved = lastSaved?.takeIf { it.recordId == u.recordId } ?: return
        closeJob?.cancel()
        service.undo(saved)
        lastSaved = null
        _state.update { it.copy(phase = CapturePhase.Editing, text = u.text) }
    }

    fun moveTo(option: MoveOption) {
        val saved = lastSaved ?: return
        closeJob?.cancel()
        viewModelScope.launch {
            onSaved(service.correct(saved, option.action).copy(alternatives = emptyList()))
        }
    }

    private fun onSaved(r: CaptureResult.Saved) {
        lastSaved = r
        val label = labelOf(r.moduleId)
        val phase = if (r.alternatives.isEmpty()) {
            CapturePhase.Saved(label)
        } else {
            CapturePhase.SavedWithAlternatives(label, r.alternatives.map { MoveOption(it, labelOf(it.moduleId)) })
        }
        _state.update { it.copy(phase = phase) }
        closeJob = viewModelScope.launch {
            delay(
                when {
                    r.input.viaVoice -> maxOf(VoiceSession.UNDO_MS, if (r.alternatives.isEmpty()) 0 else ALTERNATIVES_MS)
                    r.alternatives.isEmpty() -> CONFIRM_MS
                    else -> ALTERNATIVES_MS
                },
            )
            _state.update { it.copy(finished = true) }
        }
    }

    override fun onCleared() {
        voice.close()
    }

    private fun labelOf(id: ModuleId): Int? = registry.all.firstOrNull { it.id == id }?.labelRes

    companion object {
        const val CONFIRM_MS = 1_500L
        const val ALTERNATIVES_MS = 4_000L
    }
}
