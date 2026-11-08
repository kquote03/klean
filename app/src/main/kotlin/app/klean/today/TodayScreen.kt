package app.klean.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.R
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.ui.module.ModuleRegistry

/** Today dashboard. Lives in :app until it grows enough to justify :feature:today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(registry: ModuleRegistry, onOpen: (Any) -> Unit) {
    val cards by registry.dashboardCards.collectAsStateWithLifecycle(initialValue = emptyList())
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
                // Universal capture is wired in M4; shown disabled so the layout is final.
                CaptureField(
                    value = "",
                    onValueChange = {},
                    onSubmit = {},
                    onMic = {},
                    onPhoto = null,
                    placeholder = stringResource(R.string.today_capture_placeholder),
                    enabled = false,
                )
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
