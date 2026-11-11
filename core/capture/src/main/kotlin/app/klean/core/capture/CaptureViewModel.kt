package app.klean.core.capture

import android.net.Uri
import app.klean.core.common.ZoneProvider
import app.klean.shared.time.toLocal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import android.util.Log
import androidx.annotation.StringRes
import app.klean.core.data.photo.PhotoStorage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.core.capture.voice.VoiceSession
import app.klean.core.capture.voice.VoiceUndo
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoveOption(val action: CandidateAction, @StringRes val labelRes: Int?)

sealed interface CapturePhase {
    data object Editing : CapturePhase
    data object Saving : CapturePhase
    data class Saved(@StringRes val moduleLabelRes: Int?) : CapturePhase
    /**
     * Saved, and other modules could also fit: offer to move it. Stays open longer. [options] are the router's
     * alternatives (chips); [more] are every other module behind one "Move to…" (the text matched nothing).
     */
    data class SavedWithAlternatives(
        @StringRes val moduleLabelRes: Int?,
        val options: List<MoveOption>,
        val more: List<MoveOption> = emptyList(),
    ) : CapturePhase
    data object NoModule : CapturePhase
}

data class CaptureUiState(
    /** What is in the field. Lives here so voice can fill it and Undo can put it back. */
    val text: String = "",
    val suggestions: List<String> = emptyList(),
    /** Stored photo file name attached to the next save, and its path for the thumbnail. */
    val photoFile: String? = null,
    val photoPath: String? = null,
    val phase: CapturePhase = CapturePhase.Editing,
    val startVoice: Boolean = false,
    /** Preset time for the entry (UTC ms), shown as "For around …"; null = now. */
    val eatenAt: Long? = null,
    val eatenHour: Int = 0,
    val eatenMinute: Int = 0,
    val eatenAtLabel: String = "",
    /** The sheet should close. */
    val finished: Boolean = false,
    /** Field placeholder: the holistic one, or the target module's own (e.g. "What did you eat?"). */
    val placeholderRes: Int = R.string.capture_placeholder,
    /** The optional calorie slot (Food with Show calories on): `300`, `200g`, `150x100x200`. */
    val calories: String = "",
    val showCalorieSlot: Boolean = false,
)

/** Whether diet's Show calories is on; the capture sheet then offers the calorie slot for Food. */
fun interface CalorieSlotSetting {
    fun observe(): kotlinx.coroutines.flow.Flow<Boolean>
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val service: UniversalCaptureService,
    private val registry: ModuleRegistry,
    private val clock: Clock,
    recognizer: VoiceRecognizer,
    private val photos: PhotoStorage,
    private val zone: ZoneProvider,
    private val savedState: SavedStateHandle,
    calorieSetting: CalorieSlotSetting = CalorieSlotSetting { kotlinx.coroutines.flow.flowOf(false) },
) : ViewModel() {
    private var caloriesOn = false
    private var target: ModuleId? = null
    private var source: CaptureSource = CaptureActivity.DEFAULT_SOURCE

    /** The module this sheet is aimed at, for the voice screen its mic opens. */
    val targetModule: ModuleId? get() = target

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var lastSaved: CaptureResult.Saved? = null
    private var closeJob: Job? = null
    private var suggestionsJob: Job? = null
    private var startVoicePending = false

    val voice = VoiceSession(
        scope = viewModelScope,
        recognizer = recognizer,
        save = { text -> save(text, viaVoice = true)?.recordId },
        undo = { u -> undoVoice(u) },
    )

    init {
        // CaptureActivity's intent extras (and anything start() kept across process death) are the saved state.
        restart(
            target = savedState.get<String>(CaptureActivity.EXTRA_TARGET_MODULE)?.let(::ModuleId),
            source = savedState.get<String>(CaptureActivity.EXTRA_SOURCE)
                ?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
                ?: CaptureActivity.DEFAULT_SOURCE,
            voice = savedState.get<Boolean>(CaptureActivity.EXTRA_START_VOICE) == true,
            text = savedState.get<String>(CaptureActivity.EXTRA_TEXT),
            eatenAt = savedState.get<Long>(CaptureActivity.EXTRA_EATEN_AT),
        )
        viewModelScope.launch {
            calorieSetting.observe().collect { on ->
                caloriesOn = on
                _state.update { it.copy(showCalorieSlot = slotFor(target)) }
            }
        }
    }

    private fun slotFor(target: ModuleId?) = caloriesOn && target == ModuleId.DIET

    fun onCaloriesChange(text: String) = _state.update { it.copy(calories = text) }

    /**
     * Aims the sheet: the one entry point for CaptureActivity (from its intent) and the in-app sheet (from the Add
     * menu or Today). [target] null = universal capture; [voice] starts listening once the sheet shows; [text]
     * prefills the field (never saved without Save); [eatenAt] presets the entry's time (UTC ms).
     */
    fun start(
        target: ModuleId?,
        source: CaptureSource,
        voice: Boolean = false,
        text: String? = null,
        eatenAt: Long? = null,
    ) {
        savedState[CaptureActivity.EXTRA_TARGET_MODULE] = target?.value
        savedState[CaptureActivity.EXTRA_SOURCE] = source.name
        savedState[CaptureActivity.EXTRA_START_VOICE] = voice
        savedState[CaptureActivity.EXTRA_TEXT] = text
        savedState[CaptureActivity.EXTRA_EATEN_AT] = eatenAt
        restart(target, source, voice, text, eatenAt)
    }

    private fun restart(target: ModuleId?, source: CaptureSource, voice: Boolean, text: String?, eatenAt: Long?) {
        this.target = target
        this.source = source
        closeJob?.cancel()
        // A photo attached to an unsaved entry would be left on disk with nothing pointing at it.
        if (lastSaved == null) _state.value.photoFile?.let(photos::delete)
        lastSaved = null
        startVoicePending = voice
        _state.value = CaptureUiState(
            text = text.orEmpty(),
            startVoice = voice,
            placeholderRes = target?.let { t -> registry.all.firstOrNull { it.id == t }?.capturePlaceholderRes }
                ?: R.string.capture_placeholder,
            showCalorieSlot = slotFor(target),
        )
        eatenAt?.let(::setEatenAt)
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            val s = service.suggestions(target)
            _state.update { it.copy(suggestions = s) }
        }
    }

    /** "Change" on the "For around …" line: same local day, new time. */
    fun onEatenTimePicked(hour: Int, minute: Int) {
        val current = _state.value.eatenAt ?: return
        val date = current.toLocal(zone.current()).date
        setEatenAt(LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone.current()).toEpochMilliseconds())
    }

    private fun setEatenAt(at: Long) {
        val tz = zone.current()
        val local = at.toLocal(tz)
        val zoneId = ZoneId.of(tz.id)
        val instant = java.time.Instant.ofEpochMilli(at)
        val sameDay = local.date == clock.now().toEpochMilliseconds().toLocal(tz).date
        val label = if (sameDay) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId).format(instant)
        } else {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zoneId).format(instant)
        }
        _state.update { it.copy(eatenAt = at, eatenHour = local.hour, eatenMinute = local.minute, eatenAtLabel = label) }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }

    /** True once if the sheet was opened from a mic button (widget); the sheet then starts listening. */
    fun consumeStartVoice(): Boolean = startVoicePending.also { startVoicePending = false }

    fun submit(text: String, viaVoice: Boolean = false) {
        val clean = begin(text) ?: return
        viewModelScope.launch { finish(clean, viaVoice) }
    }

    /** Moves to Saving synchronously, so a double tap saves once. Null when there is nothing to save. */
    private fun begin(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.phase != CapturePhase.Editing) return null
        _state.update { it.copy(phase = CapturePhase.Saving, text = clean) }
        return clean
    }

    private suspend fun save(text: String, viaVoice: Boolean): CaptureResult.Saved? =
        begin(text)?.let { finish(it, viaVoice) }

    private suspend fun finish(clean: String, viaVoice: Boolean): CaptureResult.Saved? {
        val input = CaptureInput(
            clean, source, clock.now().toEpochMilliseconds(),
            viaVoice = viaVoice, photoFile = _state.value.photoFile, targetModule = target, eatenAt = _state.value.eatenAt,
            extras = _state.value.calories.trim().takeIf { it.isNotEmpty() && _state.value.showCalorieSlot }
                ?.let { mapOf(app.klean.shared.diet.CalorieInput.EXTRA_KEY to it) }
                .orEmpty(),
        )
        return when (val r = service.submit(input)) {
            CaptureResult.NoModule -> {
                _state.update { it.copy(phase = CapturePhase.NoModule) }
                null
            }
            is CaptureResult.Saved -> r.also { onSaved(it) }
        }
    }

    private suspend fun undoVoice(u: VoiceUndo) {
        val saved = lastSaved?.takeIf { it.recordId == u.recordId } ?: return
        closeJob?.cancel()
        service.undo(saved)
        lastSaved = null
        _state.update { it.copy(phase = CapturePhase.Editing, text = u.text) }
    }

    fun newCaptureUri(): Pair<String, Uri> = photos.newCaptureUri()

    /** The camera finished. Normalises the full-size original into the store, then drops the original. */
    fun onPhotoTaken(name: String, uri: Uri, success: Boolean) {
        viewModelScope.launch {
            if (success) importAndAttach(uri)
            photos.delete(name)
        }
    }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch { importAndAttach(uri) }
    }

    fun removePhoto() {
        val name = _state.value.photoFile ?: return
        photos.delete(name)
        _state.update { it.copy(photoFile = null, photoPath = null) }
    }

    private suspend fun importAndAttach(uri: Uri) {
        runCatching { photos.import(uri) }
            .onSuccess { name ->
                _state.value.photoFile?.let(photos::delete)
                _state.update { it.copy(photoFile = name, photoPath = photos.file(name).path) }
            }
            .onFailure { Log.w("Klean", "Photo import failed", it) }
    }

    /** The "Move to…" menu is open: don't close under the person's finger; closed without a choice: close soon. */
    fun holdOpen(open: Boolean) {
        closeJob?.cancel()
        if (!open && lastSaved != null) {
            closeJob = viewModelScope.launch {
                delay(CONFIRM_MS)
                _state.update { it.copy(finished = true) }
            }
        }
    }

    fun moveTo(option: MoveOption) {
        val saved = lastSaved ?: return
        closeJob?.cancel()
        viewModelScope.launch {
            onSaved(service.correct(saved, option.action).copy(alternatives = emptyList(), others = emptyList()))
        }
    }

    private fun onSaved(r: CaptureResult.Saved) {
        lastSaved = r
        val label = labelOf(r.moduleId)
        val movable = r.alternatives.isNotEmpty() || r.others.isNotEmpty()
        val phase = if (!movable) {
            CapturePhase.Saved(label)
        } else {
            CapturePhase.SavedWithAlternatives(
                label,
                r.alternatives.map { MoveOption(it, labelOf(it.moduleId)) },
                r.others.map { MoveOption(it, labelOf(it.moduleId)) },
            )
        }
        _state.update { it.copy(phase = phase) }
        closeJob = viewModelScope.launch {
            delay(
                when {
                    r.input.viaVoice -> maxOf(VoiceSession.UNDO_MS, if (!movable) 0 else ALTERNATIVES_MS)
                    !movable -> CONFIRM_MS
                    else -> ALTERNATIVES_MS
                },
            )
            _state.update { it.copy(finished = true) }
        }
    }

    override fun onCleared() {
        voice.close()
        // A photo attached but never saved would otherwise stay on disk with nothing pointing at it.
        if (lastSaved == null || lastSaved?.input?.photoFile != _state.value.photoFile) {
            _state.value.photoFile?.let(photos::delete)
        }
    }

    private fun labelOf(id: ModuleId): Int? = registry.all.firstOrNull { it.id == id }?.labelRes

    companion object {
        const val CONFIRM_MS = 1_500L
        const val ALTERNATIVES_MS = 4_000L
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object CalorieSlotModule {
    @dagger.Provides
    fun calorieSlot(settings: app.klean.core.data.settings.SettingsRepository): CalorieSlotSetting =
        CalorieSlotSetting { settings.observe(app.klean.core.data.settings.SyncedKeys.DietShowCalories) }
}
