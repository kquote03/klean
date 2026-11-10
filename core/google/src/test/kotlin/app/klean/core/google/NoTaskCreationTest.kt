package app.klean.core.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import io.ktor.http.HttpMethod
import java.io.File
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Klean shows Google tasks and edits a few fields; it must never create one (`POST …/tasks`).
 * Checked three ways: the client's sources have no POST call, a full sync with every kind of edit sends none,
 * and every request any test in this suite made (logged by [FakeGoogleServer]) was GET or PATCH.
 */
@RunWith(AndroidJUnit4::class)
class NoTaskCreationTest {
    @Test fun theGoogleSourcesNeverBuildAPost() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "src/main/kotlin").isDirectory }
        val sources = File(root, "src/main/kotlin").walk().filter { it.extension == "kt" }.toList()
        assertTrue(sources.size >= 5)
        val bad = Regex("""\.post\(|HttpMethod\.Post|submitForm|method\s*=\s*HttpMethod\.Post|"POST"""")
        sources.forEach { f -> assertTrue("${f.name} builds a POST", !bad.containsMatchIn(f.readText())) }
    }

    @Test fun aFullSyncWithEveryKindOfEditSendsOnlyGetsAndPatches() = runBlocking {
        val clock = FakeClock(1_762_768_800_000)
        val scope = tempScope()
        val db = inMemoryDb()
        val state = tempGoogleState(scope)
        val auth = FakeGoogleAuth(connected = true)
        val server = FakeGoogleServer()
        val sync = GoogleTasksSync(db, SyncStamper(db, tempDevicePrefs(clock, scope), clock), auth, server.client(auth), state, clock)
        server.list("L1", "Groceries")
        server.task("L1", GTask("a", "Milk"))
        server.task("L1", GTask("b", "Eggs", status = "completed"))
        sync.run()
        val milk = db.taskDao().get(GoogleTasksSync.taskId("a"))!!
        val eggs = db.taskDao().get(GoogleTasksSync.taskId("b"))!!
        listOf(
            milk.copy(completedAt = 1L, status = "DONE"),
            eggs.copy(completedAt = null, status = "OPEN", title = "Brown eggs", notes = "6", dueDate = "2026-11-12"),
        ).forEach { state.enqueue(it.toOutboxOp(clock.now().toEpochMilliseconds())!!) }
        sync.run()
        assertEquals(2, server.patches.size)
        assertTrue(server.requests.all { it.method == HttpMethod.Get || it.method == HttpMethod.Patch })
        db.close()
        scope.coroutineContext.job.cancelAndJoin()
    }

    @Test fun noRequestInTheWholeSuiteWasAPostToTasks() {
        val posts = GoogleRequestLog.all.filter { (m, p) -> m == HttpMethod.Post && p.contains("/tasks") }
        assertEquals(emptyList<Pair<HttpMethod, String>>(), posts)
    }
}
