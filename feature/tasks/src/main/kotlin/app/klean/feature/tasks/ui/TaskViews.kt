package app.klean.feature.tasks.ui

import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TaskSource
import app.klean.feature.tasks.data.TaskStatus
import app.klean.shared.time.NaturalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** One task row: [subtasksDone]/[subtasksTotal] is shown as "1/3" when the task has steps. */
data class TaskRowModel(
    val id: String,
    val title: String,
    val dueDate: LocalDate?,
    val dueMinutes: Int?,
    val priority: Int,
    val done: Boolean,
    val recurring: Boolean,
    val subtasksDone: Int = 0,
    val subtasksTotal: Int = 0,
    val listName: String? = null,
    /** Lives in Google Tasks: shows the small "G" chip. */
    val google: Boolean = false,
)

data class DayGroup(val date: LocalDate, val rows: List<TaskRowModel>)

/** Everything the Tasks tabs show, built from the live rows in one pass. */
data class TaskViewsModel(
    /** Open tasks due before today: "Still want these?". */
    val review: List<TaskRowModel> = emptyList(),
    val today: List<TaskRowModel> = emptyList(),
    val upcoming: List<DayGroup> = emptyList(),
    val lists: List<TaskListEntity> = emptyList(),
    val selectedListId: String? = null,
    /** The selected list: open tasks (dated ones first, then undated). */
    val listOpen: List<TaskRowModel> = emptyList(),
    val listSomeday: List<TaskRowModel> = emptyList(),
    val listDone: List<TaskRowModel> = emptyList(),
    /** Every done (not yet archived) task, most recent first. */
    val done: List<TaskRowModel> = emptyList(),
)

/**
 * Pure tab logic. Subtasks never appear on their own in these views; they show as progress on their
 * parent and are edited on the task's page.
 */
object TaskViews {
    fun build(all: List<TaskEntity>, lists: List<TaskListEntity>, today: LocalDate, selectedListId: String?): TaskViewsModel {
        // Google lists the user switched off in Settings are hidden, with their tasks.
        val shown = lists.filter { it.selected }
        val hiddenLists = lists.filterNot { it.selected }.mapTo(HashSet()) { it.id }
        val live = all.filter { !it.sync.deleted && it.archivedAt == null && it.listId !in hiddenLists }
        val children = live.filter { it.parentId != null }.groupBy { it.parentId }
        val listNames = lists.associate { it.id to it.name }
        fun row(t: TaskEntity, withList: Boolean = false): TaskRowModel {
            val subs = children[t.id].orEmpty()
            return TaskRowModel(
                id = t.id, title = t.title, dueDate = t.dueDate?.let(LocalDate::parse), dueMinutes = t.dueMinutes,
                priority = t.priority, done = t.completedAt != null, recurring = t.recurrence != null,
                subtasksDone = subs.count { it.completedAt != null }, subtasksTotal = subs.size,
                listName = if (withList) listNames[t.listId] else null,
                google = t.origin == "GOOGLE",
            )
        }
        val ids = live.mapTo(HashSet()) { it.id }
        // A subtask whose parent is gone shows on its own rather than disappearing.
        val top = live.filter { it.parentId == null || it.parentId !in ids }
        val open = top.filter { it.completedAt == null && it.status != TaskStatus.SOMEDAY }
        val byDue = compareBy<TaskEntity>({ it.dueDate }, { it.dueMinutes ?: -1 }, { -it.priority }, { it.position }, { it.sync.createdAt })
        val todayIso = today.toString()
        val selected = selectedListId?.takeIf { id -> shown.any { it.id == id } } ?: shown.firstOrNull()?.id
        val inList = top.filter { it.listId == selected }
        return TaskViewsModel(
            review = open.filter { it.dueDate != null && it.dueDate!! < todayIso }.sortedWith(byDue).map { row(it, true) },
            today = open.filter { it.dueDate == todayIso }.sortedWith(byDue).map { row(it, true) },
            upcoming = open.filter { it.dueDate != null && it.dueDate!! > todayIso }.sortedWith(byDue)
                .groupBy { LocalDate.parse(it.dueDate!!) }
                .map { (d, l) -> DayGroup(d, l.map { row(it, true) }) },
            lists = shown,
            selectedListId = selected,
            listOpen = inList.filter { it.completedAt == null && it.status != TaskStatus.SOMEDAY }
                .sortedWith(compareBy<TaskEntity>({ it.dueDate == null }).then(byDue)).map { row(it) },
            listSomeday = inList.filter { it.completedAt == null && it.status == TaskStatus.SOMEDAY }
                .sortedWith(byDue).map { row(it) },
            listDone = inList.filter { it.completedAt != null }.sortedByDescending { it.completedAt }.map { row(it) },
            done = top.filter { it.completedAt != null }.sortedByDescending { it.completedAt }.map { row(it, true) },
        )
    }
}

/**
 * Turns typed or spoken text into a task: time words become the due date and time, the rest is the title.
 * "todo"/"to do"/"task" at the start is dropped. Text with no date gets no due date.
 *
 * Capture extras (M28), each removed from the title:
 * - **list:** `#work` or "in my Work list" picks one of [lists] (Klean lists only; an unknown tag stays as text);
 * - **priority:** a standalone `!` / `!!` / `!!!` (low / medium / high), or "urgent", "high priority",
 *   "medium priority", "low priority".
 */
object TaskText {
    private val PREFIX = Regex("""^(?:todo|to do|task)\b[:\s,-]*""", RegexOption.IGNORE_CASE)
    private val BANGS = Regex("""(?:^|\s)(!{1,3})(?=\s|$)""")
    private val PRIORITY_WORDS = listOf(
        Regex("""\b(?:urgent|high priority)\b""", RegexOption.IGNORE_CASE) to 3,
        Regex("""\bmedium priority\b""", RegexOption.IGNORE_CASE) to 2,
        Regex("""\blow priority\b""", RegexOption.IGNORE_CASE) to 1,
    )
    private val TAG = Regex("""(?:^|\s)#([\p{L}\p{N}_-]+)""")
    private val LIST_PHRASE = Regex("""\b(?:in|on|to|into)\s+(?:my\s+|the\s+)?([\p{L}\p{N} _-]{1,40}?)\s+list\b""", RegexOption.IGNORE_CASE)

    fun parse(
        text: String,
        now: LocalDateTime,
        listId: String? = null,
        source: String = TaskSource.MANUAL,
        lists: List<TaskListEntity> = emptyList(),
    ): TaskDraft {
        var clean = text.trim().replace(PREFIX, "").trim()
        var priority = 0
        BANGS.find(clean)?.let { m ->
            priority = m.groupValues[1].length
            clean = clean.removeRange(m.range)
        }
        if (priority == 0) {
            for ((rx, p) in PRIORITY_WORDS) {
                val m = rx.find(clean) ?: continue
                priority = p
                clean = clean.removeRange(m.range)
                break
            }
        }
        var list = listId
        if (lists.isNotEmpty()) {
            val tag = TAG.findAll(clean).firstNotNullOfOrNull { m -> matchList(lists, m.groupValues[1])?.let { m to it } }
                ?: LIST_PHRASE.findAll(clean).firstNotNullOfOrNull { m -> matchList(lists, m.groupValues[1])?.let { m to it } }
            tag?.let { (m, l) ->
                if (list == null) list = l.id
                clean = clean.removeRange(m.range)
            }
        }
        clean = clean.replace(Regex("""\s{2,}"""), " ").trim().trim(',', ';', ':', '-').trim()
        val p = NaturalTime.parse(clean, now)
        val title = p.rest.trim().ifEmpty { clean }
        return TaskDraft(title = title, listId = list, dueDate = p.date, dueMinutes = p.minutes, priority = priority, source = source)
    }

    /** A Klean list whose name matches [name], ignoring case, spaces, dashes and underscores. */
    fun matchList(lists: List<TaskListEntity>, name: String): TaskListEntity? {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val n = norm(name).ifEmpty { return null }
        return lists.firstOrNull { it.source != "GOOGLE" && !it.sync.deleted && norm(it.name) == n }
    }
}
