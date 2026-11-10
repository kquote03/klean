package app.klean.feature.contacts.birthday

import android.content.Context
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.notifications.Reschedulable
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.R
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactChangeListener
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.ReminderDays
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.reminderDays
import app.klean.shared.id.nameUuid
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/** The reminder titles ("It's Sara's birthday today"). An interface so the sync is tested without resources. */
interface BirthdayText {
    fun today(name: String, age: Int?): String
    fun inDays(name: String, days: Int, age: Int?): String
}

class AndroidBirthdayText @Inject constructor(@ApplicationContext private val ctx: Context) : BirthdayText {
    override fun today(name: String, age: Int?): String =
        if (age != null && age > 0) ctx.getString(R.string.birthdays_reminder_today_age, name, age)
        else ctx.getString(R.string.birthdays_reminder_today, name)

    override fun inDays(name: String, days: Int, age: Int?): String =
        if (age != null && age > 0) ctx.resources.getQuantityString(R.plurals.birthdays_reminder_in_days_age, days, name, days, age)
        else ctx.resources.getQuantityString(R.plurals.birthdays_reminder_in_days, days, name, days)
}

/**
 * A contact's birthday reminders ("on the day" and "3 days before" by default, per contact or off) as quiet
 * reminders in the shared `reminder` table, written through [ReminderRepository] so the Reminders module rings
 * them (and keeps them out of quiet hours). Each goes off at the reminder hour (09:00) on its day. Reminder ids are
 * name-based on the contact and the number of days, so no extra column is needed and every device agrees. Once one
 * is done or dismissed, it moves to next year's birthday. Removed with the contact, its birthday, or the Birthdays
 * module. No AI is involved.
 *
 * Takes the repositories lazily: both repositories hold this object as a listener.
 */
@Singleton
class BirthdayReminderSync @Inject constructor(
    private val reminders: dagger.Lazy<ReminderRepository>,
    private val contacts: dagger.Lazy<ContactRepository>,
    private val db: KleanDatabase,
    private val settings: dagger.Lazy<SettingsRepository>,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val gate: ModuleGate,
    private val text: BirthdayText,
) : ContactChangeListener, ReminderChangeListener, Reschedulable {
    private val lock = Mutex()

    override suspend fun onContactsChanged(ids: List<String>) = lock.withLock {
        val ctx = context()
        for (id in ids) sync(contacts.get().get(id), id, ctx)
    }

    /** One of ours was handled: next year's takes over. */
    override suspend fun onChanged(id: String) {
        // Our own writes land here too (with the lock held): never wait for the lock on those.
        val r = db.reminderDao().get(id)?.takeUnless { it.sync.deleted } ?: return
        if (r.state != DefaultReminderRepository.DONE && r.state != DefaultReminderRepository.DISMISSED) return
        val contact = contacts.get().live().firstOrNull { c -> CANDIDATE_DAYS(c).any { reminderId(c.id, it) == id } } ?: return
        lock.withLock { sync(contact, contact.id, context()) }
    }

    /** Every contact again: startup, boot, a clock change, the module switched on or off, a settings change. */
    override suspend fun reschedule() = lock.withLock {
        val ctx = context()
        for (c in contacts.get().live()) sync(c, c.id, ctx)
    }

    private class Ctx(val on: Boolean, val hour: Int, val showAge: Boolean, val now: Long, val today: LocalDate, val zone: String)

    private suspend fun context(): Ctx {
        val tz = zone.current()
        val now = clock.now().toEpochMilliseconds()
        return Ctx(
            on = gate.isEnabled(ModuleId.BIRTHDAYS),
            hour = settings.get().get(ContactsKeys.ReminderHour).coerceIn(0, 23),
            showAge = settings.get().get(ContactsKeys.ShowAge),
            now = now, today = now.toLocal(tz).date, zone = tz.id,
        )
    }

    private suspend fun sync(c: ContactEntity?, contactId: String, ctx: Ctx) {
        val b = c?.takeUnless { it.sync.deleted }?.birthday
        val wanted = if (b == null || !ctx.on) emptyList() else c.reminderDays
        val candidates = (ReminderDays.CHOICES + wanted + (c?.reminderDays ?: emptyList())).distinct()
        for (n in candidates) {
            val rid = reminderId(contactId, n)
            val existing = db.reminderDao().get(rid)
            val live = existing?.takeUnless { it.sync.deleted }
            if (n !in wanted || b == null || c == null) {
                if (live != null) reminders.get().delete(rid)
                continue
            }
            val plan = plan(b, n, ctx.today, ctx.hour, live, ctx) ?: continue
            val title = title(c.displayName, b, n, plan.day, ctx.showAge)
            if (live != null && live.fireAt == plan.fireAt && live.title == title && live.state !in HANDLED) continue
            if (live != null && live.fireAt == plan.fireAt && live.state in HANDLED) continue
            val now = ctx.now
            reminders.get().update(
                ReminderEntity(
                    id = rid, title = title, fireAt = plan.fireAt, zone = ctx.zone, isAlarm = false,
                    state = DefaultReminderRepository.SCHEDULED,
                    sync = existing?.sync?.copy(deleted = false) ?: SyncColumns(now, now, "", false, 0),
                ),
            )
        }
    }

    private class Plan(val day: LocalDate, val fireAt: Long)

    /**
     * The celebration whose reminder day is today or later; today's counts even when the hour has passed (the
     * Reminders module then rings it at once), unless it was already handled today.
     */
    private fun plan(b: Birthday, n: Int, today: LocalDate, hour: Int, live: ReminderEntity?, ctx: Ctx): Plan? {
        val tz = zone.current()
        for (offset in 0..2) {
            val day = Birthdays.inYear(b, today.year + offset)
            val remindOn = Birthdays.reminderDate(day, n)
            if (remindOn < today) continue
            val fireAt = atMinute(remindOn, hour * 60, tz)
            val handledToday = live != null && live.state in HANDLED && live.fireAt.toLocal(tz).date == remindOn && remindOn == today
            if (handledToday) continue
            // Already moved on to a later one (handled earlier today): a passed hour today never pulls it back.
            if (fireAt < ctx.now && live != null && live.fireAt > fireAt) continue
            // A fired one still waiting for an answer stays as it is.
            if (live != null && live.state !in HANDLED && live.state != DefaultReminderRepository.SCHEDULED &&
                live.fireAt.toLocal(tz).date == remindOn
            ) return Plan(day, live.fireAt)
            return Plan(day, fireAt)
        }
        return null
    }

    private fun title(name: String, b: Birthday, n: Int, day: LocalDate, showAge: Boolean): String {
        val age = if (showAge) Birthdays.ageOn(b, day) else null
        return if (n == 0) text.today(name, age) else text.inDays(name, n, age)
    }

    companion object {
        private val HANDLED = setOf(DefaultReminderRepository.DONE, DefaultReminderRepository.DISMISSED)
        private val CANDIDATE_DAYS: (ContactEntity) -> List<Int> = { (ReminderDays.CHOICES + it.reminderDays).distinct() }

        fun reminderId(contactId: String, daysBefore: Int): String = nameUuid("contacts.birthday-reminder:$contactId:$daysBefore")
    }
}
