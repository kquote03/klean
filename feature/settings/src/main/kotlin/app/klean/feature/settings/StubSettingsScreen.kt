package app.klean.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.klean.core.design.component.KleanScreen
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Settings sub-screen: [KleanScreen] with a title and a back arrow, no Add button. [content] gets the bar's
 * height above and room below; rows keep their own side padding, as before.
 */
@Composable
internal fun SettingsSubScaffold(
    @StringRes title: Int,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    KleanScreen(title = stringResource(title), onBack = onBack, showAdd = false) { pad ->
        content(Modifier.padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()))
    }
}

@Composable
fun StubSettingsScreen(@StringRes title: Int, onBack: () -> Unit) {
    SettingsSubScaffold(title, onBack) { modifier -> Box(modifier) }
}
