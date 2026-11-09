package app.klean.feature.onboarding

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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.ModuleRegistry
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
    vm: OnboardingViewModel = viewModel { OnboardingViewModel(registry, quietHours, onboarding) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.finished) { if (s.finished) onFinished() }
    BackHandler(enabled = s.position > 1 && !s.finished) { vm.back() }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.onboarding_progress, s.position, s.steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stepTitle(s.current, vm),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                StepBody(s, vm, permissions, onOpenAiSettings)
            }
            Spacer(Modifier.height(16.dp))
            val first = s.current == OnboardingStep.Welcome
            Row(Modifier.fillMaxWidth()) {
                // Equal weight on purpose: skipping is as normal as going on.
                OutlinedButton(
                    onClick = { if (first) vm.skipAll() else vm.skip() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(if (first) R.string.onboarding_skip_setup else R.string.onboarding_skip)) }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { vm.next() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                first -> R.string.onboarding_start
                                s.isLast -> R.string.onboarding_done
                                else -> R.string.onboarding_next
                            },
                        ),
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
}

@Composable
private fun StepBody(s: OnboardingUiState, vm: OnboardingViewModel, permissions: PermissionChecker, onOpenAiSettings: (() -> Unit)?) {
    when (val step = s.current) {
        OnboardingStep.Welcome -> Body(R.string.onboarding_welcome_body)
        OnboardingStep.Modules -> {
            Body(R.string.onboarding_modules_body)
            s.modules.forEach { m ->
                ListItem(trailingContent = { Switch(checked = m.enabled, onCheckedChange = { vm.setModuleEnabled(m.id, it) }) }) {
                    Text(stringResource(m.labelRes))
                }
            }
        }
        OnboardingStep.SleepWindow -> {
            Body(R.string.onboarding_sleep_body)
            SleepWindowPicker(s.sleep, onStart = vm::setSleepStart, onEnd = vm::setSleepEnd)
        }
        is OnboardingStep.ModuleSetup -> vm.module(step.id)?.OnboardingContent(Modifier)
        OnboardingStep.Widget -> WidgetBody(vm.widgetReceivers())
        OnboardingStep.Permissions -> {
            Body(R.string.onboarding_permissions_body)
            val pvm = viewModel(key = "onboarding-permissions") { PermissionsViewModel(permissions, includeReadOnly = false) }
            PermissionRows(pvm)
        }
        OnboardingStep.Ai -> {
            Body(R.string.onboarding_ai_body)
            if (onOpenAiSettings != null) {
                FilledTonalButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
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
        FilledTonalButton(
            onClick = { runCatching { manager.requestPinAppWidget(ComponentName(ctx, receivers.first()), null, null) } },
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.onboarding_widget_add)) }
        Spacer(Modifier.height(8.dp))
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
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}
