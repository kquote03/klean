package app.klean.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.capture.voice.VoiceStatus
import app.klean.core.capture.voice.rememberVoiceInput
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.reminders.ui.DatePickDialog
import app.klean.feature.reminders.ui.RemindersTab
import app.klean.feature.tasks.R
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Tasks: one nav item with tabs Today / Upcoming / Lists / Reminders / Done (plan H5). */
@OptIn(ExperimentalMaterial3Api::class)
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

    val tabs = TasksTab.entries.filter { it != TasksTab.REMINDERS || state.remindersOn }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tasks_label)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab != TasksTab.REMINDERS) {
                FloatingActionButton(onClick = onNewTask) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tasks_new)) }
            } else {
                FloatingActionButton(onClick = { onEditReminder(null) }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tasks_new_reminder)) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val selected = tabs.indexOf(tab).coerceAtLeast(0)
            // Fixed, equal-width tabs with tight padding: all five fit a 360 dp phone instead of scrolling the last one
            // off the edge; a label that still doesn't fit shrinks rather than wrapping or clipping.
            PrimaryTabRow(selectedTabIndex = selected) {
                tabs.forEach { t ->
                    Tab(
                        selected = t == tabs[selected],
                        onClick = { vm.selectTab(t) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            stringResource(t.label),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.titleSmall,
                            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.titleSmall.fontSize),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            when (tabs[selected]) {
                TasksTab.REMINDERS -> RemindersTab(onEdit = onEditReminder)
                else -> TaskTabContent(tabs[selected], state, vm, fmt, onOpenTask)
            }
        }
    }
}

private val TasksTab.label: Int
    get() = when (this) {
        TasksTab.TODAY -> R.string.tasks_tab_today
        TasksTab.UPCOMING -> R.string.tasks_tab_upcoming
        TasksTab.LISTS -> R.string.tasks_tab_lists
        TasksTab.REMINDERS -> R.string.tasks_tab_reminders
        TasksTab.DONE -> R.string.tasks_tab_done
    }

@Composable
private fun TaskTabContent(tab: TasksTab, state: TasksUiState, vm: TasksViewModel, fmt: TaskFormatter, onOpen: (String) -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val voice by vm.voice.state.collectAsStateWithLifecycle()
    val onMic = rememberVoiceInput(vm.voice)
    val today = state.today ?: return
    val v = state.views
    LazyColumn(Modifier.fillMaxSize(), contentPadding = app.klean.core.design.component.FabClearance) {
        if (tab != TasksTab.DONE) {
            item(key = "add") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CaptureField(
                        value = draft,
                        onValueChange = { vm.draft.value = it },
                        onSubmit = vm::submit,
                        onMic = onMic,
                        onPhoto = null,
                        placeholder = stringResource(if (tab == TasksTab.TODAY) R.string.tasks_add_today else R.string.tasks_add),
                        listening = voice.listening,
                    )
                    VoiceStatus(voice, onUndo = vm.voice::undoLast)
                }
            }
        }
        when (tab) {
            TasksTab.TODAY -> {
                if (v.review.isNotEmpty()) {
                    header("review", R.string.tasks_review_title)
                    item(key = "review_hint") {
                        Text(
                            pluralStringResource(R.plurals.tasks_review_body, v.review.size, v.review.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(v.review, key = { "r" + it.id }) { r -> ReviewRow(r, today, fmt, vm, onOpen) }
                }
                header("today", R.string.tasks_section_today)
                if (v.today.isEmpty()) item(key = "today_empty") { NeutralEmpty(stringResource(R.string.tasks_today_empty)) }
                items(v.today, key = { it.id }) { r -> TaskRow(r, fmt, today, vm, onOpen, showDay = false) }
            }
            TasksTab.UPCOMING -> {
                if (v.upcoming.isEmpty()) item(key = "up_empty") { NeutralEmpty(stringResource(R.string.tasks_upcoming_empty)) }
                v.upcoming.forEach { g ->
                    item(key = "d" + g.date) { DayHeader(fmt.day(g.date, today)) }
                    items(g.rows, key = { it.id }) { r -> TaskRow(r, fmt, today, vm, onOpen, showDay = false) }
                }
            }
            TasksTab.LISTS -> listsTab(v, vm, fmt, today, onOpen)
            TasksTab.DONE -> {
                item(key = "done_note") {
                    Text(
                        stringResource(R.string.tasks_done_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (v.done.isEmpty()) item(key = "done_empty") { NeutralEmpty(stringResource(R.string.tasks_done_empty)) }
                items(v.done, key = { it.id }) { r -> TaskRow(r, fmt, today, vm, onOpen) }
            }
            TasksTab.REMINDERS -> Unit
        }
    }
}

private fun LazyListScope.header(key: String, title: Int) = item(key = "h_$key") { DayHeader(stringResource(title)) }

@Composable
private fun DayHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

private fun LazyListScope.listsTab(v: TaskViewsModel, vm: TasksViewModel, fmt: TaskFormatter, today: LocalDate, onOpen: (String) -> Unit) {
    item(key = "lists") { ListChips(v, vm) }
    if (v.listOpen.isEmpty() && v.listSomeday.isEmpty()) item(key = "list_empty") { NeutralEmpty(stringResource(R.string.tasks_list_empty)) }
    items(v.listOpen, key = { it.id }) { r -> TaskRow(r, fmt, today, vm, onOpen) }
    if (v.listSomeday.isNotEmpty()) {
        header("someday", R.string.tasks_section_someday)
        items(v.listSomeday, key = { it.id }) { r -> TaskRow(r, fmt, today, vm, onOpen) }
    }
    if (v.listDone.isNotEmpty()) {
        item(key = "list_done") { CollapsedDone(v.listDone, fmt, today, vm, onOpen) }
    }
}

@Composable
private fun CollapsedDone(rows: List<TaskRowModel>, fmt: TaskFormatter, today: LocalDate, vm: TasksViewModel, onOpen: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.tasks_done_count, rows.size, rows.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (open) R.string.tasks_collapse else R.string.tasks_expand),
            )
        }
        if (open) rows.forEach { r -> TaskRow(r, fmt, today, vm, onOpen) }
    }
}

@Composable
private fun ListChips(v: TaskViewsModel, vm: TasksViewModel) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val current = v.lists.firstOrNull { it.id == v.selectedListId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(v.lists, key = { it.id }) { l ->
                FilterChip(selected = l.id == v.selectedListId, onClick = { vm.selectList(l.id) }, label = { Text(l.name) })
            }
            item { AssistChip(onClick = { adding = true }, label = { Text(stringResource(R.string.tasks_new_list)) }) }
        }
        if (current != null) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.tasks_list_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_rename_list)) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.tasks_delete_list)) }, onClick = { menu = false; deleting = true })
                }
            }
        }
    }
    if (adding) NameDialog(R.string.tasks_new_list, "", onDismiss = { adding = false }) { vm.addList(it); adding = false }
    if (renaming && current != null) NameDialog(R.string.tasks_rename_list, current.name, onDismiss = { renaming = false }) { vm.renameList(current.id, it); renaming = false }
    if (deleting && current != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.tasks_delete_list_title, current.name)) },
            text = { Text(stringResource(R.string.tasks_delete_list_body)) },
            confirmButton = { TextButton(onClick = { vm.deleteList(current.id); deleting = false }) { Text(stringResource(R.string.tasks_delete)) } },
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
internal fun TaskRow(
    r: TaskRowModel,
    fmt: TaskFormatter,
    today: LocalDate,
    vm: TasksViewModel,
    onOpen: (String) -> Unit,
    showDay: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r.id) }.heightIn(min = 56.dp).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(r.done, r.title) { vm.setDone(r.id, it, r.title) }
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (r.done) TextDecoration.LineThrough else null,
                color = if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOfNotNull(
                if (showDay) fmt.due(r.dueDate, r.dueMinutes, today) else r.dueMinutes?.let { m -> r.dueDate?.let { fmt.time(it, m) } },
                r.listName,
                if (r.subtasksTotal > 0) "${r.subtasksDone}/${r.subtasksTotal}" else null,
            )
            if (meta.isNotEmpty() || r.recurring) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.recurring) {
                        Icon(
                            Icons.Rounded.Repeat, contentDescription = stringResource(R.string.tasks_repeats),
                            modifier = Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        priorityColor(r.priority)?.let { c ->
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        }
    }
}

@Composable
internal fun CheckCircle(done: Boolean, title: String, onChange: (Boolean) -> Unit) {
    IconButton(onClick = { onChange(!done) }, modifier = Modifier.size(48.dp)) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(if (done) R.string.tasks_mark_not_done else R.string.tasks_mark_done, title),
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One task in "Still want these?": keep for today, pick a date, someday, done, delete. No counts in red. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRow(r: TaskRowModel, today: LocalDate, fmt: TaskFormatter, vm: TasksViewModel, onOpen: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(r.title, style = MaterialTheme.typography.bodyLarge)
        fmt.from(r.dueDate, today)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.keepForToday(r.id) }, label = { Text(stringResource(R.string.tasks_review_keep)) })
            AssistChip(onClick = { picking = true }, label = { Text(stringResource(R.string.tasks_review_pick)) })
            AssistChip(onClick = { vm.someday(r.id) }, label = { Text(stringResource(R.string.tasks_review_someday)) })
            AssistChip(onClick = { vm.setDone(r.id, true, r.title) }, label = { Text(stringResource(R.string.tasks_review_done)) })
            AssistChip(onClick = { vm.delete(r.id) }, label = { Text(stringResource(R.string.tasks_review_let_go)) })
        }
    }
    if (picking) DatePickDialog(today, onDismiss = { picking = false }, onPicked = { vm.moveTo(r.id, it); picking = false })
}

internal object SystemZone : app.klean.core.common.ZoneProvider {
    override fun current() = kotlinx.datetime.TimeZone.currentSystemDefault()
}
