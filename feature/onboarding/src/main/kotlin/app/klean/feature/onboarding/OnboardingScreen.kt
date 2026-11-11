package app.klean.feature.onboarding

import app.klean.core.design.component.SwitchListItem
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.klean.core.design.theme.Space
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.OnboardingStepProvider
import app.klean.feature.settings.permissions.PermissionRows
import app.klean.feature.settings.permissions.PermissionsViewModel
import app.klean.feature.settings.quiet.QuietHoursStore
import app.klean.feature.settings.quiet.SleepWindowPicker

/**
 * The first-run wizard. Each page has Next and Skip side by side with the same weight; nothing is
 * required. It never asks for notification-listener access or locations: those are offered later,
 * in context, by the features that use them.
 */
@Composable
fun OnboardingScreen(
    registry: ModuleRegistry,
    quietHours: QuietHoursStore,
    onboarding: OnboardingState,
    permissions: PermissionChecker,
    onFinished: () -> Unit,
    /** Opens Settings > AI providers; back returns to this page. Null hides the button. */
    onOpenAiSettings: (() -> Unit)? = null,
    appWidgets: List<Class<*>> = emptyList(),
    steps: List<OnboardingStepProvider> = emptyList(),
    vm: OnboardingViewModel = viewModel { OnboardingViewModel(registry, quietHours, onboarding, appWidgets, steps) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.finished) { if (s.finished) onFinished() }
    BackHandler(enabled = s.position > 1 && !s.finished) { vm.back() }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Space.screenEdge)) {
            // Where you are, as a quiet bar and its words: never a count of what's left to do.
            LinearProgressIndicator(
                progress = { s.position.toFloat() / s.steps.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            )
            Text(
                stringResource(R.string.onboarding_progress, s.position, s.steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
            Text(
                stepTitle(s.current, vm),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Space.lg, bottom = Space.md).semantics { heading() },
            )
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                StepBody(s, vm, permissions, onOpenAiSettings)
            }
            val first = s.current == OnboardingStep.Welcome
            // Same height even when large text wraps one label onto two lines.
            Row(Modifier.fillMaxWidth().padding(top = Space.md).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                // Equal width on purpose: skipping is as normal as going on. Next is the page's one primary action.
                OutlinedButton(
                    onClick = { if (first) vm.skipAll() else vm.skip() },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = Space.touchTarget),
                ) {
                    Text(
                        stringResource(if (first) R.string.onboarding_skip_setup else R.string.onboarding_skip),
                        textAlign = TextAlign.Center,
                    )
                }
                FilledTonalButton(
                    onClick = { vm.next() },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = Space.touchTarget),
                ) {
                    Text(
                        stringResource(
                            when {
                                first -> R.string.onboarding_start
                                s.isLast -> R.string.onboarding_done
                                else -> R.string.onboarding_next
                            },
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun stepTitle(step: OnboardingStep, vm: OnboardingViewModel): String = when (step) {
    OnboardingStep.Welcome -> stringResource(R.string.onboarding_welcome_title)
    OnboardingStep.Modules -> stringResource(R.string.onboarding_modules_title)
    OnboardingStep.SleepWindow -> stringResource(R.string.onboarding_sleep_title)
    is OnboardingStep.ModuleSetup -> vm.module(step.id)?.onboardingTitleRes?.let { stringResource(it) } ?: ""
    OnboardingStep.Widget -> stringResource(R.string.onboarding_widget_title)
    OnboardingStep.Permissions -> stringResource(R.string.onboarding_permissions_title)
    OnboardingStep.Ai -> stringResource(R.string.onboarding_ai_title)
    is OnboardingStep.Extra -> vm.extra(step.key)?.titleRes?.let { stringResource(it) } ?: ""
}

@Composable
private fun StepBody(s: OnboardingUiState, vm: OnboardingViewModel, permissions: PermissionChecker, onOpenAiSettings: (() -> Unit)?) {
    when (val step = s.current) {
        OnboardingStep.Welcome -> Body(R.string.onboarding_welcome_body)
        OnboardingStep.Modules -> {
            Body(R.string.onboarding_modules_body)
            s.modules.forEach { m ->
                SwitchListItem(
                    checked = m.enabled,
                    onCheckedChange = { vm.setModuleEnabled(m.id, it) },
                    headline = { Text(stringResource(m.labelRes)) },
                )
            }
        }
        OnboardingStep.SleepWindow -> {
            Body(R.string.onboarding_sleep_body)
            SleepWindowPicker(s.sleep, onStart = vm::setSleepStart, onEnd = vm::setSleepEnd)
        }
        is OnboardingStep.ModuleSetup -> vm.module(step.id)?.OnboardingContent(Modifier)
        is OnboardingStep.Extra -> vm.extra(step.key)?.Content()
        OnboardingStep.Widget -> WidgetBody(vm.widgetReceivers())
        OnboardingStep.Permissions -> {
            Body(R.string.onboarding_permissions_body)
            val pvm = viewModel(key = "onboarding-permissions") { PermissionsViewModel(permissions, includeReadOnly = false) }
            PermissionRows(pvm)
        }
        OnboardingStep.Ai -> {
            Body(R.string.onboarding_ai_body)
            if (onOpenAiSettings != null) {
                OutlinedButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.padding(vertical = Space.xs).heightIn(min = Space.touchTarget),
                ) { Text(stringResource(R.string.onboarding_ai_open)) }
            }
            Body(R.string.onboarding_ai_later)
        }
    }
}

@Composable
private fun WidgetBody(receivers: List<Class<*>>) {
    val ctx = LocalContext.current
    val manager = remember(ctx) { AppWidgetManager.getInstance(ctx) }
    val canPin = receivers.isNotEmpty() && runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)
    Body(R.string.onboarding_widget_body)
    if (canPin) {
        receivers.forEach { receiver ->
            val component = ComponentName(ctx, receiver)
            // The widget's own name, as the launcher's picker shows it (manifest label).
            val name = remember(component) {
                runCatching { ctx.packageManager.getReceiverInfo(component, 0).loadLabel(ctx.packageManager).toString() }.getOrNull()
            }
            OutlinedButton(
                onClick = { runCatching { manager.requestPinAppWidget(component, null, null) } },
                modifier = Modifier.padding(vertical = Space.xxs).heightIn(min = Space.touchTarget),
            ) {
                Text(name?.let { stringResource(R.string.onboarding_widget_add_named, it) } ?: stringResource(R.string.onboarding_widget_add))
            }
        }
        Body(R.string.onboarding_widget_also)
    } else {
        Body(R.string.onboarding_widget_manual)
    }
}

@Composable
private fun Body(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = Space.xs),
    )
}
