package app.klean.feature.contacts.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.PersonAdd
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.contacts.ContactEditRoute
import app.klean.feature.contacts.ContactsLinks
import app.klean.feature.contacts.R
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** People's one tap on Today: a new contact (Contacts has no capture target, so it opens the editor). */
@Singleton
class PeopleGlanceProvider @Inject constructor(@ApplicationContext private val ctx: Context) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.CONTACTS

    override fun glance(): Flow<GlanceContribution> = flowOf(
        GlanceContribution(quickAction = QuickAction.Open(moduleId, ctx.getString(R.string.contacts_glance_quick), Icons.Rounded.PersonAdd, ContactEditRoute())),
    )
}

/** Who needs attention: birthdays today and in the next [WITHIN_DAYS] days ("Sara turns 30 · Today"). */
@Singleton
class BirthdayGlanceProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val surfaces: BirthdaySurfaces,
    private val text: BirthdaySurfaceText,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.BIRTHDAYS

    override fun glance(): Flow<GlanceContribution> = surfaces.upcoming(WITHIN_DAYS).map { (_, list) ->
        GlanceContribution(
            attention = list.map { b ->
                GlanceRow(
                    key = "birthday:${b.contactId}",
                    module = moduleId,
                    text = text.title(b.name, b.age),
                    at = atMinute(b.date, 0, zone.current()),
                    detail = when (b.daysUntil) {
                        0 -> ctx.getString(R.string.contacts_glance_today)
                        1 -> text.tomorrow()
                        else -> java.time.DayOfWeek.of(b.date.dayOfWeek.ordinal + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
                    },
                    icon = Icons.Rounded.Cake,
                    route = ContactsLinks.contact(b.contactId),
                )
            },
        )
    }

    companion object {
        const val WITHIN_DAYS = 3
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class PeopleGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun peopleGlanceProvider(p: PeopleGlanceProvider): TodayGlanceProvider
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun birthdayGlanceProvider(p: BirthdayGlanceProvider): TodayGlanceProvider
}
