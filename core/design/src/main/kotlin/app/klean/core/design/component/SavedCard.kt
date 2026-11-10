package app.klean.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/**
 * A saved thing that starts with one tap (a workout template): a `large` tonal card with a [title], one [line]
 * and a "Start" affordance (an accent disc with a play icon and [startLabel]). The whole card starts it; there is
 * no separate button. A row of these ends with [NewSavedCard].
 */
@Composable
fun SavedCard(title: String, line: String?, startLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SavedSurface(onClick, modifier, highlighted = true) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Affordance(Icons.Rounded.PlayArrow, startLabel)
    }
}

/** The last card of a saved row: makes a new one. */
@Composable
fun NewSavedCard(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SavedSurface(onClick, modifier, highlighted = false) { Affordance(Icons.Rounded.Add, label) }
}

@Composable
private fun SavedSurface(onClick: () -> Unit, modifier: Modifier, highlighted: Boolean, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.width(168.dp).heightIn(min = 136.dp),
    ) {
        Column(Modifier.padding(Space.cardPadding), verticalArrangement = Arrangement.spacedBy(Space.xxs)) { content() }
    }
}

@Composable
private fun Affordance(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.xs))
    }
}
