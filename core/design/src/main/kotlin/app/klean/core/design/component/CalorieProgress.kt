package app.klean.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import app.klean.core.design.R
import app.klean.core.design.theme.Space

/**
 * "1,240 of 2,000 kcal" over a calm bar. Over the goal the bar is simply full in the same colour: no warning
 * colour, no "over". A static (non-wavy) bar, so there's no motion to turn off.
 */
@Composable
fun CalorieProgress(eaten: Int, goal: Int, estimated: Boolean, modifier: Modifier = Modifier) {
    val fraction = if (goal <= 0) 0f else (eaten.toFloat() / goal).coerceIn(0f, 1f)
    val nf = java.text.NumberFormat.getIntegerInstance()
    val text = stringResource(R.string.design_kcal_of_goal, (if (estimated) "≈ " else "") + nf.format(eaten), nf.format(goal))
    Column(modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.xs))
        LinearProgressIndicator(
            progress = { fraction },
            // The default track is secondaryContainer, the same as a SECONDARY card: use the surface so it reads.
            trackColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
