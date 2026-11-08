package app.klean.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.shared.module.ModuleId
import kotlinx.serialization.Serializable

@Serializable object NestedGraph
@Serializable object NestedStart
@Serializable object NestedInner

/** A module laid out like Diet (a start screen plus inner screens in one graph), without Hilt. */
class NestedModule : KleanModule {
    override val id: ModuleId = ModuleId.DIET
    override val labelRes: Int = app.klean.feature.diet.R.string.diet_label
    override val icon: ImageVector get() = Icons.Rounded.Restaurant
    override val navOrder: Int = 10
    override val dashboardSlot: DashboardSlot? = null
    override val startRoute: Any = NestedStart
    override val graphRoute: Any = NestedGraph
    override val settingsRoute: Any? = null
    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<NestedGraph>(startDestination = NestedStart) {
            composable<NestedStart> { Button(onClick = { nav.navigate(NestedInner) }) { Text("Open inner") } }
            composable<NestedInner> { Text("Inner screen") }
        }
    }
}
