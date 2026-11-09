package app.klean.feature.diet.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeliveryAppsState(
    val packages: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val loaded: Boolean = false,
)

/** Diet settings → Delivery apps: which apps count, and extra words meaning "it arrived" (synced). */
@HiltViewModel
class DeliveryAppsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<DeliveryAppsState> = combine(
        settings.observe(SyncedKeys.DietDeliveryApps),
        settings.observe(SyncedKeys.DietDeliveryMatchWords),
    ) { apps, words -> DeliveryAppsState(apps, words, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryAppsState())

    /** Adds a package name (e.g. `com.example.food`). False if it isn't one or is already listed. */
    fun addPackage(raw: String): Deferred<Boolean> = viewModelScope.async {
        val pkg = raw.trim()
        if (!PACKAGE.matches(pkg)) return@async false
        var added = false
        settings.update(SyncedKeys.DietDeliveryApps) { list -> if (pkg in list) list else list + pkg.also { added = true } }
        added
    }

    fun removePackage(pkg: String): Job = viewModelScope.launch {
        settings.update(SyncedKeys.DietDeliveryApps) { it - pkg }
    }

    /** Adds a phrase; matching ignores case. False if blank or already listed. */
    fun addWord(raw: String): Deferred<Boolean> = viewModelScope.async {
        val word = raw.trim()
        if (word.isEmpty()) return@async false
        var added = false
        settings.update(SyncedKeys.DietDeliveryMatchWords) { list ->
            if (list.any { it.equals(word, ignoreCase = true) }) list else list + word.also { added = true }
        }
        added
    }

    fun removeWord(word: String): Job = viewModelScope.launch {
        settings.update(SyncedKeys.DietDeliveryMatchWords) { it - word }
    }

    private companion object {
        val PACKAGE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
    }
}
