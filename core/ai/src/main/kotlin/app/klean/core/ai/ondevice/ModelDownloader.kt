package app.klean.core.ai.ondevice

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** One HTTP GET, optionally from byte [from] on. An interface so the downloader is tested without a server. */
fun interface ModelHttp {
    /** Throws [IOException] when the server can't be reached. */
    fun get(url: String, from: Long): ModelResponse
}

class ModelResponse(
    val status: Int,
    /** Bytes in this response's body, or -1 when unknown. */
    val length: Long,
    val body: InputStream?,
    val close: () -> Unit = {},
)

class UrlConnectionModelHttp @Inject constructor() : ModelHttp {
    override fun get(url: String, from: Long): ModelResponse {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        val status = c.responseCode
        val body = if (status in 200..299) c.inputStream else null
        return ModelResponse(status, c.contentLengthLong, body) { c.disconnect() }
    }
}

sealed interface DownloadResult {
    data class Done(val result: ModelInstallResult) : DownloadResult
    /** Worth trying again later (network dropped, server busy); the part file is kept to resume. */
    data object Retry : DownloadResult
    /** The address is wrong or needs a login (404, 401/403); retrying won't help. */
    data class Refused(val status: Int) : DownloadResult
    data object NoSpace : DownloadResult
}

/**
 * Downloads a model into [OnDeviceModels.partFile], resuming with `Range` after an interruption, then
 * installs it. WorkManager runs it ([ModelDownloadWorker]); this class holds the logic so it's testable.
 */
class ModelDownloader @Inject constructor(
    private val models: OnDeviceModels,
    private val http: ModelHttp,
) {
    suspend fun download(url: String, onProgress: suspend (done: Long, total: Long) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val part = models.partFile.also { it.parentFile?.mkdirs() }
            val have = if (part.isFile) part.length() else 0L
            val res = try {
                http.get(url, have)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return@withContext DownloadResult.Retry
            } catch (e: IllegalArgumentException) {
                return@withContext DownloadResult.Refused(0) // not a URL at all
            }
            try {
                val resumed = res.status == 206
                when {
                    res.status == 416 && have > 0 -> return@withContext DownloadResult.Done(finish(part, url)) // already complete
                    res.status == 408 || res.status == 429 || res.status >= 500 -> return@withContext DownloadResult.Retry
                    res.status !in 200..299 || res.body == null -> return@withContext DownloadResult.Refused(res.status)
                }
                val start = if (resumed) have else 0L
                val total = if (res.length >= 0) start + res.length else -1L
                val free = part.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (total > 0 && total - start + SPACE_MARGIN > free) return@withContext DownloadResult.NoSpace
                try {
                    java.io.FileOutputStream(part, resumed).use { out -> pump(res.body!!, out, start, total, onProgress) }
                } catch (e: CancellationException) {
                    throw e // part file kept; the next run resumes
                } catch (e: IOException) {
                    return@withContext if (e.message?.contains("ENOSPC") == true) DownloadResult.NoSpace else DownloadResult.Retry
                }
                if (total > 0 && part.length() < total) return@withContext DownloadResult.Retry
                DownloadResult.Done(finish(part, url))
            } finally {
                runCatching { res.body?.close() }
                runCatching { res.close() }
            }
        }

    private suspend fun finish(part: File, url: String) = models.install(part, nameFrom(url))

    private suspend fun pump(input: InputStream, out: java.io.OutputStream, start: Long, total: Long, onProgress: suspend (Long, Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var done = start
        var lastReport = -1L
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (done - lastReport >= REPORT_EVERY) {
                onProgress(done, total)
                lastReport = done
            }
        }
        onProgress(done, total)
    }

    companion object {
        private const val REPORT_EVERY = 4L shl 20
        private const val SPACE_MARGIN = 50L shl 20

        fun nameFrom(url: String): String =
            url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { OnDeviceModels.DEFAULT_NAME }
    }
}
