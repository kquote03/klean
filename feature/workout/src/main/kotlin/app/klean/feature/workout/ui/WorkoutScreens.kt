package app.klean.feature.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.capture.CaptureActivity
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.workout.R
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.model.TemplateText
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.datetime.TimeZone

/** The device zone for screens built without Hilt (formatter in previews and screen tests). */
internal object SystemZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}

@Composable
internal fun rememberFormatter(): WorkoutFormatter {
    val ctx = LocalContext.current
    return remember { WorkoutFormatter(ctx.applicationContext, SystemZone) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.workout_back))
                }
            }
        },
        actions = { actions() },
    )
}

/** Workout home: quick start, saved workouts, a freeform log field, recent sessions, exercises. */
@Composable
fun WorkoutScreen(
    onOpenSession: (String) -> Unit,
    onEditTemplate: (String?) -> Unit,
    onExercises: () -> Unit,
    onSettings: () -> Unit,
    vm: WorkoutHomeViewModel = hiltViewModel(),
    run: app.klean.feature.workout.jog.RunViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val logged = stringResource(R.string.workout_logged)
    val undo = stringResource(R.string.workout_undo)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e is WorkoutEvent.Logged && snackbar.showSnackbar(logged, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                vm.undo(e.sessionId)
            }
        }
    }
    Scaffold(
        topBar = {
            WorkoutTopBar(stringResource(R.string.workout_label), onBack = null) {
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.workout_settings))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val open = s.open
                    if (open != null) {
                        Text(stringResource(R.string.workout_in_progress, fmt.time(open.session.startedAt)), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { onOpenSession(open.session.id) }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.workout_continue))
                        }
                    } else {
                        Button(onClick = { vm.start(null, onOpenSession) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.workout_start_empty), Modifier.padding(start = 8.dp))
                        }
                    }
                    if (s.open == null) app.klean.feature.workout.jog.RunSection(run, Modifier.padding(top = 8.dp))
                    CaptureField(
                        value = text,
                        onValueChange = { text = it },
                        onSubmit = {
                            vm.log(text)
                            text = ""
                        },
                        onMic = { ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.MODULE_SCREEN, ModuleId.WORKOUT, startVoice = true)) },
                        onPhoto = null,
                        placeholder = stringResource(R.string.workout_log_placeholder),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item { SectionTitle(stringResource(R.string.workout_templates)) }
            if (s.loaded && s.templates.isEmpty()) {
                item { Hint(stringResource(R.string.workout_templates_empty)) }
            }
            items(s.templates, key = { "t:" + it.template.id }) { t ->
                ListItem(
                    modifier = Modifier.clickable { onEditTemplate(t.template.id) }.heightIn(min = 56.dp),
                    headlineContent = { Text(t.template.name) },
                    supportingContent = { Text(t.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        FilledTonalButton(onClick = { vm.start(t.template.id, onOpenSession) }, enabled = s.open == null, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.workout_start))
                        }
                    },
                )
            }
            item {
                TextButton(onClick = { onEditTemplate(null) }, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.workout_new_template), Modifier.padding(start = 8.dp))
                }
            }
            item { SectionTitle(stringResource(R.string.workout_recent)) }
            if (s.loaded && s.recent.isEmpty()) {
                item { Hint(stringResource(R.string.workout_recent_empty)) }
            }
            items(s.recent, key = { "s:" + it.session.id }) { r ->
                val today = s.today
                EntryRow(
                    time = if (today != null) fmt.day(r.session.startedAt, today) else "",
                    text = fmt.names(r),
                    meta = { Text(fmt.summary(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { onOpenSession(r.session.id) },
                )
            }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                ListItem(
                    modifier = Modifier.clickable(onClick = onExercises).heightIn(min = 56.dp),
                    headlineContent = { Text(stringResource(R.string.workout_exercises)) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

@Composable
private fun Hint(text: String) = Text(
    text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
)

/** A saved workout: name and the text, with a live reading of what it parses to. */
@Composable
fun TemplateEditScreen(onBack: () -> Unit, vm: TemplateEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            WorkoutTopBar(stringResource(if (s.isNew) R.string.workout_template_title_new else R.string.workout_template_title_edit), onBack) {
                if (!s.isNew) {
                    IconButton(onClick = { vm.delete(onBack) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.workout_delete))
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = s.name, onValueChange = vm::setName, singleLine = true,
                label = { Text(stringResource(R.string.workout_template_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.text, onValueChange = vm::setText, minLines = 3,
                label = { Text(stringResource(R.string.workout_template_items)) },
                placeholder = { Text(stringResource(R.string.workout_template_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(stringResource(R.string.workout_template_preview), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            val preview = s.preview
            if (preview.isEmpty()) {
                Text(stringResource(R.string.workout_template_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            preview.forEach { Text("· " + TemplateText.formatItem(it), modifier = Modifier.padding(vertical = 2.dp)) }
            Button(onClick = { vm.save(onBack) }, enabled = s.loaded, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workout_save))
            }
        }
    }
}

/** A session: running (one tap per set, Finish) or finished (the same rows, editable). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(onBack: () -> Unit, onDeleted: (String) -> Unit, vm: SessionViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    var text by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<WorkoutSetEntity?>(null) }
    val session = s.session
    Scaffold(
        topBar = {
            WorkoutTopBar(stringResource(R.string.workout_session_title), onBack) {
                if (session != null) {
                    IconButton(onClick = { vm.delete(onDeleted) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.workout_delete_session))
                    }
                }
            }
        },
    ) { pad ->
        if (session == null) {
            if (s.loaded) NeutralEmpty(stringResource(R.string.workout_recent_empty), Modifier.padding(pad))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                val today = s.today
                val header = if (session.isOpen) {
                    stringResource(R.string.workout_elapsed, fmt.duration(((s.now - session.session.startedAt) / 60_000).toInt().coerceAtLeast(0)))
                } else {
                    stringResource(R.string.workout_finished_at, if (today != null) fmt.dayTime(session.session.startedAt, today) else "", fmt.summary(session))
                }
                Text(header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                session.session.notes?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            if (s.rows.isEmpty()) item { Hint(stringResource(R.string.workout_session_empty)) }
            items(s.rows, key = { "r:" + it.exercise.lowercase() }) { row ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.exercise, style = MaterialTheme.typography.titleSmall)
                            row.planned?.let { Text(TemplateText.formatItem(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        val add = stringResource(R.string.workout_add_set, row.exercise)
                        FilledTonalButton(
                            onClick = { vm.addSet(row) },
                            modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp).semanticsLabel(add),
                        ) { Text(stringResource(R.string.workout_add_set_short)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.done.forEach { set ->
                            AssistChip(onClick = { editing = set }, label = { Text(SessionRows.setLabel(set)) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
            }
            item {
                CaptureField(
                    value = text,
                    onValueChange = { text = it },
                    onSubmit = {
                        vm.addText(text)
                        text = ""
                    },
                    onMic = {},
                    onPhoto = null,
                    placeholder = stringResource(R.string.workout_add_exercise),
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (session.isOpen) {
                item {
                    Button(onClick = { vm.finish(onBack) }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.workout_finish))
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
    editing?.let { set ->
        SetDialog(
            set = set,
            onDismiss = { editing = null },
            onSave = { reps, kg, dur, dist ->
                vm.updateSet(set, reps, kg, dur, dist)
                editing = null
            },
            onRemove = {
                vm.deleteSet(set)
                editing = null
            },
        )
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = this.semantics { contentDescription = label }

@Composable
private fun SetDialog(set: WorkoutSetEntity, onDismiss: () -> Unit, onSave: (Int?, Double?, Int?, Int?) -> Unit, onRemove: () -> Unit) {
    var reps by remember { mutableStateOf(set.reps?.toString().orEmpty()) }
    var kg by remember { mutableStateOf(set.weightKg?.let { TemplateText.formatItem(app.klean.feature.workout.model.WorkoutItem("", app.klean.feature.workout.model.WorkoutKind.STRENGTH, weightKg = it)).removePrefix(" @ ").removeSuffix(" kg") }.orEmpty()) }
    var seconds by remember { mutableStateOf(set.durationS?.toString().orEmpty()) }
    var metres by remember { mutableStateOf(set.distanceM?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_edit_set)) },
        text = {
            Column {
                val number = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                if (set.durationS == null && set.distanceM == null) {
                    OutlinedTextField(reps, { reps = it }, label = { Text(stringResource(R.string.workout_reps)) }, singleLine = true, keyboardOptions = number)
                    OutlinedTextField(kg, { kg = it }, label = { Text(stringResource(R.string.workout_weight)) }, singleLine = true, keyboardOptions = number)
                }
                if (set.durationS != null) {
                    OutlinedTextField(seconds, { seconds = it }, label = { Text(stringResource(R.string.workout_seconds)) }, singleLine = true, keyboardOptions = number)
                }
                if (set.distanceM != null) {
                    OutlinedTextField(metres, { metres = it }, label = { Text(stringResource(R.string.workout_metres)) }, singleLine = true, keyboardOptions = number)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    reps.trim().toIntOrNull(), kg.trim().replace(',', '.').toDoubleOrNull(),
                    seconds.trim().toIntOrNull(), metres.trim().toIntOrNull(),
                )
            }) { Text(stringResource(R.string.workout_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.workout_remove_set)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.workout_cancel)) }
            }
        },
    )
}

@Composable
fun ExercisesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: ExercisesViewModel = hiltViewModel()) {
    val list by vm.exercises.collectAsStateWithLifecycle()
    Scaffold(topBar = { WorkoutTopBar(stringResource(R.string.workout_exercises), onBack) }) { pad ->
        val l = list
        if (l != null && l.isEmpty()) {
            NeutralEmpty(stringResource(R.string.workout_exercises_empty), Modifier.padding(pad))
        } else {
            LazyColumn(Modifier.padding(pad)) {
                items(l.orEmpty(), key = { it.id }) { e ->
                    ListItem(modifier = Modifier.clickable { onOpen(e.id) }.heightIn(min = 56.dp), headlineContent = { Text(e.name) })
                }
            }
        }
    }
}

/** Every set of one exercise over time: a plain list by day. No records, no targets. */
@Composable
fun ExerciseHistoryScreen(onBack: () -> Unit, vm: ExerciseHistoryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    Scaffold(topBar = { WorkoutTopBar(stringResource(R.string.workout_history_title, s.name), onBack) }) { pad ->
        val today = s.today
        LazyColumn(Modifier.padding(pad)) {
            val bySession = s.rows.groupBy { it.sessionId }
            items(bySession.values.toList(), key = { it.first().sessionId }) { rows ->
                ListItem(
                    headlineContent = { Text(if (today != null) fmt.dayTime(rows.first().startedAt, today) else "") },
                    supportingContent = { Text(rows.joinToString("  ·  ") { SessionRows.setLabel(it.set) }) },
                )
            }
        }
    }
}

@Composable
fun WorkoutSettingsScreen(onBack: () -> Unit, vm: WorkoutSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Scaffold(topBar = { WorkoutTopBar(stringResource(R.string.workout_settings_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SwitchListItem(
                checked = s.suggest, onCheckedChange = vm::setSuggest,
                headline = { Text(stringResource(R.string.workout_settings_suggest)) },
                supporting = { Text(stringResource(R.string.workout_settings_suggest_hint)) },
            )
            androidx.compose.foundation.layout.Box {
                ListItem(
                    modifier = Modifier.clickable(enabled = s.suggest) { menu = true }.heightIn(min = 56.dp),
                    headlineContent = { Text(stringResource(R.string.workout_settings_suggest_days)) },
                    supportingContent = { Text(ctx.resources.getQuantityString(R.plurals.workout_days, s.days, s.days)) },
                )
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(2, 3, 5, 7).forEach { d ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(ctx.resources.getQuantityString(R.plurals.workout_days, d, d)) },
                            onClick = {
                                vm.setDays(d)
                                menu = false
                            },
                        )
                    }
                }
            }
            app.klean.core.health.HealthConnectRow(
                needed = setOf(app.klean.core.health.HealthAccess.READ_EXERCISE, app.klean.core.health.HealthAccess.WRITE_EXERCISE),
            )
        }
    }
}

/** For screens and cards that list sessions. */
internal fun List<SessionWithSets>.finished() = filter { !it.isOpen }
