package app.klean.feature.delegation.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.delegation.DubaiZone
import app.klean.feature.delegation.FakeClock
import app.klean.feature.delegation.data.DelegationMode
import app.klean.feature.delegation.data.DelegationRepository
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.delegation.inMemoryDb
import app.klean.feature.delegation.tempDevicePrefs
import app.klean.feature.tasks.data.DefaultTasksRepository
import app.klean.feature.tasks.data.TaskDraft
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DelegationGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 11, d, h, m).toInstant(TimeZone.of("Asia/Dubai")).toEpochMilliseconds()
    private val clock = FakeClock(at(10, 9))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val tasks = DefaultTasksRepository(db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), emptySet())
    private val repo = DelegationRepository(db, stamper, clock, emptySet())
    private val provider = DelegationGlanceProvider(ctx, { db }, clock, DubaiZone)

    @After fun close() = db.close()

    @Test fun approvalsAndQuestionsNeedAttention() = runBlocking {
        val book = repo.create(tasks.add(TaskDraft("Book a table")), DelegationMode.INTERACTIVE, "hermes")
        repo.addApproval(book, "send_email", "Hi", listOf("a@b.c"))
        val venue = repo.create(tasks.add(TaskDraft("Find a venue")), DelegationMode.INTERACTIVE, "hermes")
        repo.update(venue, status = DelegationStatus.NEEDS_INPUT)
        val g = provider.glance().first()
        assertEquals(setOf("Book a table", "Find a venue"), g.attention.map { it.text }.toSet())
        assertEquals("Wants to: send email", g.attention.first { it.text == "Book a table" }.detail)
        assertTrue(g.attention.first { it.text == "Book a table" }.route!!.startsWith("klean://delegation/approval/"))
        assertTrue(g.attention.all { it.module == ModuleId.DELEGATION })
        assertNull(g.quickAction)
        assertFalse(g.attention.flatMap { listOfNotNull(it.text, it.detail) }.any { "!" in it })
    }

    @Test fun finishedTodayOnly() = runBlocking {
        clock.nowMs = at(9, 22)
        val old = repo.create(tasks.add(TaskDraft("Yesterday's errand")), DelegationMode.PARTIAL, "ai")
        repo.update(old, status = DelegationStatus.DONE)
        clock.nowMs = at(10, 8)
        val d = repo.create(tasks.add(TaskDraft("Draft the invite")), DelegationMode.PARTIAL, "ai")
        repo.update(d, status = DelegationStatus.DONE)
        clock.nowMs = at(10, 9)
        assertEquals(listOf("Draft the invite"), provider.glance().first().done.map { it.text })
    }
}
