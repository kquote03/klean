package app.klean.core.capture.voice

import android.content.Intent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * The voice chain (plan 2026-11-11, decision 7):
 * 1. the phone's own speech service ([system], `SpeechRecognizer.createSpeechRecognizer`) with the device language
 *    and no "prefer offline": it goes offline by itself when its pack is there;
 * 2. only when offline, the on-device recogniser ([onDevice]), and only with its language pack installed
 *    (API 33+ `checkRecognitionSupport`); a missing pack is offered as a download instead;
 * 3. if both fail, the system voice dialog (`ACTION_RECOGNIZE_SPEECH`) through [VoiceStep.LaunchSystemDialog].
 *
 * Every failure keeps its real cause ([mapError]). One utterance at a time, on the main thread.
 * Logs under [TAG] ("voice start …", "voice error=…") for diagnosing a phone over adb.
 */
class VoiceEngine(
    private val system: SpeechBackend,
    private val onDevice: OnDeviceBackend,
    private val connectivity: Connectivity,
    private val dialog: DialogResolver,
    private val timer: VoiceTimer,
    private val language: () -> String = { Locale.getDefault().toLanguageTag() },
) : VoiceRecognizer {
    private var out: VoiceRecognizer.Listener? = null
    private var active: SpeechBackend? = null

    /** Bumped on every start, cancel and end: callbacks from an older attempt are ignored. */
    private var attempt = 0
    private var cancelTimer: (() -> Unit)? = null

    override fun mode(): VoiceMode = when {
        system.isAvailable() -> VoiceMode.SYSTEM
        onDevice.isAvailable() -> VoiceMode.ON_DEVICE
        dialog.resolves(VoiceIntents.dialog(language())) -> VoiceMode.DIALOG
        else -> VoiceMode.NONE
    }

    override fun start(listener: VoiceRecognizer.Listener) {
        cancel()
        out = listener
        val tag = language()
        when {
            system.isAvailable() -> listen(system, tag, "SYSTEM") { code -> afterSystem(code, tag) }
            onDevice.isAvailable() -> tryOnDevice(tag, languageError = false)
            else -> toDialog(tag, VoiceError.NoService)
        }
    }

    override fun stop() {
        active?.stop()
    }

    override fun cancel() {
        val a = active
        end()
        a?.cancel()
    }

    override fun downloadLanguagePack(languageTag: String): Boolean {
        if (!onDevice.isAvailable() || !onDevice.canDownload) return false
        Log.i(TAG, "voice download lang=$languageTag")
        return onDevice.triggerDownload(VoiceIntents.listen(languageTag))
    }

    override fun systemDialog(): Intent? = VoiceIntents.dialog(language()).takeIf(dialog::resolves)

    private fun listen(backend: SpeechBackend, tag: String, name: String, onFail: (Int) -> Unit) {
        val id = ++attempt
        active = backend
        var ready = false
        clearTimer()
        // A recogniser that fails to bind may never call back (seen on Waydroid): move on after a short wait.
        cancelTimer = timer.schedule(SILENT_MS) {
            if (id != attempt || ready) return@schedule
            Log.i(TAG, "voice silent mode=$name")
            active = null
            backend.cancel()
            onFail(ERROR_SILENT)
        }
        Log.i(TAG, "voice start mode=$name lang=$tag")
        backend.start(
            VoiceIntents.listen(tag),
            object : RawSpeechListener {
                private fun live() = id == attempt

                override fun onReady() {
                    if (!live()) return
                    ready = true
                    clearTimer()
                    Log.i(TAG, "voice ready mode=$name")
                    out?.onReady()
                }

                override fun onLevel(rmsDb: Float) {
                    if (live()) out?.onLevel(rmsDb)
                }

                override fun onPartial(text: String) {
                    if (live()) out?.onPartial(text)
                }

                override fun onFinal(text: String) {
                    if (!live()) return
                    Log.i(TAG, "voice final mode=$name")
                    val o = out
                    end()
                    o?.onFinal(text)
                }

                override fun onError(code: Int) {
                    if (!live()) return
                    clearTimer()
                    active = null
                    Log.i(TAG, "voice error=$code mode=$name")
                    onFail(code)
                }
            },
        )
    }

    private fun afterSystem(code: Int, tag: String) {
        terminal(code)?.let { return fail(it) }
        val online = connectivity.isOnline()
        when {
            !online -> tryOnDevice(tag, languageError = code in LANGUAGE)
            code in NETWORK -> fail(VoiceError.Network)
            else -> toDialog(tag, mapError(code, online, tag))
        }
    }

    private fun tryOnDevice(tag: String, languageError: Boolean) {
        val fallback = if (connectivity.isOnline()) VoiceError.NoResponse else VoiceError.OfflineNoPack
        if (!onDevice.isAvailable()) {
            return toDialog(tag, if (languageError) VoiceError.NeedsLanguagePack(tag, canDownload = false) else fallback)
        }
        val id = attempt
        var answered = false
        clearTimer()
        // The support check is a service call too: don't wait on it forever.
        cancelTimer = timer.schedule(SUPPORT_MS) {
            if (id != attempt || answered) return@schedule
            answered = true
            listenOnDevice(tag)
        }
        onDevice.checkSupport(VoiceIntents.listen(tag)) { support ->
            if (id != attempt || answered) return@checkSupport
            answered = true
            clearTimer()
            Log.i(TAG, "voice support installed=${support?.installed} downloadable=${support?.downloadable}")
            if (support == null) return@checkSupport listenOnDevice(tag)
            val installed = VoiceIntents.pickLanguage(tag, support.installed)
            val downloadable = VoiceIntents.pickLanguage(tag, support.downloadable)
            when {
                installed != null -> listenOnDevice(installed)
                downloadable != null -> fail(VoiceError.NeedsLanguagePack(downloadable, canDownload = onDevice.canDownload))
                languageError -> toDialog(tag, VoiceError.NeedsLanguagePack(tag, canDownload = false))
                else -> toDialog(tag, fallback)
            }
        }
    }

    private fun listenOnDevice(tag: String) = listen(onDevice, tag, "ON_DEVICE") { code ->
        terminal(code)?.let { return@listen fail(it) }
        val online = connectivity.isOnline()
        when (code) {
            in LANGUAGE -> fail(VoiceError.NeedsLanguagePack(tag, canDownload = onDevice.canDownload))
            else -> toDialog(tag, if (online) mapError(code, online = true, languageTag = tag) else VoiceError.OfflineNoPack)
        }
    }

    private fun toDialog(tag: String, fallback: VoiceError) {
        val intent = VoiceIntents.dialog(tag)
        if (!dialog.resolves(intent)) return fail(fallback)
        Log.i(TAG, "voice dialog lang=$tag")
        val o = out
        end()
        o?.onStep(VoiceStep.LaunchSystemDialog(intent))
    }

    private fun fail(error: VoiceError) {
        Log.i(TAG, "voice failed $error")
        val o = out
        end()
        o?.onError(error)
    }

    private fun end() {
        attempt++
        clearTimer()
        active = null
        out = null
    }

    private fun clearTimer() {
        cancelTimer?.invoke()
        cancelTimer = null
    }

    companion object {
        const val TAG = "KleanVoice"

        /** How long a recogniser may stay silent after starting before the next one is tried. */
        const val SILENT_MS = 3_000L
        const val SUPPORT_MS = 2_000L

        /** Not a platform code: the recogniser never called back. */
        const val ERROR_SILENT = -1

        private val NETWORK = setOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        )
        private val LANGUAGE = setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)

        /** Errors that end the attempt whichever recogniser gave them: the next one wouldn't do better. */
        private fun terminal(code: Int): VoiceError? = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NoMatch
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NoPermission
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceError.Busy
            else -> null
        }

        /** One recogniser error code → its real cause. Only a real offline problem says offline. */
        fun mapError(code: Int, online: Boolean, languageTag: String): VoiceError = terminal(code) ?: when (code) {
            in NETWORK -> if (online) VoiceError.Network else VoiceError.OfflineNoPack
            in LANGUAGE -> VoiceError.NeedsLanguagePack(languageTag, canDownload = false)
            ERROR_SILENT -> VoiceError.NoResponse
            else -> VoiceError.Other
        }
    }
}
