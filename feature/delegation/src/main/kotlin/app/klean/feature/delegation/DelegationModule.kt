package app.klean.feature.delegation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.TaskExtension
import app.klean.feature.delegation.agent.DelegationAgent
import app.klean.feature.delegation.agent.HermesAgent
import app.klean.feature.delegation.agent.PartialAgent
import app.klean.feature.delegation.data.DelegationMode
import app.klean.feature.delegation.hermes.HermesSettings
import app.klean.feature.delegation.notify.AndroidDelegationNotices
import app.klean.feature.delegation.notify.DelegationLinks
import app.klean.feature.delegation.ui.ApprovalScreen
import app.klean.feature.delegation.ui.ApprovalsCard
import app.klean.feature.delegation.ui.ApprovalsCardViewModel
import app.klean.feature.delegation.ui.DelegateSection
import app.klean.feature.delegation.ui.DelegationScreen
import app.klean.feature.delegation.ui.DelegationTopBar
import app.klean.feature.delegation.ui.DelegationsEmpty
import app.klean.feature.delegation.ui.HermesSettingsScreen
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TaskSource
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.delegation.KleanBlocks
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable data object DelegationHomeRoute
@Serializable data class DelegationRoute(val delegationId: String)
@Serializable data class ApprovalRoute(val approvalId: String)
@Serializable data object HermesSettingsRoute

/**
 * AI delegation (M23): hand a task to the user's AI (a draft and subtasks) or to their Hermes (questions and
 * approvals come back as notifications and the APPROVALS card, first on Today). Lives on the task screen
 * (\"Delegate\") and in capture (\"ask Hermes to…\"), not in the bottom bar.
 */
class DelegationKleanModule @Inject constructor() : KleanModule {
    override val id: ModuleId = ModuleId.DELEGATION
    override val labelRes: Int = R.string.delegation_label
    override val icon: ImageVector get() = Icons.Rounded.AutoAwesome
    override val navOrder: Int = 70
    override val dashboardSlot: DashboardSlot = DashboardSlot.APPROVALS
    override val inNavigation: Boolean = false
    override val startRoute: Any = DelegationHomeRoute
    override val settingsRoute: Any = HermesSettingsRoute

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        composable<DelegationHomeRoute>(deepLinks = listOf(navDeepLink { uriPattern = DelegationLinks.BASE })) {
            DelegationHome(onBack = { nav.popBackStack() }, onOpenApproval = { nav.navigate(ApprovalRoute(it)) })
        }
        composable<HermesSettingsRoute>(deepLinks = listOf(navDeepLink { uriPattern = DelegationLinks.SETTINGS })) {
            HermesSettingsScreen(onBack = { nav.popBackStack() })
        }
        composable<ApprovalRoute>(deepLinks = listOf(navDeepLink { uriPattern = "${DelegationLinks.BASE}/approval/{approvalId}" })) {
            ApprovalScreen(onBack = { nav.popBackStack() })
        }
        composable<DelegationRoute>(deepLinks = listOf(navDeepLink { uriPattern = "${DelegationLinks.BASE}/{delegationId}" })) {
            DelegationScreen(onBack = { nav.popBackStack() }, onOpenApproval = { nav.navigate(ApprovalRoute(it)) })
        }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        ApprovalsCard(
            onOpenApproval = { id ->
                runCatching {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(DelegationLinks.approval(id))).setPackage(ctx.packageName))
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun DelegationHome(onBack: () -> Unit, onOpenApproval: (String) -> Unit, vm: ApprovalsCardViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    Scaffold(topBar = { DelegationTopBar(stringResource(R.string.delegation_label), onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            if (pending?.isEmpty() == true) DelegationsEmpty() else ApprovalsCard(onOpenApproval, vm = vm)
        }
    }
}

/** \"Delegate\" on the task screen, while Delegation is on. */
class DelegateTaskExtension @Inject constructor(private val gate: ModuleGate) : TaskExtension {
    @Composable
    override fun Content(taskId: String, title: String, modifier: Modifier) {
        val off by gate.disabledIds.collectAsStateWithLifecycle(initialValue = setOf(ModuleId.DELEGATION))
        if (ModuleId.DELEGATION !in off) DelegateSection(taskId, modifier)
    }
}

/**
 * Capture: \"ask Hermes to book a table…\" makes the task and hands it to Hermes (interactive). Without Hermes
 * set up it stays a task with a partial (AI) delegation, which never blocks the capture itself.
 */
class DelegationCaptureHandler @Inject constructor(
    private val tasks: TasksRepository,
    private val runner: DelegationRunner,
    private val hermes: HermesSettings,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.DELEGATION
    override val actionSpecs: List<ActionSpec> = listOf(ActionSpec(RuleCaptureClassifier.DELEGATE, "Hand a task to Hermes or AI"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(ModuleId.DELEGATION, RuleCaptureClassifier.DELEGATE, 0.9f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        val title = KleanBlocks.delegateTitle(input.text)
        val id = tasks.add(TaskDraft(title, source = TaskSource.CAPTURE))
        val mode = if (hermes.configured()) DelegationMode.INTERACTIVE else DelegationMode.PARTIAL
        runCatching { runner.delegate(id, mode) }
        return id
    }

    override suspend fun undo(recordId: String) = tasks.delete(recordId)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DelegationBindings {
    @Binds @IntoSet abstract fun module(m: DelegationKleanModule): KleanModule
    @Binds @IntoSet abstract fun capture(h: DelegationCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun taskExtension(e: DelegateTaskExtension): TaskExtension
    @Binds abstract fun notices(n: AndroidDelegationNotices): DelegationNotices
    @Binds @Named(DelegationRunner.PARTIAL) abstract fun partial(a: PartialAgent): DelegationAgent
    @Multibinds abstract fun listeners(): Set<app.klean.feature.delegation.data.DelegationChangeListener>

    companion object {
        @Provides @Singleton @Named(HermesSettings.HTTP)
        fun http(): HttpClient = HttpClient(OkHttp) {
            // Runs stream for as long as the agent works; only connecting is bounded.
            install(HttpTimeout) { connectTimeoutMillis = 15_000 }
        }

        @Provides @Singleton @Named(DelegationRunner.HERMES)
        fun hermes(@Named(HermesSettings.HTTP) client: HttpClient, settings: HermesSettings): DelegationAgent = HermesAgent(client, settings)
    }
}
