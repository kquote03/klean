package app.klean.core.ai.ondevice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.klean.core.ai.AiConfigStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class ModelInfo(val name: String, val sizeBytes: Long)

sealed interface ModelInstallResult {
    data class Installed(val info: ModelInfo) : ModelInstallResult
    /** Too small to be a model, or not a file at all. */
    data object NotAModel : ModelInstallResult
    data object NoSpace : ModelInstallResult
    data object Failed : ModelInstallResult
}

/**
 * The model file lives in `noBackupFilesDir/models/` (never in backups: it is large and can be fetched
 * again). Only one model is kept; installing a new one removes the old. Installing switches the
 * provider on, because getting a model is already the user saying yes; removing switches it off.
 */
/** What the settings screen needs from [OnDeviceModels]; faked in view-model tests. */
interface OnDeviceModelStore {
    val current: Flow<ModelInfo?>
    suspend fun importFrom(uri: Uri): ModelInstallResult
    suspend fun remove()
}

@Singleton
class OnDeviceModels internal constructor(
    val dir: File,
    private val config: AiConfigStore,
    private val opener: (Uri) -> Pair<String?, InputStream?>,
) : OnDeviceModelStore {
    @Inject constructor(@ApplicationContext ctx: Context, config: AiConfigStore) : this(
        File(ctx.noBackupFilesDir, "models"),
        config,
        { uri -> displayName(ctx, uri) to ctx.contentResolver.openInputStream(uri) },
    )

    override val current: Flow<ModelInfo?> = config.config
        .map { OnDeviceProvider.modelFile(it.providers.onDevice.modelPath)?.let { f -> ModelInfo(f.name, f.length()) } }
        .distinctUntilChanged()

    /** Where a download in progress is written, so it can resume after an interruption. */
    val partFile: File get() = File(dir, "download.part")

    /** Copies a file the user picked (SAF) into app storage and makes it the model. */
    override suspend fun importFrom(uri: Uri): ModelInstallResult = withContext(Dispatchers.IO) {
        val tmp = File(dir.apply { mkdirs() }, "import.part")
        try {
            val (name, input) = opener(uri)
            input ?: return@withContext ModelInstallResult.Failed
            input.use { inp -> tmp.outputStream().use { out -> copy(inp, out) } }
            install(tmp, name ?: DEFAULT_NAME)
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: IOException) {
            tmp.delete()
            if (e.message?.contains("ENOSPC") == true) ModelInstallResult.NoSpace else ModelInstallResult.Failed
        }
    }

    /** Moves a finished file into place as the model. */
    suspend fun install(file: File, name: String): ModelInstallResult = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() < MIN_MODEL_BYTES || !OnDeviceProvider.looksLikeModel(file)) {
            file.delete()
            return@withContext ModelInstallResult.NotAModel
        }
        dir.mkdirs()
        val target = File(dir, safeName(name))
        dir.listFiles()?.filter { it != file && it.name != partFile.name }?.forEach { it.delete() }
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
        config.updateProviders { it.copy(onDevice = it.onDevice.copy(enabled = true, modelPath = target.absolutePath)) }
        ModelInstallResult.Installed(ModelInfo(target.name, target.length()))
    }

    /** Deletes the model (and any half-finished download) and switches the provider off. */
    override suspend fun remove() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        config.updateProviders { it.copy(onDevice = it.onDevice.copy(enabled = false, modelPath = null)) }
    }

    private suspend fun copy(input: InputStream, out: java.io.OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
    }

    companion object {
        const val DEFAULT_NAME = "model.litertlm"
        /** Real models are hundreds of MB; anything under 1 MB is a wrong pick or an error page. */
        const val MIN_MODEL_BYTES = 1L shl 20

        fun safeName(name: String): String =
            name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').ifBlank { DEFAULT_NAME }
                .let { if (it == "download.part" || it == "import.part") DEFAULT_NAME else it }

        private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
