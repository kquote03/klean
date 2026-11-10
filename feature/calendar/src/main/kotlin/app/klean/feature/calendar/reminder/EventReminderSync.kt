package app.klean.feature.calendar.reminder

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.notifications.Reschedulable
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventChangeListener
import app.klean.feature.calendar.data.Exdates
import app.klean.feature.calendar.data.RecurrenceExpander
import app.klean.shared.id.nameUuid
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone

/**
 * An event's "remind me N minutes before" as one quiet reminder in the shared `reminder` table (isAlarm = false
 * unless the event asks to ring), written through [ReminderRepository] so the Reminders module rings it. The
 * reminder id is name-based on the event id, so the event ↔ reminder link needs no extra column and every device
 * agrees on it. A recurring event's reminder moves on to the next occurrence once the current one is done or
 * dismissed, and after boot or a clock change.
 *
 * Takes the repositories lazily: both repositories hold this object as a listener.
 */
@Singleton
class EventReminderSync @Inject constructor(
    private val reminders: dagger.Lazy<ReminderRepository>,
    private val events: dagger.Lazy<CalendarRepository>,
    private val db: KleanDatabase,
    private val clock: Clock,
    private val gate: ModuleGate,
) : EventChangeListener, ReminderChangeListener, Reschedulable {
    private val lock = Mutex()

    override suspend fun onEventsChanged(ids: List<String>) = lock.withLock {
        val on = gate.isEnabled(ModuleId.CALENDAR)
        for (id in ids) sync(events.get().get(id), id, on)
    }

    /** A reminder changed: if it is one of ours and was handled, the event's next occurrence takes over. */
    override suspend fun onChanged(id: String) {
        // Our own deletes land here too (with the lock held): never wait for the lock on those.
        val r = db.reminderDao().get(id)?.takeUnless { it.sync.deleted } ?: return
        if (r.state != DefaultReminderRepository.DONE && r.state != DefaultReminderRepository.DISMISSED) return
        val event = events.get().liveEvents().firstOrNull { reminderId(it.id) == id && it.rrule != null } ?: return
        lock.withLock { sync(event, event.id, gate.isEnabled(ModuleId.CALENDAR)) }
    }

    /** Every event again: after boot, a clock change, and when the module is switched on or off. */
    override suspend fun reschedule() = lock.withLock {
        val on = gate.isEnabled(ModuleId.CALENDAR)
        for (e in events.get().liveEvents()) sync(e, e.id, on)
    }

    /** Tombstones every event reminder (the module was switched off). */
    suspend fun removeAll() = reschedule()

    private suspend fun sync(e: EventEntity?, eventId: String, moduleOn: Boolean) {
        val rid = reminderId(eventId)
        val existing = db.reminderDao().get(rid)
        val live = existing?.takeUnless { it.sync.deleted }
        if (e == null || e.sync.deleted || e.reminderMinutes == null || !moduleOn) {
            if (live != null) reminders.get().delete(rid)
            return
        }
        // No future occurrence left: keep whatever the user did with the last one.
        val fireAt = nextFire(e, clock.now().toEpochMilliseconds()) ?: return
        val title = e.title
        if (live != null && live.fireAt == fireAt && live.title == title && live.isAlarm == e.alarm) return
        val now = clock.now().toEpochMilliseconds()
        reminders.get().update(
            ReminderEntity(
                id = rid, title = title, fireAt = fireAt, zone = e.zone, isAlarm = e.alarm,
                state = DefaultReminderRepository.SCHEDULED,
                sync = existing?.sync?.copy(deleted = false) ?: SyncColumns(now, now, "", false, 0),
            ),
        )
    }

    companion object {
        private const val LOOKAHEAD_MS = 400L * 86_400_000L

        fun reminderId(eventId: String): String = nameUuid("calendar.event-reminder:$eventId")

        /** When the reminder for the first occurrence whose reminder time is still ahead should fire, or null. */
        fun nextFire(e: EventEntity, nowMs: Long): Long? {
            val before = (e.reminderMinutes ?: return null) * 60_000L
            val zone = runCatching { TimeZone.of(e.zone) }.getOrDefault(TimeZone.UTC)
            val starts = RecurrenceExpander.occurrences(
                e.startAt, 0, e.rrule, Exdates.decode(e.exdates).toSet(), zone, nowMs + before, nowMs + before + LOOKAHEAD_MS,
            )
            return starts.firstOrNull()?.minus(before)
        }
    }
}
