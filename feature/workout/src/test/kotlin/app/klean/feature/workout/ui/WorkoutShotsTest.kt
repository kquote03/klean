package app.klean.feature.workout.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.DubaiZone
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.jog.RunUi
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Track › Workout in light and dark: typical, empty, long, a session running and a run in progress. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutShotsTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate(2026, 11, 10)
    private val sync = SyncColumns(0, 0, "d", false, 0)

    private fun at(day: Int, minute: Int) = atMinute(LocalDate(2026, 11, day), minute, Dubai)

    private fun session(id: String, day: Int, start: Int, minutes: Int?, vararg exercises: String) = SessionWithSets(
        WorkoutSessionEntity(id, startedAt = at(day, start), endedAt = minutes?.let { at(day, start + it) }, sync = sync),
        exercises.mapIndexed { i, e -> WorkoutSetEntity("$id-$i", id, e, "STRENGTH", i, reps = 10, sync = sync) },
    )

    private fun template(id: String, name: String, vararg items: String) = TemplateRow(
        WorkoutTemplateEntity(id, name, "[]", sync = sync),
        items.map { WorkoutItem(it, WorkoutKind.STRENGTH, sets = 3, reps = 10) },
    )

    private val recent = listOf(
        session("s1", 9, 18 * 60, 28, "Run"),
        session("s2", 5, 7 * 60, 47, "Bench press", "Rows", "Dips"),
    )
    private val days = lastSevenDays(today)
    private fun state(recent: List<SessionWithSets> = this.recent, templates: List<TemplateRow> = listOf(template("t1", "Push day", "Bench press", "Dips")), open: SessionWithSets? = null) =
        WorkoutHomeState(
            loaded = true, today = today, open = open, templates = templates, recent = recent,
            week = WorkoutWeek.of(recent, days) { atDate(it) },
        )

    private fun atDate(ms: Long) = ms.toLocal(Dubai).date

    private fun render(name: String, s: WorkoutHomeState, run: RunUi = RunUi(), actions: WorkoutActions = WorkoutActions()) = compose.shot(name, screen = true) {
        WorkoutContent(s, run, WorkoutFormatter(LocalContext.current, DubaiZone), actions)
    }

    @Test fun typical() {
        var started: String? = "none"
        var edited: String? = "none"
        render("workout-typical", state(), actions = WorkoutActions(start = { started = it }, editTemplate = { edited = it }))
        compose.onNodeWithText("This week").assertExists()
        compose.onNodeWithText("2 sessions, 1 h 15 min").assertExists()
        compose.onNodeWithText("Saved workouts").assertExists()
        compose.onNodeWithText("Recent").assertExists()
        compose.onNodeWithText("Push day").performClick()
        assertEquals("t1", started)
        compose.onNodeWithText("Push day").performTouchInput { longClick() }
        assertEquals("t1", edited)
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        var started: String? = "none"
        render("workout-empty", state(recent = emptyList(), templates = emptyList()), actions = WorkoutActions(start = { started = it }))
        compose.onNodeWithText("Your workouts will show up here.").assertExists()
        compose.onNodeWithText("Start a workout").performClick()
        assertEquals(null, started)
        compose.assertClutterBudget()
    }

    @Test fun longContent() {
        val many = (1..10).map { session("m$it", 10 - (it % 7), 6 * 60 + it, 30 + it, "Squats", "Lunges", "A long exercise name that needs room") }
        val templates = (1..4).map { template("t$it", "Saved workout number $it with a long name", "Squats", "Lunges") }
        render("workout-long", state(recent = many, templates = templates))
        compose.assertClutterBudget()
    }

    @Test fun sessionRunningLeadsAndStartsReturnToIt() {
        val open = SessionWithSets(WorkoutSessionEntity("o", startedAt = at(10, 18 * 60), sync = sync), emptyList())
        var opened: String? = null
        render("workout-open", state(open = open), actions = WorkoutActions(openSession = { opened = it }, start = { error("not while one runs") }))
        compose.onNodeWithText("In progress").assertExists()
        compose.onNodeWithText("Push day").performClick()
        assertEquals("o", opened)
        compose.assertClutterBudget()
    }

    @Test fun runInProgress() {
        var finished = 0
        render("workout-run", state(), RunUi(running = true, distanceM = 3200.0, elapsedS = 1080, paceSecPerKm = 337), WorkoutActions(finishRun = { finished++ }))
        compose.onNodeWithText("Run in progress").assertExists()
        compose.onNodeWithText("Finish run").performClick()
        assertEquals(1, finished)
        compose.assertClutterBudget()
    }

    @Test fun overflowHoldsExercisesAndSettings() {
        val shot = render("workout-menu", state())
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Exercises").assertExists()
        compose.onNodeWithText("Workout settings").assertExists()
        shot.capture("workout-menu-open")
    }
}
