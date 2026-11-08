package app.klean.core.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.FakeClock
import app.klean.core.data.inMemoryDb
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import app.klean.shared.id.nameUuid
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private val clock = FakeClock(1_000_000L)
    private val db = inMemoryDb()
    private val repo = SettingsRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock))

    @After fun close() = db.close()

    @Test fun defaultWhenRowMissing() = runBlocking {
        assertEquals(QuietHours.DEFAULT, repo.get(SyncedKeys.QuietHoursKey))
        assertEquals(240, repo.get(SyncedKeys.DietGapThresholdMinutes))
        assertEquals(emptySet<String>(), repo.get(SyncedKeys.DisabledModules))
    }

    @Test fun setThenObserveEmitsNewValue() = runBlocking {
        repo.observe(SyncedKeys.DietCadenceHours).test {
            assertEquals(6, awaitItem())
            repo.set(SyncedKeys.DietCadenceHours, 4)
            assertEquals(4, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repo.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        assertEquals(QuietHours(23 * 60, 7 * 60), repo.get(SyncedKeys.QuietHoursKey))
    }

    @Test fun rowIdIsNameUuidOfKeyAndChangeSeqIncrements() = runBlocking {
        repo.set(SyncedKeys.DietShowCalories, true)
        val first = db.settingDao().get("diet.showCalories")!!
        assertEquals(nameUuid("diet.showCalories"), first.id)
        assertEquals("true", first.valueJson)
        clock.nowMs += 1_000
        repo.set(SyncedKeys.DietShowCalories, false)
        val second = db.settingDao().get("diet.showCalories")!!
        assertEquals(first.id, second.id)
        assertTrue(second.sync.changeSeq > first.sync.changeSeq)
        assertEquals(first.sync.createdAt, second.sync.createdAt)
        assertEquals(1, db.settingDao().all().size)
    }
}
