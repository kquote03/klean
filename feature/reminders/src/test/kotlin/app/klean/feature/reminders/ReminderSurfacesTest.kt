package app.klean.feature.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.StatusSurface
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.atMinute
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderSurfacesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nov9 = LocalDate(2026, 11, 9)
    private val noon = atMinute(nov9, 12 * 60, Dubai)
    private val clock = FakeClock(noon)
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private var refreshes = 0
    private val surfaces = ReminderSurfaces(
        { db }, ReminderFormatter(ctx, DubaiZone, clock), clock, DubaiZone,
        { StatusRefresh { setOf(object : StatusSurface { override suspend fun refresh() { refreshes++ } }) } },
    )
    private val repo = DefaultReminderRepository(db, stamper, clock, setOf<ReminderChangeListener>(surfaces))

    @After fun close() = db.close()

    @Test fun captureParsesAndUndoes() = runBlocking {
        val h = ReminderCaptureHandler(repo, DubaiZone)
        val id = h.execute(CaptureInput("remind me in 2 min to stretch", CaptureSource.QS_TILE, noon), h.defaultAction(CaptureInput("", CaptureSource.QS_TILE, noon)))
        val r = db.reminderDao().get(id)!!
        assertEquals("stretch", r.title)
        assertEquals(noon + 2 * MINUTE_MS, r.fireAt)
        assertEquals("Asia/Dubai", r.zone)
        h.undo(id)
        assertTrue(db.reminderDao().get(id)!!.sync.deleted)
    }

    @Test fun nextReminderOnTheStatusLineTileAndWidget() = runBlocking {
        repo.add("Call Ahmed", noon + 5 * HOUR_MS, "Asia/Dubai", isAlarm = false)
        repo.add("Tomorrow thing", noon + 30 * HOUR_MS, "Asia/Dubai", isAlarm = false)
        surfaces.fragments().test(timeout = 10.seconds) {
            var l = awaitItem()
            while (l.isEmpty()) l = awaitItem()
            assertEquals(ReminderSurfaces.KEY_NEXT, l.single().key)
            assertTrue(l.single().text.startsWith("Call Ahmed "))
            cancelAndIgnoreRemainingEvents()
        }
        surfaces.items().test(timeout = 10.seconds) {
            var l = awaitItem()
            while (l.isEmpty()) l = awaitItem()
            assertEquals(listOf("Call Ahmed"), l.map { it.text })
            cancelAndIgnoreRemainingEvents()
        }
        surfaces.summaries().test(timeout = 10.seconds) {
            var l = awaitItem()
            while (l.isEmpty()) l = awaitItem()
            assertEquals("Next reminder", l.single().label)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(refreshes >= 2)
    }

    @Test fun aFiredReminderComesFirst() = runBlocking {
        val id = repo.add("Pills", noon, "Asia/Dubai", isAlarm = false)
        repo.update(db.reminderDao().get(id)!!.copy(state = RINGING, lastFiredAt = noon))
        surfaces.fragments().test(timeout = 10.seconds) {
            var l = awaitItem()
            while (l.isEmpty()) l = awaitItem()
            assertEquals(ReminderSurfaces.KEY_FIRED, l.first().key)
            assertEquals("Pills", l.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
