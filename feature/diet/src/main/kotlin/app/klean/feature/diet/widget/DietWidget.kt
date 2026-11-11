package app.klean.feature.diet.widget

import android.content.Context
import android.content.Intent
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
import android.os.Build
import androidx.glance.GlanceTheme
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.unit.ColorProvider
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.klean.core.capture.CaptureActivity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.theme.KleanColors
import app.klean.core.design.theme.WidgetTokens
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.notification.PromptHighlight
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DietWidgetEntryPoint {
    fun repo(): DietRepository
    fun gate(): ModuleGate
    fun prefs(): DevicePrefs
    fun formatter(): DietFormatter
    fun highlight(): PromptHighlight
}

/** Klean's pastel scheme for the widget; [DynamicThemeColorProviders] when "Use wallpaper colours" is on. */
val KleanGlanceColors = ColorProviders(light = KleanColors.lightScheme, dark = KleanColors.darkScheme)

/**
 * Widget card colour. Glance's scheme has no surfaceContainer slot, and its widgetBackground maps to
 * secondaryContainer (the green accent), which looked off next to the lavender app.
 */
private val WidgetSurface = DayNightColorProvider(
    day = KleanColors.light.getValue(WidgetTokens.SURFACE),
    night = KleanColors.dark.getValue(WidgetTokens.SURFACE),
)

/** Home-screen widget: today's food, a one-tap capture pill and a mic (plan §5.4). */
class DietWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context, DietWidgetEntryPoint::class.java)
        val fmt = ep.formatter()
        val highlight = ep.highlight()
        val states = dietWidgetStates(
            ep.repo(), ep.gate().disabledIds, ep.prefs().highlightPromptId,
            highlightLabel = { highlight.phrasing()?.let { context.getString(it.res) } },
            timeLabel = fmt::time,
        )
        val initial = states.first()
        val dynamicColor = ep.prefs().dynamicColor
        val initialDynamic = dynamicColor.first() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        provideContent {
            val state by remember { states }.collectAsState(initial)
            // "Use wallpaper colours" (Appearance): the system's Material You scheme instead of Klean's.
            val dynamic by remember { dynamicColor }.collectAsState(initialDynamic)
            val wallpaper = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            GlanceTheme(colors = if (wallpaper) DynamicThemeColorProviders else KleanGlanceColors) {
                DietWidgetContent(state, if (wallpaper) GlanceTheme.colors.surface else WidgetSurface)
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(200.dp, 120.dp)
        val LARGE = DpSize(260.dp, 260.dp)
    }
}

class DietWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DietWidget()
}

private fun captureAction(context: Context, voice: Boolean): Action =
    actionStartActivity(
        CaptureActivity.intent(context, CaptureSource.WIDGET, ModuleId.DIET, startVoice = voice)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

private fun openAppAction(context: Context): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, "klean://today".toUri()).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

@Composable
private fun DietWidgetContent(state: DietWidgetState, surface: ColorProvider) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier.fillMaxSize().background(surface).cornerRadius(WidgetTokens.cardRadius).padding(WidgetTokens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !state.dietEnabled -> DisabledContent()
            size.height < DietWidget.MEDIUM.height || size.width < DietWidget.MEDIUM.width -> CompactContent(state)
            else -> FullContent(state)
        }
    }
}

@Composable
private fun CompactContent(state: DietWidgetState) {
    val ctx = LocalContext.current
    // 2×1 leaves ~60 dp beside the 48 dp button: no extra padding, and up to 3 lines for the entry.
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val text = compactText(ctx, state)
        Column(GlanceModifier.defaultWeight().clickable(captureAction(ctx, voice = false))) {
            Text(text.primary, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetTokens.meta), maxLines = 3)
            text.secondary?.let {
                Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.meta), maxLines = 1)
            }
        }
        SquareIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = ctx.getString(R.string.diet_widget_add),
            onClick = captureAction(ctx, voice = false),
            modifier = GlanceModifier.size(WidgetTokens.icon),
        )
    }
}

@Composable
private fun FullContent(state: DietWidgetState) {
    val ctx = LocalContext.current
    val pillBg = if (state.highlighted) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.primaryContainer
    val pillFg = if (state.highlighted) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onPrimaryContainer
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                GlanceModifier.defaultWeight().height(WidgetTokens.pillHeight).background(pillBg).cornerRadius(WidgetTokens.pillRadius)
                    .padding(horizontal = WidgetTokens.pillPadding).clickable(captureAction(ctx, voice = false)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.highlightText ?: ctx.getString(R.string.diet_widget_prompt),
                    style = TextStyle(color = pillFg, fontSize = WidgetTokens.label, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(WidgetTokens.gap))
            SquareIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_mic),
                contentDescription = ctx.getString(R.string.diet_widget_speak),
                onClick = captureAction(ctx, voice = true),
                modifier = GlanceModifier.size(WidgetTokens.icon),
            )
        }
        Spacer(GlanceModifier.height(WidgetTokens.gap))
        if (state.entries.isEmpty()) {
            Text(
                ctx.getString(R.string.diet_widget_empty),
                modifier = GlanceModifier.padding(horizontal = WidgetTokens.rowPadding),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.meta),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(state.entries, itemId = { it.id.hashCode().toLong() }) { e ->
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = WidgetTokens.rowPadding, vertical = WidgetTokens.rowPadding)) {
                        Text(e.time, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = WidgetTokens.meta))
                        Spacer(GlanceModifier.width(WidgetTokens.gap))
                        Text(e.text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetTokens.body), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledContent() {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = WidgetTokens.rowPadding), verticalAlignment = Alignment.CenterVertically) {
        Text(
            ctx.getString(R.string.diet_widget_disabled_title),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetTokens.title, fontWeight = FontWeight.Bold),
        )
        Text(
            ctx.getString(R.string.diet_widget_open),
            modifier = GlanceModifier.height(WidgetTokens.pillHeight).background(GlanceTheme.colors.primaryContainer).cornerRadius(WidgetTokens.pillRadius)
                .padding(horizontal = WidgetTokens.pillPadding, vertical = WidgetTokens.gap).clickable(openAppAction(ctx)),
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = WidgetTokens.label, fontWeight = FontWeight.Bold),
        )
    }
}
