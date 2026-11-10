package app.klean.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/**
 * One calm message for an empty section: a tonal icon, one sentence and at most one action (a text button, so the
 * screen keeps a single primary action). Replaces stacked grey hint sentences.
 */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.WbSunny, action: MenuAction? = null) {
    Column(
        modifier.fillMaxWidth().padding(top = Space.xl, bottom = Space.md, start = Space.md, end = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            TextButton(onClick = action.onClick, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(action.label) }
        }
    }
}
