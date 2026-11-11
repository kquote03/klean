package app.klean.feature.calendar.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.calendar.CalendarRoutes
import app.klean.feature.calendar.R
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

/**
 * Calendar on Today's whiteboard (Klean's events and the phone's):
 * - **attention:** what is on now ("Until 3:30 PM") or starts within [SOON_MS] ("Starts in 20 min");
 * - **coming up:** the rest of today's events with their start and end (all-day ones too);
 * - **so far today:** the events that have ended.
 * Built in [CalendarGlanceBindings] from the same agenda as the Calendar screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarGlanceProvider(
    private val ctx: Context,
    private val agenda: AgendaSource,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val text: CalendarText,
    private val ticker: CalendarTicker,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.CALENDAR

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone.current()).date

    override fun glance(): Flow<GlanceContribution> = ticker.ticks().map { today() }.distinctUntilChanged()
        .flatMapLatest { d -> combine(agenda.items(d, d), ticker.ticks()) { items, _ -> build(d, items) } }
        .distinctUntilChanged()

    private fun build(day: LocalDate, items: List<AgendaItem>): GlanceContribution {
        val now = clock.now().toEpochMilliseconds()
        val events = items.filter { (it.kind == AgendaKind.EVENT || it.kind == AgendaKind.DEVICE) && it.isOn(day) }
        val link = CalendarRoutes.dayLink(day)
        fun row(e: AgendaItem, at: Long?, detail: String?) =
            GlanceRow("calendar:${e.key}", moduleId, e.title, at, detail, Icons.Rounded.Event, link)
        val timed = events.filter { !it.allDay }
        val now0 = timed.filter { it.start <= now && it.end > now }
        val soon = timed.filter { it.start > now && it.start - now <= SOON_MS }
        val later = timed.filter { it.start - now > SOON_MS }
        val ended = timed.filter { it.end <= now }
        return GlanceContribution(
            attention = now0.map { row(it, it.start, ctx.getString(R.string.calendar_glance_until, text.time(it.end))) } +
                soon.map { row(it, it.start, ctx.getString(R.string.calendar_glance_starts_in, ((it.start - now + MINUTE_MS - 1) / MINUTE_MS).toInt())) },
            upcoming = events.filter { it.allDay }.map { row(it, null, text.allDay()) } +
                later.map { row(it, it.start, ctx.getString(R.string.calendar_glance_range, text.time(it.start), text.time(it.end))) },
            done = ended.map { row(it, it.start, ctx.getString(R.string.calendar_glance_range, text.time(it.start), text.time(it.end))) },
            quickAction = QuickAction.Capture(moduleId, ctx.getString(R.string.calendar_glance_quick), Icons.Rounded.EventAvailable),
        )
    }

    companion object {
        private const val MINUTE_MS = 60_000L
        const val SOON_MS = 30 * MINUTE_MS
    }
}

/** Puts Calendar on Today's whiteboard, from the same agenda as the Calendar screen. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object CalendarGlanceBindings {
    @dagger.Provides @dagger.multibindings.IntoSet
    fun glance(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context,
        agenda: AgendaSource,
        clock: Clock,
        zone: ZoneProvider,
        text: CalendarText,
        ticker: CalendarTicker,
    ): TodayGlanceProvider = CalendarGlanceProvider(ctx, agenda, clock, zone, text, ticker)
}
