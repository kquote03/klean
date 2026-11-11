package app.klean.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/**
 * The floating chrome when the app shell draws it once over its pages (so it stays put while the pages slide):
 * [clearance] is the room it takes at the bottom, which each page leaves under its last row. A [KleanScreen] under a
 * host draws neither the group toolbar nor the Add button itself.
 */
@Stable
class FloatingChromeHost {
    var clearance by mutableStateOf<Dp?>(null)
}

/** Provided by the shell to the pages under its floating chrome; null everywhere else. */
val LocalFloatingChromeHost = staticCompositionLocalOf<FloatingChromeHost?> { null }

/** Test tag of the floating group toolbar. */
const val GROUP_TOOLBAR_TAG = "group-toolbar"

/**
 * What floats at the bottom of a top-level screen, in the [BoxScope] the screen fills: the group's [tabs] in a toolbar
 * (when there are two or more), with the Add button from [addSpec] at its end (the start with [leftHanded]), and the
 * scrim under the open Add menu. [onClearance] gets the room it takes above the bottom edge, null until measured.
 * Moving between groups cross-fades the tabs; the toolbar itself stays where it is.
 */
@Composable
fun BoxScope.FloatingChrome(
    tabs: GroupChrome?,
    addSpec: AddMenuSpec?,
    leftHanded: Boolean,
    onClearance: (Dp?) -> Unit = {},
) {
    val shownTabs = tabs?.takeIf { it.tabs.size > 1 }
    val density = LocalDensity.current
    var addOpen by rememberSaveable { mutableStateOf(false) }
    // Where things sit, in root pixels: the screen, the Add button (collapsed or not, it stays put) and the toolbar.
    var screenRect by remember { mutableStateOf<Rect?>(null) }
    var fabRect by remember { mutableStateOf<Rect?>(null) }
    var toolbarRect by remember { mutableStateOf<Rect?>(null) }
    val fab = fabRect?.takeIf { addSpec != null }
    val floating = listOfNotNull(fab, toolbarRect?.takeIf { shownTabs != null })
    val clearance: Dp? = screenRect?.let { s ->
        floating.takeIf { it.isNotEmpty() }?.let { r -> with(density) { (s.bottom - r.minOf { it.top }).coerceAtLeast(0f).toDp() } }
    }
    SideEffect { onClearance(clearance) }
    Box(Modifier.matchParentSize().onGloballyPositioned { screenRect = it.boundsInRoot() })
    if (shownTabs != null) {
        val s = screenRect
        val place = if (addSpec != null && fab != null && s != null) {
            // One row with the Add button: as tall as the button, centred on it, a small gap before (or after) it.
            with(density) {
                val beside = if (leftHanded) (fab.right - s.left).toDp() + Space.sm else (s.right - fab.left).toDp() + Space.sm
                Modifier.padding(
                    start = if (leftHanded) beside else Space.screenEdge,
                    end = if (leftHanded) Space.screenEdge else beside,
                    bottom = (s.bottom - fab.bottom).coerceAtLeast(0f).toDp(),
                ).heightIn(min = fab.height.toDp())
            }
        } else {
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = Space.screenEdge, end = Space.screenEdge, bottom = Space.md)
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().then(place)
                // Hidden for the first frame, until the Add button it lines up with has been placed.
                .alpha(if (addSpec != null && fab == null) 0f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            // A new group's tabs cross-fade in; a new selection within the group just moves the indicator.
            AnimatedContent(
                targetState = shownTabs,
                contentKey = { it.tabs },
                transitionSpec = { fadeIn(tween(TABS_FADE_MS)) togetherWith fadeOut(tween(TABS_FADE_MS)) },
                contentAlignment = Alignment.Center,
                label = "group tabs",
            ) { t ->
                GroupToolbar(t, Modifier.onGloballyPositioned { toolbarRect = it.boundsInRoot() })
            }
        }
    }
    if (addSpec != null) {
        if (addOpen) {
            BackHandler { addOpen = false }
            // The screen stays legible behind the menu (surface at 82 %); a tap anywhere closes it.
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { addOpen = false },
            )
        }
        val side = if (leftHanded) WindowInsetsSides.Start else WindowInsetsSides.End
        AddMenu(
            spec = addSpec,
            expanded = addOpen,
            onExpandedChange = { addOpen = it },
            modifier = Modifier.align(if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + side)),
            horizontalAlignment = if (leftHanded) Alignment.Start else Alignment.End,
            buttonModifier = Modifier.onGloballyPositioned { fabRect = it.boundsInRoot() },
        )
    }
}

/**
 * The floating toolbar holding a group's tabs: a raised pill on `surfaceContainer` whose segmented tabs fill it. The
 * tabs are navigation, so for the clutter budget the toolbar is one exempt region (like the navigation bar).
 */
@Composable
private fun GroupToolbar(group: GroupChrome, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = ToolbarElevation,
        shadowElevation = ToolbarElevation,
        modifier = modifier.fillMaxWidth().testTag(CLUTTER_EXEMPT),
    ) {
        SegmentedTabs(
            labels = group.tabs,
            selected = group.selected,
            onSelect = group.onSelect,
            modifier = Modifier.padding(Space.xxs).testTag(GROUP_TOOLBAR_TAG),
        )
    }
}

private val ToolbarElevation = 3.dp

private const val TABS_FADE_MS = 150
