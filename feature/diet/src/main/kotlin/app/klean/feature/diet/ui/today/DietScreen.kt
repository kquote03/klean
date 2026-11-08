package app.klean.feature.diet.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.dietRows

@Composable
fun DietScreen(onOpenEntry: (String) -> Unit, vm: DietViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DietScreenContent(state, onSubmit = vm::onSubmit, onRepeat = vm::onRepeat, onOpenEntry = onOpenEntry)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DietScreenContent(
    state: DietUiState,
    onSubmit: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.diet_today_title)) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Pinned under the top bar: logging is always one action away.
            CaptureField(
                value = text,
                onValueChange = { text = it },
                onSubmit = {
                    onSubmit(text)
                    text = ""
                },
                onMic = {}, // Voice input arrives in M6.
                onPhoto = null,
                placeholder = stringResource(R.string.diet_capture_placeholder),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
            )
            if (state.repeats.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.repeats.forEach { r -> SuggestionChip(onClick = { onRepeat(r) }, label = { Text(r) }) }
                }
            }
            if (state.loaded && state.rows.none { it is DietRow.Entry }) {
                NeutralEmpty(stringResource(R.string.diet_today_empty))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                dietRows(state.rows, onOpenEntry = onOpenEntry, onGapAdd = { focus.requestFocus() })
            }
        }
    }
}
