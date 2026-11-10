package app.klean.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/**
 * Seven bars, one per day, for a card's hero (Sleep, Workout, Track's week). [values] are 0..1 or null for a day
 * with no data, which shows a short stub: never a gap or a mark. The current day ([today]) uses [accent].
 * Read as one item: [description].
 */
@Composable
fun WeekBars(
    values: List<Float?>,
    labels: List<String>,
    today: Int,
    description: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
    other: Color = LocalContentColor.current.copy(alpha = 0.35f),
    height: Dp = 64.dp,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val h = if (v == null) 4.dp else (height * v.coerceIn(0.08f, 1f))
                    Box(
                        Modifier.fillMaxWidth().height(h)
                            .background(if (i == today) accent else other, MaterialTheme.shapes.small),
                    )
                }
                labels.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.xxs)) }
            }
        }
    }
}
