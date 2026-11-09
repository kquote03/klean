package app.klean.core.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.StringRes
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What the tile shows for the set of enabled capture modules. */
data class CaptureTileModel(val available: Boolean, @StringRes val labelRes: Int) {
    companion object {
        /** Always "Add to Klean": the tile is the holistic capture entry, whichever modules are on. */
        fun from(enabled: List<ModuleId>): CaptureTileModel = CaptureTileModel(enabled.isNotEmpty(), R.string.tile_label)
    }
}

/** Quick Settings tile: one tap opens the capture sheet over whatever is on screen. */
@AndroidEntryPoint
class CaptureTileService : TileService() {
    @Inject lateinit var capture: UniversalCaptureService

    private val scope = MainScope()
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            capture.availableHandlers.collect { hs -> render(CaptureTileModel.from(hs.map { it.moduleId })) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(model: CaptureTileModel) {
        val tile = qsTile ?: return
        tile.state = if (model.available) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        tile.label = getString(model.labelRes)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (qsTile?.state == Tile.STATE_UNAVAILABLE) return
        if (isLocked) unlockAndRun(::openSheet) else openSheet()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSheet() {
        val intent = CaptureActivity.intent(this, CaptureSource.QS_TILE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to re-bind the tile so it re-renders, e.g. after a module was switched off. */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, CaptureTileService::class.java))
            } catch (e: RuntimeException) {
                Log.w("Klean", "tile update request failed", e)
            }
        }
    }
}
