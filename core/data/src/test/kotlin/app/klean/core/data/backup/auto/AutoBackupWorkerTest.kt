package app.klean.core.data.backup.auto

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.klean.core.common.ZoneProvider
import app.klean.core.data.backup.BackupTestRig
import java.io.File
import java.io.OutputStream
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A backup folder in a temp directory, standing in for a SAF tree. */
class DirBackupTarget(val dir: File) : BackupTarget {
    override fun list(): List<String> = dir.list()?.toList().orEmpty()
    override fun create(name: String): OutputStream = File(dir, name).outputStream()
    override fun delete(name: String): Boolean = File(dir, name).delete()
}

@RunWith(AndroidJUnit4::class)
class AutoBackupWorkerTest {
    private val rig = BackupTestRig()
    private val dir = createTempDirectory("tree").toFile()
    private var target: BackupTarget? = DirBackupTarget(dir)
    private val openedUris = mutableListOf<String>()
    private var failureNotices = 0
    private val utc = object : ZoneProvider {
        override fun current() = TimeZone.UTC
    }

    @After fun close() = rig.db.close()

    private fun worker(attempt: Int = 0): AutoBackupWorker =
        TestListenableWorkerBuilder<AutoBackupWorker>(rig.ctx)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    AutoBackupWorker(
                        appContext, workerParameters, rig.manager(), rig.prefs,
                        { uri -> openedUris += uri; target }, { failureNotices++ }, rig.clock, utc,
                    )
            })
            .build()

    private suspend fun enable(keep: Int = 7) {
        rig.prefs.setAutoBackupEnabled(true)
        rig.prefs.setAutoBackupTreeUri("content://tree/backups")
        rig.prefs.setAutoBackupKeep(keep)
    }

    @Test fun nineRunsWithKeepSevenLeaveTheNewestSeven() = runBlocking {
        enable(keep = 7)
        rig.logEntry("e1", "toast", 1_000L)
        File(dir, "notes.txt").writeText("not ours")
        rig.clock.nowMs = 1_762_603_200_000L // 2025-11-08 12:00 UTC
        val names = mutableListOf<String>()
        repeat(9) {
            assertEquals(ListenableWorker.Result.success(), worker().doWork())
            names += AutoBackupWorker.fileName(rig.clock.now(), TimeZone.UTC)
            rig.clock.nowMs += 24 * 3_600_000L
        }
        assertEquals("klean-backup-20251108-1200.sqlite", names.first())
        val left = dir.list()!!.filter { it.startsWith("klean-backup-") }.sorted()
        assertEquals(names.takeLast(7), left)
        assertTrue(File(dir, "notes.txt").exists())
        assertEquals("content://tree/backups", openedUris.distinct().single())
        val info = rig.manager().inspect(File(dir, left.last()).inputStream())
        assertEquals(1, info.entryCount)
    }

    @Test fun rotationIgnoresOtherFilesAndSortsByName() {
        val names = listOf(
            "klean-backup-20250101-0900.sqlite", "klean-backup-20250103-0900.sqlite", "photo.jpg",
            "klean-backup-20250102-0900.sqlite", "klean-backup-x.txt",
        )
        assertEquals(listOf("klean-backup-20250101-0900.sqlite"), AutoBackupWorker.toDelete(names, keep = 2))
        assertEquals(emptyList<String>(), AutoBackupWorker.toDelete(names, keep = 7))
    }

    @Test fun disabledDoesNothing() = runBlocking {
        rig.prefs.setAutoBackupEnabled(false)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(emptyList<String>(), openedUris)
        assertEquals(0, failureNotices)
    }

    @Test fun unreachableFolderRetriesThenPostsAQuietNotice() = runBlocking {
        enable()
        target = null
        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 2).doWork())
        assertEquals(0, failureNotices)
        assertEquals(ListenableWorker.Result.failure(), worker(attempt = AutoBackupWorker.MAX_RETRIES).doWork())
        assertEquals(1, failureNotices)
    }

    @Test fun schedulerEnqueuesOneDailyBatteryAwareJobAndCancels() {
        WorkManagerTestInitHelper.initializeTestWorkManager(rig.ctx, Configuration.Builder().build())
        val wm = WorkManager.getInstance(rig.ctx)
        val scheduler = WorkManagerAutoBackupScheduler(rig.ctx)
        scheduler.apply(true)
        scheduler.apply(true)
        val infos = wm.getWorkInfosForUniqueWork(WorkManagerAutoBackupScheduler.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertTrue(infos.single().constraints.requiresBatteryNotLow())
        assertEquals(24 * 3_600_000L, infos.single().periodicityInfo!!.repeatIntervalMillis)
        scheduler.apply(false)
        assertEquals(WorkInfo.State.CANCELLED, wm.getWorkInfosForUniqueWork(WorkManagerAutoBackupScheduler.WORK_NAME).get().single().state)
    }

    @Test fun aFailedWriteLeavesNoPartialFile() = runBlocking {
        enable()
        target = object : BackupTarget by DirBackupTarget(dir) {
            override fun create(name: String): OutputStream {
                File(dir, name).writeText("partial")
                return object : OutputStream() {
                    override fun write(b: Int) = throw java.io.IOException("disk full")
                }
            }
        }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}
