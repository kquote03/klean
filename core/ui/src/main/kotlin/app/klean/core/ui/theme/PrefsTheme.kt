package app.klean.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.theme.KleanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * [KleanTheme] with the user's "Use wallpaper colours" choice from [DevicePrefs]. The first value is
 * read once up front (a tiny DataStore read) so a Material You user never sees a pastel flash.
 */
@Composable
fun KleanThemeFromPrefs(prefs: DevicePrefs, content: @Composable () -> Unit) {
    val initial = remember(prefs) { runBlocking { prefs.dynamicColor.first() } }
    val dynamic by remember(prefs) { prefs.dynamicColor }.collectAsState(initial)
    KleanTheme(dynamicColor = dynamic, content = content)
}
