package app.klean.feature.reminders.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.DONE
import app.klean.feature.reminders.RINGING
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SCHEDULED
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SNOOZED
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.reminders.R
import app.klean.feature.reminders.ReminderTiming
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Reminders on Today's whiteboard: one that went off and is still waiting needs attention; the rest of today's are
 * coming up; the ones marked done today are in "so far today". All open the Reminders tab.
 */
@Singleton
class RemindersGlanceProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: dagger.Lazy<KleanDatabase>,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.REMINDERS

    private val minutes: Flow<Long> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(60_000)
        }
    }

    override fun glance(): Flow<GlanceContribution> = combine(db.get().reminderDao().observeAll(), minutes) { all, now ->
        val tz = zone.current()
        val today = now.toLocal(tz).date
        fun isToday(ms: Long) = ms.toLocal(tz).date == today
        val live = all.filter { !it.sync.deleted }
        fun row(r: ReminderEntity, at: Long) =
            GlanceRow("reminder:${r.id}", moduleId, r.title, at, icon = Icons.Rounded.NotificationsNone, route = AndroidReminderNotifier.DEEP_LINK_REMINDERS)
        GlanceContribution(
            attention = live.filter { it.state == RINGING }.map { row(it, it.lastFiredAt ?: it.fireAt) }
                .filter { isToday(it.at!!) },
            upcoming = live.filter { (it.state == SCHEDULED || it.state == SNOOZED) && isToday(ReminderTiming.dueAt(it)) }
                .map { row(it, ReminderTiming.dueAt(it)) },
            done = live.filter { it.state == DONE && isToday(it.lastFiredAt ?: it.fireAt) }.map { row(it, it.lastFiredAt ?: it.fireAt) },
            quickAction = QuickAction.Capture(moduleId, ctx.getString(R.string.reminders_glance_quick), Icons.Rounded.NotificationsNone),
        )
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class RemindersGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun remindersGlanceProvider(p: RemindersGlanceProvider): TodayGlanceProvider
}
