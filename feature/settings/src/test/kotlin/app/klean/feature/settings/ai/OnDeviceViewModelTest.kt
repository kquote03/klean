package app.klean.feature.settings.ai

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.AiService
import app.klean.core.ai.Capability
import app.klean.core.ai.OnDeviceConfig
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.ondevice.ModelDownloadState
import app.klean.core.ai.ondevice.ModelDownloadWorker
import app.klean.core.ai.ondevice.ModelDownloads
import app.klean.core.ai.ondevice.ModelInfo
import app.klean.core.ai.ondevice.ModelInstallResult
import app.klean.core.ai.ondevice.OnDeviceModelStore
import app.klean.core.ai.provider.AiReasons
import app.klean.feature.settings.R
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class OdStore : AiConfigStore {
    val state = MutableStateFlow(AiConfig())
    override val config = state
    override suspend fun updateProviders(transform: (AiProvidersConfig) -> AiProvidersConfig) =
        state.update { it.copy(providers = transform(it.providers)) }
    override suspend fun assign(task: AiTask, provider: ProviderId?) = Unit
}

private class FakeModels(private val store: OdStore) : OnDeviceModelStore {
    override val current = MutableStateFlow<ModelInfo?>(null)
    var next: ModelInstallResult = ModelInstallResult.Installed(ModelInfo("m.litertlm", 600L shl 20))
    override suspend fun importFrom(uri: Uri): ModelInstallResult {
        (next as? ModelInstallResult.Installed)?.let {
            current.value = it.info
            store.updateProviders { p -> p.copy(onDevice = p.onDevice.copy(enabled = true, modelPath = "/m")) }
        }
        return next
    }
    override suspend fun remove() {
        current.value = null
        store.updateProviders { p -> p.copy(onDevice = p.onDevice.copy(enabled = false, modelPath = null)) }
    }
}

private class FakeDownloads : ModelDownloads {
    override val state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val started = mutableListOf<String>()
    var cancelled = 0
    override fun start(url: String) { started += url }
    override fun cancel() { cancelled++ }
}

private class OdProvider(private val store: OdStore) : AiProvider {
    override val id = ProviderId.ON_DEVICE
    override val needsNetwork = false
    override suspend fun capabilities() = setOf(Capability.OFFLINE)
    override suspend fun isConfiguredAndEnabled() = store.state.value.providers.onDevice.let { it.enabled && it.modelPath != null }
    override suspend fun generate(req: StructuredRequest) = error("use service")
}

private class OdService(val p: OdProvider) : AiService {
    var answer: AiOutcome = AiOutcome.Failed(AiReasons.MODEL_UNUSABLE)
    override suspend fun hasProviderFor(task: AiTask) = false
    override suspend fun providerFor(task: AiTask): AiProvider? = null
    override suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean) = AiOutcome.NoProvider
    override suspend fun runWith(p: AiProvider, req: StructuredRequest, sendImage: Boolean) = answer
    override fun provider(id: ProviderId): AiProvider? = p.takeIf { id == it.id }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OnDeviceViewModelTest {
    private val store = OdStore()
    private val models = FakeModels(store)
    private val downloads = FakeDownloads()
    private val service = OdService(OdProvider(store))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnDeviceViewModel(store, models, downloads, service)

    @Test fun downloadUsesTheTypedAddressAndRemembersIt() = runTest {
        val vm = vm()
        assertEquals(OnDeviceConfig.DEFAULT_MODEL_URL, vm.state.first { it.loaded }.url)
        vm.onUrlChange(" http://192.168.240.1:8000/tiny.litertlm ")
        vm.startDownload().join()
        assertEquals(listOf("http://192.168.240.1:8000/tiny.litertlm"), downloads.started)
        assertEquals("http://192.168.240.1:8000/tiny.litertlm", store.state.value.providers.onDevice.downloadUrl)
    }

    @Test fun notAUrlIsExplainedAndNothingStarts() = runTest {
        val vm = vm()
        vm.onUrlChange("Qwen3.litertlm")
        vm.startDownload().join()
        assertTrue(downloads.started.isEmpty())
        assertEquals(R.string.ai_on_device_note_bad_url, vm.state.value.note)
    }

    @Test fun failedDownloadShowsACalmNoteOnce() = runTest {
        val vm = vm()
        downloads.state.value = ModelDownloadState.Failed(ModelDownloadWorker.ERROR_REFUSED)
        assertEquals(R.string.ai_on_device_note_refused, vm.state.first { it.note != null }.note)
        vm.import(Uri.parse("content://x/model")).join()
        assertNull("a later success clears the old note", vm.state.value.note)
    }

    @Test fun importingAModelShowsItAndSwitchesItOn() = runTest {
        val vm = vm()
        vm.import(Uri.parse("content://x/model")).join()
        val s = vm.state.first { it.model != null }
        assertEquals("m.litertlm", s.model!!.name)
        assertTrue(s.enabled)
        assertFalse(s.importing)
    }

    @Test fun tooSmallFileIsExplained() = runTest {
        val vm = vm()
        models.next = ModelInstallResult.NotAModel
        vm.import(Uri.parse("content://x/readme")).join()
        assertEquals(R.string.ai_on_device_note_not_a_model, vm.state.value.note)
        assertNull(vm.state.value.model)
    }

    @Test fun aModelThatWontRunGetsACalmMessageNotACrash() = runTest {
        val vm = vm()
        vm.import(Uri.parse("content://x/model")).join()
        vm.test().join()
        assertEquals(TestResult.Error(R.string.ai_error_model_unusable), vm.state.value.test)
    }

    @Test fun removeStopsDownloadsAndClearsTheModel() = runTest {
        val vm = vm()
        vm.import(Uri.parse("content://x/model")).join()
        vm.remove().join()
        assertEquals(1, downloads.cancelled)
        val s = vm.state.first { it.model == null }
        assertFalse(s.enabled)
    }
}
