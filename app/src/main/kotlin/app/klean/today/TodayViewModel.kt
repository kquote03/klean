package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.module.ModuleId
import app.klean.shared.diet.CaptureSource
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodaySaved(val module: ModuleId, val options: List<CandidateAction>)

/** The universal capture bar on Today. Saving is immediate; a short "Saved" line confirms it. */
class TodayViewModel(private val capture: UniversalCaptureService, private val clock: Clock) : ViewModel() {
    /** False when every module that could take a capture is switched off. */
    val captureAvailable: StateFlow<Boolean> = capture.availableHandlers
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()
    private var flash: Job? = null

    /** Where the last capture went and where it could move ("Move to…", M28); null once the line fades. */
    private val _saved = MutableStateFlow<TodaySaved?>(null)
    val saved: StateFlow<TodaySaved?> = _saved.asStateFlow()
    private var last: CaptureResult.Saved? = null

    private fun show(r: CaptureResult.Saved, options: List<CandidateAction>) {
        last = r
        _saved.value = TodaySaved(r.moduleId, options)
        flash?.cancel()
        _justSaved.value = true
        flash = viewModelScope.launch {
            delay(if (options.isEmpty()) SAVED_MS else MOVE_MS)
            _justSaved.value = false
            _saved.value = null
        }
    }

    /** The "Move to…" menu is open: keep the line; closed without a choice: fade soon. */
    fun holdOpen(open: Boolean) {
        flash?.cancel()
        if (!open) {
            flash = viewModelScope.launch {
                delay(SAVED_MS)
                _justSaved.value = false
                _saved.value = null
            }
        }
    }

    /** Moves the last capture: saves it with [to]'s module, then undoes the original. */
    fun moveTo(to: CandidateAction) {
        val s = last ?: return
        viewModelScope.launch { show(capture.correct(s, to), emptyList()) }
    }

    /** Returns false when nothing was saved, so the field keeps the text. */
    fun submit(text: String, onDone: (saved: Boolean) -> Unit = {}) {
        val clean = text.trim()
        if (clean.isEmpty()) return onDone(false)
        viewModelScope.launch {
            val r = capture.submit(CaptureInput(clean, CaptureSource.DASHBOARD, clock.now().toEpochMilliseconds()))
            val saved = r is CaptureResult.Saved
            onDone(saved)
            if (r is CaptureResult.Saved) show(r, r.alternatives + r.others)
        }
    }

    companion object {
        const val SAVED_MS = 1_500L
        /** Long enough to read "Saved to Diet" and tap "Move to…". */
        const val MOVE_MS = 6_000L
    }
}
