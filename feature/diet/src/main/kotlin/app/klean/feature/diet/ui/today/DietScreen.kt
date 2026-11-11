package app.klean.feature.diet.ui.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.CalorieProgress
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.PhotoThumbnail
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.feature.diet.R
import app.klean.feature.diet.delivery.DeliveryOfferCard
import app.klean.feature.diet.ui.RemovedEntrySnackbar

@Composable
fun DietScreen(
    onOpenEntry: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    onAddForGap: (DietRow.GapRow) -> Unit = {},
    backStackEntry: NavBackStackEntry? = null,
    vm: DietViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    backStackEntry?.let { RemovedEntrySnackbar(it, snackbar, onUndo = vm::undoRemove) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    DietScreenContent(
        state = state,
        onOpenEntry = onOpenEntry,
        onOpenHistory = onOpenHistory,
        onAddForGap = onAddForGap,
        onFillCalories = vm::fillMissing,
        snackbar = snackbar,
        offer = { DeliveryOfferCard() },
    )
}

/**
 * Track › Diet (Soft cards): today's latest entry in the hero, today's entries (and calm gap markers), then the rest
 * of the week with History behind "See all". Logging, voice and "Log again" live in the Add menu's capture sheet.
 */
@Composable
fun DietScreenContent(
    state: DietUiState,
    onOpenEntry: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onAddForGap: (DietRow.GapRow) -> Unit,
    snackbar: SnackbarHostState? = null,
    /** A one-time, dismissible card (the delivery offer); nothing once answered. */
    offer: @Composable () -> Unit = {},
    onFillCalories: () -> Unit = {},
) {
    val menu = listOfNotNull(
        state.withoutCalories?.let { MenuAction(stringResource(R.string.diet_fill_calories), onClick = onFillCalories) },
        MenuAction(stringResource(R.string.diet_history_open), onClick = onOpenHistory),
    )
    KleanScreen(title = stringResource(R.string.diet_label), menu = menu, snackbarHostState = snackbar) { pad ->
        if (!state.loaded) return@KleanScreen
        val entries = state.rows.filterIsInstance<DietRow.Entry>()
        val latest = entries.lastOrNull()
        LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
            if (entries.isEmpty() && state.earlier.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.diet_today_empty), icon = Icons.Rounded.Restaurant) }
                item(key = "offer") { offer() }
                return@LazyColumn
            }
            item(key = "hero") {
                ModuleCard(
                    label = stringResource(R.string.diet_summary_today),
                    fact = latest?.text ?: stringResource(R.string.diet_summary_none),
                    supporting = listOfNotNull(
                        latest?.timeLabel,
                        state.caloriesSoFar.takeIf { state.calorieGoal <= 0 },
                        state.withoutCalories,
                    ).joinToString(" · ").ifEmpty { null },
                    tone = CardTone.SECONDARY,
                    hero = state.calories?.takeIf { state.calorieGoal > 0 }?.let { kcal ->
                        { CalorieProgress(eaten = kcal.total, goal = state.calorieGoal, estimated = kcal.estimated) }
                    },
                )
            }
            item(key = "offer") { offer() }
            if (entries.isNotEmpty()) {
                item(key = "h_today") { SectionHeader(stringResource(R.string.diet_section_today)) }
                listGroup("today", state.rows, { it.key }) { row -> DietListRow(row, onOpenEntry, onAddForGap) }
            }
            if (state.earlier.isNotEmpty()) {
                item(key = "h_earlier") { SectionHeader(stringResource(R.string.diet_section_earlier)) }
                listGroup("earlier", state.earlier, { it.id }) { e ->
                    ListRow(title = e.text, meta = "${e.day}\n${e.time}", onClick = { onOpenEntry(e.id) })
                }
            }
        }
    }
}

/** An entry (text, AI items, time, photo) or a calm gap marker that adds at that time. */
@Composable
private fun DietListRow(row: DietRow, onOpenEntry: (String) -> Unit, onAddForGap: (DietRow.GapRow) -> Unit) {
    when (row) {
        is DietRow.Entry -> ListRow(
            title = row.text,
            supporting = (listOfNotNull(row.kcalLabel) + row.itemLines).joinToString(" · ").ifEmpty { null },
            meta = row.timeLabel,
            onClick = { onOpenEntry(row.id) },
            trailing = row.photoPath?.let { path -> { PhotoThumbnail(path, contentDescription = stringResource(R.string.diet_has_photo)) } },
        )
        is DietRow.GapRow -> ListRow(
            title = row.label,
            leading = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            muted = false,
            onClick = { onAddForGap(row) },
            onClickLabel = stringResource(app.klean.core.design.R.string.design_gap_add_something),
        )
    }
}
