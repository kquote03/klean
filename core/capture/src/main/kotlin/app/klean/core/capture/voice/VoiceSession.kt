package app.klean.core.capture.voice

import androidx.annotation.StringRes
import app.klean.core.capture.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What was just saved by voice, while it can still be undone. */
data class VoiceUndo(val text: String, val recordId: String)

data class VoiceUiState(
    val listening: Boolean = false,
    /** What the recogniser has heard so far. */
    val partial: String = "",
    /** The mic permission dialog is up: show the one-line rationale. */
    val askingPermission: Boolean = false,
    /** A calm, one-line note (offline voice missing, nothing heard, mic off). */
    @StringRes val messageRes: Int? = null,
    val undo: VoiceUndo? = null,
)

/**
 * One voice capture flow shared by every screen with a mic: listen, then auto-save the final result
 * (a single action, per the product rules) and offer Undo for [UNDO_MS].
 *
 * [save] returns the created record id, or null when nothing was saved. [undo] reverses a save.
 * The caller handles the RECORD_AUDIO request (see `rememberVoiceInput`) and reports back through
 * [onPermissionRequested] and [onPermissionResult].
 */
class VoiceSession(
    private val scope: CoroutineScope,
    private val recognizer: VoiceRecognizer,
    private val save: suspend (String) -> String?,
    private val undo: suspend (VoiceUndo) -> Unit,
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()
    private var undoJob: Job? = null

    /** Mic tapped with the permission granted: start, or stop when already listening. */
    fun onMic() {
        if (_state.value.listening) {
            recognizer.stop()
            return
        }
        if (recognizer.mode() == VoiceMode.NONE) {
            _state.update { it.copy(messageRes = R.string.voice_unavailable_offline) }
            return
        }
        _state.update { it.copy(listening = true, partial = "", messageRes = null, askingPermission = false) }
        recognizer.start(listener)
    }

    fun onPermissionRequested() = _state.update { it.copy(askingPermission = true, messageRes = null) }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(askingPermission = false) }
        if (granted) onMic() else _state.update { it.copy(messageRes = R.string.voice_permission_off) }
    }

    fun undoLast() {
        val u = _state.value.undo ?: return
        undoJob?.cancel()
        _state.update { it.copy(undo = null) }
        scope.launch { undo(u) }
    }

    fun dismissMessage() = _state.update { it.copy(messageRes = null) }

    /** Stops any recognition; call from the owner's onCleared. */
    fun close() {
        if (_state.value.listening) recognizer.cancel()
        _state.update { it.copy(listening = false, partial = "") }
    }

    private val listener = object : VoiceRecognizer.Listener {
        override fun onPartial(text: String) = _state.update { it.copy(partial = text) }

        override fun onFinal(text: String) {
            val clean = text.trim()
            if (clean.isEmpty()) {
                _state.update { it.copy(listening = false, partial = "", messageRes = R.string.voice_no_match) }
                return
            }
            _state.update { it.copy(listening = false, partial = clean) }
            scope.launch {
                val id = save(clean)
                _state.update { it.copy(partial = "", undo = id?.let { r -> VoiceUndo(clean, r) }) }
                if (id != null) {
                    undoJob?.cancel()
                    undoJob = scope.launch {
                        delay(UNDO_MS)
                        _state.update { s -> if (s.undo?.recordId == id) s.copy(undo = null) else s }
                    }
                }
            }
        }

        override fun onError(error: VoiceError) = _state.update {
            it.copy(listening = false, partial = "", messageRes = messageFor(error))
        }
    }

    companion object {
        const val UNDO_MS = 5_000L

        @StringRes
        fun messageFor(error: VoiceError): Int = when (error) {
            VoiceError.UNAVAILABLE_OFFLINE -> R.string.voice_unavailable_offline
            VoiceError.NO_MATCH -> R.string.voice_no_match
            VoiceError.NO_PERMISSION -> R.string.voice_permission_off
            VoiceError.BUSY, VoiceError.OTHER -> R.string.voice_try_again
        }
    }
}
