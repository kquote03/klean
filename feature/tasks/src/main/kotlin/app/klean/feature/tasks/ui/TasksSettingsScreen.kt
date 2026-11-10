package app.klean.feature.tasks.ui

import app.klean.core.google.ui.GoogleTasksSection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.reminders.alarm.TimePickDialog
import app.klean.feature.tasks.R
import app.klean.feature.tasks.data.TasksKeys
import app.klean.feature.tasks.data.TasksRepository
import app.klean.feature.tasks.review.TaskReview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TasksSettingsState(
    val reviewOn: Boolean = true,
    val reviewMinute: Int = 600,
    val defaultListId: String = "",
    val lists: List<TaskListEntity> = emptyList(),
)

@HiltViewModel
class TasksSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repo: TasksRepository,
    private val review: TaskReview,
) : ViewModel() {
    val state: StateFlow<TasksSettingsState> = combine(
        settings.observe(TasksKeys.ReviewEnabled), settings.observe(TasksKeys.ReviewMinute),
        settings.observe(TasksKeys.DefaultListId), repo.observeLists(),
    ) { on, minute, list, lists -> TasksSettingsState(on, minute, list, lists.filter { it.source != "GOOGLE" }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksSettingsState())

    fun setReviewOn(v: Boolean) = viewModelScope.launch {
        settings.set(TasksKeys.ReviewEnabled, v)
        review.reschedule()
    }

    fun setReviewMinute(v: Int) = viewModelScope.launch {
        settings.set(TasksKeys.ReviewMinute, v)
        review.reschedule()
    }

    fun setDefaultList(id: String) = viewModelScope.launch { settings.set(TasksKeys.DefaultListId, id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksSettingsScreen(onBack: () -> Unit, vm: TasksSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fmt = remember { TaskFormatter(ctx.applicationContext, SystemZone) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.tasks_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SwitchListItem(
                checked = s.reviewOn, onCheckedChange = vm::setReviewOn,
                headline = { Text(stringResource(R.string.tasks_settings_review)) },
                supporting = { Text(stringResource(R.string.tasks_settings_review_hint)) },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = s.reviewOn) { pickTime = true }.heightIn(min = 56.dp),
                headlineContent = { Text(stringResource(R.string.tasks_settings_review_time)) },
                supportingContent = { Text(fmt.time(LocalDate(2026, 1, 1), s.reviewMinute)) },
            )
            Box {
                ListItem(
                    modifier = Modifier.clickable { listMenu = true }.heightIn(min = 56.dp),
                    headlineContent = { Text(stringResource(R.string.tasks_settings_default_list)) },
                    supportingContent = { Text(s.lists.firstOrNull { it.id == s.defaultListId }?.name ?: s.lists.firstOrNull()?.name.orEmpty()) },
                )
                DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                    s.lists.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { vm.setDefaultList(l.id); listMenu = false }) }
                }
            }
            GoogleTasksSection()
        }
    }
    if (pickTime) TimePickDialog(onDismiss = { pickTime = false }, onPicked = { vm.setReviewMinute(it); pickTime = false }, initialMinute = s.reviewMinute)
}
