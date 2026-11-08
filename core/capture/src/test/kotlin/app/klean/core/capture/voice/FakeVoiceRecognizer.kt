package app.klean.core.capture.voice

class FakeVoiceRecognizer(var mode: VoiceMode = VoiceMode.ON_DEVICE) : VoiceRecognizer {
    var listener: VoiceRecognizer.Listener? = null
    var starts = 0
    var stops = 0
    var cancels = 0

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

    fun ready() = listener!!.onReady()
    fun partial(text: String) = listener!!.onPartial(text)
    fun final(text: String) = listener!!.onFinal(text)
    fun error(e: VoiceError) = listener!!.onError(e)
}
