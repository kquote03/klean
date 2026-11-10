package app.klean.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import android.graphics.drawable.Icon
import app.klean.wear.R
import app.klean.wear.data.WatchGraph
import app.klean.wear.ui.WatchCaptureActivity

/** One-tap food log: shows today's entry count; tapping opens the watch capture for Diet. */
class FoodLogComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val s = WatchGraph.store(this).load()
        return data(if (s.dietOn) s.todayFood.size else null)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) data(3) else null

    /** [count] null = Diet is off on the phone: a plain "Klean" capture shortcut. */
    private fun data(count: Int?): ComplicationData {
        val text = count?.toString() ?: getString(R.string.complication_capture)
        val desc = count?.let { resources.getQuantityString(R.plurals.complication_food_desc, it, it) } ?: getString(R.string.complication_capture_desc)
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, WatchCaptureActivity::class.java).apply { if (count != null) putExtra(WatchCaptureActivity.EXTRA_MODULE, "diet") },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return ShortTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), PlainComplicationText.Builder(desc).build())
            .setTitle(PlainComplicationText.Builder(getString(R.string.complication_title)).build())
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_food)).build())
            .setTapAction(tap)
            .build()
    }
}
