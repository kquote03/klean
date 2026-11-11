package app.klean.core.capture.voice

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/** A recogniser's callbacks with the raw [android.speech.SpeechRecognizer] error codes, before [VoiceEngine] reads them. */
interface RawSpeechListener {
    fun onReady()
    fun onLevel(rmsDb: Float)
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(code: Int)
}

/** One way to recognise speech: the phone's default service ([SystemRecognizer]) or the on-device one. */
interface SpeechBackend {
    fun isAvailable(): Boolean
    fun start(intent: Intent, listener: RawSpeechListener)
    fun stop()
    fun cancel()
}

/** Which languages the on-device recogniser has ([installed]) and could fetch ([downloadable]). BCP 47 tags. */
data class LanguageSupport(val installed: List<String>, val downloadable: List<String>)

interface OnDeviceBackend : SpeechBackend {
    /** Whether [triggerDownload] can work on this phone (API 33+). */
    val canDownload: Boolean get() = true

    /** API 33+ `checkRecognitionSupport`; null when unknown (older Android, or the check failed). */
    fun checkSupport(intent: Intent, result: (LanguageSupport?) -> Unit)

    /** API 33+ `triggerModelDownload`; false when the phone can't. */
    fun triggerDownload(intent: Intent): Boolean
}

/** Whether there is a working (validated) internet connection right now. */
fun interface Connectivity {
    fun isOnline(): Boolean
}

/** Whether an activity on the phone handles [intent] (the system voice dialog may be missing). */
fun interface DialogResolver {
    fun resolves(intent: Intent): Boolean
}

/** Runs [block] after [ms] on the main thread; returns a cancel. */
fun interface VoiceTimer {
    fun schedule(ms: Long, block: () -> Unit): () -> Unit
}

object VoiceIntents {
    /** For a [SpeechBackend]: the device language, partial results, and never "prefer offline". */
    fun listen(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /** The system voice dialog, started for a result (`RecognizerIntent.EXTRA_RESULTS`). */
    fun dialog(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    /**
     * The best of [available] for [wanted]: the same tag, else the same language (the phone says en-AE, the pack
     * is en-US). Null when none fits.
     */
    fun pickLanguage(wanted: String, available: List<String>): String? {
        available.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        val lang = Locale.forLanguageTag(wanted).language
        return available.firstOrNull { Locale.forLanguageTag(it).language == lang }
    }

    /** "English" for en-US, in the phone's language. */
    fun languageName(tag: String): String =
        Locale.forLanguageTag(tag).let { it.getDisplayLanguage(Locale.getDefault()).ifBlank { tag } }
}
