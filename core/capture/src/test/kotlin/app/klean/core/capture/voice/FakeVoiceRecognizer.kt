package app.klean.core.capture.voice

import android.content.Intent

class FakeVoiceRecognizer(var mode: VoiceMode = VoiceMode.SYSTEM) : VoiceRecognizer {
    var listener: VoiceRecognizer.Listener? = null
    var starts = 0
    var stops = 0
    var cancels = 0
    val downloads = mutableListOf<String>()
    /** What [systemDialog] answers; null = no system voice dialog on this phone. */
    var dialog: Intent? = null

    override fun mode() = mode
    override fun start(listener: VoiceRecognizer.Listener) {
        starts++
        this.listener = listener
    }
    override fun stop() { stops++ }
    override fun cancel() {
        cancels++
        listener = null
    }
    override fun downloadLanguagePack(languageTag: String): Boolean {
        downloads += languageTag
        return true
    }
    override fun systemDialog(): Intent? = dialog

    fun ready() = listener!!.onReady()
    fun level(db: Float) = listener!!.onLevel(db)
    fun partial(text: String) = listener!!.onPartial(text)
    fun final(text: String) = listener!!.onFinal(text)
    fun error(e: VoiceError) = listener!!.onError(e)
    fun step(s: VoiceStep) = listener!!.onStep(s)
}
