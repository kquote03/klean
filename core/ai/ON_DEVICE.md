# On-device model: runtime choice (Task 14.1)

Spike date: 2026-11-09. Timebox: 1 h.

## Choice: LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`)

| Candidate | State in Nov 2026 | Verdict |
|---|---|---|
| MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`) | Still published (0.10.35) but Google AI Edge points new work at LiteRT-LM; `.task` bundles only | Not chosen |
| **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`) | Stable 0.19.1 on Google Maven (published 2026-11-05), Apache-2.0, Kotlin API (`Engine`, `Conversation`), CPU/GPU/NPU backends | **Chosen** |

Checked against `https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml`.
Dependencies it pulls in: gson, kotlin-reflect, kotlinx-coroutines-android.

API used (from `javap` on the 0.19.1 AAR):
```kotlin
val engine = Engine(EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = 1024, cacheDir = cacheDir))
engine.initialize()                                // slow: loads the model; throws on a bad file
engine.createConversation(ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = …)).use {
    it.sendMessage(user).contents.contents.filterIsInstance<Content.Text>().joinToString("") { t -> t.text }
}
engine.close()
```
The provider uses the CPU backend: it works on every phone and on the x86_64 emulator; GPU/NPU need
device-specific model files.

## APK size

The AAR ships `liblitertlm_jni.so` for **arm64-v8a (22.7 MB)** and **x86_64 (26.9 MB)** only. No 32-bit ABIs:
on a 32-bit-only phone the library fails to load and the provider reports "This phone can't run the model"
(it catches `UnsatisfiedLinkError`). The debug APK grows by about 20 MB compressed; a release build
should split by ABI (or ship an App Bundle) so each phone downloads only its own ~10 MB.

## Model

Nothing is bundled. The user either:
- taps **Download model** (WorkManager, Wi-Fi only, resumable with `Range`, stored in
  `noBackupFilesDir/models/`), from an editable URL; or
- taps **Choose a model file** and picks a `.litertlm` file they downloaded themselves (SAF copy into the same folder).

Default URL: `litert-community/Qwen3-0.6B` → `Qwen3-0.6B.litertlm` (~614 MB, Apache-2.0, not gated).
The plan's Gemma 3 1B int4 (`litert-community/Gemma3-1B-IT`, `gemma3-1b-it-int4.litertlm`, ~584 MB) is
**gated** on Hugging Face (licence click-through + token), so an anonymous in-app download fails; users
who want Gemma download it in a browser and use "Choose a model file". Qwen3 has a thinking mode; the
prompt ends with `/no_think` and any `<think>…</think>` block is stripped before JSON extraction.

## Output

No constrained decoding: the prompt carries the JSON schema and "Reply with JSON only", then the same
lenient extraction as the OpenAI-compatible provider (`extractJsonObject`), one retry on unreadable output.

## Waydroid (x86_64) result

See TASKS.md 14.2 for what ran on Waydroid.
