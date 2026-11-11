package app.klean.core.capture.voice

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.CaptureActivity
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.R
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureRouter
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One "where it goes" chip on the voice screen. [labelRes] is voice's own short name ("Food", "Task"). */
data class VoiceDestination(val module: ModuleId, @StringRes val labelRes: Int?)

data class VoiceReviewState(
    /** The final text is in, and the person is checking it and where it goes. */
    val reviewing: Boolean = false,
    val text: String = "",
    /** Bounded (at most [VoiceCaptureViewModel.MAX_DESTINATIONS]), the predicted one first. */
    val destinations: List<VoiceDestination> = emptyList(),
    val selected: ModuleId? = null,
    val saving: Boolean = false,
    /** Saved to this destination; Undo is offered until the screen closes. */
    val saved: VoiceDestination? = null,
    /** Every module that could take it is off: nothing was saved. */
    val noModule: Boolean = false,
    /** The screen should close. */
    val finished: Boolean = false,
)

/**
 * The full-screen voice capture (plan 2026-11-11 V3): listen with [voice] (which doesn't save by itself), then
 * review the text with the router's destination chips, the predicted one selected, then Save with Undo.
 */
@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    recognizer: VoiceRecognizer,
    private val service: UniversalCaptureService,
    private val router: CaptureRouter,
    private val registry: ModuleRegistry,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** Aimed at a module (the Food capture sheet's mic, `klean://voice?module=diet`): its chip starts selected. */
    private var target: ModuleId? = (savedState.get<String>(VoiceRoute.ARG_MODULE) ?: savedState.get<String>(CaptureActivity.EXTRA_TARGET_MODULE))
        ?.let(::ModuleId)

    val voice = VoiceSession(viewModelScope, recognizer, save = null)

    private val _state = MutableStateFlow(VoiceReviewState())
    val state: StateFlow<VoiceReviewState> = _state.asStateFlow()

    /** The router's own pick for the current text; saving to it routes exactly like typed capture. */
    private var predicted: ModuleId? = null
    private var lastSaved: CaptureResult.Saved? = null
    private var closeJob: Job? = null
    private var rankJob: Job? = null

    init {
        viewModelScope.launch {
            voice.state.map { it.heard }.distinctUntilChanged().filterNotNull().collect { heard ->
                voice.clearHeard()
                review(heard)
            }
        }
    }

    fun setTarget(module: ModuleId?) {
        target = module
    }

    /** Shows [text] for review and asks the router where it goes. Also used by tests and the dialog result. */
    fun review(text: String) {
        _state.update { VoiceReviewState(reviewing = true, text = text) }
        rankJob?.cancel()
        rankJob = viewModelScope.launch {
            val handlers = service.enabledHandlers()
            val enabled = handlers.map { it.moduleId }
            val ranked = router.route(input(text, null), handlers).map { it.moduleId }.distinct()
            predicted = ranked.firstOrNull()
            val first = target?.takeIf { it in enabled } ?: predicted
            val order = (listOfNotNull(first) + ranked + UniversalCaptureService.MOVE_ORDER.filter { it in enabled } + enabled)
                .distinct()
                .take(MAX_DESTINATIONS)
            _state.update { s -> s.copy(destinations = order.map(::destination), selected = s.selected ?: first) }
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }

    fun select(module: ModuleId) = _state.update { it.copy(selected = module) }

    fun save(text: String, module: ModuleId) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.saving || _state.value.saved != null) return
        _state.update { it.copy(saving = true, noModule = false, text = clean) }
        viewModelScope.launch {
            // The router's own pick goes through it again, so a reminder's time or a task's list is read as usual.
            val aimed = module.takeUnless { it == predicted && target == null }
            when (val r = service.submit(input(clean, aimed))) {
                CaptureResult.NoModule -> _state.update { it.copy(saving = false, noModule = true) }
                is CaptureResult.Saved -> {
                    lastSaved = r
                    _state.update { it.copy(saving = false, saved = destination(r.moduleId)) }
                    closeJob?.cancel()
                    closeJob = viewModelScope.launch {
                        delay(SAVED_MS)
                        _state.update { it.copy(finished = true) }
                    }
                }
            }
        }
    }

    fun undo() {
        val saved = lastSaved ?: return
        closeJob?.cancel()
        lastSaved = null
        viewModelScope.launch {
            service.undo(saved)
            _state.update { it.copy(saved = null, finished = false) }
        }
    }

    /** "Speak again": drop the review; the screen then starts listening. */
    fun resetReview() {
        rankJob?.cancel()
        _state.value = VoiceReviewState()
    }

    /** Closing the screen: stop listening, keep any save. */
    fun close() = voice.close()

    override fun onCleared() = voice.close()

    private fun input(text: String, aimed: ModuleId?) = CaptureInput(
        text, SOURCE, clock.now().toEpochMilliseconds(), viaVoice = true, targetModule = aimed,
    )

    private fun destination(id: ModuleId) = VoiceDestination(id, labelOf(id) ?: registry.all.firstOrNull { it.id == id }?.labelRes)

    companion object {
        /** One bounded chip row (SKILL: suggestion rows are capped). */
        const val MAX_DESTINATIONS = 4

        /** "Saved to Food · Undo" stays this long, then the screen closes. */
        const val SAVED_MS = 4_000L

        val SOURCE = CaptureSource.DASHBOARD

        @StringRes
        fun labelOf(id: ModuleId): Int? = when (id) {
            ModuleId.DIET -> R.string.voice_dest_food
            ModuleId.TASKS -> R.string.voice_dest_task
            ModuleId.REMINDERS -> R.string.voice_dest_reminder
            ModuleId.CALENDAR -> R.string.voice_dest_event
            ModuleId.WORKOUT -> R.string.voice_dest_workout
            ModuleId.SLEEP -> R.string.voice_dest_sleep
            ModuleId.CONTACTS -> R.string.voice_dest_contact
            ModuleId.BIRTHDAYS -> R.string.voice_dest_birthday
            else -> null
        }
    }
}
