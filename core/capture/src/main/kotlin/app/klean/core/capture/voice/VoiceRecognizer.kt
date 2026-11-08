package app.klean.core.capture.voice

/** How speech will be recognised on this phone, best first. */
enum class VoiceMode {
    /** A dedicated on-device recogniser (API 31+). Works offline. */
    ON_DEVICE,

    /** The default recogniser, asked to prefer offline. May still need a network on some phones. */
    PREFER_OFFLINE,

    /** No recogniser installed. */
    NONE,
}

enum class VoiceError {
    /** The recogniser needs a network or a language pack that isn't there. */
    UNAVAILABLE_OFFLINE,

    /** Heard nothing usable. */
    NO_MATCH,

    /** RECORD_AUDIO is not granted. */
    NO_PERMISSION,

    /** Another app is using the recogniser. */
    BUSY,
    OTHER,
}

/**
 * Speech to text, one utterance at a time. An interface so view models are tested with a fake;
 * [AndroidVoiceRecognizer] wraps [android.speech.SpeechRecognizer]. Callbacks arrive on the main thread.
 */
interface VoiceRecognizer {
    fun mode(): VoiceMode

    fun start(listener: Listener)

    /** Stops listening; the recogniser still delivers a final result for what it heard. */
    fun stop()

    /** Stops and drops any result. */
    fun cancel()

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(error: VoiceError)
    }
}
