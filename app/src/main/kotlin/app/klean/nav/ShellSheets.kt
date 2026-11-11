package app.klean.nav

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.klean.core.capture.CaptureLauncher
import app.klean.core.capture.CaptureSheet
import app.klean.core.capture.LocalCaptureLauncher
import app.klean.core.capture.CaptureViewModel
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId

/** The capture sheet to show in the app: aimed at [target] (null = anything), optionally listening at once. */
data class CaptureRequest(
    val target: ModuleId?,
    val voice: Boolean = false,
    val source: CaptureSource = CaptureSource.DASHBOARD,
    /** The entry's preset time (UTC ms), e.g. the middle of a diet gap; null = now. */
    val eatenAt: Long? = null,
)

/**
 * Sheets the shell shows over any screen, in the app's own activity (plan 2026-11-11, decision 5). Adding from inside
 * the app used to start CaptureActivity, a separate task, so Back was a cross-task return that predictive back
 * draws as the whole window zooming out. CaptureActivity stays for outside entry points only (tile, widget,
 * notification, shortcuts, deep links, the watch).
 */
@Stable
class ShellSheets {
    var capture by mutableStateOf<CaptureRequest?>(null)

    /** What module screens get through [LocalCaptureLauncher]: the sheet in place. */
    val launcher = CaptureLauncher { target, source, voice, eatenAt -> capture = CaptureRequest(target, voice, source, eatenAt) }
}

/**
 * The real capture sheet over [request]: a fresh [CaptureViewModel] per request in its own store, cleared when the
 * sheet closes (so an unsaved photo is cleaned up and no view model outlives its sheet).
 */
@Composable
fun InAppCaptureSheet(request: CaptureRequest, onDismiss: () -> Unit, onVoice: ((ModuleId?) -> Unit)? = null) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current)
    val owner = remember(request) { ScopedViewModelOwner { parent } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val vm = hiltViewModel<CaptureViewModel>(owner)
    LaunchedEffect(vm, request) { vm.start(request.target, request.source, request.voice, eatenAt = request.eatenAt) }
    CaptureSheet(vm = vm, onDismiss = onDismiss, onVoice = onVoice)
}

/** Shows [ShellSheets.capture] with [sheet]; Back closes it (and only it) even if the sheet doesn't take Back itself. */
@Composable
internal fun ShellSheetsHost(sheets: ShellSheets, sheet: @Composable (CaptureRequest, () -> Unit) -> Unit) {
    val request = sheets.capture ?: return
    val dismiss = { sheets.capture = null }
    BackHandler(onBack = dismiss)
    sheet(request, dismiss)
}

/**
 * A view-model store of its own (one sheet, one pager page), created through [parent]'s (the activity's Hilt) factory
 * with no intent arguments. [parent] is read on use, so a holder that outlives an activity never keeps the old one.
 */
internal class ScopedViewModelOwner(private val parent: () -> ViewModelStoreOwner) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (parent() as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras((parent() as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras).apply {
            set(VIEW_MODEL_STORE_OWNER_KEY, this@ScopedViewModelOwner)
            // The main activity's intent isn't a capture request or a page's arguments.
            set(DEFAULT_ARGS_KEY, Bundle())
        }
}
