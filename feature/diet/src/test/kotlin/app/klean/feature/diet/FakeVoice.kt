package app.klean.feature.diet

import app.klean.core.capture.voice.VoiceMode
import app.klean.core.capture.voice.VoiceRecognizer

class FakeVoice(var mode: VoiceMode = VoiceMode.ON_DEVICE) : VoiceRecognizer {
    var listener: VoiceRecognizer.Listener? = null
    override fun mode() = mode
    override fun start(listener: VoiceRecognizer.Listener) { this.listener = listener }
    override fun stop() = Unit
    override fun cancel() { listener = null }
}
