package app.klean.feature.tasks.ui

import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskViewsTest {
    private val today = LocalDate(2026, 11, 9)
    private var seq = 0L
    private fun sync(deleted: Boolean = false) = SyncColumns(++seq, seq, "d", deleted, seq)
    private val personal = TaskListEntity("p", "Personal", "PERSONAL", 0, sync = sync())
    private val work = TaskListEntity("w", "Work", "WORK", 1, sync = sync())
    private fun task(
        id: String, due: String? = null, list: String = "p", done: Long? = null, status: String = "OPEN",
        parent: String? = null, minutes: Int? = null, archived: Long? = null, deleted: Boolean = false,
    ) = TaskEntity(
        id = id, listId = list, title = id, dueDate = due, dueMinutes = minutes, completedAt = done,
        status = if (done != null) "DONE" else status, parentId = parent, archivedAt = archived, sync = sync(deleted),
    )

    private fun build(vararg t: TaskEntity, selected: String? = null) =
        TaskViews.build(t.toList(), listOf(personal, work), today, selected)

    @Test fun splitsReviewTodayAndUpcoming() {
        val v = build(
            task("old", "2026-11-07"), task("yesterday", "2026-11-08"),
            task("later today", "2026-11-09", minutes = 900), task("today", "2026-11-09", minutes = 600),
            task("fri", "2026-11-13"), task("tomorrow", "2026-11-10"), task("undated"),
            task("done today", "2026-11-09", done = 5), task("someday", status = "SOMEDAY"),
        )
        assertEquals(listOf("old", "yesterday"), v.review.map { it.id })
        assertEquals(listOf("today", "later today"), v.today.map { it.id })
        assertEquals(listOf(LocalDate(2026, 11, 10), LocalDate(2026, 11, 13)), v.upcoming.map { it.date })
        assertEquals("Personal", v.today.first().listName)
    }

    @Test fun listViewDefaultsToTheFirstListAndSplitsSomedayAndDone() {
        val v = build(
            task("a", "2026-11-12"), task("b"), task("w1", list = "w"),
            task("s", status = "SOMEDAY"), task("d1", done = 10), task("d2", done = 20),
        )
        assertEquals("p", v.selectedListId)
        assertEquals(listOf("a", "b"), v.listOpen.map { it.id })
        assertEquals(listOf("s"), v.listSomeday.map { it.id })
        assertEquals(listOf("d2", "d1"), v.listDone.map { it.id })
        assertEquals(listOf("w1"), build(task("w1", list = "w"), selected = "w").listOpen.map { it.id })
        assertNull(build(selected = "gone").listOpen.firstOrNull())
        assertEquals("p", build(selected = "gone").selectedListId)
    }

    @Test fun subtasksShowAsProgressOnTheirParent() {
        val v = build(task("trip", "2026-11-09"), task("passport", parent = "trip", done = 3), task("bag", parent = "trip"))
        assertEquals(listOf("trip"), v.today.map { it.id })
        assertEquals(1, v.today.single().subtasksDone)
        assertEquals(2, v.today.single().subtasksTotal)
        assertEquals(listOf("trip"), v.listOpen.map { it.id })
    }

    @Test fun orphanSubtaskStillShows() {
        assertEquals(listOf("step"), build(task("step", parent = "gone")).listOpen.map { it.id })
    }

    @Test fun archivedAndDeletedAreHidden() {
        val v = build(task("arch", done = 1, archived = 2), task("del", deleted = true), task("d", done = 3))
        assertEquals(listOf("d"), v.done.map { it.id })
        assertEquals(emptyList<String>(), v.listOpen.map { it.id })
    }

    @Test fun textBecomesATask() {
        val now = LocalDateTime(2026, 11, 9, 10, 0)
        val d = TaskText.parse("pay rent friday", now)
        assertEquals("pay rent", d.title)
        assertEquals(LocalDate(2026, 11, 13), d.dueDate)
        assertNull(d.dueMinutes)
        val t = TaskText.parse("todo: call the bank tomorrow 5pm", now, listId = "w")
        assertEquals("call the bank", t.title)
        assertEquals(LocalDate(2026, 11, 10), t.dueDate)
        assertEquals(17 * 60, t.dueMinutes)
        assertEquals("w", t.listId)
        val plain = TaskText.parse("buy milk", now)
        assertNull(plain.dueDate)
        assertEquals("buy milk", plain.title)
    }
}
