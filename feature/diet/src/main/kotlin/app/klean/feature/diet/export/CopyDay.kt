package app.klean.feature.diet.export

import android.content.ClipData
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import app.klean.feature.diet.R
import kotlinx.coroutines.launch

/**
 * Puts text on the clipboard. Below Android 13 a quiet "Copied" snackbar confirms it; from 13 on the system
 * shows its own confirmation, so Klean adds nothing.
 */
@Composable
fun rememberCopyText(snackbar: SnackbarHostState?): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.diet_copied)
    return { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Klean food", text)))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar?.showSnackbar(copied)
        }
    }
}
