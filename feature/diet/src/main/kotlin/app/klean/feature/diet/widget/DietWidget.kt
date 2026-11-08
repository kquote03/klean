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
import androidx.glance.GlanceTheme
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.klean.core.capture.CaptureActivity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.theme.KleanColors
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

/** Klean's pastel scheme for the widget (dynamic colour on the home screen is left to M13). */
val KleanGlanceColors = ColorProviders(light = KleanColors.lightScheme, dark = KleanColors.darkScheme)

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
        provideContent {
            val state by remember { states }.collectAsState(initial)
            GlanceTheme(colors = KleanGlanceColors) { DietWidgetContent(state) }
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
private fun DietWidgetContent(state: DietWidgetState) {
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(8.dp),
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
private fun summary(state: DietWidgetState): String {
    val ctx = LocalContext.current
    val last = state.lastText
    return if (state.count == 0 || last == null) {
        ctx.getString(R.string.diet_widget_empty)
    } else {
        ctx.resources.getQuantityString(R.plurals.diet_widget_summary, state.count, state.count, last)
    }
}

@Composable
private fun CompactContent(state: DietWidgetState) {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            summary(state),
            modifier = GlanceModifier.defaultWeight().clickable(captureAction(ctx, voice = false)),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 2,
        )
        SquareIconButton(
            imageProvider = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = ctx.getString(R.string.diet_widget_add),
            onClick = captureAction(ctx, voice = false),
            modifier = GlanceModifier.size(40.dp),
        )
    }
}

@Composable
private fun FullContent(state: DietWidgetState) {
    val ctx = LocalContext.current
    val pillBg = if (state.highlighted) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.secondaryContainer
    val pillFg = if (state.highlighted) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                GlanceModifier.defaultWeight().height(48.dp).background(pillBg).cornerRadius(24.dp)
                    .padding(horizontal = 16.dp).clickable(captureAction(ctx, voice = false)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.highlightText ?: ctx.getString(R.string.diet_widget_prompt),
                    style = TextStyle(color = pillFg, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            SquareIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_mic),
                contentDescription = ctx.getString(R.string.diet_widget_speak),
                onClick = captureAction(ctx, voice = true),
                modifier = GlanceModifier.size(48.dp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (state.entries.isEmpty()) {
            Text(
                ctx.getString(R.string.diet_widget_empty),
                modifier = GlanceModifier.padding(horizontal = 8.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(state.entries, itemId = { it.id.hashCode().toLong() }) { e ->
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(e.time, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                        Spacer(GlanceModifier.width(10.dp))
                        Text(e.text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledContent() {
    val ctx = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            ctx.getString(R.string.diet_widget_disabled_title),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            ctx.getString(R.string.diet_widget_open),
            modifier = GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp).clickable(openAppAction(ctx)),
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp),
        )
    }
}
