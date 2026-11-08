package app.klean.core.capture

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        viewModelScope.launch {
            val s = service.suggestions(target)
            _state.update { it.copy(suggestions = s) }
        }
    }

    fun submit(text: String, viaVoice: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.phase != CapturePhase.Editing) return
        _state.update { it.copy(phase = CapturePhase.Saving) }
        viewModelScope.launch {
            val input = CaptureInput(clean, source, clock.now().toEpochMilliseconds(), viaVoice = viaVoice, targetModule = target)
            when (val r = service.submit(input)) {
                CaptureResult.NoModule -> _state.update { it.copy(phase = CapturePhase.NoModule) }
                is CaptureResult.Saved -> onSaved(r)
            }
        }
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
            delay(if (r.alternatives.isEmpty()) CONFIRM_MS else ALTERNATIVES_MS)
            _state.update { it.copy(finished = true) }
        }
    }

    private fun labelOf(id: ModuleId): Int? = registry.all.firstOrNull { it.id == id }?.labelRes

    companion object {
        const val CONFIRM_MS = 1_500L
        const val ALTERNATIVES_MS = 4_000L
    }
}
