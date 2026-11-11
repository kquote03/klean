package app.klean.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * A one-screen module with a framework string as its label (no Hilt, no feature resources). Its start screen is
 * [screen], both as a page and at its route ([register]).
 */
class FakeScreenModule(
    override val id: ModuleId,
    override val navOrder: Int,
    override val labelRes: Int,
    private val route: Any,
    private val screen: @Composable () -> Unit,
    private val register: NavGraphBuilder.() -> Unit,
) : KleanModule {
    override val icon: ImageVector get() = Icons.Rounded.Star
    override val dashboardSlot: DashboardSlot? = null
    override val startRoute: Any = route
    override val settingsRoute: Any? = null
    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = builder.register()

    @Composable override fun StartScreen(nav: NavController, args: android.os.Bundle?) = screen()
}

/** A [FakeScreenModule] whose start route [route] shows [screen]. */
inline fun <reified R : Any> fakeModule(
    id: ModuleId,
    navOrder: Int,
    labelRes: Int,
    route: R,
    noinline screen: @Composable () -> Unit,
): FakeScreenModule = FakeScreenModule(id, navOrder, labelRes, route, screen) { composable<R> { screen() } }

/** Five modules labelled OK, Cancel, Copy, Paste, Cut (in nav order), each opening "Screen of <label>". */
fun fiveFakeModules(): Set<KleanModule> = setOf(
    fakeModule(ModuleId.TASKS, 20, android.R.string.ok, FakeRoute1) { Text("Screen of OK") },
    fakeModule(ModuleId.REMINDERS, 30, android.R.string.cancel, FakeRoute2) { Text("Screen of Cancel") },
    fakeModule(ModuleId.CALENDAR, 40, android.R.string.copy, FakeRoute3) { Text("Screen of Copy") },
    fakeModule(ModuleId.CONTACTS, 50, android.R.string.paste, FakeRoute4) { Text("Screen of Paste") },
    fakeModule(ModuleId.WORKOUT, 60, android.R.string.cut, FakeRoute5) { Text("Screen of Cut") },
)
