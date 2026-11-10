package app.klean.feature.workout.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.DubaiZone
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.data.DefaultWorkoutRepository
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.tempDevicePrefs
import app.klean.shared.time.atMinute
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SessionScreenTest {
    @get:Rule val rule = createComposeRule()
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 10), 18 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val repo = DefaultWorkoutRepository(db, stamper, clock, emptySet())
    private val store = ViewModelStore()

    @After fun tearDown() {
        store.clear()
        db.close()
    }

    @Test fun plusOneSetLogsTheTemplateItemAndFinishCloses() {
        val template = runBlocking { repo.saveTemplate(null, "Morning", "3x15 pushups, 1 min plank") }
        val id = runBlocking { repo.start(template) }
        val vm = SessionViewModel(SavedStateHandle(mapOf("sessionId" to id)), repo, FixedTicker(clock), DubaiZone).also { store.put("vm", it) }
        var done = false
        rule.setContent { SessionScreen(onBack = { done = true }, onDeleted = {}, vm = vm) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("pushups").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("3×15 pushups").assertExists()
        rule.onNodeWithContentDescription("One more set of pushups").performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("15").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Finish").performClick()
        // Room answers on its IO thread; pump the main looper until the VM's callback runs.
        repeat(100) {
            if (!done) {
                rule.waitForIdle()
                Thread.sleep(20)
            }
        }
        assertTrue(done)
        val s = runBlocking { repo.session(id)!! }
        assertEquals(15, s.sets.single().reps)
        assertNull(runBlocking { repo.openSession() })
    }
}
