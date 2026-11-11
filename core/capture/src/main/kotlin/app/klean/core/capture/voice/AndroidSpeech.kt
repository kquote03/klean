package app.klean.core.capture.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** One [SpeechRecognizer] per utterance, destroyed when it ends. Main thread only. */
abstract class AndroidSpeechBackend(protected val ctx: Context) : SpeechBackend {
    private var current: SpeechRecognizer? = null

    protected abstract fun create(): SpeechRecognizer

    override fun start(intent: Intent, listener: RawSpeechListener) {
        release()
        val sr = runCatching { create() }.getOrElse {
            Log.w(VoiceEngine.TAG, "voice create failed", it)
            return listener.onError(SpeechRecognizer.ERROR_CLIENT)
        }
        current = sr
        sr.setRecognitionListener(Bridge(sr, listener))
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

    private inner class Bridge(private val sr: SpeechRecognizer, private val out: RawSpeechListener) : RecognitionListener {
        /** Callbacks from a recogniser that was already replaced or released are ignored. */
        private val stale get() = current !== sr

        override fun onReadyForSpeech(params: Bundle?) {
            if (!stale) out.onReady()
        }
        override fun onRmsChanged(rmsdB: Float) {
            if (!stale) out.onLevel(rmsdB)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!stale) first(partialResults)?.let(out::onPartial)
        }
        override fun onResults(results: Bundle?) {
            if (stale) return
            release()
            out.onFinal(first(results).orEmpty())
        }
        override fun onError(error: Int) {
            if (stale) return
            release()
            out.onError(error)
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }
}

/** The phone's default speech service: what "the system's voice to text" means (on Pixels, Google's). */
class SystemRecognizer(ctx: Context) : AndroidSpeechBackend(ctx) {
    override fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(ctx)
    override fun create(): SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
}

/** The dedicated on-device recogniser (API 31+); its language packs are checked and fetched on API 33+. */
class OnDeviceRecognizer(ctx: Context) : AndroidSpeechBackend(ctx), OnDeviceBackend {
    /** Kept until the next download request: destroying it at once could drop the request. */
    private var downloader: SpeechRecognizer? = null

    override val canDownload: Boolean get() = Build.VERSION.SDK_INT >= 33

    override fun isAvailable() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)

    override fun create(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) else error("API 31+")

    override fun checkSupport(intent: Intent, result: (LanguageSupport?) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) return result(null)
        runCatching { check(intent, result) }.onFailure {
            Log.w(VoiceEngine.TAG, "voice support check failed", it)
            result(null)
        }
    }

    @RequiresApi(33)
    private fun check(intent: Intent, result: (LanguageSupport?) -> Unit) {
        val sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        sr.checkRecognitionSupport(
            intent,
            ContextCompat.getMainExecutor(ctx),
            object : RecognitionSupportCallback {
                override fun onSupportResult(s: RecognitionSupport) {
                    sr.destroy()
                    result(LanguageSupport(s.installedOnDeviceLanguages, (s.pendingOnDeviceLanguages + s.supportedOnDeviceLanguages).distinct()))
                }

                override fun onError(error: Int) {
                    sr.destroy()
                    Log.i(VoiceEngine.TAG, "voice support error=$error")
                    result(null)
                }
            },
        )
    }

    override fun triggerDownload(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            downloader?.destroy()
            downloader = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx).also { it.triggerModelDownload(intent) }
        }.onFailure { Log.w(VoiceEngine.TAG, "voice download failed", it) }.isSuccess
    }
}

class AndroidConnectivity(private val ctx: Context) : Connectivity {
    override fun isOnline(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** De-Googled phones may have no voice dialog; then "Use Google voice" is hidden. */
class AndroidDialogResolver(private val ctx: Context) : DialogResolver {
    override fun resolves(intent: Intent): Boolean =
        ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
}

class HandlerTimer : VoiceTimer {
    private val main = Handler(Looper.getMainLooper())
    override fun schedule(ms: Long, block: () -> Unit): () -> Unit {
        val r = Runnable(block)
        main.postDelayed(r, ms)
        return { main.removeCallbacks(r) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {
    /** One engine per owner (view model): it holds the state of one utterance. */
    @Provides
    fun recognizer(@ApplicationContext ctx: Context): VoiceRecognizer = VoiceEngine(
        system = SystemRecognizer(ctx),
        onDevice = OnDeviceRecognizer(ctx),
        connectivity = AndroidConnectivity(ctx),
        dialog = AndroidDialogResolver(ctx),
        timer = HandlerTimer(),
    )
}
