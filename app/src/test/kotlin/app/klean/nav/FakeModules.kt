package app.klean.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.shared.module.ModuleId
import kotlinx.serialization.Serializable

@Serializable object FakeRoute1
@Serializable object FakeRoute2
@Serializable object FakeRoute3
@Serializable object FakeRoute4
@Serializable object FakeRoute5

/** A one-screen module with a framework string as its label (no Hilt, no feature resources). */
class FakeScreenModule(
    override val id: ModuleId,
    override val navOrder: Int,
    override val labelRes: Int,
    private val route: Any,
    private val register: NavGraphBuilder.() -> Unit,
) : KleanModule {
    override val icon: ImageVector get() = Icons.Rounded.Star
    override val dashboardSlot: DashboardSlot? = null
    override val startRoute: Any = route
    override val settingsRoute: Any? = null
    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = builder.register()
}

/** Five modules labelled OK, Cancel, Copy, Paste, Cut (in nav order), each opening "Screen of <label>". */
fun fiveFakeModules(): Set<KleanModule> = setOf(
    FakeScreenModule(ModuleId.TASKS, 20, android.R.string.ok, FakeRoute1) { composable<FakeRoute1> { Text("Screen of OK") } },
    FakeScreenModule(ModuleId.REMINDERS, 30, android.R.string.cancel, FakeRoute2) { composable<FakeRoute2> { Text("Screen of Cancel") } },
    FakeScreenModule(ModuleId.CALENDAR, 40, android.R.string.copy, FakeRoute3) { composable<FakeRoute3> { Text("Screen of Copy") } },
    FakeScreenModule(ModuleId.CONTACTS, 50, android.R.string.paste, FakeRoute4) { composable<FakeRoute4> { Text("Screen of Paste") } },
    FakeScreenModule(ModuleId.WORKOUT, 60, android.R.string.cut, FakeRoute5) { composable<FakeRoute5> { Text("Screen of Cut") } },
)
