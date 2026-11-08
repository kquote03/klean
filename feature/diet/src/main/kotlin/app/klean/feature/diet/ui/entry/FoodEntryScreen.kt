package app.klean.feature.diet.ui.entry

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.diet.R
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private const val UNDO_WINDOW_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryScreen(onBack: () -> Unit, vm: FoodEntryViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val deletedMsg = stringResource(R.string.diet_entry_deleted)
    val undoLabel = stringResource(R.string.diet_undo)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.replacePhoto(uri)
    }
    val leave = {
        vm.save()
        onBack()
    }
    BackHandler(onBack = leave)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showDate = true }, enabled = !state.deleted, label = { Text(state.dateLabel) })
                AssistChip(onClick = { showTime = true }, enabled = !state.deleted, label = { Text(state.timeLabel) })
            }
            PhotoSection(
                photo = state.photo,
                enabled = !state.deleted,
                onPick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = vm::removePhoto,
            )
            // AI items section appears with the provider layer (M10).
            TextButton(
                enabled = !state.deleted,
                onClick = {
                    vm.delete()
                    scope.launch {
                        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                            snackbar.showSnackbar(deletedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
                        }
                        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else onBack()
                    }
                },
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
private fun PhotoSection(photo: File?, enabled: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { f ->
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bitmap?.let {
            Image(
                it,
                contentDescription = stringResource(R.string.diet_has_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        TextButton(onClick = onPick, enabled = enabled) {
            Text(stringResource(if (photo == null) R.string.diet_photo_add else R.string.diet_photo_replace))
        }
        if (photo != null) {
            TextButton(onClick = onRemove, enabled = enabled) { Text(stringResource(R.string.diet_photo_remove)) }
        }
    }
}
