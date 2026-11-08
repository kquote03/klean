package app.klean.core.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuietHoursServiceTest {
    private val day = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(day, 3 * 60, Dubai))
    private val db = inMemoryDb()
    private val settings = SettingsRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock))
    private val service = QuietHoursService(settings, DubaiZone, clock)

    @After fun tearDown() = db.close()

    @Test fun threeAmIsQuietWithDefaults() = runBlocking {
        assertTrue(service.isQuiet())
    }

    @Test fun exactUserAlarmsAreExempt() = runBlocking {
        assertFalse(service.isQuiet(exactUserAlarm = true))
    }

    @Test fun middayIsNotQuiet() = runBlocking {
        assertFalse(service.isQuiet(atMinute(day, 12 * 60, Dubai)))
    }

    @Test fun nextAllowedIsTheEndOfTheSleepWindow() = runBlocking {
        assertEquals(atMinute(day, 8 * 60, Dubai), service.nextAllowed(clock.nowMs))
        val noon = atMinute(day, 12 * 60, Dubai)
        assertEquals(noon, service.nextAllowed(noon))
    }

    @Test fun followsTheSyncedSetting() = runBlocking {
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(22 * 60, 6 * 60))
        assertFalse(service.isQuiet(atMinute(day, 7 * 60, Dubai)))
        assertTrue(service.isQuiet(atMinute(day, 23 * 60, Dubai)))
    }
}
