package app.klean.feature.sleep.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import app.klean.core.common.ZoneProvider
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.InstantResult
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.sleep.R
import app.klean.feature.sleep.data.SleepMath
import app.klean.feature.sleep.data.SleepRepository
import app.klean.feature.sleep.ui.SleepFormatter
import app.klean.feature.sleep.ui.SleepTicker
import app.klean.shared.module.ModuleId
import app.klean.shared.time.DayWindows
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Sleep on Today's whiteboard: last night ("Slept 7 h 20 m", "11:40 PM – 7:00 AM") in "so far today", and the
 * one-tap button that acts at once. Its label says what a tap does: "Going to bed", or "I'm up" while a night is
 * open; the result comes back as a snackbar with Undo.
 */
@Singleton
class SleepGlanceProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<SleepRepository>,
    private val schedule: dagger.Lazy<SleepScheduleRepository>,
    private val fmt: SleepFormatter,
    private val ticker: SleepTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.SLEEP

    override fun glance(): Flow<GlanceContribution> =
        combine(ticker.ticks(), schedule.get().quietHours, repo.get().observeSessions()) { now, q, sessions ->
            val open = sessions.firstOrNull { it.wakeAt == null && !it.sync.deleted }
            val last = if (open != null) null else SleepMath.lastNight(sessions, DayWindows.logicalDate(now, q, zone.current()), q, zone.current())
            GlanceContribution(
                done = listOfNotNull(
                    last?.let {
                        GlanceRow(
                            key = "sleep:${it.id}",
                            module = moduleId,
                            text = ctx.getString(R.string.sleep_glance_slept, fmt.duration(it.wakeAt!! - it.bedAt)),
                            at = it.wakeAt,
                            detail = fmt.range(it),
                            icon = Icons.Rounded.Bedtime,
                            route = LINK,
                        )
                    },
                ),
                quickAction = QuickAction.Instant(
                    moduleId,
                    ctx.getString(if (open != null) R.string.sleep_im_up else R.string.sleep_quick_bed),
                    Icons.Rounded.Bedtime,
                    run = ::toggle,
                ),
            )
        }

    /** Bed or wake now. Undo removes the new night, or reopens the one just closed. */
    private suspend fun toggle(): InstantResult? = runCatching {
        val r = repo.get()
        val t = r.toggle(clock.now().toEpochMilliseconds())
        if (t.asleep) {
            InstantResult(ctx.getString(R.string.sleep_glance_good_night)) { r.delete(t.id) }
        } else {
            val s = r.get(t.id) ?: return@runCatching null
            InstantResult(ctx.getString(R.string.sleep_glance_morning, fmt.duration((s.wakeAt ?: s.bedAt) - s.bedAt))) { r.update(s.id, s.bedAt, null) }
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    companion object {
        const val LINK = "klean://sleep"
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class SleepGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun sleepGlanceProvider(p: SleepGlanceProvider): TodayGlanceProvider
}
