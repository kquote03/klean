package app.klean.core.ai.ondevice

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** LiteRT-LM on the CPU backend (see ON_DEVICE.md for why). */
class LiteRtLmEngineFactory @Inject constructor(@ApplicationContext private val ctx: Context) : InferenceEngineFactory {
    override fun create(model: File): InferenceEngine {
        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = ctx.cacheDir.absolutePath,
            ),
        )
        try {
            engine.initialize()
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
        return LiteRtLmEngine(engine)
    }

    companion object {
        /** Prompt (system + schema + entry) plus the answer; entries are short. */
        const val MAX_TOKENS = 1024
    }
}

private class LiteRtLmEngine(private val engine: Engine) : InferenceEngine {
    @Volatile private var running: Conversation? = null

    override fun generate(system: String, user: String): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2, seed = 0),
        )
        return engine.createConversation(config).use { c ->
            running = c
            try {
                c.sendMessage(user).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            } finally {
                running = null
            }
        }
    }

    override fun cancel() {
        runCatching { running?.cancelProcess() }
    }

    override fun close() = engine.close()
}
