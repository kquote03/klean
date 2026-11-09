package app.klean.core.data.module

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.FakeClock
import app.klean.core.data.inMemoryDb
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsModuleGateTest {
    private val clock = FakeClock(1_000L)
    private val db = inMemoryDb()
    private val settings = SettingsRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock))
    private val gate = SettingsModuleGate(settings)

    @After fun close() = db.close()

    @Test fun everythingEnabledByDefault() = runBlocking {
        assertTrue(gate.isEnabled(ModuleId.DIET))
        assertEquals(emptySet<ModuleId>(), gate.disabledIds.first())
    }

    @Test fun disablePersistsInSyncedSetting() = runBlocking {
        gate.disabledIds.test {
            assertEquals(emptySet<ModuleId>(), awaitItem())
            gate.setEnabled(ModuleId.DIET, false)
            assertEquals(setOf(ModuleId.DIET), awaitItem())
            gate.setEnabled(ModuleId.DIET, true)
            assertEquals(emptySet<ModuleId>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        gate.setEnabled(ModuleId.SLEEP, false)
        assertFalse(gate.isEnabled(ModuleId.SLEEP))
        assertEquals(setOf("sleep"), settings.get(SyncedKeys.DisabledModules))
    }

    @Test fun seedingRecordsKnownIdsAndKeepsNewOnesOn() = runBlocking {
        // Fresh install: everything on, nothing to announce.
        assertEquals(emptySet<ModuleId>(), gate.seedNew(setOf(ModuleId.DIET)))
        assertEquals(setOf("diet"), settings.get(KnownModules))
        // An update adds two: both on, both announced once.
        assertEquals(setOf(ModuleId.TASKS, ModuleId.SLEEP), gate.seedNew(setOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP)))
        assertEquals(setOf("diet", "tasks", "sleep"), settings.get(KnownModules))
        assertEquals(emptySet<ModuleId>(), gate.disabledIds.first())
        gate.setEnabled(ModuleId.TASKS, false)
        assertEquals(emptySet<ModuleId>(), gate.seedNew(setOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP)))
        assertFalse("switched-off module stays off", gate.isEnabled(ModuleId.TASKS))
        assertTrue(gate.isEnabled(ModuleId.SLEEP))
    }
}
