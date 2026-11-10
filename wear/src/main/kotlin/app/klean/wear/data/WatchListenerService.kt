package app.klean.wear.data

import android.content.ComponentName
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import app.klean.wear.complication.FoodLogComplicationService
import app.klean.wear.tile.NextUpTileService
import app.klean.wear.tile.QuickLogTileService
import app.klean.wear.tile.TodayFoodTileService
import app.klean.wear.tile.WorkoutTileService
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** What the watch does with what the phone sends. Separate from the service so tests can call it. */
object WatchInbox {
    /** A new [WatchState]: store it, then refresh every tile and the complication. Returns false if ignored. */
    fun onState(ctx: Context, bytes: ByteArray): Boolean {
        if (!WatchGraph.store(ctx).save(bytes)) return false
        refreshSurfaces(ctx)
        return true
    }

    /** Messages from the phone. Only the alarm ping is expected; anything else is ignored. */
    fun onMessage(ctx: Context, path: String, bytes: ByteArray): WatchMessage? {
        val m = WearCodec.decode(path, bytes) as? WatchMessage.Alarm ?: return null
        if (m.ringing) vibrate(ctx)
        return m
    }

    fun refreshSurfaces(ctx: Context) {
        listOf(QuickLogTileService::class.java, TodayFoodTileService::class.java, NextUpTileService::class.java, WorkoutTileService::class.java)
            .forEach { runCatching { TileService.getUpdater(ctx).requestUpdate(it) } }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, FoodLogComplicationService::class.java)).requestUpdateAll()
        }
    }

    private fun vibrate(ctx: Context) {
        val v = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        // Three long pulses: the phone is ringing an alarm.
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1))
    }
}

/** Data Layer entry point on the watch (manifest filters on `/klean`). */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearPaths.STATE) {
                e.dataItem.data?.let { WatchInbox.onState(this, it) }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        WatchInbox.onMessage(this, event.path, event.data)
    }
}
