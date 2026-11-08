package app.klean.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Settings sub-screen with a title and back button only. Shared top bar for real screens too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubScaffold(
    @StringRes title: Int,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun StubSettingsScreen(@StringRes title: Int, onBack: () -> Unit) {
    SettingsSubScaffold(title, onBack) { modifier -> Box(modifier) }
}
