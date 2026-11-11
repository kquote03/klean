package app.klean.core.capture.voice

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A recogniser backend that records what it was asked to do and lets the test answer for it. */
class FakeSpeechBackend(var available: Boolean = true) : OnDeviceBackend {
    val started = mutableListOf<Intent>()
    val downloads = mutableListOf<Intent>()
    var support: LanguageSupport? = null
    var supportChecks = 0
    var cancels = 0
    private var listener: RawSpeechListener? = null

    override fun isAvailable() = available
    override fun start(intent: Intent, listener: RawSpeechListener) {
        started += intent
        this.listener = listener
    }
    override fun stop() = Unit
    override fun cancel() { cancels++ }
    override fun checkSupport(intent: Intent, result: (LanguageSupport?) -> Unit) {
        supportChecks++
        result(support)
    }
    override fun triggerDownload(intent: Intent): Boolean {
        downloads += intent
        return true
    }

    fun ready() = listener!!.onReady()
    fun final(text: String) = listener!!.onFinal(text)
    fun error(code: Int) = listener!!.onError(code)
}

class FakeTimer : VoiceTimer {
    private val pending = mutableListOf<() -> Unit>()
    override fun schedule(ms: Long, block: () -> Unit): () -> Unit {
        pending += block
        return { pending.remove(block) }
    }
    /** Runs everything scheduled so far, as if its time had come. */
    fun fire() = pending.toList().also { pending.clear() }.forEach { it() }
}

class RecordingListener : VoiceRecognizer.Listener {
    val errors = mutableListOf<VoiceError>()
    val steps = mutableListOf<VoiceStep>()
    val finals = mutableListOf<String>()
    override fun onPartial(text: String) = Unit
    override fun onFinal(text: String) { finals += text }
    override fun onError(error: VoiceError) { errors += error }
    override fun onStep(step: VoiceStep) { steps += step }
}

/**
 * Plan 2026-11-11 V1: the phone's own recogniser first (with the device language, never "prefer offline"),
 * the on-device one only offline and only with its pack installed, then the system voice dialog. Each failure
 * says its real cause: \"not available offline\" was shown for network, server and language errors alike.
 */
@RunWith(AndroidJUnit4::class)
class VoiceEngineTest {
    private val system = FakeSpeechBackend()
    private val onDevice = FakeSpeechBackend()
    private var online = true
    private var dialogResolves = true
    private val timer = FakeTimer()
    private val out = RecordingListener()
    private val savedLocale: Locale = Locale.getDefault()

    @After fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun engine(language: String = "en-US") = VoiceEngine(
        system = system,
        onDevice = onDevice,
        connectivity = { online },
        dialog = { dialogResolves },
        timer = timer,
        language = { language },
    )

    private fun Intent.language() = getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)

    @Test fun systemFirst() {
        Locale.setDefault(Locale.forLanguageTag("en-AE"))
        val e = VoiceEngine(system, onDevice, { online }, { dialogResolves }, timer)
        assertEquals(VoiceMode.SYSTEM, e.mode())
        e.start(out)
        val intent = system.started.single()
        assertEquals(Locale.getDefault().toLanguageTag(), intent.language())
        assertEquals("en-AE", intent.language())
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertTrue(onDevice.started.isEmpty())
    }

    @Test fun languageUnavailableOffersDownload() {
        online = false
        onDevice.support = LanguageSupport(installed = emptyList(), downloadable = listOf("en-US", "ar-AE"))
        val e = engine()
        e.start(out)
        system.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(listOf<VoiceError>(VoiceError.NeedsLanguagePack("en-US", canDownload = true)), out.errors)
        assertTrue(onDevice.started.isEmpty())

        assertTrue(e.downloadLanguagePack("en-US"))
        assertEquals("en-US", onDevice.downloads.single().language())

        // 12 (not supported) is the same story offline.
        out.errors.clear()
        e.start(out)
        system.error(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        assertEquals(listOf<VoiceError>(VoiceError.NeedsLanguagePack("en-US", canDownload = true)), out.errors)
    }

    @Test fun networkErrorOfflineTriesOnDevice() {
        online = false
        onDevice.support = LanguageSupport(installed = listOf("en-US"), downloadable = emptyList())
        engine().start(out)
        system.error(SpeechRecognizer.ERROR_NETWORK)
        assertEquals("en-US", onDevice.started.single().language())
        assertTrue(out.errors.isEmpty())
        onDevice.ready()
        onDevice.final("two manakish")
        assertEquals(listOf("two manakish"), out.finals)
    }

    @Test fun networkErrorOfflineSkipsOnDeviceWithoutItsPack() {
        online = false
        dialogResolves = false
        onDevice.support = LanguageSupport(installed = listOf("de-DE"), downloadable = emptyList())
        engine().start(out)
        system.error(SpeechRecognizer.ERROR_NETWORK)
        assertTrue(onDevice.started.isEmpty())
        assertEquals(listOf<VoiceError>(VoiceError.OfflineNoPack), out.errors)
    }

    /** The Pixel's locale may be en-AE while the installed pack is en-US: the same language is good enough. */
    @Test fun onDeviceUsesTheInstalledPackOfTheSameLanguage() {
        online = false
        onDevice.support = LanguageSupport(installed = listOf("en-US"), downloadable = emptyList())
        engine(language = "en-AE").start(out)
        system.error(SpeechRecognizer.ERROR_NETWORK)
        assertEquals("en-US", onDevice.started.single().language())
    }

    @Test fun bothFailFallsBackToDialog() {
        online = false
        onDevice.support = LanguageSupport(installed = listOf("en-US"), downloadable = emptyList())
        engine().start(out)
        system.error(SpeechRecognizer.ERROR_CLIENT)
        onDevice.error(SpeechRecognizer.ERROR_CLIENT)
        val step = out.steps.single() as VoiceStep.LaunchSystemDialog
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, step.intent.action)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, step.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertEquals("en-US", step.intent.language())
        assertTrue(out.errors.isEmpty())
    }

    @Test fun noRecognizerAtAll() {
        system.available = false
        onDevice.available = false
        dialogResolves = false
        val e = engine()
        assertEquals(VoiceMode.NONE, e.mode())
        e.start(out)
        assertEquals(listOf<VoiceError>(VoiceError.NoService), out.errors)
        assertEquals(null, e.systemDialog())
        assertEquals(R.string.voice_no_service, VoiceSession.messageFor(VoiceError.NoService))
    }

    @Test fun errorsAreNotAllOffline() {
        assertEquals(VoiceError.NoMatch, VoiceEngine.mapError(SpeechRecognizer.ERROR_NO_MATCH, online = true, languageTag = "en-US"))
        assertEquals(VoiceError.NoPermission, VoiceEngine.mapError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, online = true, languageTag = "en-US"))
        assertEquals(VoiceError.Network, VoiceEngine.mapError(SpeechRecognizer.ERROR_NETWORK, online = true, languageTag = "en-US"))
        assertEquals(VoiceError.Network, VoiceEngine.mapError(SpeechRecognizer.ERROR_SERVER, online = true, languageTag = "en-US"))
        assertEquals(VoiceError.OfflineNoPack, VoiceEngine.mapError(SpeechRecognizer.ERROR_NETWORK, online = false, languageTag = "en-US"))
        assertEquals(VoiceError.Busy, VoiceEngine.mapError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, online = true, languageTag = "en-US"))

        // Online, a network error is said as such and doesn't wander off to the on-device recogniser.
        engine().start(out)
        system.error(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(listOf<VoiceError>(VoiceError.Network), out.errors)
        assertTrue(onDevice.started.isEmpty())
        assertEquals(R.string.voice_network, VoiceSession.messageFor(VoiceError.Network))
    }

    /** Waydroid: a recogniser that never binds never calls back. After a short wait the next step runs. */
    @Test fun silentSystemRecogniserMovesOn() {
        engine().start(out)
        timer.fire()
        assertEquals(1, system.cancels)
        assertTrue(out.steps.single() is VoiceStep.LaunchSystemDialog)
    }

    @Test fun noMatchEndsWithoutFallingBack() {
        engine().start(out)
        system.ready()
        system.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf<VoiceError>(VoiceError.NoMatch), out.errors)
        assertTrue(out.steps.isEmpty())
    }
}
