package app.klean.core.sync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.sync.R
import app.klean.core.ui.module.OnboardingStepProvider
import javax.inject.Inject

/** Setup page "Sync with your server" (order 810): optional; Klean works fully without it. */
class SyncOnboardingStep @Inject constructor() : OnboardingStepProvider {
    override val key = "core.sync"
    override val order = ORDER
    override val titleRes = R.string.sync_setup_title

    @Composable override fun Content() {
        val vm: SyncViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        Column { SyncPanel(state, vm, showDevices = false) }
    }

    companion object {
        const val ORDER = 810
    }
}
