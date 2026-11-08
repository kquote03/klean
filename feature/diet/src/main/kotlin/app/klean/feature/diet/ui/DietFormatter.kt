package app.klean.feature.diet.ui

import android.content.Context
import app.klean.core.common.ZoneProvider
import app.klean.feature.diet.R
import app.klean.shared.diet.Gap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/** Turns times and gaps into display text in the device zone and locale. */
interface DietFormatter {
    fun time(epochMs: Long): String
    fun date(date: LocalDate): String
    fun gap(gap: Gap): String
}

class AndroidDietFormatter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val zone: ZoneProvider,
) : DietFormatter {
    private fun zoneId(): ZoneId = ZoneId.of(zone.current().id)

    override fun time(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId()).format(Instant.ofEpochMilli(epochMs))

    override fun date(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date.toJavaLocalDate())

    override fun gap(gap: Gap): String =
        if (gap.ongoing) ctx.getString(R.string.diet_gap_ongoing, time(gap.start))
        else ctx.getString(R.string.diet_gap_between, time(gap.start), time(gap.end))
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietUiModule {
    @Binds abstract fun formatter(impl: AndroidDietFormatter): DietFormatter
}
