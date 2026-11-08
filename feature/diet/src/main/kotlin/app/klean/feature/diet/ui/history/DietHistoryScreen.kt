package app.klean.feature.diet.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.klean.feature.diet.R
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * Month calendar with a small neutral count per day. Days without entries show only their number:
 * no colours, no heat map, nothing that reads as a score. Swipe or use the arrows to change month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietHistoryScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, vm: DietHistoryViewModel = hiltViewModel()) {
    val pager = rememberPagerState(initialPage = vm.pageCount - 1) { vm.pageCount }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diet_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(state = pager, modifier = Modifier.padding(padding).fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
            val first = vm.monthForPage(page)
            val flow = remember(first) { vm.month(first) }
            val state by flow.collectAsState(initial = HistoryMonth(first))
            MonthPage(
                state = state,
                onPrevious = { scope.launch { pager.animateScrollToPage(page - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(page + 1) } },
                hasPrevious = page > 0,
                onOpenDay = onOpenDay,
            )
        }
    }
}

@Composable
private fun MonthPage(
    state: HistoryMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val title = remember(state.month, locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.month.toJavaLocalDate())
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.diet_history_previous))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = state.canGoNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.diet_history_next))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            (0 until 7).forEach { i ->
                val dow = DayOfWeek.of(((firstDayOfWeek.value - 1 + i) % 7) + 1)
                Text(
                    dow.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        val firstDow = state.month.toJavaLocalDate().dayOfWeek
        val blanks = (firstDow.value - firstDayOfWeek.value + 7) % 7
        val cells: List<HistoryDay?> = List(blanks) { null } + state.days
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth()) {
            items(cells.size) { i ->
                val day = cells[i]
                if (day == null) Box(Modifier.aspectRatio(1f)) else DayCell(day, onOpenDay)
            }
        }
    }
}

@Composable
private fun DayCell(day: HistoryDay, onOpenDay: (LocalDate) -> Unit) {
    val dateText = remember(day.date) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(day.date.toJavaLocalDate())
    }
    val description = if (day.count > 0) {
        pluralStringResource(R.plurals.diet_history_day_entries, day.count, dateText, day.count)
    } else {
        dateText
    }
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .then(if (day.isToday) Modifier.border(BorderStroke(1.dp, outline), CircleShape) else Modifier)
            .clickable(enabled = !day.isFuture) { onOpenDay(day.date) }
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.date.day.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (day.isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
        if (day.count > 0) {
            Text(
                day.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
