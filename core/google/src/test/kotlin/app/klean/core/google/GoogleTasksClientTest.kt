package app.klean.core.google

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleTasksClientTest {
    private val server = FakeGoogleServer()
    private val auth = FakeGoogleAuth(connected = true)
    private val api = server.client(auth)

    @Test fun readsEveryListAndEveryPageOfTasksWithCompletedAndHiddenOnes() = runTest {
        server.list("L1", "Groceries")
        server.list("L2", "Work")
        repeat(5) { server.task("L1", GTask("t$it", "Task $it")) }
        assertEquals(listOf("Groceries", "Work"), api.lists().map { it.title })
        assertEquals((0 until 5).map { "t$it" }, api.tasks("L1").map { it.id })
        val taskGets = server.requests.filter { it.url.encodedPath.endsWith("/lists/L1/tasks") }
        assertEquals(3, taskGets.size) // pages of 2
        assertTrue(taskGets.all { it.url.parameters["showCompleted"] == "true" && it.url.parameters["showHidden"] == "true" })
        assertTrue(server.requests.all { it.headers["Authorization"] == "Bearer fake-token" })
    }

    @Test fun completeSendsStatusAndUncompleteClearsTheCompletionTime() = runTest {
        server.list("L1", "Groceries")
        server.task("L1", GTask("t1", "Milk"))
        api.patch("L1", "t1", GTaskPatch("Milk", notes = null, due = "2026-11-12", done = true, completedAt = "2026-11-10T10:00:00Z"))
        val (_, done) = server.patches.last()
        assertEquals("completed", done["status"]!!.jsonPrimitive.content)
        assertEquals("2026-11-12T00:00:00.000Z", done["due"]!!.jsonPrimitive.content)
        assertEquals("2026-11-10T10:00:00Z", done["completed"]!!.jsonPrimitive.content)

        val back = api.patch("L1", "t1", GTaskPatch("Oat milk", notes = "2 l", due = null, done = false))
        val (_, open) = server.patches.last()
        assertEquals("needsAction", open["status"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, open["completed"])
        assertEquals(JsonNull, open["due"])
        assertEquals("Oat milk", back.title)
        assertEquals(HttpMethod.Patch, server.requests.last().method)
    }

    @Test fun anExpiredTokenIsDroppedAndTheCallRetriedOnceWithAFreshOne() = runTest {
        server.list("L1", "Groceries")
        auth.token = "old"
        auth.nextTokens += "fake-token"
        assertEquals(1, api.lists().size)
        assertEquals(listOf("old"), auth.invalidated)
    }

    @Test fun notConnectedMeansNoRequestAtAll() = runTest {
        auth.disconnect()
        try {
            api.lists()
            fail("expected GoogleNotConnectedException")
        } catch (_: GoogleNotConnectedException) {
        }
        assertTrue(server.requests.isEmpty())
    }
}
