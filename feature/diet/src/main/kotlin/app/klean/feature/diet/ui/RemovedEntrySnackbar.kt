package app.klean.feature.diet.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import app.klean.feature.diet.R
import kotlinx.coroutines.withTimeoutOrNull

/** Back-stack key the entry screen sets on the screen below it after a delete. */
const val REMOVED_ENTRY_KEY = "diet.removedEntryId"

/** Pops the entry screen and tells the list below which entry was removed, so it can offer Undo. */
fun NavController.popAfterRemove(entryId: String) {
    previousBackStackEntry?.savedStateHandle?.set(REMOVED_ENTRY_KEY, entryId)
    popBackStack()
}

private const val UNDO_WINDOW_MS = 5_000L

/** Shows "Entry removed · Undo" for 5 s when the entry screen above just deleted something. */
@Composable
fun RemovedEntrySnackbar(backStackEntry: NavBackStackEntry, snackbar: SnackbarHostState, onUndo: (String) -> Unit) {
    val handle = backStackEntry.savedStateHandle
    val removed by handle.getStateFlow<String?>(REMOVED_ENTRY_KEY, null).collectAsStateWithLifecycle()
    val message = stringResource(R.string.diet_entry_deleted)
    val undo = stringResource(R.string.diet_undo)
    val onUndoNow by rememberUpdatedState(onUndo)
    LaunchedEffect(removed) {
        val id = removed ?: return@LaunchedEffect
        handle[REMOVED_ENTRY_KEY] = null
        val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
            snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) onUndoNow(id)
    }
}
