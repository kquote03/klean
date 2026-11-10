package app.klean.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.core.design.theme.Space
import app.klean.core.google.ui.GoogleChip
import app.klean.feature.reminders.ui.DatePickDialog
import app.klean.feature.reminders.ui.RemindersTab
import app.klean.feature.tasks.R
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Tasks, the first module of Plan (Soft cards). One list: Today, Coming up, and "Still want these?". Lists,
 * Reminders and Finished open from the overflow menu; adding is the Add button. A task's check completes it; tapping
 * the row opens it; a long press on a "Still want these?" row offers keep, move, someday, done or let go.
 */
@Composable
fun TasksScreen(
    initialTab: String?,
    onOpenTask: (String) -> Unit,
    onNewTask: () -> Unit,
    onEditReminder: (String?) -> Unit,
    vm: TasksViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fmt = remember { TaskFormatter(ctx.applicationContext, SystemZone) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialTab) {
        initialTab?.let { t -> TasksTab.entries.firstOrNull { it.name.equals(t, ignoreCase = true) }?.let(vm::selectTab) ?: vm.selectTab(TasksTab.TODAY) }
    }
    val doneText = stringResource(R.string.tasks_snackbar_done)
    val addedText = stringResource(R.string.tasks_snackbar_added)
    val undoText = stringResource(R.string.tasks_undo)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            scope.launch {
                when (e) {
                    is TasksEvent.Completed -> if (snackbar.showSnackbar(doneText, undoText, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoComplete(e.completion)
                    is TasksEvent.Added -> if (snackbar.showSnackbar(addedText, undoText, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoAdd(e.id)
                }
            }
        }
    }
    TasksContent(
        state = state,
        view = tab,
        fmt = fmt,
        actions = TaskActions(
            onOpen = onOpenTask,
            setDone = { id, done, title -> vm.setDone(id, done, title) },
            keepForToday = { vm.keepForToday(it) },
            moveTo = { id, d -> vm.moveTo(id, d) },
            someday = { vm.someday(it) },
            letGo = { vm.delete(it) },
            selectList = vm::selectList,
            addList = { vm.addList(it) },
            renameList = { id, n -> vm.renameList(id, n) },
            deleteList = { vm.deleteList(it) },
        ),
        onView = vm::selectTab,
        onNewTask = onNewTask,
        onNewReminder = { onEditReminder(null) },
        snackbar = snackbar,
        reminders = { pad -> RemindersTab(onEdit = onEditReminder, contentPadding = pad) },
    )
}

/** What a row can do; kept apart from the view model so the screen renders in screenshot tests. */
data class TaskActions(
    val onOpen: (String) -> Unit = {},
    val setDone: (id: String, done: Boolean, title: String) -> Unit = { _, _, _ -> },
    val keepForToday: (String) -> Unit = {},
    val moveTo: (String, LocalDate) -> Unit = { _, _ -> },
    val someday: (String) -> Unit = {},
    val letGo: (String) -> Unit = {},
    val selectList: (String) -> Unit = {},
    val addList: (String) -> Unit = {},
    val renameList: (String, String) -> Unit = { _, _ -> },
    val deleteList: (String) -> Unit = {},
)

/** The main view; Upcoming (old deep links) folds into it. */
private val TasksTab.main: Boolean get() = this == TasksTab.TODAY || this == TasksTab.UPCOMING

@Composable
fun TasksContent(
    state: TasksUiState,
    view: TasksTab,
    fmt: TaskFormatter,
    actions: TaskActions,
    onView: (TasksTab) -> Unit,
    onNewTask: () -> Unit,
    onNewReminder: () -> Unit,
    snackbar: SnackbarHostState? = null,
    reminders: @Composable (PaddingValues) -> Unit = {},
) {
    val menu = buildList {
        if (view == TasksTab.REMINDERS) {
            add(MenuAction(stringResource(R.string.tasks_new_reminder), onClick = onNewReminder))
        } else {
            add(MenuAction(stringResource(R.string.tasks_new), onClick = onNewTask))
        }
        if (view != TasksTab.LISTS) add(MenuAction(stringResource(R.string.tasks_tab_lists)) { onView(TasksTab.LISTS) })
        if (state.remindersOn && view != TasksTab.REMINDERS) add(MenuAction(stringResource(R.string.tasks_tab_reminders)) { onView(TasksTab.REMINDERS) })
        if (view != TasksTab.DONE) add(MenuAction(stringResource(R.string.tasks_view_finished)) { onView(TasksTab.DONE) })
    }
    KleanScreen(title = stringResource(R.string.tasks_label), menu = menu, snackbarHostState = snackbar) { pad ->
        val back = MenuAction(stringResource(R.string.tasks_view_all)) { onView(TasksTab.TODAY) }
        if (view == TasksTab.REMINDERS) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                SubViewHeader(stringResource(R.string.tasks_tab_reminders), back, pad)
                reminders(PaddingValues(start = pad.left(), end = pad.left(), bottom = pad.calculateBottomPadding()))
            }
            return@KleanScreen
        }
        val today = state.today ?: return@KleanScreen
        val v = state.views
        LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
            when {
                view.main -> mainView(v, today, fmt, actions)
                view == TasksTab.LISTS -> {
                    item(key = "h") { SectionHeader(stringResource(R.string.tasks_tab_lists), first = true, action = back) }
                    item(key = "chips") { ListChips(v, actions) }
                    if (v.listOpen.isEmpty() && v.listSomeday.isEmpty()) {
                        item(key = "empty") { EmptyState(stringResource(R.string.tasks_list_empty)) }
                    }
                    taskGroup("open", v.listOpen, fmt, today, actions)
                    if (v.listSomeday.isNotEmpty()) {
                        item(key = "hs") { SectionHeader(stringResource(R.string.tasks_section_someday)) }
                        taskGroup("someday", v.listSomeday, fmt, today, actions)
                    }
                    if (v.listDone.isNotEmpty()) {
                        item(key = "hd") { SectionHeader(stringResource(R.string.tasks_view_finished)) }
                        taskGroup("ldone", v.listDone, fmt, today, actions)
                    }
                }
                else -> {
                    item(key = "h") { SectionHeader(stringResource(R.string.tasks_view_finished), first = true, action = back) }
                    if (v.done.isEmpty()) {
                        item(key = "empty") { EmptyState(stringResource(R.string.tasks_done_note)) }
                    } else {
                        taskGroup("done", v.done, fmt, today, actions)
                        item(key = "note") {
                            Text(
                                stringResource(R.string.tasks_done_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.md, start = Space.md, end = Space.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaddingValues.left() = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)

@Composable
private fun SubViewHeader(title: String, back: MenuAction, pad: PaddingValues) {
    SectionHeader(
        title,
        first = true,
        action = back,
        modifier = Modifier.padding(top = pad.calculateTopPadding(), start = pad.left(), end = pad.left()),
    )
}

/** Today, Coming up, Still want these? (the prototype's order). One calm message when all three are empty. */
private fun LazyListScope.mainView(v: TaskViewsModel, today: LocalDate, fmt: TaskFormatter, actions: TaskActions) {
    if (v.today.isEmpty() && v.upcoming.isEmpty() && v.review.isEmpty()) {
        item(key = "empty") { EmptyState(stringResource(R.string.tasks_all_clear)) }
        return
    }
    var first = true
    if (v.today.isNotEmpty()) {
        item(key = "h_today") { SectionHeader(stringResource(R.string.tasks_section_today), first = true) }
        taskGroup("today", v.today, fmt, today, actions, showDay = false)
        first = false
    }
    if (v.upcoming.isNotEmpty()) {
        val isFirst = first
        item(key = "h_up") { SectionHeader(stringResource(R.string.tasks_section_coming_up), first = isFirst) }
        taskGroup("up", v.upcoming.flatMap { it.rows }, fmt, today, actions)
        first = false
    }
    if (v.review.isNotEmpty()) {
        val isFirst = first
        item(key = "h_review") { SectionHeader(stringResource(R.string.tasks_review_title), first = isFirst) }
        taskGroup("review", v.review, fmt, today, actions, review = true)
    }
}

private fun LazyListScope.taskGroup(
    key: String,
    rows: List<TaskRowModel>,
    fmt: TaskFormatter,
    today: LocalDate,
    actions: TaskActions,
    showDay: Boolean = true,
    review: Boolean = false,
) = listGroup(key, rows, { it.id }) { r -> TaskRow(r, fmt, today, actions, showDay, review) }

@Composable
internal fun TaskRow(
    r: TaskRowModel,
    fmt: TaskFormatter,
    today: LocalDate,
    actions: TaskActions,
    showDay: Boolean = true,
    review: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val meta = when {
        review -> fmt.from(r.dueDate, today)?.replaceFirstChar { it.uppercase() }
        showDay -> fmt.due(r.dueDate, r.dueMinutes, today)
        else -> r.dueMinutes?.let { m -> r.dueDate?.let { fmt.time(it, m) } }
    }
    val supporting = listOfNotNull(r.listName, if (r.subtasksTotal > 0) "${r.subtasksDone}/${r.subtasksTotal}" else null)
        .joinToString(" · ").ifEmpty { null }
    Box {
        ListRow(
            title = r.title,
            supporting = supporting,
            meta = meta,
            muted = r.done,
            leading = { CheckCircle(r.done, r.title) { actions.setDone(r.id, it, r.title) } },
            trailing = if (r.google || r.recurring || r.priority > 0) {
                { RowMarks(r) }
            } else {
                null
            },
            onClick = { actions.onOpen(r.id) },
            onLongClick = if (review) ({ menu = true }) else null,
        )
        if (review) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    R.string.tasks_review_keep to { actions.keepForToday(r.id) },
                    R.string.tasks_review_pick to { picking = true },
                    R.string.tasks_review_someday to { actions.someday(r.id) },
                    R.string.tasks_review_done to { actions.setDone(r.id, true, r.title) },
                    R.string.tasks_review_let_go to { actions.letGo(r.id) },
                ).forEach { (label, act) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; act() })
                }
            }
        }
    }
    if (picking) DatePickDialog(today, onDismiss = { picking = false }, onPicked = { actions.moveTo(r.id, it); picking = false })
}

@Composable
private fun RowMarks(r: TaskRowModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
        if (r.google) GoogleChip()
        if (r.recurring) {
            Icon(
                Icons.Rounded.Repeat, contentDescription = stringResource(R.string.tasks_repeats),
                modifier = Modifier.size(Space.md), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        priorityColor(r.priority)?.let { c -> Box(Modifier.size(Space.xs).clip(CircleShape).background(c)) }
    }
}

@Composable
private fun ListChips(v: TaskViewsModel, actions: TaskActions) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val current = v.lists.firstOrNull { it.id == v.selectedListId }
    // One bounded chip row: the lists, then "New list". A list's rename and delete sit on a long press of its chip.
    LazyRow(Modifier.fillMaxWidth().padding(bottom = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        items(v.lists, key = { it.id }) { l ->
            Box {
                FilterChip(
                    selected = l.id == v.selectedListId,
                    onClick = { if (l.id == v.selectedListId && l.source != "GOOGLE") menu = true else actions.selectList(l.id) },
                    label = { Text(l.name) },
                )
                if (l.id == v.selectedListId) {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tasks_rename_list)) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tasks_delete_list)) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        }
        item { AssistChip(onClick = { adding = true }, label = { Text(stringResource(R.string.tasks_new_list)) }) }
    }
    if (adding) NameDialog(R.string.tasks_new_list, "", onDismiss = { adding = false }) { actions.addList(it); adding = false }
    if (renaming && current != null) NameDialog(R.string.tasks_rename_list, current.name, onDismiss = { renaming = false }) { actions.renameList(current.id, it); renaming = false }
    if (deleting && current != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tasks_delete_list_title, current.name)) },
            text = { Text(stringResource(R.string.tasks_delete_list_body)) },
            confirmButton = { TextButton(onClick = { actions.deleteList(current.id); deleting = false }) { Text(stringResource(R.string.tasks_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}

@Composable
private fun NameDialog(title: Int, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.tasks_list_name)) }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.tasks_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) } },
    )
}

/** Neutral priority dots: no red anywhere. */
@Composable
internal fun priorityColor(p: Int): Color? = when (p) {
    1 -> MaterialTheme.colorScheme.outlineVariant
    2 -> MaterialTheme.colorScheme.secondary
    3 -> MaterialTheme.colorScheme.primary
    else -> null
}

@Composable
internal fun CheckCircle(done: Boolean, title: String, onChange: (Boolean) -> Unit) {
    IconButton(onClick = { onChange(!done) }, modifier = Modifier.size(Space.touchTarget)) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(if (done) R.string.tasks_mark_not_done else R.string.tasks_mark_done, title),
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal object SystemZone : app.klean.core.common.ZoneProvider {
    override fun current() = kotlinx.datetime.TimeZone.currentSystemDefault()
}
