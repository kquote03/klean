package app.klean.today

import android.content.Context
import app.klean.R
import app.klean.core.common.ZoneProvider
import app.klean.core.notifications.QuietHoursService
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.SummaryItem
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone

/** One "Quiet hours · until 8:00 AM" tile while quiet hours are on, otherwise nothing. */
fun quietTile(quiet: QuietHours, nowMs: Long, tz: TimeZone, label: String, until: (Long) -> String): List<SummaryItem> =
    if (quiet.contains(nowMs, tz)) listOf(SummaryItem(QuietHoursSummary.KEY, label, until(quiet.nextAllowed(nowMs, tz)))) else emptyList()

/**
 * App-owned tile on Today: shown only during quiet hours, so it explains why no check-ins arrive.
 * Owned by [ModuleId.CORE], which is never switched off, and sorted before the modules' tiles.
 */
class QuietHoursSummary @Inject constructor(
    private val quiet: QuietHoursService,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val ticker: MinuteTicker,
    @ApplicationContext private val ctx: Context,
) : DashboardSummaryProvider {
    override val moduleId: ModuleId = ModuleId.CORE

    override fun summaries(): Flow<List<SummaryItem>> =
        combine(ticker.ticks(), quiet.quietHours) { _, hours ->
            val tz = zone.current()
            quietTile(hours, clock.now().toEpochMilliseconds(), tz, ctx.getString(R.string.today_quiet_label)) { end ->
                ctx.getString(R.string.today_quiet_value, time(end, tz))
            }
        }.distinctUntilChanged()

    private fun time(epochMs: Long, tz: TimeZone): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(tz.id)))

    companion object {
        const val KEY = "core.quiet"
    }
}
