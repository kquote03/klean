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
import app.klean.feature.diet.ui.entryKcal
import app.klean.feature.diet.ui.itemLine
import app.klean.feature.diet.ui.kcalWithSource
import app.klean.shared.diet.KcalSource
import app.klean.feature.diet.ui.providerOf
import app.klean.shared.diet.FoodItemState
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** One AI item on the entry screen. */
data class AiItemUi(val id: String, val line: String, val name: String, val portion: String?, val calories: Int?)

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
    /** Set once the user deleted this entry: the screen closes and the list offers Undo. */
    val removed: String? = null,
    /** Visible AI items (not rejected), in order. */
    val items: List<AiItemUi> = emptyList(),
    /** Wire id of the provider that produced [items], for the chip. */
    val providerId: String? = null,
    val showCalories: Boolean = false,
    /** "300 kcal", "≈ 105 kcal · from earlier", "≈ 260 kcal · estimate"; null when there are none. */
    val kcalLabel: String? = null,
    /** The calorie slot: the user's own number, or the grams they gave; empty for filled-in calories. */
    val calories: String = "",
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
    private var savedCalories: String? = null
    private val state = MutableStateFlow(FoodEntryUiState())
    val uiState: StateFlow<FoodEntryUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.observeItems(listOf(id)), repo.observeViewPrefs()) { items, prefs -> items to prefs.showCalories }
                .collect { (items, showCalories) ->
                    val visible = items.filter { it.state != FoodItemState.REJECTED }.sortedBy { it.position }
                    state.update {
                        it.copy(
                            items = visible.map { i -> AiItemUi(i.id, itemLine(i, showCalories, fmt), i.name, i.portion, i.calories) },
                            providerId = providerOf(visible),
                            showCalories = showCalories,
                        )
                    }
                }
        }
        viewModelScope.launch {
            combine(repo.observeEntry(id), repo.observeItems(listOf(id)), repo.observeViewPrefs()) { e, items, prefs ->
                Triple(e, items.filter { it.state != FoodItemState.REJECTED }, prefs.showCalories)
            }.collect { (e, items, show) ->
                if (e == null) return@collect
                val slot = when {
                    e.kcal != null && e.kcalSource == KcalSource.USER.name -> e.kcal.toString()
                    e.grams != null -> "${e.grams}g"
                    else -> ""
                }
                val first = savedCalories == null
                // Seed the slot once, and again whenever what's stored moves under an untouched slot.
                val untouched = state.value.calories == savedCalories
                if (first || untouched) savedCalories = slot
                state.update {
                    it.copy(
                        kcalLabel = if (show) entryKcal(e, items)?.let(fmt::kcalWithSource) else null,
                        calories = if (first || untouched) slot else it.calories,
                    )
                }
            }
        }
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

    fun onCaloriesChange(text: String) = state.update { it.copy(calories = text) }

    /** Saves the calorie slot if it changed: `300`, `200g`, `150x100x200`; empty lets Klean fill it in again. */
    fun saveCalories() {
        val s = state.value
        if (!s.loaded || s.deleted || savedCalories == null || s.calories.trim() == savedCalories) return
        val input = s.calories.trim()
        if (app.klean.shared.diet.CalorieInput.parse(input) == app.klean.shared.diet.CalorieInput.Invalid) return
        savedCalories = input
        viewModelScope.launch { repo.setCalories(id, input) }
    }

    /** Saves the text (and the calorie slot) if they changed. Called by the Save button and on back. */
    fun save() {
        saveCalories()
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

    /** Correct an AI item. It is marked edited and keeps the provider it came from. */
    fun editItem(itemId: String, name: String, portion: String?, calories: Int?) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.editItem(itemId, name, portion, calories) }
    }

    /** "Not right": hide this item. */
    fun rejectItem(itemId: String) {
        viewModelScope.launch { repo.rejectItem(itemId) }
    }

    /** "Remove AI details": hide every item. The entry text stays. */
    fun removeAiDetails() {
        viewModelScope.launch { repo.rejectAllItems(id) }
    }

    fun delete() {
        state.update { it.copy(deleted = true, removed = id) }
        viewModelScope.launch { repo.delete(id) }
    }
}
