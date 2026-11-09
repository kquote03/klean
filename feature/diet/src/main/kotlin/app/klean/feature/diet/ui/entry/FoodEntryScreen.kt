package app.klean.feature.diet.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import app.klean.core.design.component.ProviderChip
import app.klean.feature.diet.ui.providerLabelRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import app.klean.core.capture.photo.PhotoMenu
import app.klean.core.capture.photo.PhotoPicker
import app.klean.core.capture.photo.rememberPhotoPicker
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.PhotoThumbnail
import app.klean.feature.diet.R
import java.io.File
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryScreen(onBack: () -> Unit, onRemoved: (String) -> Unit, vm: FoodEntryViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val removed = state.removed
    LaunchedEffect(removed) { if (removed != null) onRemoved(removed) }
    val photoPicker = rememberPhotoPicker(vm::newCaptureUri, vm::onPhotoTaken, vm::replacePhoto)
    val leave = {
        vm.save()
        onBack()
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diet_entry_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
                actions = { TextButton(onClick = leave, enabled = !state.deleted) { Text(stringResource(R.string.diet_save)) } },
            )
        },
    ) { padding ->
        if (state.loaded && state.missing) {
            NeutralEmpty(stringResource(R.string.diet_entry_missing), Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onTextChange,
                label = { Text(stringResource(R.string.diet_entry_text_label)) },
                enabled = !state.deleted,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.diet_entry_eaten_at), style = MaterialTheme.typography.titleSmall)
            // Wraps the time under the date when large text leaves no room for both.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showDate = true }, enabled = !state.deleted, label = { Text(state.dateLabel) })
                AssistChip(onClick = { showTime = true }, enabled = !state.deleted, label = { Text(state.timeLabel) })
            }
            PhotoSection(
                photo = state.photo,
                enabled = !state.deleted,
                picker = photoPicker,
                onRemove = vm::removePhoto,
            )
            if (state.items.isNotEmpty()) {
                AiItemsSection(
                    state = state,
                    onEdit = vm::editItem,
                    onReject = vm::rejectItem,
                    onRemoveAll = vm::removeAiDetails,
                )
            }
            TextButton(
                enabled = !state.deleted,
                // Goes back to the list, which offers Undo.
                onClick = vm::delete,
            ) { Text(stringResource(R.string.diet_delete)) }
        }
    }

    if (showDate && state.date != null) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.date!!.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        vm.onDatePicked(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date)
                    }
                    showDate = false
                }) { Text(stringResource(R.string.diet_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.diet_cancel)) } },
        ) { DatePicker(picker) }
    }
    if (showTime) {
        val picker = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.onTimePicked(picker.hour, picker.minute)
                    showTime = false
                }) { Text(stringResource(R.string.diet_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.diet_cancel)) } },
            text = { TimePicker(picker) },
        )
    }
}

@Composable
private fun PhotoSection(photo: File?, enabled: Boolean, picker: PhotoPicker, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photo?.let { PhotoThumbnail(it.path, contentDescription = stringResource(R.string.diet_has_photo), size = 96.dp) }
            Box {
                TextButton(onClick = { menu = true }, enabled = enabled) {
                    Text(stringResource(if (photo == null) R.string.diet_photo_add else R.string.diet_photo_replace))
                }
                PhotoMenu(expanded = menu, onDismiss = { menu = false }, picker = picker)
            }
            if (photo != null) {
                TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.diet_photo_remove)) }
            }
        }
        if (picker.noCamera) {
            Text(
                stringResource(app.klean.core.capture.R.string.photo_no_camera),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiItemsSection(
    state: FoodEntryUiState,
    onEdit: (String, String, String?, Int?) -> Unit,
    onReject: (String) -> Unit,
    onRemoveAll: () -> Unit,
) {
    var editing by remember { mutableStateOf<AiItemUi?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.diet_ai_items_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            providerLabelRes(state.providerId)?.let { ProviderChip(stringResource(it)) }
        }
        state.items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = item }, enabled = !state.deleted) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.diet_ai_item_edit, item.name))
                }
                TextButton(onClick = { onReject(item.id) }, enabled = !state.deleted) { Text(stringResource(R.string.diet_ai_item_not_right)) }
            }
        }
        TextButton(onClick = onRemoveAll, enabled = !state.deleted) { Text(stringResource(R.string.diet_ai_remove_all)) }
    }
    editing?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.name) }
        var portion by remember(item.id) { mutableStateOf(item.portion.orEmpty()) }
        var calories by remember(item.id) { mutableStateOf(item.calories?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.diet_ai_item_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.diet_ai_item_name)) }, singleLine = true)
                    OutlinedTextField(portion, { portion = it }, label = { Text(stringResource(R.string.diet_ai_item_portion)) }, singleLine = true)
                    if (state.showCalories) {
                        OutlinedTextField(
                            calories,
                            { calories = it.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.diet_ai_item_calories)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val kcal = if (state.showCalories) calories.toIntOrNull() else item.calories
                    onEdit(item.id, name, portion.ifBlank { null }, kcal)
                    editing = null
                }) { Text(stringResource(R.string.diet_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.diet_cancel)) } },
        )
    }
}
