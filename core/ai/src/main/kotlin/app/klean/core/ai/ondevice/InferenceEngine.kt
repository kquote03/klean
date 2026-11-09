package app.klean.core.ai.ondevice

import java.io.File

/**
 * A loaded on-device language model. Blocking calls: the provider runs them on a background dispatcher.
 * An interface so [OnDeviceProvider] is tested with a fake instead of a 600 MB model.
 */
interface InferenceEngine : AutoCloseable {
    /** One self-contained exchange (no history). Throws on a runtime error. */
    fun generate(system: String, user: String): String

    /** Asks a running [generate] to stop early; it may still return or throw. */
    fun cancel() {}
}

/** Loads a model file. Slow (seconds) and memory-hungry (~the file size). */
fun interface InferenceEngineFactory {
    /** Throws (including [Error]s such as `UnsatisfiedLinkError`) when the file or the phone can't run it. */
    fun create(model: File): InferenceEngine
}
