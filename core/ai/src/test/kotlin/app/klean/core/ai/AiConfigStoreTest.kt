package app.klean.core.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.klean.core.data.prefs.DevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

fun tempPrefs(): DevicePrefs {
    val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
    return DevicePrefs(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { file }))
}

class AiConfigStoreTest {
    @Test fun defaultsAreAllOffWithPlanDefaults() = runBlocking {
        val c = DevicePrefsAiConfigStore(tempPrefs(), emptySet()).current()
        assertFalse(c.providers.gemini.enabled)
        assertEquals("gemini-2.5-flash", c.providers.gemini.model)
        assertFalse(c.providers.gemini.sendPhotos)
        assertFalse(c.providers.openAi.enabled)
        assertEquals("http://192.168.1.10:11434/v1", c.providers.openAi.baseUrl)
        assertEquals("llama3.2", c.providers.openAi.model)
        assertEquals(emptyMap<AiTask, ProviderId>(), c.assignments)
    }

    @Test fun updatesPersistAndNotifyListenersOnlyOnChange() = runBlocking {
        var calls = 0
        val prefs = tempPrefs()
        val store = DevicePrefsAiConfigStore(prefs, setOf(AiConfigListener { calls++ }))
        store.updateProviders { it.copy(openAi = it.openAi.copy(enabled = true, baseUrl = "http://192.168.240.1:8765/v1")) }
        store.assign(AiTask.ENTRY_PARSE, ProviderId.OPENAI_COMPAT)
        store.assign(AiTask.ENTRY_PARSE, ProviderId.OPENAI_COMPAT) // no change
        assertEquals(2, calls)

        val reread = DevicePrefsAiConfigStore(prefs, emptySet())
        assertEquals(ProviderId.OPENAI_COMPAT, reread.assignment(AiTask.ENTRY_PARSE))
        assertEquals("http://192.168.240.1:8765/v1", reread.config.first().providers.openAi.baseUrl)
        store.assign(AiTask.ENTRY_PARSE, null)
        assertNull(reread.assignment(AiTask.ENTRY_PARSE))
    }

    @Test fun corruptJsonFallsBackToDefaults() = runBlocking {
        val prefs = tempPrefs()
        prefs.setAiProviders("{not json")
        prefs.setAiTaskAssignment("""{"ENTRY_PARSE":"NOPE","CAPTURE_ROUTING":"GEMINI"}""")
        val c = DevicePrefsAiConfigStore(prefs, emptySet()).current()
        assertEquals(AiProvidersConfig(), c.providers)
        assertEquals(mapOf(AiTask.CAPTURE_ROUTING to ProviderId.GEMINI), c.assignments)
    }
}
