package app.klean.core.sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.entity.DeviceEntity
import app.klean.core.data.device.DeviceRepository
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.sync.PairOutcome
import app.klean.core.sync.SyncAccounts
import app.klean.core.sync.SyncScheduler
import app.klean.core.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val status: SyncStatus = SyncStatus(),
    val link: String = "",
    val busy: Boolean = false,
    /** The last pairing attempt's result, until the link is edited. */
    val outcome: PairOutcome? = null,
    val devices: List<DeviceEntity> = emptyList(),
    val thisDevice: String? = null,
    val confirmUnpair: Boolean = false,
)

/** Settings › Sync and the setup page. */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val accounts: SyncAccounts,
    private val scheduler: SyncScheduler,
    devices: DeviceRepository,
    prefs: DevicePrefs,
) : ViewModel() {
    private val local = MutableStateFlow(SyncUiState())

    val state: StateFlow<SyncUiState> = combine(
        local, accounts.status, devices.observeAll(), flow { emit(prefs.deviceId()) },
    ) { l, s, d, me -> l.copy(status = s, devices = d, thisDevice = me) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun setLink(v: String) = local.update { it.copy(link = v, outcome = null) }

    /** A scanned QR code: pair straight away. */
    fun scanned(v: String) {
        setLink(v)
        pair()
    }

    fun pair() {
        val text = local.value.link
        if (local.value.busy) return
        local.update { it.copy(busy = true, outcome = null) }
        viewModelScope.launch {
            val o = accounts.pair(text)
            local.update { it.copy(busy = false, outcome = o, link = if (o == PairOutcome.PAIRED) "" else it.link) }
        }
    }

    fun syncNow() = scheduler.syncNow()

    fun askUnpair(v: Boolean) = local.update { it.copy(confirmUnpair = v) }

    fun unpair() {
        local.update { it.copy(confirmUnpair = false, busy = true) }
        viewModelScope.launch {
            accounts.unpair()
            local.update { it.copy(busy = false, outcome = null) }
        }
    }
}
