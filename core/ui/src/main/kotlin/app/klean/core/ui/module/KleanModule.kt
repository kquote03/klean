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

/** Fixed order of cards on Today. Independent of navigation order. */
enum class DashboardSlot { DIET, TASKS, REMINDERS, EVENTS, BIRTHDAYS, SLEEP, WORKOUT }

/** A life module (diet, tasks, ...). Each feature contributes one into the Hilt `Set<KleanModule>`. */
interface KleanModule {
    val id: ModuleId
    @get:StringRes val labelRes: Int
    val icon: ImageVector
    val navOrder: Int
    val dashboardSlot: DashboardSlot?
    val startRoute: Any
    val settingsRoute: Any?
    fun registerGraph(builder: NavGraphBuilder, nav: NavController)
    @Composable fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
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

    suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        gate.setEnabled(id, enabled)
        all.firstOrNull { it.id == id }?.onEnabledChanged(enabled)
    }
}
