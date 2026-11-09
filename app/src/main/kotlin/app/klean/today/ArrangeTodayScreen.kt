package app.klean.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.ui.module.ModuleRegistry

/** "Arrange Today": arrow buttons rather than drag, so every move is one labelled tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeTodayScreen(
    registry: ModuleRegistry,
    layout: TodayLayoutStore,
    onDone: () -> Unit,
    vm: ArrangeTodayViewModel = viewModel { ArrangeTodayViewModel(registry, layout) },
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_arrange)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.today_arrange_back))
                    }
                },
                actions = { TextButton(onClick = onDone) { Text(stringResource(R.string.today_arrange_done)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(rows, key = { _, r -> r.module.id.value }) { i, row ->
                val name = stringResource(row.module.labelRes)
                val id = row.module.id.value
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    IconButton(onClick = { vm.moveUp(id) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.today_arrange_up, name))
                    }
                    IconButton(onClick = { vm.moveDown(id) }, enabled = i < rows.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.today_arrange_down, name))
                    }
                    val showLabel = stringResource(R.string.today_arrange_show, name)
                    Switch(
                        checked = row.visible,
                        onCheckedChange = { vm.setVisible(id, it) },
                        modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = showLabel },
                    )
                }
            }
        }
    }
}
