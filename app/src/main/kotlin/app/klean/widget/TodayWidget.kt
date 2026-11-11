package app.klean.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.klean.R
import app.klean.core.capture.CaptureActivity
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.theme.KleanColors
import app.klean.core.design.theme.WidgetTokens
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.StatusSurface
import app.klean.core.ui.module.TodayGlance
import app.klean.feature.sleep.data.SleepRepository
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.today.EMPTY_GLANCE
import app.klean.today.buildGlance
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun glance(): TodayGlance
    fun prefs(): DevicePrefs
    fun clock(): Clock
    fun zone(): ZoneProvider
}

private val Colors = ColorProviders(light = KleanColors.lightScheme, dark = KleanColors.darkScheme)
private val Surface = DayNightColorProvider(
    day = KleanColors.light.getValue(WidgetTokens.SURFACE),
    night = KleanColors.dark.getValue(WidgetTokens.SURFACE),
)

/** The widget's buttons first (Food, Task, Sleep), then the other enabled modules, so the grid's cap never drops them. */
private val WIDGET_FIRST = listOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP)

/**
 * "Klean Today" (plan 2026-11-11 C5): a small whiteboard of the day. Speak, Food, Task and Sleep as one-tap buttons
 * (capture opens CaptureActivity, the right place outside the app; Sleep toggles in place through
 * [SleepToggleReceiver]), the next two things coming up, and the last three things done today, foods included.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, TodayWidgetEntryPoint::class.java)
        val clock = ep.clock()
        val states = ep.glance().byModule().map { parts ->
            val ids = parts.keys.sortedBy { it.value }
            todayWidgetState(buildGlance(WIDGET_FIRST.filter { it in ids } + (ids - WIDGET_FIRST.toSet()), parts, clock.now().toEpochMilliseconds()))
        }
        val initial = runCatching { states.first() }.getOrDefault(todayWidgetState(EMPTY_GLANCE))
        val zoneId = java.time.ZoneId.of(ep.zone().current().id)
        val date = DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault(),
        ).format(java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zoneId))
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context).apply { timeZone = java.util.TimeZone.getTimeZone(zoneId) }
        val timeOf: (Long) -> String = { ms -> timeFormat.format(java.util.Date(ms)) }
        val dynamicColor = ep.prefs().dynamicColor
        val initialDynamic = dynamicColor.first() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        provideContent {
            val state by remember { states }.collectAsState(initial)
            val dynamic by remember { dynamicColor }.collectAsState(initialDynamic)
            val wallpaper = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            GlanceTheme(colors = if (wallpaper) DynamicThemeColorProviders else Colors) {
                TodayWidgetContent(state, date, timeOf, if (wallpaper) GlanceTheme.colors.surface else Surface)
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(200.dp, 120.dp)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * The widget's Sleep button: "Going to bed" or "I'm up" right away, without opening the app. Sleep's own write
 * refreshes the widget (and the status line) through `StatusRefresh`.
 */
@AndroidEntryPoint
class SleepToggleReceiver : BroadcastReceiver() {
    @Inject lateinit var sleep: SleepRepository
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        scope.launch {
            try {
                sleep.toggle(clock.now().toEpochMilliseconds())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Klean", "sleep toggle from the widget failed", e)
            } finally {
                done.finish()
            }
        }
    }
}

/** Re-renders placed Today widgets through the same `StatusRefresh` hook as the status line. */
@Singleton
class TodayWidgetRefresher @Inject constructor(@ApplicationContext private val ctx: Context) : StatusSurface {
    override suspend fun refresh() = TodayWidget().updateAll(ctx)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TodayWidgetModule {
    @Binds @IntoSet abstract fun surface(r: TodayWidgetRefresher): StatusSurface
}

private fun actionFor(context: Context, a: WidgetAction): Action = when (a.kind) {
    WidgetActionKind.SLEEP -> actionSendBroadcast(Intent(context, SleepToggleReceiver::class.java))
    WidgetActionKind.VOICE -> actionStartActivity(
        CaptureActivity.intent(context, CaptureSource.WIDGET, startVoice = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    WidgetActionKind.CAPTURE -> actionStartActivity(
        CaptureActivity.intent(context, CaptureSource.WIDGET, target = a.module).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openAction(context: Context, route: String): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, route.toUri()).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** A small Soft card (docs/ui/design-system.md): the date, the one-tap buttons, then what's next and the day so far. */
@Composable
private fun TodayWidgetContent(state: TodayWidgetState, date: String, timeOf: (Long) -> String, surface: ColorProvider) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier.fillMaxSize().background(surface).cornerRadius(WidgetTokens.cardRadius).padding(WidgetTokens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (size.height < TodayWidget.MEDIUM.height || size.width < TodayWidget.MEDIUM.width) {
            Actions(state.actions.take(2))
        } else {
            FullContent(state, date, timeOf)
        }
    }
}

/** The one-tap buttons in a row; Speak leads on the primary container, the rest sit on the surface variant. */
@Composable
private fun Actions(actions: List<WidgetAction>) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { i, a ->
            if (i > 0) Spacer(GlanceModifier.width(WidgetTokens.gap))
            val primary = a.kind == WidgetActionKind.VOICE
            Row(
                GlanceModifier.defaultWeight().height(WidgetTokens.pillHeight)
                    .background(if (primary) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(WidgetTokens.pillRadius)
                    .clickable(actionFor(ctx, a)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    a.label ?: ctx.getString(R.string.today_quick_voice),
                    style = TextStyle(
                        color = if (primary) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = WidgetTokens.meta,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FullContent(state: TodayWidgetState, date: String, timeOf: (Long) -> String) {
    val ctx = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            date,
            modifier = GlanceModifier.padding(horizontal = WidgetTokens.rowPadding, vertical = WidgetTokens.rowPadding),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetTokens.title, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(WidgetTokens.gap))
        Actions(state.actions)
        Spacer(GlanceModifier.height(WidgetTokens.gap))
        if (state.isEmpty) {
            Text(
                ctx.getString(R.string.today_widget_empty),
                modifier = GlanceModifier.padding(horizontal = WidgetTokens.rowPadding),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.meta),
            )
        } else {
            // Coming up first (what's next), then the day so far, quieter: one list, so it scrolls on a small widget.
            val rows = state.upcoming.map { it to false } + state.done.map { it to true }
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { (it.first.key + it.second).hashCode().toLong() }) { (row, done) -> WidgetRow(row, done, timeOf) }
            }
        }
    }
}

@Composable
private fun WidgetRow(row: GlanceRow, done: Boolean, timeOf: (Long) -> String) {
    val ctx = LocalContext.current
    val route = row.route
    Row(
        GlanceModifier.fillMaxWidth().padding(horizontal = WidgetTokens.rowPadding, vertical = WidgetTokens.rowPadding)
            .let { if (route != null) it.clickable(openAction(ctx, route)) else it },
    ) {
        row.at?.let {
            Text(timeOf(it), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.meta), maxLines = 1)
            Spacer(GlanceModifier.width(WidgetTokens.gap))
        }
        Text(
            row.text,
            style = TextStyle(color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface, fontSize = WidgetTokens.body),
            maxLines = 1,
        )
    }
}
