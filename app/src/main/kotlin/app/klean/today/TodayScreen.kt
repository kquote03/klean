package app.klean.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.ui.module.ModuleRegistry
import kotlin.time.Clock

/** Today dashboard. Lives in :app until it grows enough to justify :feature:today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    registry: ModuleRegistry,
    capture: UniversalCaptureService,
    clock: Clock,
    onOpen: (Any) -> Unit,
    vm: TodayViewModel = viewModel { TodayViewModel(capture, clock) },
) {
    val cards by registry.dashboardCards.collectAsStateWithLifecycle(initialValue = emptyList())
    val available by vm.captureAvailable.collectAsStateWithLifecycle()
    val justSaved by vm.justSaved.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_today)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    CaptureField(
                        value = text,
                        onValueChange = { text = it },
                        onSubmit = {
                            val submitted = text
                            vm.submit(submitted) { saved -> if (saved && text == submitted) text = "" }
                        },
                        onMic = {}, // Voice input arrives in M6.
                        onPhoto = null,
                        placeholder = stringResource(R.string.today_capture_placeholder),
                        enabled = available,
                    )
                    if (justSaved) {
                        Text(
                            stringResource(R.string.today_capture_saved),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        )
                    }
                }
            }
            if (cards.isEmpty()) {
                item { NeutralEmpty(stringResource(R.string.today_empty)) }
            } else {
                items(cards, key = { it.id.value }) { module ->
                    module.DashboardCard(onOpen = { onOpen(module.startRoute) }, modifier = Modifier)
                }
            }
        }
    }
}
