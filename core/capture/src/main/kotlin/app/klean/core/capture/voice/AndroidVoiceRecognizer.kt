package app.klean.core.capture.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * [SpeechRecognizer] behind [VoiceRecognizer], per plan §6.4: the on-device recogniser when the
 * phone has one (API 31+), else the default one with EXTRA_PREFER_OFFLINE. One recogniser per
 * utterance, destroyed when it ends. Must be called on the main thread.
 */
class AndroidVoiceRecognizer @Inject constructor(@ApplicationContext private val ctx: Context) : VoiceRecognizer {
    private var current: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var fallback: Runnable? = null

    override fun mode(): VoiceMode = when {
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) -> VoiceMode.ON_DEVICE
        SpeechRecognizer.isRecognitionAvailable(ctx) -> VoiceMode.PREFER_OFFLINE
        else -> VoiceMode.NONE
    }

    override fun start(listener: VoiceRecognizer.Listener) = start(listener, mode())

    private fun start(listener: VoiceRecognizer.Listener, mode: VoiceMode) {
        release()
        val sr = when {
            mode == VoiceMode.ON_DEVICE && Build.VERSION.SDK_INT >= 31 -> SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            mode == VoiceMode.PREFER_OFFLINE -> SpeechRecognizer.createSpeechRecognizer(ctx)
            else -> return listener.onError(VoiceError.UNAVAILABLE_OFFLINE)
        }
        current = sr
        if (mode == VoiceMode.ON_DEVICE && SpeechRecognizer.isRecognitionAvailable(ctx)) {
            // Some phones report an on-device recogniser that then fails to bind and never calls back
            // (seen on Waydroid: "Bind to system recognition service failed"). Fall back to the default one.
            fallback = Runnable {
                Log.i(TAG, "on-device recogniser silent; falling back to default")
                start(listener, VoiceMode.PREFER_OFFLINE)
            }.also { main.postDelayed(it, ON_DEVICE_FALLBACK_MS) }
        }
        sr.setRecognitionListener(Bridge(sr, listener))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        Log.i(TAG, "voice start mode=$mode")
        sr.startListening(intent)
    }

    override fun stop() {
        current?.stopListening()
    }

    override fun cancel() {
        current?.cancel()
        release()
    }

    private fun release() {
        fallback?.let(main::removeCallbacks)
        fallback = null
        current?.destroy()
        current = null
    }

    private inner class Bridge(private val sr: SpeechRecognizer, private val out: VoiceRecognizer.Listener) : RecognitionListener {
        private var done = false
        private var ready = false

        /** Callbacks from a recogniser we already replaced are ignored. */
        private val stale get() = current !== sr

        override fun onPartialResults(partialResults: Bundle?) {
            if (done || stale) return
            firstResult(partialResults)?.let(out::onPartial)
        }

        override fun onResults(results: Bundle?) {
            if (done || stale) return
            done = true
            Log.i(TAG, "voice final")
            out.onFinal(firstResult(results).orEmpty())
            release()
        }

        override fun onError(error: Int) {
            if (done || stale) return
            done = true
            Log.i(TAG, "voice error=$error")
            val pending = fallback
            if (!ready && pending != null) {
                // The on-device recogniser failed before it started: try the default one now.
                main.removeCallbacks(pending)
                pending.run()
                return
            }
            out.onError(mapError(error))
            release()
        }

        override fun onReadyForSpeech(params: Bundle?) {
            if (stale) return
            ready = true
            Log.i(TAG, "voice ready")
            fallback?.let(main::removeCallbacks)
            fallback = null
            out.onReady()
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun firstResult(b: Bundle?): String? =
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }

    companion object {
        private const val TAG = "KleanVoice"
        private const val ON_DEVICE_FALLBACK_MS = 3_000L

        fun mapError(error: Int): VoiceError = when (error) {
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> VoiceError.UNAVAILABLE_OFFLINE
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NO_MATCH
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> VoiceError.BUSY
            else -> VoiceError.OTHER
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds abstract fun recognizer(impl: AndroidVoiceRecognizer): VoiceRecognizer
}
