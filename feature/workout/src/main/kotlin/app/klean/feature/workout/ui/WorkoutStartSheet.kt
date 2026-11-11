package app.klean.feature.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.ListGroup
import app.klean.core.design.component.ListRow
import app.klean.core.design.theme.Space
import app.klean.feature.workout.R
import app.klean.feature.workout.jog.RunViewModel
import app.klean.feature.workout.jog.rememberRunStarter

/** What the start sheet offers: a session already running, the saved workouts, and whether a run can start. */
data class StartChoices(val openSessionId: String? = null, val templates: List<TemplateRow> = emptyList(), val canRun: Boolean = true)

private data class Choice(val title: String, val line: String?, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The Workout start sheet (Today's Workout tile): saved workouts, Run and an empty workout, each one tap.
 * A session already running is offered instead, so a tap never starts a second one.
 */
@Composable
fun WorkoutStartSheet(
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRunStarted: () -> Unit,
    vm: WorkoutHomeViewModel = hiltViewModel(),
    run: RunViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val runState by run.state.collectAsStateWithLifecycle()
    val starter = rememberRunStarter(run)
    val wasRunning = remember { runState.running }
    LaunchedEffect(runState.running) { if (runState.running && !wasRunning) onRunStarted() }
    WorkoutStartSheetContent(
        choices = StartChoices(s.open?.session?.id, s.templates, canRun = !runState.running),
        onStart = { id -> vm.start(id, onOpenSession) },
        onContinue = onOpenSession,
        onRun = starter.start,
        onDismiss = onDismiss,
    )
}

/** The sheet without its view models, for tests. A tap outside the sheet closes it, as Back does. */
@Composable
fun WorkoutStartSheetContent(
    choices: StartChoices,
    onStart: (templateId: String?) -> Unit,
    onContinue: (sessionId: String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
) {
    val open = choices.openSessionId
    val rows = if (open != null) {
        listOf(Choice(stringResource(R.string.workout_start_sheet_continue), null, Icons.Rounded.PlayArrow) { onContinue(open) })
    } else {
        choices.templates.map { t -> Choice(t.template.name, t.text, Icons.Rounded.FitnessCenter) { onStart(t.template.id) } } +
            listOfNotNull(
                if (choices.canRun) {
                    Choice(stringResource(R.string.workout_run_card), stringResource(R.string.workout_run_card_line), Icons.AutoMirrored.Rounded.DirectionsRun, onRun)
                } else {
                    null
                },
                Choice(stringResource(R.string.workout_empty_card), stringResource(R.string.workout_empty_card_line), Icons.Rounded.Add) { onStart(null) },
            )
    }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            // Taps on the sheet itself stay on the sheet.
            modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = Space.lg, topEnd = Space.lg),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.navigationBarsPadding().padding(Space.md).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.workout_start_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Space.xxs, bottom = Space.sm).semantics { heading() },
                )
                ListGroup(rows, Modifier.testTag(CLUTTER_EXEMPT)) { c ->
                    ListRow(
                        title = c.title,
                        supporting = c.line,
                        titleMaxLines = 1,
                        leading = { Icon(c.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = c.onClick,
                    )
                }
            }
        }
    }
}
