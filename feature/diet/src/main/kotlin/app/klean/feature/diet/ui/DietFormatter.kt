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
    fun kcal(n: Int): String = "~$n kcal"
    fun kcalExact(n: Int): String = "$n kcal"
    fun kcalApprox(n: Int): String = "≈ $n kcal"
    fun kcalFromEarlier(label: String): String = "$label · from earlier"
    fun kcalEstimate(label: String): String = "$label · estimate"
    fun kcalSoFar(label: String): String = "$label so far"
    fun withoutCalories(n: Int): String = if (n == 1) "1 food without calories" else "$n foods without calories"
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

    private fun number(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)

    override fun kcal(n: Int): String = ctx.getString(R.string.diet_kcal, number(n))

    override fun kcalExact(n: Int): String = ctx.getString(R.string.diet_kcal_exact, number(n))

    override fun kcalApprox(n: Int): String = ctx.getString(R.string.diet_kcal_approx, number(n))

    override fun kcalFromEarlier(label: String): String = ctx.getString(R.string.diet_kcal_from_earlier, label)

    override fun kcalEstimate(label: String): String = ctx.getString(R.string.diet_kcal_estimate, label)

    override fun kcalSoFar(label: String): String = ctx.getString(R.string.diet_kcal_so_far, label)

    override fun withoutCalories(n: Int): String = ctx.resources.getQuantityString(R.plurals.diet_without_calories, n, n)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietUiModule {
    @Binds abstract fun formatter(impl: AndroidDietFormatter): DietFormatter
}
