package app.klean.feature.settings.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.AiConfig
import app.klean.core.ai.AiConfigStore
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.AiProvidersConfig
import app.klean.core.ai.AiService
import app.klean.core.ai.Capability
import app.klean.core.ai.StructuredRequest
import app.klean.core.ai.provider.AiReasons
import app.klean.core.data.secret.SecretCipher
import app.klean.core.data.secret.SecretStore
import app.klean.feature.settings.R
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class Store : AiConfigStore {
    val state = MutableStateFlow(AiConfig())
    override val config = state
    override suspend fun updateProviders(transform: (AiProvidersConfig) -> AiProvidersConfig) =
        state.update { it.copy(providers = transform(it.providers)) }
    override suspend fun assign(task: AiTask, provider: ProviderId?) = state.update {
        it.copy(assignments = if (provider == null) it.assignments - task else it.assignments + (task to provider))
    }
}

/** Mirrors the real providers' "on" rule from config + key presence. */
private class Provider(override val id: ProviderId, val store: Store, val secrets: SecretStore) : AiProvider {
    override val needsNetwork = true
    override suspend fun capabilities() = emptySet<Capability>()
    override suspend fun isConfiguredAndEnabled() = when (id) {
        ProviderId.GEMINI -> store.state.value.providers.gemini.enabled && secrets.get(SecretStore.GEMINI_API_KEY) != null
        ProviderId.OPENAI_COMPAT -> store.state.value.providers.openAi.enabled
        ProviderId.ON_DEVICE -> false
    }
    override suspend fun generate(req: StructuredRequest) = error("use service")
}

private class Service(val providers: List<Provider>) : AiService {
    var answer: AiOutcome = AiOutcome.NoProvider
    val sent = mutableListOf<StructuredRequest>()
    override suspend fun hasProviderFor(task: AiTask) = false
    override suspend fun providerFor(task: AiTask): AiProvider? = null
    override suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean) = AiOutcome.NoProvider
    override suspend fun runWith(p: AiProvider, req: StructuredRequest, sendImage: Boolean): AiOutcome {
        sent += req
        return answer
    }
    override fun provider(id: ProviderId): AiProvider? = providers.firstOrNull { it.id == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiSettingsViewModelTest {
    private val store = Store()
    private val secrets = run {
        val f = File(createTempDirectory("secrets").toFile(), "secrets.preferences_pb")
        SecretStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { f }),
            object : SecretCipher {
                override fun encrypt(plain: ByteArray) = plain.reversedArray()
                override fun decrypt(blob: ByteArray) = blob.reversedArray()
            },
        )
    }
    private val service = Service(ProviderId.entries.filter { it != ProviderId.ON_DEVICE }.map { Provider(it, store, secrets) })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    private val vms = ViewModelStore()
    private fun <T : ViewModel> T.tracked(): T = also { vms.put("vm-${System.nanoTime()}", it) }

    @After fun tearDown() {
        // Cancel VM collectors while Main is still the test dispatcher; otherwise a late DataStore
        // emission resumes on the real (absent) Main and fails whichever test runs next.
        vms.clear()
        Dispatchers.resetMain()
    }

    private fun detail(id: ProviderId) = AiProviderViewModel(SavedStateHandle(mapOf("provider" to id.name)), store, secrets, service).tracked()

    @Test fun listShowsOffUntilEnabledAndOffersOnlyProvidersThatAreOn() = runTest {
        val vm = AiProvidersViewModel(store, secrets, service).tracked()
        val s0 = vm.state.first { it.loaded }
        assertFalse(s0.statusOf(ProviderId.GEMINI))
        assertFalse(s0.statusOf(ProviderId.OPENAI_COMPAT))
        assertEquals(emptyList<ProviderId>(), s0.options)

        store.updateProviders { it.copy(openAi = it.openAi.copy(enabled = true)) }
        val s1 = vm.state.first { it.statusOf(ProviderId.OPENAI_COMPAT) }
        assertEquals(listOf(ProviderId.OPENAI_COMPAT), s1.options)

        vm.assign(AiTask.ENTRY_PARSE, ProviderId.OPENAI_COMPAT)
        assertEquals(ProviderId.OPENAI_COMPAT, store.state.value.assignments[AiTask.ENTRY_PARSE])
        assertEquals(ProviderId.OPENAI_COMPAT, vm.state.first { it.assignments.isNotEmpty() }.assignments[AiTask.ENTRY_PARSE])
        vm.assign(AiTask.ENTRY_PARSE, null)
        assertNull(store.state.value.assignments[AiTask.ENTRY_PARSE])
    }

    @Test fun geminiKeyGoesToSecretStoreNeverToConfig() = runTest {
        val vm = detail(ProviderId.GEMINI)
        vm.state.first { it.loaded }
        vm.onKeyChange("AIza-123")
        vm.saveKey().join()
        assertEquals("AIza-123", secrets.get(SecretStore.GEMINI_API_KEY))
        assertFalse(store.state.value.toString().contains("AIza"))
        val s = vm.state.first { it.hasKey }
        assertEquals("", s.keyInput)
        vm.removeKey().join()
        assertNull(secrets.get(SecretStore.GEMINI_API_KEY))
    }

    @Test fun geminiSwitchesAndModelPersist() = runTest {
        val vm = detail(ProviderId.GEMINI)
        vm.state.first { it.loaded }
        assertEquals("gemini-2.5-flash", vm.state.value.model)
        vm.setEnabled(true)
        vm.setSendPhotos(true)
        vm.onModelChange("gemini-2.5-pro")
        vm.flush().join()
        val g = store.state.value.providers.gemini
        assertTrue(g.enabled)
        assertTrue(g.sendPhotos)
        assertEquals("gemini-2.5-pro", g.model)
    }

    @Test fun openAiFieldsPersist() = runTest {
        val vm = detail(ProviderId.OPENAI_COMPAT)
        vm.state.first { it.loaded }
        vm.onBaseUrlChange("http://192.168.240.1:8765/v1")
        vm.onModelChange("fake")
        vm.setAcceptsImages(true)
        vm.setEnabled(true)
        vm.flush().join()
        val o = store.state.value.providers.openAi
        assertEquals("http://192.168.240.1:8765/v1", o.baseUrl)
        assertEquals("fake", o.model)
        assertTrue(o.acceptsImages)
        assertTrue(o.enabled)
    }

    @Test fun testConnectionNeedsTheProviderOnThenShowsParsedResult() = runTest {
        val vm = detail(ProviderId.OPENAI_COMPAT)
        vm.state.first { it.loaded }
        vm.testConnection().join()
        assertEquals(TestResult.Error(R.string.ai_test_turn_on), vm.state.value.test)
        assertTrue(service.sent.isEmpty())

        vm.setEnabled(true)
        service.answer = AiOutcome.Success(
            Json.parseToJsonElement("""{"items":[{"name":"toast","portion":"2 slices","calories":180},{"name":"butter","portion":"1 pat","calories":35}],"mealType":"breakfast"}"""),
            ProviderId.OPENAI_COMPAT, "fake",
        )
        vm.testConnection().join()
        assertEquals("toast with butter", service.sent.single().user)
        assertEquals(TestResult.Ok("toast · 2 slices, butter · 1 pat"), vm.state.value.test)

        service.answer = AiOutcome.Unavailable(AiReasons.NETWORK)
        vm.testConnection().join()
        assertEquals(TestResult.Error(R.string.ai_error_network), vm.state.value.test)
    }

    @Test fun geminiRejectionUsesThePlanMessage() = runTest {
        secrets.put(SecretStore.GEMINI_API_KEY, "bad")
        store.updateProviders { it.copy(gemini = it.gemini.copy(enabled = true)) }
        val vm = detail(ProviderId.GEMINI)
        vm.state.first { it.loaded }
        service.answer = AiOutcome.Failed(AiReasons.GEMINI_REJECTED)
        vm.testConnection().join()
        assertEquals(TestResult.Error(R.string.ai_error_gemini_rejected), vm.state.value.test)
    }
}
