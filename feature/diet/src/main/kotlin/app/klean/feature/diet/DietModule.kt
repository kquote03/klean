package app.klean.feature.diet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.feature.diet.ui.dashboard.DietDashboardCard
import app.klean.feature.diet.ui.entry.FoodEntryScreen
import app.klean.feature.diet.ui.settings.DietSettingsScreen
import app.klean.feature.diet.ui.today.DietScreen
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

/** The diet module as seen by the registry: nav item, routes, dashboard card, settings entry. */
@Singleton
class DietKleanModule @Inject constructor() : KleanModule {
    override val id: ModuleId = ModuleId.DIET
    override val labelRes: Int = R.string.diet_label
    override val icon: ImageVector get() = Icons.Rounded.Restaurant
    override val navOrder: Int = 10
    override val dashboardSlot: DashboardSlot = DashboardSlot.DIET
    override val startRoute: Any = DietRoute
    override val settingsRoute: Any = DietSettingsRoute

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        composable<DietRoute>(deepLinks = listOf(navDeepLink { uriPattern = DietStatusNotifier.DEEP_LINK_DIET })) {
            DietScreen(onOpenEntry = { nav.navigate(FoodEntryRoute(it)) })
        }
        composable<FoodEntryRoute> { FoodEntryScreen(onBack = { nav.popBackStack() }) }
        composable<DietSettingsRoute> { DietSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = DietDashboardCard(onOpen, modifier)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietModuleBindings {
    @Binds @IntoSet abstract fun module(m: DietKleanModule): KleanModule
}
