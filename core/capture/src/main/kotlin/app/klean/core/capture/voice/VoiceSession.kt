package app.klean.core.capture.voice

import android.content.Intent
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
    /** Input level, 0..1, for the level ring. */
    val level: Float = 0f,
    /** The mic permission dialog is up: show the one-line rationale. */
    val askingPermission: Boolean = false,
    /** A calm, one-line note: the real reason voice didn't work, nothing heard, mic off. */
    @StringRes val messageRes: Int? = null,
    /** Fills `%1$s` in [messageRes] (a language name). */
    val messageArg: String? = null,
    /** The error behind [messageRes], so a screen can offer its one action (Download, Try again …). */
    val error: VoiceError? = null,
    /** The system voice dialog can be offered ("Use Google voice"). */
    val systemDialogAvailable: Boolean = false,
    val undo: VoiceUndo? = null,
    /** Launch this system voice dialog now; the screen reports back via [VoiceSession.onDialogLaunched]. */
    val dialog: Intent? = null,
    /** The final text, for a session that doesn't save by itself (the voice screen reviews it first). */
    val heard: String? = null,
)

/**
 * One voice capture flow shared by every screen with a mic: listen, then auto-save the final result
 * (a single action, per the product rules) and offer Undo for [UNDO_MS].
 *
 * [save] returns the created record id, or null when nothing was saved; a null [save] keeps the final text in
 * [VoiceUiState.heard] instead (the voice screen, where the person picks where it goes). [undo] reverses a save.
 * The caller handles the RECORD_AUDIO request and the system dialog (see `rememberVoiceInput`).
 */
class VoiceSession(
    private val scope: CoroutineScope,
    private val recognizer: VoiceRecognizer,
    private val save: (suspend (String) -> String?)?,
    private val undo: suspend (VoiceUndo) -> Unit = {},
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()
    private var undoJob: Job? = null
    private var watchdog: Job? = null

    /** Ends listening calmly if the recogniser goes quiet (e.g. a service that fails to bind never calls back). */
    private fun arm(timeoutMs: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(timeoutMs)
            if (_state.value.listening) {
                recognizer.cancel()
                showError(VoiceError.NoResponse)
            }
        }
    }

    /** Mic tapped with the permission granted: start, or stop when already listening. */
    fun onMic() {
        if (_state.value.listening) {
            recognizer.stop()
            arm(STOP_TIMEOUT_MS)
            return
        }
        if (recognizer.mode() == VoiceMode.NONE) return showError(VoiceError.NoService)
        _state.update {
            it.copy(
                listening = true, partial = "", level = 0f, messageRes = null, messageArg = null, error = null,
                askingPermission = false, heard = null, dialog = null,
            )
        }
        arm(START_TIMEOUT_MS)
        recognizer.start(listener)
    }

    /** "Done" while listening: stop and take what was heard. */
    fun stop() {
        if (_state.value.listening) onMic()
    }

    fun onPermissionRequested() = _state.update { it.copy(askingPermission = true, messageRes = null, error = null) }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(askingPermission = false) }
        if (granted) onMic() else showError(VoiceError.NoPermission)
    }

    /** "Download" on the needs-pack message. */
    fun downloadPack() {
        val need = _state.value.error as? VoiceError.NeedsLanguagePack ?: return
        if (recognizer.downloadLanguagePack(need.languageTag)) {
            _state.update {
                it.copy(messageRes = R.string.voice_pack_downloading, messageArg = VoiceIntents.languageName(need.languageTag), error = null)
            }
        }
    }

    /** "Use Google voice": hand over to the system voice dialog. */
    fun useSystemDialog() {
        val intent = recognizer.systemDialog() ?: return
        recognizer.cancel()
        watchdog?.cancel()
        _state.update { it.copy(listening = false, partial = "", dialog = intent, messageRes = null, error = null) }
    }

    fun onDialogLaunched() = _state.update { it.copy(dialog = null) }

    /** The system dialog's answer: its first result, or null when it was dismissed or heard nothing. */
    fun onDialogResult(text: String?) {
        if (text.isNullOrBlank()) showError(VoiceError.NoMatch) else onFinalText(text)
    }

    fun undoLast() {
        val u = _state.value.undo ?: return
        undoJob?.cancel()
        _state.update { it.copy(undo = null) }
        scope.launch { undo(u) }
    }

    fun dismissMessage() = _state.update { it.copy(messageRes = null, messageArg = null, error = null) }

    /** The voice screen took [VoiceUiState.heard]. */
    fun clearHeard() = _state.update { it.copy(heard = null, partial = "") }

    /** Stops any recognition; call from the owner's onCleared. */
    fun close() {
        watchdog?.cancel()
        if (_state.value.listening) recognizer.cancel()
        _state.update { it.copy(listening = false, partial = "") }
    }

    private fun showError(error: VoiceError) {
        watchdog?.cancel()
        _state.update {
            it.copy(
                listening = false, partial = "", level = 0f, askingPermission = false,
                messageRes = messageFor(error),
                messageArg = (error as? VoiceError.NeedsLanguagePack)?.let { e -> VoiceIntents.languageName(e.languageTag) },
                error = error,
                systemDialogAvailable = error != VoiceError.NoPermission && recognizer.systemDialog() != null,
            )
        }
    }

    private fun onFinalText(text: String) {
        watchdog?.cancel()
        val clean = text.trim()
        if (clean.isEmpty()) return showError(VoiceError.NoMatch)
        val save = save
        if (save == null) {
            _state.update { it.copy(listening = false, partial = clean, level = 0f, heard = clean) }
            return
        }
        _state.update { it.copy(listening = false, partial = clean, level = 0f) }
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

    private val listener = object : VoiceRecognizer.Listener {
        override fun onReady() {
            watchdog?.cancel()
        }

        override fun onLevel(rmsDb: Float) = _state.update { it.copy(level = levelOf(rmsDb)) }

        override fun onPartial(text: String) {
            watchdog?.cancel()
            _state.update { it.copy(partial = text) }
        }

        override fun onFinal(text: String) = onFinalText(text)

        override fun onError(error: VoiceError) = showError(error)

        override fun onStep(step: VoiceStep) {
            watchdog?.cancel()
            when (step) {
                is VoiceStep.LaunchSystemDialog -> _state.update { it.copy(listening = false, partial = "", dialog = step.intent) }
            }
        }
    }

    companion object {
        const val UNDO_MS = 5_000L

        /** No sign of life after starting. Longer than the engine's own chain (system → support check → on-device). */
        const val START_TIMEOUT_MS = 10_000L

        /** No result after the user tapped stop. */
        const val STOP_TIMEOUT_MS = 3_000L

        /** SpeechRecognizer's rms dB (about -2 to 10) → 0..1. */
        fun levelOf(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)

        @StringRes
        fun messageFor(error: VoiceError): Int = when (error) {
            VoiceError.NoMatch -> R.string.voice_no_match
            VoiceError.NoPermission -> R.string.voice_permission_off
            VoiceError.Busy -> R.string.voice_try_again
            VoiceError.Network -> R.string.voice_network
            VoiceError.OfflineNoPack -> R.string.voice_offline_no_pack
            VoiceError.NoService -> R.string.voice_no_service
            VoiceError.NoResponse -> R.string.voice_no_response
            is VoiceError.NeedsLanguagePack -> R.string.voice_needs_pack
            VoiceError.Other -> R.string.voice_other
        }
    }
}
