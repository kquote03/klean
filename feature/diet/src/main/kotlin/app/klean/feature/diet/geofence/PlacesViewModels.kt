package app.klean.feature.diet.geofence

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.place.Place
import app.klean.core.data.place.PlaceDraft
import app.klean.core.data.place.PlaceRepository
import app.klean.feature.diet.BuildConfig
import app.klean.feature.diet.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class LatLng(val latitude: Double, val longitude: Double)

/** "Use my current location". An interface so the edit screen is tested without GPS. */
fun interface CurrentLocation {
    /** Null when there's no fix (no permission, location off, or nothing within the timeout). */
    suspend fun get(): LatLng?
}

/** One fresh fix from GPS or the network, falling back to the last known one. No Play services. */
class AndroidCurrentLocation @Inject constructor(@ApplicationContext private val ctx: Context) : CurrentLocation {
    @SuppressLint("MissingPermission") // the screen asks for the permission before calling
    override suspend fun get(): LatLng? {
        if (!ProximityAlertBackend.hasFineLocation(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val fresh = if (Build.VERSION.SDK_INT >= 30) {
            providers.firstNotNullOfOrNull { p ->
                withTimeoutOrNull(FIX_TIMEOUT_MS / providers.size.coerceAtLeast(1)) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching { lm.getCurrentLocation(p, signal, ctx.mainExecutor) { cont.resume(it) } }
                            .onFailure { cont.resume(null) }
                    }
                }
            }
        } else {
            null
        }
        val loc = fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return loc?.let { LatLng(it.latitude, it.longitude) }
    }

    companion object {
        const val FIX_TIMEOUT_MS = 20_000L
    }
}

data class PlacesUiState(
    val places: List<Place> = emptyList(),
    val loaded: Boolean = false,
    /** Debug builds show "Simulate leaving" per place, which runs the real exit path. */
    val debug: Boolean = false,
    /** Debug: the last simulated exit, place name to whether it asked. */
    val simulated: Pair<String, Boolean>? = null,
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val places: PlaceRepository,
    private val registrar: GeofenceRegistrar,
    private val exits: GeofenceExitHandler,
) : ViewModel() {
    private val simulated = MutableStateFlow<Pair<String, Boolean>?>(null)

    val state: StateFlow<PlacesUiState> = combine(places.observeAll(), simulated) { list, sim ->
        PlacesUiState(list, loaded = true, debug = BuildConfig.DEBUG, simulated = sim)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlacesUiState())

    fun setEnabled(id: String, enabled: Boolean): Job = viewModelScope.launch {
        places.setEnabled(id, enabled)
        registrar.reschedule()
    }

    /** After returning from a permission dialog or the system settings. */
    fun rewatch(): Job = viewModelScope.launch { registrar.reschedule() }

    /** Debug: the system says the user just left [place] (skips only the location part). */
    fun simulateLeaving(place: Place): Job = viewModelScope.launch {
        val asked = exits.onTransition(place.id, entering = false)
        simulated.value = place.label to asked
    }
}

data class PlaceEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val label: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusM: Int = PlaceDraft.DEFAULT_RADIUS_M,
    val enabled: Boolean = true,
    val locating: Boolean = false,
    @param:StringRes val note: Int? = null,
    /** Saved or deleted: the screen closes. */
    val done: Boolean = false,
) {
    val canSave: Boolean get() = label.isNotBlank() && parseCoordinates(latitude, longitude) != null

    companion object {
        fun parseCoordinates(lat: String, lng: String): LatLng? {
            val a = lat.trim().replace(',', '.').toDoubleOrNull() ?: return null
            val b = lng.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (a !in -90.0..90.0 || b !in -180.0..180.0) return null
            return LatLng(a, b)
        }
    }
}

@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val places: PlaceRepository,
    private val registrar: GeofenceRegistrar,
    private val location: CurrentLocation,
) : ViewModel() {
    private val id: String? = savedState.get<String>("placeId")
    private val _state = MutableStateFlow(PlaceEditUiState(isNew = id == null))
    val state: StateFlow<PlaceEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = id?.let { places.get(it) }
            _state.update {
                if (p == null) {
                    it.copy(loaded = true)
                } else {
                    it.copy(
                        loaded = true, label = p.label, latitude = p.latitude.toString(), longitude = p.longitude.toString(),
                        radiusM = p.radiusM, enabled = p.enabled,
                    )
                }
            }
        }
    }

    fun onLabel(v: String) = _state.update { it.copy(label = v) }
    fun onLatitude(v: String) = _state.update { it.copy(latitude = v, note = null) }
    fun onLongitude(v: String) = _state.update { it.copy(longitude = v, note = null) }
    fun onRadius(v: Int) = _state.update { it.copy(radiusM = v.coerceIn(PlaceDraft.MIN_RADIUS_M, PlaceDraft.MAX_RADIUS_M)) }
    fun onEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }

    /** Call once location permission is granted. */
    fun useCurrentLocation(): Job = viewModelScope.launch {
        _state.update { it.copy(locating = true, note = null) }
        val fix = location.get()
        _state.update {
            if (fix == null) {
                it.copy(locating = false, note = R.string.places_location_unavailable)
            } else {
                it.copy(locating = false, latitude = "%.6f".format(java.util.Locale.ROOT, fix.latitude), longitude = "%.6f".format(java.util.Locale.ROOT, fix.longitude))
            }
        }
    }

    fun onLocationDenied() = _state.update { it.copy(note = R.string.places_location_denied) }

    fun save(): Job = viewModelScope.launch {
        val s = _state.value
        val at = PlaceEditUiState.parseCoordinates(s.latitude, s.longitude)
        if (s.label.isBlank() || at == null) {
            _state.update { it.copy(note = R.string.places_needs_name_and_spot) }
            return@launch
        }
        places.save(id, PlaceDraft(s.label, at.latitude, at.longitude, s.radiusM, s.enabled))
        registrar.reschedule()
        _state.update { it.copy(done = true) }
    }

    fun delete(): Job = viewModelScope.launch {
        id?.let { places.delete(it) }
        registrar.reschedule()
        _state.update { it.copy(done = true) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CurrentLocationModule {
    @Binds abstract fun location(impl: AndroidCurrentLocation): CurrentLocation
}
