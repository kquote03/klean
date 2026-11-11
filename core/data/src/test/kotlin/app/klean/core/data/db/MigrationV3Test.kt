package app.klean.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.ApprovalRequestEntity
import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.DelegationEntity
import app.klean.core.data.db.entity.DelegationMessageEntity
import app.klean.core.data.db.entity.DeviceEntity
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import app.klean.core.data.db.entity.SleepScheduleEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.SyncCursorEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The committed v2 fixture (one row per v2 table, written by [V2FixtureWriter]) opens on v3 through
 * the 2→3 AutoMigration: rows intact, new columns at their defaults, new tables empty and usable.
 * Room validates the migrated schema against 3.json on open, so a wrong default fails here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV3Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<KleanDatabase>()

    private fun open(resource: String, name: String): KleanDatabase {
        val file = File(ctx.cacheDir, name).apply {
            listOf("", "-wal", "-shm").forEach { File(path + it).delete() }
            writeBytes(MigrationV3Test::class.java.getResourceAsStream(resource)!!.use { it.readBytes() })
        }
        return Room.databaseBuilder(ctx, KleanDatabase::class.java, file.path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .also { opened += it }
    }

    private val db by lazy { open("/backup-v2.sqlite", "migration-v3-test.db") }

    @After fun close() = opened.forEach { it.close() }

    private suspend fun KleanDatabase.userVersion(): Long =
        useReaderConnection { c -> c.usePrepared("PRAGMA user_version") { s -> s.step(); s.getLong(0) } }

    @Test fun v2FixtureMigratesToV3() = runBlocking {
        assertEquals(KleanDatabase.VERSION.toLong(), db.userVersion())
        assertEquals(listOf("porridge"), db.foodEntryDao().range(0, Long.MAX_VALUE).map { it.rawText })
        // The change counter carries on from v2, so new writes still sort after old ones.
        assertEquals(13L, db.changeCounterDao().next())
    }

    @Test fun existingRowsGetColumnDefaults() = runBlocking {
        val open = db.taskDao().get("task-1")!!
        assertEquals("buy milk", open.title)
        assertNull(open.parentId)
        assertEquals("OPEN", open.status)
        assertNull(open.archivedAt)
        assertEquals("KLEAN", open.origin)
        assertNull(open.remoteId)
        assertNull(open.remoteListId)
        assertNull(open.remoteEtag)
        assertEquals("MANUAL", open.source)

        val list = db.taskListDao().get("list-1")!!
        assertEquals("KLEAN", list.source)
        assertNull(list.remoteId)
        assertTrue(list.selected)

        val contact = db.contactDao().get("c-1")!!
        assertEquals("Ahmed", contact.displayName)
        assertNull(contact.nameJson)
        assertEquals("[]", contact.addresses)
        assertNull(contact.org)
        assertNull(contact.photoBlob)
        assertNull(contact.anniversary)
        assertEquals("{}", contact.custom)
        assertEquals("KLEAN", contact.sourceAccount)
        assertNull(contact.remoteUid)

        val event = db.eventDao().get("ev-1")!!
        assertEquals("dentist", event.title)
        assertNull(event.calendarId)
        assertNull(event.uid)
        assertNull(event.rrule)
        assertEquals("[]", event.exdates)
        assertFalse(event.alarm)
        assertNull(event.remoteEtag)

        val set = db.workoutSetDao().get("wset-1")!!
        assertEquals("pushups", set.exercise)
        assertNull(set.exerciseId)

        val session = db.workoutSessionDao().get("ws-1")!!
        assertNull(session.routeBlob)
        assertNull(session.distanceM)
        assertNull(session.hcRecordId)

        assertNull(db.sleepDao().get("sl-1")!!.hcRecordId)
        assertEquals("rem-1", db.reminderDao().get("rem-1")!!.id)
        assertEquals("4", db.settingDao().get("diet.gapHours")?.valueJson)
    }

    @Test fun tasksDoneBeforeV3AreMarkedDone() = runBlocking {
        val done = db.taskDao().get("task-2")!!
        assertEquals(1_762_700_100_000L, done.completedAt)
        assertEquals("DONE", done.status)
    }

    @Test fun newTablesStartEmptyAndAcceptRows() = runBlocking {
        assertTrue(db.delegationDao().observeAll().first().isEmpty())
        assertTrue(db.delegationMessageDao().observeAll().first().isEmpty())
        assertTrue(db.approvalRequestDao().observeAll().first().isEmpty())
        assertTrue(db.calendarSourceDao().observeAll().first().isEmpty())
        assertTrue(db.exerciseDefinitionDao().observeAll().first().isEmpty())
        assertTrue(db.sleepScheduleDao().observeAll().first().isEmpty())
        assertTrue(db.deviceDao().observeAll().first().isEmpty())
        assertNull(db.syncCursorDao().get())

        val s = SyncColumns(1L, 1L, "dev", false, 20L)
        db.delegationDao().upsert(DelegationEntity("d-1", "task-1", "AGENTIC", "HERMES", sync = s))
        db.delegationMessageDao().upsert(DelegationMessageEntity("m-1", "d-1", "AGENT", "Which bank?", 5L, s))
        db.approvalRequestDao().upsert(ApprovalRequestEntity("a-1", "d-1", "send_email", "Hello", sync = s))
        db.calendarSourceDao().upsert(CalendarSourceEntity("cal-1", "DEVICE", "Work", sync = s))
        db.exerciseDefinitionDao().upsert(ExerciseDefinitionEntity("ex-1", "Pushups", "STRENGTH", sync = s))
        db.sleepScheduleDao().upsert(SleepScheduleEntity("ss-1", 23 * 60, 7 * 60, 127, sync = s))
        db.deviceDao().upsert(DeviceEntity("dev", "Pixel", "ANDROID", isMainPhone = true, lastSeenAt = 9L, sync = s))
        db.syncCursorDao().upsert(SyncCursorEntity(cursor = 4, lastPushSeq = 12))

        assertEquals(listOf("d-1"), db.delegationDao().active().map { it.id })
        assertEquals("QUEUED", db.delegationDao().get("d-1")!!.status)
        assertEquals(listOf("Which bank?"), db.delegationMessageDao().forDelegation("d-1").map { it.text })
        val approval = db.approvalRequestDao().observePending().first().single()
        assertEquals("PENDING", approval.decision)
        assertEquals("[]", approval.recipients)
        assertEquals("{}", approval.editable)
        assertTrue(db.calendarSourceDao().observeSelected().first().single().selected)
        assertEquals("ex-1", db.exerciseDefinitionDao().byName("pushups")?.id)
        assertEquals(30, db.sleepScheduleDao().all().single().reminderMinutesBefore)
        assertEquals("dev", db.deviceDao().mainPhone()?.id)
        assertEquals(SyncCursorEntity("server", 4, 12), db.syncCursorDao().get())
    }

    @Test fun v1FileMigratesThroughV2ToV3() = runBlocking {
        val chained = open("/backup-v1.sqlite", "migration-v1-v3-test.db")
        assertEquals(KleanDatabase.VERSION.toLong(), chained.userVersion())
        assertEquals(
            listOf("porridge", "shawarma and fries", "tea"),
            chained.foodEntryDao().range(0, Long.MAX_VALUE).map { it.rawText },
        )
        assertTrue(chained.taskDao().observeAll().first().isEmpty())
        assertTrue(chained.deviceDao().observeAll().first().isEmpty())
        assertNull(chained.syncCursorDao().get())
    }
}
