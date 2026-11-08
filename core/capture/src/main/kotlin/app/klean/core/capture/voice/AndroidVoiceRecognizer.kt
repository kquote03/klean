package app.klean.core.capture.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

    override fun mode(): VoiceMode = when {
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) -> VoiceMode.ON_DEVICE
        SpeechRecognizer.isRecognitionAvailable(ctx) -> VoiceMode.PREFER_OFFLINE
        else -> VoiceMode.NONE
    }

    override fun start(listener: VoiceRecognizer.Listener) {
        release()
        val mode = mode()
        val sr = when {
            mode == VoiceMode.ON_DEVICE && Build.VERSION.SDK_INT >= 31 -> SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            mode == VoiceMode.PREFER_OFFLINE -> SpeechRecognizer.createSpeechRecognizer(ctx)
            else -> return listener.onError(VoiceError.UNAVAILABLE_OFFLINE)
        }
        current = sr
        sr.setRecognitionListener(Bridge(listener))
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
        current?.destroy()
        current = null
    }

    private inner class Bridge(private val out: VoiceRecognizer.Listener) : RecognitionListener {
        private var done = false

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(out::onPartial)
        }

        override fun onResults(results: Bundle?) {
            if (done) return
            done = true
            Log.i(TAG, "voice final")
            out.onFinal(firstResult(results).orEmpty())
            release()
        }

        override fun onError(error: Int) {
            if (done) return
            done = true
            Log.i(TAG, "voice error=$error")
            out.onError(mapError(error))
            release()
        }

        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "voice ready")
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
