package app.klean.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpen: (Any) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding))
    }
}

/** Registers the settings sub-screens. Filled in by Task 1.10. */
fun NavGraphBuilder.settingsGraph(nav: NavController) {}
