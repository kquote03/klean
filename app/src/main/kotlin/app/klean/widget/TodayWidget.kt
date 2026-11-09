package app.klean.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.klean.core.common.ZoneProvider
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.theme.KleanColors
import app.klean.core.ui.module.StatusSurface
import app.klean.core.ui.module.TodayItems
import app.klean.shared.diet.CaptureSource
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun items(): TodayItems
    fun prefs(): DevicePrefs
    fun clock(): Clock
    fun zone(): ZoneProvider
}

private val Colors = ColorProviders(light = KleanColors.lightScheme, dark = KleanColors.darkScheme)
private val Surface = DayNightColorProvider(
    day = KleanColors.light.getValue("surfaceContainer"),
    night = KleanColors.dark.getValue("surfaceContainer"),
)

/**
 * "Klean Today" (holistic plan H1): the date, the next few items across modules and an "Add anything"
 * pill that opens universal capture. Diet keeps its own widget and adds no rows here.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, TodayWidgetEntryPoint::class.java)
        val states = ep.items().items().map { todayWidgetState(it) }
        val initial = runCatching { states.first() }.getOrDefault(TodayWidgetState(emptyList(), 0))
        val date = DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault(),
        ).format(java.time.Instant.ofEpochMilli(ep.clock().now().toEpochMilliseconds()).atZone(java.time.ZoneId.of(ep.zone().current().id)))
        val dynamicColor = ep.prefs().dynamicColor
        val initialDynamic = dynamicColor.first() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        provideContent {
            val state by remember { states }.collectAsState(initial)
            val dynamic by remember { dynamicColor }.collectAsState(initialDynamic)
            val wallpaper = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            GlanceTheme(colors = if (wallpaper) DynamicThemeColorProviders else Colors) {
                TodayWidgetContent(state, date, if (wallpaper) GlanceTheme.colors.surface else Surface)
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

private fun captureAction(context: Context): Action =
    actionStartActivity(CaptureActivity.intent(context, CaptureSource.WIDGET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun openAction(context: Context, route: String): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, route.toUri()).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

@Composable
private fun TodayWidgetContent(state: TodayWidgetState, date: String, surface: ColorProvider) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier.fillMaxSize().background(surface).cornerRadius(20.dp).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (size.height < TodayWidget.MEDIUM.height || size.width < TodayWidget.MEDIUM.width) {
            CompactContent(state)
        } else {
            FullContent(state, date)
        }
    }
}

@Composable
private fun Pill(modifier: GlanceModifier) {
    val ctx = LocalContext.current
    Row(
        modifier.height(48.dp).background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp)
            .padding(horizontal = 16.dp).clickable(captureAction(ctx)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ctx.getString(R.string.today_widget_add),
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactContent(state: TodayWidgetState) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill(GlanceModifier.defaultWeight())
        if (state.total > 0) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                ctx.resources.getQuantityString(R.plurals.today_widget_count, state.total, state.total),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun FullContent(state: TodayWidgetState, date: String) {
    val ctx = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            date,
            modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Pill(GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(8.dp))
        if (state.isEmpty) {
            Text(
                ctx.getString(R.string.today_widget_empty),
                modifier = GlanceModifier.padding(horizontal = 8.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(state.rows, itemId = { it.key.hashCode().toLong() }) { row ->
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(openAction(ctx, row.route))) {
                        row.timeLabel?.let {
                            Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                            Spacer(GlanceModifier.width(10.dp))
                        }
                        Text(row.text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}
