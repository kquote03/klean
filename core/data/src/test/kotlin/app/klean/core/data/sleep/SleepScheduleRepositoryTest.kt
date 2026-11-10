package app.klean.core.data.sleep

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.FakeClock
import app.klean.core.data.inMemoryDb
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepScheduleRepositoryTest {
    private val clock = FakeClock(1_800_000_000_000)
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = SleepScheduleRepository(db, stamper, settings)

    @After fun close() = db.close()

    @Test fun beforeMigrationTheLegacyKeyIsTheWindow() = runBlocking {
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        assertNull(db.sleepScheduleDao().get(SleepScheduleRepository.PRIMARY_ID))
        assertEquals(QuietHours(23 * 60, 7 * 60), repo.current())
        assertEquals(QuietHours(23 * 60, 7 * 60), repo.quietHours.first())
    }

    @Test fun migratesTheSyncedKeyIntoTheFirstScheduleRowExactlyOnce() = runBlocking {
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        repo.ensureMigrated()
        val row = db.sleepScheduleDao().get(SleepScheduleRepository.PRIMARY_ID)!!
        assertEquals(23 * 60, row.bedMinutes)
        assertEquals(7 * 60, row.wakeMinutes)
        assertEquals(127, row.days)
        assertEquals(30, row.reminderMinutesBefore)
        val seq = row.sync.changeSeq

        // A later change to the old key (e.g. an older device) never re-runs the migration.
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(1 * 60, 9 * 60))
        repo.ensureMigrated()
        val again = db.sleepScheduleDao().get(SleepScheduleRepository.PRIMARY_ID)!!
        assertEquals(23 * 60, again.bedMinutes)
        assertEquals(seq, again.sync.changeSeq)
        // From then on the schedule is the source.
        assertEquals(QuietHours(23 * 60, 7 * 60), repo.current())
    }

    @Test fun aFreshInstallMigratesTheDefaultWindow() = runBlocking {
        repo.ensureMigrated()
        assertEquals(QuietHours.DEFAULT, repo.current())
        assertEquals(1, db.sleepScheduleDao().all().size)
    }

    @Test fun settingTheWindowWritesTheScheduleAndMirrorsTheKey() = runBlocking {
        repo.setWindow(QuietHours(22 * 60 + 30, 6 * 60 + 30))
        val row = db.sleepScheduleDao().get(SleepScheduleRepository.PRIMARY_ID)!!
        assertEquals(22 * 60 + 30, row.bedMinutes)
        assertEquals(6 * 60 + 30, row.wakeMinutes)
        assertEquals(QuietHours(22 * 60 + 30, 6 * 60 + 30), repo.quietHours.first())
        // Readers that still use the synced key (diet) see the same window.
        assertEquals(QuietHours(22 * 60 + 30, 6 * 60 + 30), settings.get(SyncedKeys.QuietHoursKey))
    }

    @Test fun reminderMinutesAreKept() = runBlocking {
        repo.setReminderMinutesBefore(45)
        repo.setWindow(QuietHours(23 * 60, 7 * 60))
        assertEquals(45, repo.primary().reminderMinutesBefore)
        assertEquals(45, repo.observePrimary().first().reminderMinutesBefore)
    }
}
