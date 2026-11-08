package app.klean.feature.diet.ui.entry

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.photo.PhotoStorage
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

data class FoodEntryUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val text: String = "",
    val eatenAt: Long = 0L,
    val date: LocalDate? = null,
    val hour: Int = 0,
    val minute: Int = 0,
    val dateLabel: String = "",
    val timeLabel: String = "",
    val photo: File? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class FoodEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: DietRepository,
    private val zone: ZoneProvider,
    private val fmt: DietFormatter,
    private val photos: PhotoStorage,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["entryId"])
    private var savedText: String = ""
    private val state = MutableStateFlow(FoodEntryUiState())
    val uiState: StateFlow<FoodEntryUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeEntry(id).collect { e ->
                if (e == null) {
                    state.update { it.copy(loaded = true, missing = true) }
                    return@collect
                }
                val local = e.eatenAt.toLocal(zone.current())
                val firstLoad = !state.value.loaded
                if (firstLoad) savedText = e.rawText
                state.update {
                    it.copy(
                        loaded = true,
                        // Keep what the user is typing; the stored text only seeds the field.
                        text = if (firstLoad) e.rawText else it.text,
                        eatenAt = e.eatenAt,
                        date = local.date,
                        hour = local.hour,
                        minute = local.minute,
                        dateLabel = fmt.date(local.date),
                        timeLabel = fmt.time(e.eatenAt),
                        photo = e.photoFile?.let(photos::file),
                        deleted = e.sync.deleted,
                    )
                }
            }
        }
    }

    fun onTextChange(text: String) = state.update { it.copy(text = text) }

    /** Saves the text if it changed. Called by the Save button and on back. */
    fun save() {
        val clean = state.value.text.trim()
        if (!state.value.loaded || state.value.deleted || clean.isEmpty() || clean == savedText) return
        savedText = clean
        viewModelScope.launch { repo.edit(id, text = clean) }
    }

    fun onTimePicked(hour: Int, minute: Int) {
        val date = state.value.date ?: return
        setEatenAt(date, hour, minute)
    }

    fun onDatePicked(date: LocalDate) {
        val s = state.value
        if (!s.loaded || s.date == null) return
        setEatenAt(date, s.hour, s.minute)
    }

    private fun setEatenAt(date: LocalDate, hour: Int, minute: Int) {
        val at = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone.current()).toEpochMilliseconds()
        if (at == state.value.eatenAt) return
        viewModelScope.launch { repo.edit(id, eatenAt = at) }
    }

    fun removePhoto() {
        viewModelScope.launch { repo.edit(id, clearPhoto = true) }
    }

    fun newCaptureUri(): Pair<String, Uri> = photos.newCaptureUri()

    /** The camera finished: normalise the full-size original into the store, then drop the original. */
    fun onPhotoTaken(name: String, uri: Uri, success: Boolean) {
        viewModelScope.launch {
            if (success) {
                runCatching { photos.import(uri) }
                    .onSuccess { stored -> repo.edit(id, photoFile = stored) }
                    .onFailure { Log.w("Klean", "Photo import failed", it) }
            }
            photos.delete(name)
        }
    }

    fun replacePhoto(uri: Uri) {
        viewModelScope.launch {
            runCatching { photos.import(uri) }
                .onSuccess { name -> repo.edit(id, photoFile = name) }
                .onFailure { Log.w("Klean", "Photo import failed", it) }
        }
    }

    fun delete() {
        state.update { it.copy(deleted = true) }
        viewModelScope.launch { repo.delete(id) }
    }

    fun undoDelete() {
        state.update { it.copy(deleted = false) }
        viewModelScope.launch { repo.restore(id) }
    }
}
