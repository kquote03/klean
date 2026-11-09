package app.klean.core.ai.ondevice

import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.FakeAiConfigStore
import app.klean.core.ai.OnDeviceConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cfg = FakeAiConfigStore(AiConfig(providers = AiProvidersConfig(onDevice = OnDeviceConfig())))
    private val models by lazy { OnDeviceModels(File(tmp.root, "models"), cfg) { null to null } }
    private val modelBytes = ByteArray((OnDeviceModels.MIN_MODEL_BYTES + 1234).toInt()) { (it % 251).toByte() }
    private val url = "https://example.org/files/Qwen3-0.6B.litertlm?download=true"

    /** Serves [bytes]; honours Range unless [ignoreRange]; can drop the connection after [cutAt] bytes. */
    private inner class FakeServer(
        val bytes: ByteArray = modelBytes,
        val status: Int = 200,
        val ignoreRange: Boolean = false,
        var cutAt: Int? = null,
    ) : ModelHttp {
        val ranges = mutableListOf<Long>()
        override fun get(url: String, from: Long): ModelResponse {
            ranges += from
            if (status != 200) return ModelResponse(status, -1, null)
            val start = if (from > 0 && !ignoreRange) from.toInt() else 0
            if (start >= bytes.size && from > 0) return ModelResponse(416, -1, null)
            val body = bytes.copyOfRange(start, bytes.size)
            val cut = cutAt
            cutAt = null
            val stream: InputStream = if (cut == null) ByteArrayInputStream(body) else object : InputStream() {
                var i = 0
                override fun read(): Int = if (i >= cut) throw IOException("connection reset") else body[i++].toInt() and 0xff
            }
            return ModelResponse(if (start > 0) 206 else 200, body.size.toLong(), stream)
        }
    }

    @Test fun downloadsInstallsAndSwitchesTheProviderOn() = runTest {
        val progress = mutableListOf<Pair<Long, Long>>()
        val out = ModelDownloader(models, FakeServer()).download(url) { d, t -> progress += d to t }
        val installed = (out as DownloadResult.Done).result as ModelInstallResult.Installed
        assertEquals("Qwen3-0.6B.litertlm", installed.info.name)
        val file = File(models.dir, "Qwen3-0.6B.litertlm")
        assertArrayEquals(modelBytes, file.readBytes())
        assertFalse(models.partFile.exists())
        assertEquals(modelBytes.size.toLong() to modelBytes.size.toLong(), progress.last())
        val c = cfg.current().providers.onDevice
        assertTrue(c.enabled)
        assertEquals(file.absolutePath, c.modelPath)
        assertEquals("Qwen3-0.6B.litertlm", models.current.first()?.name)
    }

    @Test fun interruptedDownloadResumesWithRange() = runTest {
        val server = FakeServer(cutAt = 500_000)
        val d = ModelDownloader(models, server)
        assertEquals(DownloadResult.Retry, d.download(url) { _, _ -> })
        assertEquals(500_000L, models.partFile.length())
        assertTrue(d.download(url) { _, _ -> } is DownloadResult.Done)
        assertEquals(listOf(0L, 500_000L), server.ranges)
        assertArrayEquals(modelBytes, File(models.dir, "Qwen3-0.6B.litertlm").readBytes())
    }

    @Test fun serverThatIgnoresRangeRestartsFromZero() = runTest {
        models.partFile.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(1000)) }
        val out = ModelDownloader(models, FakeServer(ignoreRange = true)).download(url) { _, _ -> }
        assertTrue(out is DownloadResult.Done)
        assertArrayEquals(modelBytes, File(models.dir, "Qwen3-0.6B.litertlm").readBytes())
    }

    @Test fun gatedOrMissingIsRefusedAndBusyIsRetried() = runTest {
        assertEquals(DownloadResult.Refused(401), ModelDownloader(models, FakeServer(status = 401)).download(url) { _, _ -> })
        assertEquals(DownloadResult.Refused(404), ModelDownloader(models, FakeServer(status = 404)).download(url) { _, _ -> })
        assertEquals(DownloadResult.Retry, ModelDownloader(models, FakeServer(status = 503)).download(url) { _, _ -> })
        assertEquals(DownloadResult.Retry, ModelDownloader(models) { _, _ -> throw IOException("offline") }.download(url) { _, _ -> })
        assertNull(cfg.current().providers.onDevice.modelPath)
    }

    @Test fun aTinyFileIsNotAModel() = runTest {
        val out = ModelDownloader(models, FakeServer(bytes = "<html>log in</html>".toByteArray())).download(url) { _, _ -> }
        assertEquals(DownloadResult.Done(ModelInstallResult.NotAModel), out)
        assertNull(cfg.current().providers.onDevice.modelPath)
        assertFalse(models.partFile.exists())
    }

    @Test fun newModelReplacesTheOldAndRemoveCleansUp() = runTest {
        ModelDownloader(models, FakeServer()).download("https://x/old.litertlm") { _, _ -> }
        ModelDownloader(models, FakeServer()).download("https://x/new.litertlm") { _, _ -> }
        assertEquals(listOf("new.litertlm"), models.dir.list()!!.toList())
        models.remove()
        assertTrue(models.dir.list()!!.isEmpty())
        val c = cfg.current().providers.onDevice
        assertFalse(c.enabled)
        assertNull(c.modelPath)
        assertNull(models.current.first())
    }

    @Test fun namesAreMadeSafe() {
        assertEquals("evil.litertlm", OnDeviceModels.safeName("../../evil.litertlm"))
        assertEquals(OnDeviceModels.DEFAULT_NAME, OnDeviceModels.safeName("download.part"))
        assertEquals("model.task", ModelDownloader.nameFrom("https://h/a/model.task?x=1"))
    }
}
