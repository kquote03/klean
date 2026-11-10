package app.klean.wear.tile

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.klean.wear.data.WatchGraph
import app.klean.wear.data.WatchState
import com.google.common.util.concurrent.ListenableFuture
import java.time.ZoneId

/** Shared plumbing: every Klean tile renders the stored [WatchState] with no network and no waiting. */
abstract class KleanTileService : TileService() {
    abstract fun layout(ctx: Context, s: WatchState): LayoutElement

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val s = WatchGraph.store(this).load()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(PAD)).setEnd(dp(PAD)).setTop(dp(PAD)).setBottom(dp(PAD)).build())
                    .build(),
            )
            .addContent(layout(this, s))
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun <T> done(v: T): ListenableFuture<T> = ResolvableFuture.create<T>().also { it.set(v) }

    companion object {
        const val RES_VERSION = "1"
        /** The phone pushes changes; this is only a safety refresh (times on the Today tile). */
        const val FRESHNESS_MS = 15 * 60_000L
        private const val PAD = 14f
    }
}

class QuickLogTileService : KleanTileService() {
    override fun layout(ctx: Context, s: WatchState) = TileLayouts.quickLog(ctx, s)
}

class TodayFoodTileService : KleanTileService() {
    override fun layout(ctx: Context, s: WatchState) = TileLayouts.todayFood(ctx, s, ZoneId.systemDefault())
}

class NextUpTileService : KleanTileService() {
    override fun layout(ctx: Context, s: WatchState) = TileLayouts.nextUp(ctx, s, ZoneId.systemDefault())
}

class WorkoutTileService : KleanTileService() {
    override fun layout(ctx: Context, s: WatchState) = TileLayouts.workouts(ctx, s)
}
