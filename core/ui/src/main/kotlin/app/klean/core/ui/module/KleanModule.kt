package app.klean.core.ui.module

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Default order of cards on Today (holistic plan H4): approvals first, diet last. Independent of
 * navigation order; the user can rearrange or hide cards once (device-local, `arrangeCards`).
 */
enum class DashboardSlot { APPROVALS, TASKS, AGENDA, BIRTHDAYS, SLEEP, WORKOUT, DIET }

/** A life module (diet, tasks, ...). Each feature contributes one into the Hilt `Set<KleanModule>`. */
interface KleanModule {
    val id: ModuleId
    @get:StringRes val labelRes: Int
    val icon: ImageVector
    val navOrder: Int
    val dashboardSlot: DashboardSlot?
    val startRoute: Any
    /**
     * The nested graph holding all of this module's screens. The bottom bar marks the module as
     * selected anywhere inside it (history, day, entry), not just on [startRoute].
     */
    val graphRoute: Any get() = startRoute
    val settingsRoute: Any?
    /**
     * False for a module that lives inside another one's screen (Reminders is a tab in Tasks, plan H5): it keeps
     * its own gate and settings entry but gets no bottom-bar or More item.
     */
    val inNavigation: Boolean get() = true
    fun registerGraph(builder: NavGraphBuilder, nav: NavController)
    @Composable fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}

    /** Title of this module's page in the setup wizard, or null for no page. */
    @get:StringRes val onboardingTitleRes: Int? get() = null
    /** Body of this module's setup page (its main switches). Settings are written as they change. */
    @Composable fun OnboardingContent(modifier: Modifier) {}
    /** The module's home-screen widget receiver, offered on the widget page of the setup wizard. */
    val widgetReceiver: Class<*>? get() = null

    /** Placeholder of the capture sheet when it is opened for this module only (e.g. its widget). */
    @get:StringRes val capturePlaceholderRes: Int? get() = null

    /** Cancel alarms/notifications/widgets on disable; reschedule on enable. */
    suspend fun onEnabledChanged(enabled: Boolean) {}
}

@Singleton
class ModuleRegistry @Inject constructor(
    modules: Set<@JvmSuppressWildcards KleanModule>,
    private val gate: ModuleGate,
) {
    val all: List<KleanModule> = modules.sortedBy { it.navOrder }
    val enabled: Flow<List<KleanModule>> = gate.disabledIds.map { off -> all.filter { it.id !in off } }
    val dashboardCards: Flow<List<KleanModule>> =
        enabled.map { l -> l.filter { it.dashboardSlot != null }.sortedBy { it.dashboardSlot!!.ordinal } }

    /**
     * Startup: records every module as known. New ones arrive switched on; returns those an existing
     * install should list once ("New in Klean").
     */
    suspend fun seed(): Set<ModuleId> = gate.seedNew(all.map { it.id }.toSet())

    suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        gate.setEnabled(id, enabled)
        all.firstOrNull { it.id == id }?.onEnabledChanged(enabled)
    }
}
