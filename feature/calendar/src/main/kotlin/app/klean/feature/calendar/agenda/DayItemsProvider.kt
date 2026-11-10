package app.klean.feature.calendar.agenda

import app.klean.shared.module.ModuleId
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

enum class DayItemKind { BIRTHDAY, TASK, REMINDER, OTHER }

/**
 * One dated thing another module shows on the calendar: a birthday, a task's due date, a reminder.
 * [minutes] is the local minute of day, or null for the whole day. [route] is a deep link, or null.
 */
data class DayItem(
    val key: String,
    val date: LocalDate,
    val title: String,
    val minutes: Int? = null,
    val kind: DayItemKind = DayItemKind.OTHER,
    val route: String? = null,
)

/**
 * A module's dated items for the calendar views. Bind with @IntoSet; gate-filtered by [moduleId].
 *
 * Defined here for now so Calendar and Contacts (birthdays) can be built in parallel; after the M19 merge the
 * parent moves it to `core/ui/.../module/Dashboard.kt` and both modules bind into the one set.
 */
interface DayItemsProvider {
    val moduleId: ModuleId
    /** Items dated [from]..[to], both inclusive. */
    fun items(from: LocalDate, to: LocalDate): Flow<List<DayItem>>
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DayItemsMultibindings {
    @Multibinds abstract fun dayItems(): Set<DayItemsProvider>
}
