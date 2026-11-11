package app.klean.core.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Accounts: the profile after a grant, switching, per-account edits and lists, Disconnect, the daily refresh. */
@RunWith(AndroidJUnit4::class)
class GoogleAccountsTest {
    private val clock = FakeClock(1_762_768_800_000)
    private val scope = tempScope()
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock, scope), clock)
    private val state = tempGoogleState(scope)
    private val auth = FakeGoogleAuth(connected = false).apply {
        tokensByAccount["a@gmail.com"] = "tok-a"
        tokensByAccount["b@gmail.com"] = "tok-b"
    }
    private val tasksServer = FakeGoogleServer().apply { validTokens = setOf("tok-a", "tok-b", "fake-token") }
    private val profiles = FakeProfileServer().apply {
        profile("tok-a", "a@gmail.com", "Ada Lovelace", "Ada", "https://lh3.googleusercontent.com/a/A=s96-c")
        profile("tok-b", "b@gmail.com", "Bo Chen", "Bo")
        photos["https://lh3.googleusercontent.com/a/A=s192-c"] = FAKE_PNG
    }
    private val sync = GoogleTasksSync(db, stamper, auth, tasksServer.client(auth), state, clock)
    private val photoDir = File(createTempDirectory("acct").toFile(), GoogleAccounts.PHOTO_DIR)
    private val accounts = GoogleAccounts(auth, profiles.client(), state, sync, photoDir, clock)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.coroutineContext.job.cancelAndJoin()
    }

    private suspend fun connectAs(email: String) {
        auth.pickedAccount = email
        assertEquals(AuthStep.Granted, auth.connect())
        assertTrue(accounts.adoptGranted() is AdoptResult.Identified)
    }

    @Test fun aGrantStoresTheProfileAndPhotoButNeverTheToken() = runBlocking {
        connectAs("a@gmail.com")
        val s = accounts.state.first()
        assertTrue(s.connected)
        assertEquals("a@gmail.com", s.current!!.email)
        assertEquals("Ada", s.current!!.givenName)
        assertEquals("Ada Lovelace", s.current!!.name)
        val photo = s.photos.getValue("a@gmail.com")
        assertTrue(photo.readBytes().contentEquals(FAKE_PNG))
        assertEquals(photoDir, photo.parentFile)
        assertFalse("the file name never shows the address", photo.name.contains("gmail"))
        assertTrue(profiles.requested.any { it.endsWith("=s192-c") })
        // Nothing in the DataStore file is a token.
        val stored = state.accounts().toString() + state.allOutbox()
        assertFalse(stored.contains("tok-a"))
    }

    @Test fun anAccountWithoutAPhotoShowsItsInitial() = runBlocking {
        connectAs("b@gmail.com")
        val s = accounts.state.first()
        assertNull(s.photos["b@gmail.com"])
        assertEquals("B", s.current!!.initial)
    }

    @Test fun anInstallConnectedBeforeTheProfileScopesKeepsWorkingAndIsAskedQuietly() = runBlocking {
        val legacy = FakeGoogleAuth(connected = true, profileGranted = false)
        state.setConnected(true)
        val acc = GoogleAccounts(legacy, profiles.client(), state, GoogleTasksSync(db, stamper, legacy, tasksServer.client(legacy), state, clock), photoDir, clock)
        assertEquals(AdoptResult.NoProfile, acc.adoptGranted())
        val s = acc.state.first()
        assertTrue(s.connected)
        assertTrue(s.needsProfile)
        assertNull(s.current)
        assertEquals("fake-token", legacy.accessToken())
    }

    @Test fun nextAndPreviousWrapAround() {
        val list = listOf(GoogleAccount("a@x"), GoogleAccount("b@x"), GoogleAccount("c@x"))
        assertEquals("b@x", GoogleAccounts.neighbour(list, "a@x", 1)!!.email)
        assertEquals("a@x", GoogleAccounts.neighbour(list, "c@x", 1)!!.email)
        assertEquals("c@x", GoogleAccounts.neighbour(list, "a@x", -1)!!.email)
        assertEquals("b@x", GoogleAccounts.neighbour(list, "c@x", -1)!!.email)
        assertNull(GoogleAccounts.neighbour(list.take(1), "a@x", 1))
        assertNull(GoogleAccounts.neighbour(emptyList(), null, 1))
        assertEquals("a@x", GoogleAccounts.neighbour(list, "gone@x", 1)!!.email)
    }

    @Test fun switchingClearsTheOtherAccountsCacheAndNeverSendsItsEditsToTheNewOne() = runBlocking {
        tasksServer.list("LA", "Ada's list")
        tasksServer.list("LA2", "Ada's other list")
        tasksServer.task("LA", GTask("ta", "Ada's task"))
        connectAs("a@gmail.com")
        sync.run()
        val adaTask = db.taskDao().get(GoogleTasksSync.taskId("ta"))!!
        sync.setSelected(GoogleTasksSync.listId("LA2"), false)
        // An edit queued for Ada while offline.
        tasksServer.offline = true
        state.enqueue(adaTask.copy(title = "Ada's edit").toOutboxOp(clock.now().toEpochMilliseconds())!!)
        sync.run()
        tasksServer.offline = false
        assertEquals("a@gmail.com", state.allOutbox().single().account)

        // Switch to Bo: Ada's rows leave Klean, her edit waits under her email.
        tasksServer.lists.clear()
        tasksServer.items.clear()
        tasksServer.list("LB", "Bo's list")
        assertEquals(AuthStep.Granted, accounts.switchTo("b@gmail.com"))
        val r = accounts.adoptGranted() as AdoptResult.Identified
        assertTrue(r.switched)
        assertTrue(db.taskDao().get(GoogleTasksSync.taskId("ta"))!!.sync.deleted)
        assertTrue(state.outbox().isEmpty())
        sync.run()
        assertTrue("Ada's edit never went out with Bo's token", tasksServer.patchTokens.none { it == "tok-b" })
        assertTrue(tasksServer.patches.isEmpty())
        assertEquals(listOf("Bo's list"), db.taskListDao().observeAll().first().filter { !it.sync.deleted }.map { it.name })

        // Back to Ada: her edit goes to her, and her chosen lists come back as she left them.
        tasksServer.lists.clear()
        tasksServer.list("LA", "Ada's list")
        tasksServer.list("LA2", "Ada's other list")
        tasksServer.task("LA", GTask("ta", "Ada's task"))
        assertEquals(AuthStep.Granted, accounts.switchTo("a@gmail.com"))
        accounts.adoptGranted()
        sync.run()
        assertEquals(listOf("tok-a"), tasksServer.patchTokens)
        assertEquals("ta", tasksServer.patches.single().first)
        assertTrue(state.allOutbox().isEmpty())
        val shown = db.taskListDao().observeAll().first().filter { !it.sync.deleted }.associate { it.remoteId to it.selected }
        assertEquals(mapOf("LA" to true, "LA2" to false), shown)
    }

    @Test fun editsQueuedBeforeAccountsWereKnownBelongToTheFirstAccountIdentified() = runBlocking {
        state.enqueue(OutboxOp("t", "L", "r", "Title", queuedAt = 1))
        assertNull(state.allOutbox().single().account)
        connectAs("a@gmail.com")
        assertEquals("a@gmail.com", state.outbox().single().account)
    }

    @Test fun swipeSwitchesSilentlyToTheNextAccount() = runBlocking {
        connectAs("a@gmail.com")
        connectAs("b@gmail.com")
        assertEquals("b@gmail.com", state.currentEmail())
        assertTrue(accounts.switchBy(1))
        assertEquals("a@gmail.com", state.currentEmail())
        assertEquals(listOf("a@gmail.com", "b@gmail.com"), accounts.state.first().accounts.map { it.email })
    }

    @Test fun disconnectDeletesThePhotoAndTheEntry() = runBlocking {
        connectAs("a@gmail.com")
        val photo = accounts.state.first().photos.getValue("a@gmail.com")
        assertTrue(photo.exists())
        auth.disconnect()
        sync.forget()
        accounts.forgetAll()
        assertFalse(photo.exists())
        assertTrue(state.accounts().isEmpty())
        assertNull(state.currentEmail())
        val s = accounts.state.first()
        assertFalse(s.connected)
        assertTrue(s.accounts.isEmpty())
    }

    @Test fun theProfileIsRefreshedAtMostDaily() = runBlocking {
        connectAs("a@gmail.com")
        val before = profiles.requested.count { it.endsWith("/userinfo") }
        clock.nowMs += 60 * 60_000L
        accounts.refreshIfStale()
        assertEquals(before, profiles.requested.count { it.endsWith("/userinfo") })
        clock.nowMs += GoogleAccounts.DAY_MS
        profiles.profile("tok-a", "a@gmail.com", "Ada King", "Ada", "https://lh3.googleusercontent.com/a/A=s96-c")
        accounts.refreshIfStale()
        assertEquals(before + 1, profiles.requested.count { it.endsWith("/userinfo") })
        assertEquals("Ada King", state.accounts().single().name)
        // The same photo URL isn't downloaded again.
        assertEquals(1, profiles.requested.count { it.endsWith("=s192-c") })
    }

    @Test fun offlineProfileReadChangesNothingAndDropsTheUnknownToken() = runBlocking {
        connectAs("a@gmail.com")
        profiles.offline = true
        auth.pickedAccount = "b@gmail.com"
        auth.connect(pickAccount = true)
        assertEquals(AdoptResult.Failed, accounts.adoptGranted())
        assertEquals("a@gmail.com", state.currentEmail())
        assertTrue(auth.invalidated.isNotEmpty())
    }
}
