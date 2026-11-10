package app.klean.feature.tasks.ui

import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.feature.tasks.data.TaskSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M28: a captured task can carry its list, due date and priority. */
class TaskCaptureTextTest {
    private val now = LocalDateTime(2026, 11, 9, 10, 0)
    private val sync = SyncColumns(0, 0, "d", false, 0)
    private val lists = listOf(
        TaskListEntity("p", "Personal", "PERSONAL", 0, sync = sync),
        TaskListEntity("w", "Work", "WORK", 1, sync = sync),
        TaskListEntity("h", "Home stuff", "CUSTOM", 2, sync = sync),
        TaskListEntity("g", "Groceries", "CUSTOM", 1000, source = "GOOGLE", remoteId = "L1", sync = sync),
    )
    private fun parse(t: String) = TaskText.parse(t, now, source = TaskSource.CAPTURE, lists = lists)

    @Test fun listByTagOrPhrase() {
        assertEquals("w", parse("send the report #work tomorrow").listId)
        assertEquals("send the report", parse("send the report #work tomorrow").title)
        assertEquals("h", parse("fix the tap in my home stuff list").listId)
        assertEquals("fix the tap", parse("fix the tap in my home stuff list").title)
        assertEquals("h", parse("#home-stuff fix the tap").listId)
    }

    @Test fun unknownTagsAndGoogleListsStayText() {
        val t = parse("read #kotlin docs")
        assertNull(t.listId)
        assertEquals("read #kotlin docs", t.title)
        // Klean never creates a task in a Google list.
        assertNull(parse("milk #groceries").listId)
    }

    @Test fun priorityFromBangsOrWords() {
        assertEquals(3, parse("!!! pay rent").priority)
        assertEquals("pay rent", parse("!!! pay rent").title)
        assertEquals(2, parse("call the plumber !!").priority)
        assertEquals(1, parse("tidy desk !").priority)
        assertEquals(3, parse("urgent: renew passport").priority)
        assertEquals("renew passport", parse("urgent: renew passport").title)
        assertEquals(1, parse("water plants low priority").priority)
        // An exclamation inside a sentence is just punctuation.
        assertEquals(0, parse("buy cake!").priority)
        assertEquals("buy cake!", parse("buy cake!").title)
    }

    @Test fun everythingAtOnce() {
        val t = parse("todo !! email Sam #work friday 9:30")
        assertEquals("email Sam", t.title)
        assertEquals("w", t.listId)
        assertEquals(2, t.priority)
        assertEquals(LocalDate(2026, 11, 13), t.dueDate)
        assertEquals(9 * 60 + 30, t.dueMinutes)
        assertEquals(TaskSource.CAPTURE, t.source)
    }
}
