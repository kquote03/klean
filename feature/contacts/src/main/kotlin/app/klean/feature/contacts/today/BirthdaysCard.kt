package app.klean.feature.contacts.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.design.component.KleanCard
import app.klean.feature.contacts.R
import app.klean.feature.contacts.ui.whenLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BirthdaysCardState(val loaded: Boolean = false, val rows: List<UpcomingBirthday> = emptyList())

/** The Today card: the next 3 birthdays within 30 days. */
@HiltViewModel
class BirthdaysCardViewModel @Inject constructor(surfaces: BirthdaySurfaces) : ViewModel() {
    val state: StateFlow<BirthdaysCardState> = surfaces.upcoming(WITHIN_DAYS)
        .map { (_, l) -> BirthdaysCardState(true, l.take(MAX)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BirthdaysCardState())

    companion object {
        const val WITHIN_DAYS = 30
        const val MAX = 3
    }
}

@Composable
fun BirthdaysCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: BirthdaysCardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    BirthdaysCardContent(s, onOpen, modifier)
}

@Composable
fun BirthdaysCardContent(s: BirthdaysCardState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    KleanCard(title = stringResource(R.string.birthdays_card_title), onOpen = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (s.loaded && s.rows.isEmpty()) {
                Text(
                    stringResource(R.string.birthdays_card_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            s.rows.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        r.age?.takeIf { it > 0 }?.let {
                            Text(stringResource(R.string.birthdays_turns, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(whenLabel(r.date, r.daysUntil), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
