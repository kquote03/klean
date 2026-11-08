package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.shared.capture.CaptureInput
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

/** The universal capture bar on Today. Saving is immediate; a short "Saved" line confirms it. */
class TodayViewModel(private val capture: UniversalCaptureService, private val clock: Clock) : ViewModel() {
    /** False when every module that could take a capture is switched off. */
    val captureAvailable: StateFlow<Boolean> = capture.availableHandlers
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()
    private var flash: Job? = null

    /** Returns false when nothing was saved, so the field keeps the text. */
    fun submit(text: String, onDone: (saved: Boolean) -> Unit = {}) {
        val clean = text.trim()
        if (clean.isEmpty()) return onDone(false)
        viewModelScope.launch {
            val r = capture.submit(CaptureInput(clean, CaptureSource.DASHBOARD, clock.now().toEpochMilliseconds()))
            val saved = r is CaptureResult.Saved
            onDone(saved)
            if (saved) {
                flash?.cancel()
                _justSaved.value = true
                flash = launch {
                    delay(SAVED_MS)
                    _justSaved.value = false
                }
            }
        }
    }

    companion object {
        const val SAVED_MS = 1_500L
    }
}
