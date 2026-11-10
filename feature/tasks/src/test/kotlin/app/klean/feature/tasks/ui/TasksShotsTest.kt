package app.klean.feature.tasks.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.feature.tasks.DubaiZone
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Plan › Tasks in light and dark: empty, typical, long, and the Lists and Finished views. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TasksShotsTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate(2026, 11, 10)

    private fun row(id: String, title: String, due: LocalDate? = today, minutes: Int? = null, list: String? = null, steps: Int = 0, priority: Int = 0) =
        TaskRowModel(id, title, due, minutes, priority, done = false, recurring = false, subtasksDone = 0, subtasksTotal = steps, listName = list)

    private val typical = TaskViewsModel(
        today = listOf(row("1", "Buy groceries", list = "Personal"), row("2", "Call the bank", minutes = 16 * 60)),
        upcoming = listOf(DayGroup(LocalDate(2026, 11, 12), listOf(row("3", "Book a dentist check-up", LocalDate(2026, 11, 12))))),
        review = listOf(row("4", "Renew car registration", LocalDate(2026, 11, 7))),
    )

    private fun render(name: String, views: TaskViewsModel, view: TasksTab = TasksTab.TODAY) = compose.shot(name, screen = true) {
        val fmt = TaskFormatter(LocalContext.current, DubaiZone)
        TasksContent(
            state = TasksUiState(views = views, today = today, loaded = true),
            view = view,
            fmt = fmt,
            actions = TaskActions(),
            onView = {},
            onNewTask = {},
            onNewReminder = {},
        )
    }

    @Test fun typical() {
        render("tasks-typical", typical)
        compose.onNodeWithText("Still want these?").assertExists()
        compose.onNodeWithText("Coming up").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        render("tasks-empty", TaskViewsModel())
        compose.assertClutterBudget()
    }

    @Test fun longContent() {
        val many = TaskViewsModel(
            today = (1..12).map { row("t$it", "A task with a long title that needs two lines to say what it is, number $it", list = "Personal", steps = 3, priority = it % 4) },
            review = (1..4).map { row("r$it", "Older task $it", LocalDate(2026, 11, 1 + it)) },
        )
        render("tasks-long", many)
        compose.assertClutterBudget()
    }

    @Test fun listsAndFinished() {
        val sync = app.klean.core.data.db.entity.SyncColumns(0, 0, "d", false, 0)
        val lists = listOf(
            TaskListEntity("p", "Personal", "PERSONAL", 0, sync = sync),
            TaskListEntity("w", "Work", "WORK", 1, sync = sync),
        )
        render("tasks-lists", typical.copy(lists = lists, selectedListId = "p", listOpen = typical.today), TasksTab.LISTS)
        compose.onNodeWithText("All tasks").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun overflowHoldsTheOtherViews() {
        val shot = render("tasks-menu", typical)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Finished").assertExists()
        compose.onNodeWithText("New task").assertExists()
        shot.capture("tasks-menu-open")
    }
}
