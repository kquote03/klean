package app.klean.feature.diet.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.RemovedEntrySnackbar
import app.klean.feature.diet.ui.dietRows
import app.klean.feature.diet.ui.today.DietRow

/**
 * Swipe between days, newest on the right, up to today. Each page reuses Today's row list,
 * gaps included, so a past day looks exactly like it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietDayScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddForGap: (DietRow.GapRow) -> Unit,
    backStackEntry: NavBackStackEntry? = null,
    vm: DietDayViewModel = hiltViewModel(),
) {
    val pager = rememberPagerState(initialPage = vm.initialPage) { vm.pageCount }
    val snackbar = remember { SnackbarHostState() }
    backStackEntry?.let { RemovedEntrySnackbar(it, snackbar, onUndo = vm::undoRemove) }
    val current = vm.dateForPage(pager.currentPage)
    val titleFlow = remember(current) { vm.day(current) }
    val titleState by titleFlow.collectAsState(initial = null)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titleState?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.padding(padding).fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val date = vm.dateForPage(page)
            val flow = remember(date) { vm.day(date) }
            val state by flow.collectAsState(initial = null)
            val s = state
            Column(Modifier.fillMaxSize()) {
                if (s != null && s.loaded && !s.hasEntries) NeutralEmpty(stringResource(R.string.diet_day_empty))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    dietRows(s?.rows.orEmpty(), onOpenEntry = onOpenEntry, onGapAdd = onAddForGap)
                }
            }
        }
    }
}
