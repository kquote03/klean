package app.klean.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Bottom padding for a list on a screen with a floating action button, so the last rows can scroll clear of it
 * (a 56 dp FAB + its 16 dp margin + breathing room). Use as `LazyColumn(contentPadding = FabClearance)`.
 */
val FabClearance = PaddingValues(bottom = 96.dp)
