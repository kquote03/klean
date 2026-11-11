package app.klean.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.klean.core.design.R
import app.klean.core.design.theme.Space

/**
 * A secondary action: an overflow-menu item, an empty state's action, a section's trailing text button.
 * [selected] marks the current choice in a menu of options (a calendar's view).
 */
data class MenuAction(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/**
 * The group a top-level module screen sits in (navigation A: Plan, Track, People). When the shell resolves one for a
 * screen, [KleanScreen] shows the group's [title] and its module [tabs] instead of the module's own title, and adds
 * the group's [menu] (Settings) to the overflow.
 */
data class GroupChrome(
    val title: String,
    val tabs: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
    val menu: List<MenuAction> = emptyList(),
)

/**
 * Resolves a screen's [GroupChrome] from its lifecycle owner (a navigation back-stack entry), or null for screens
 * outside a group and for detail screens. Provided by the app shell; core/design doesn't know about navigation.
 */
fun interface GroupChromeResolver {
    fun chromeFor(owner: Any?): GroupChrome?
}

val LocalGroupChrome = staticCompositionLocalOf { GroupChromeResolver { null } }

/** The Add menu offered on every top-level screen; null hides the Add button (tests, the setup wizard). */
val LocalAddMenu = staticCompositionLocalOf<AddMenuSpec?> { null }

/**
 * The one screen scaffold (docs/ui/design-system.md). A top bar with a `titleLarge` title, optional [subtitle],
 * icon [actions] and an overflow [menu]; the group's segmented tabs when the screen is a module inside a group;
 * the Add button (from [LocalAddMenu]) and a snackbar host. [header] replaces the title row (Today's weekday).
 * A detail screen passes [onBack] for a back arrow (and usually `showAdd = false`).
 *
 * [content] gets padding that already holds the top bar, the screen edge on both sides, and room under the last
 * item for the Add button; pass it to a lazy list's `contentPadding` so rows scroll edge to edge.
 */
@Composable
fun KleanScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    menu: List<MenuAction> = emptyList(),
    snackbarHostState: SnackbarHostState? = null,
    showAdd: Boolean = true,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val group = LocalGroupChrome.current.chromeFor(LocalLifecycleOwner.current)
    val addSpec = LocalAddMenu.current?.takeIf { showAdd }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    val allMenu = menu + group?.menu.orEmpty()
    Box(modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    if (header != null) {
                        header()
                    } else {
                        TopBar(group?.title ?: title, subtitle.takeIf { group == null }, actions, allMenu, onBack.takeIf { group == null })
                    }
                    if (group != null && group.tabs.size > 1) {
                        SegmentedTabs(
                            labels = group.tabs,
                            selected = group.selected,
                            onSelect = group.onSelect,
                            modifier = Modifier.padding(horizontal = Space.screenEdge).padding(bottom = Space.xs),
                        )
                    }
                }
            },
            snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        ) { inner ->
            content(
                PaddingValues(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding() + if (addSpec != null) AddButtonClearance else Space.md,
                    start = Space.screenEdge,
                    end = Space.screenEdge,
                ),
            )
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
            AddMenu(
                spec = addSpec,
                expanded = addOpen,
                onExpandedChange = { addOpen = it },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)),
            )
        }
    }
}

/** Room under a list's last row so the Add button never covers it. */
val AddButtonClearance = 96.dp

@Composable
private fun TopBar(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit, menu: List<MenuAction>, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) Space.xxs else Space.screenEdge, end = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.design_back))
            }
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        actions()
        OverflowMenu(menu)
    }
}

/** A ⋮ button with [items]; nothing when there are none. */
@Composable
fun OverflowMenu(items: List<MenuAction>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Space.touchTarget)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.design_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = { open = false; item.onClick() },
                    trailingIcon = if (item.selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.design_selected)) }
                    } else {
                        null
                    },
                    modifier = Modifier.heightIn(min = Space.touchTarget),
                )
            }
        }
    }
}
