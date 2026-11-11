package app.klean.core.capture.voice

import android.content.Intent

/** How speech will be recognised on this phone, best first (see [VoiceEngine]). */
enum class VoiceMode {
    /** The phone's own speech service (on Pixels, Google's). Works offline by itself when its pack is there. */
    SYSTEM,

    /** Only a dedicated on-device recogniser (API 31+). */
    ON_DEVICE,

    /** No recogniser service, but the system voice dialog opens. */
    DIALOG,

    /** Nothing on this phone can turn speech into text. */
    NONE,
}

/** Why voice didn't work, each with its own message (plan 2026-11-11 V2: never one lumped "offline"). */
sealed interface VoiceError {
    /** Heard nothing usable. */
    data object NoMatch : VoiceError

    /** RECORD_AUDIO is not granted. */
    data object NoPermission : VoiceError

    /** Another app is using the recogniser. */
    data object Busy : VoiceError

    /** Online, but the voice service couldn't be reached. Trying again usually works. */
    data object Network : VoiceError

    /** No connection, and no offline pack for this language. */
    data object OfflineNoPack : VoiceError

    /** The phone has no speech recognition at all (no service, no dialog). */
    data object NoService : VoiceError

    /** The recogniser didn't start or answer at all. */
    data object NoResponse : VoiceError

    /** Offline voice needs [languageTag]'s pack; [canDownload] when the phone can fetch it (API 33+). */
    data class NeedsLanguagePack(val languageTag: String, val canDownload: Boolean) : VoiceError

    data object Other : VoiceError
}

/** Something the screen has to do for the recogniser. */
sealed interface VoiceStep {
    /** Both recognisers failed; the system voice dialog (`ACTION_RECOGNIZE_SPEECH`) can still listen. */
    data class LaunchSystemDialog(val intent: Intent) : VoiceStep
}

/**
 * Speech to text, one utterance at a time. An interface so view models are tested with a fake;
 * [VoiceEngine] chains the phone's recognisers. Callbacks arrive on the main thread.
 */
interface VoiceRecognizer {
    fun mode(): VoiceMode

    fun start(listener: Listener)

    /** Stops listening; the recogniser still delivers a final result for what it heard. */
    fun stop()

    /** Stops and drops any result. */
    fun cancel()

    /** Asks the phone to download [languageTag]'s offline pack. False when it can't. */
    fun downloadLanguagePack(languageTag: String): Boolean = false

    /** The system voice dialog for the device language, or null when nothing on the phone handles it. */
    fun systemDialog(): Intent? = null

    interface Listener {
        /** The recogniser is bound and listening. */
        fun onReady() {}

        /** Input level in dB (roughly -2 to 10), for the level ring. */
        fun onLevel(rmsDb: Float) {}
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(error: VoiceError)

        /** The recogniser needs the screen's help; a listener that can't give it reports a calm error. */
        fun onStep(step: VoiceStep) = onError(VoiceError.Other)
    }
}
