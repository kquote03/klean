package app.klean.core.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.klean.core.design.theme.Space

/**
 * A section's title (`titleMedium`), `sectionGap` above it unless it opens the screen ([first]), `xs` below,
 * and an optional trailing text button ("See all"). At most three per viewport.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, first: Boolean = false, action: MenuAction? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = if (first) Space.xs else Space.sectionGap, bottom = Space.xs)
            .heightIn(min = if (action != null) Space.touchTarget else Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) {
            TextButton(onClick = action.onClick, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(action.label) }
        }
    }
}
