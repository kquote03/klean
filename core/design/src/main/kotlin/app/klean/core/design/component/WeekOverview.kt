package app.klean.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.KleanTheme

/** One day in the week overview. [count] 0 renders as blank, never as "0". */
data class WeekDayCell(
    val key: String,
    val weekday: String,
    val dayNumber: String,
    val count: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val description: String,
)

/** Seven days at a glance. Neutral: no colours by amount, no streaks, empty days stay empty. */
@Composable
fun WeekOverview(
    title: String,
    days: List<WeekDayCell>,
    onDay: (WeekDayCell) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, previousDescription) }
                IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, nextDescription) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { day -> DayCell(day, onDay, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: WeekDayCell, onDay: (WeekDayCell) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val outline = if (day.isToday) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier
    val numberColor = if (day.isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
    // Seven cells share a phone's width; at very large font scales the day number steps down a size.
    val large = LocalDensity.current.fontScale > 1.5f
    Column(
        modifier
            .padding(2.dp)
            .heightIn(min = 72.dp)
            .clip(shape)
            .then(outline)
            .clickable(enabled = !day.isFuture, role = Role.Button) { onDay(day) }
            .clearAndSetSemantics {
                contentDescription = day.description
                role = Role.Button
                if (day.isFuture) disabled() else onClick { onDay(day); true }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.weekday, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            day.dayNumber,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            color = numberColor,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        Text(
            if (day.count > 0) day.count.toString() else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun WeekOverviewPreview() {
    KleanTheme {
        WeekOverview(
            title = "This week",
            days = (9..15).map { WeekDayCell("$it", "MTWTFSS"[it - 9].toString(), "$it", if (it < 11) it - 8 else 0, it == 11, it > 11, "Day $it") },
            onDay = {}, onPrevious = {}, onNext = {}, canGoNext = false,
            previousDescription = "Previous week", nextDescription = "Next week",
        )
    }
}
