package app.klean.feature.calendar.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import android.util.Log
import androidx.core.content.ContextCompat
import app.klean.core.data.account.KleanAccount
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.DeviceCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** One occurrence of a phone-calendar event (CalendarContract.Instances). Read-only in Klean. */
data class DeviceInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarName: String?,
    val color: Int?,
    val location: String? = null,
)

/** The phone's calendars as the agenda sees them. */
interface DeviceInstances {
    /** Occurrences overlapping [fromMs, toMs), following provider changes. Empty without READ_CALENDAR. */
    fun instances(fromMs: Long, toMs: Long): Flow<List<DeviceInstance>>
    /** The `calendar_source` id of phone calendar [calendarId], for the selection toggle. */
    suspend fun sourceId(calendarId: Long): String
}

/** The provider reads, behind an interface so the source is tested without a ContentResolver. */
interface DeviceCalendarQuery {
    fun canRead(): Boolean
    /** Visible calendars on the phone, without Klean's own mirror. */
    fun calendars(): List<DeviceCalendar>
    fun instances(fromMs: Long, toMs: Long): List<DeviceInstance>
    /** Emits when anything in the calendar provider changes. */
    fun changes(): Flow<Unit>
}

/** Reads the phone's calendars, read-only, after READ_CALENDAR was granted just in time. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DeviceCalendarSource @Inject constructor(
    private val query: DeviceCalendarQuery,
    private val repo: dagger.Lazy<CalendarRepository>,
) : DeviceInstances {
    private val tick = MutableStateFlow(0)

    fun canRead(): Boolean = query.canRead()

    /** Call after the permission was granted or refused, so every open view reads again. */
    fun refresh() {
        tick.value++
    }

    override fun instances(fromMs: Long, toMs: Long): Flow<List<DeviceInstance>> = tick.flatMapLatest {
        if (!query.canRead()) {
            flowOf(emptyList())
        } else {
            query.changes().onStart { emit(Unit) }.map {
                runCatching { query.instances(fromMs, toMs) }
                    .onFailure { Log.w("Klean", "device calendar read failed", it) }
                    .getOrDefault(emptyList())
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sourceId(calendarId: Long): String = repo.get().deviceSourceId(calendarId)

    /** Writes one `calendar_source` row per phone calendar (selection toggles live there). */
    suspend fun syncSources() {
        if (!query.canRead()) return
        val cals = withContext(Dispatchers.IO) { runCatching { query.calendars() }.getOrDefault(emptyList()) }
        repo.get().syncDeviceSources(cals)
    }

    fun calendars(): List<DeviceCalendar> = if (query.canRead()) runCatching { query.calendars() }.getOrDefault(emptyList()) else emptyList()
}

@Singleton
class AndroidDeviceCalendarQuery @Inject constructor(@ApplicationContext private val ctx: Context) : DeviceCalendarQuery {
    private val cr get() = ctx.contentResolver

    override fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private data class Cal(val cal: DeviceCalendar, val visible: Boolean, val klean: Boolean)

    private fun allCalendars(): List<Cal> = cr.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.CALENDAR_COLOR, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE, Calendars.VISIBLE),
        null, null, Calendars.CALENDAR_DISPLAY_NAME,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val cal = DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1) ?: c.getString(3) ?: "",
                    color = if (c.isNull(2)) null else c.getInt(2),
                    accountName = c.getString(3),
                )
                add(Cal(cal, c.getInt(5) != 0, c.getString(4) == KleanAccount.accountType(ctx)))
            }
        }
    }.orEmpty()

    override fun calendars(): List<DeviceCalendar> = allCalendars().filter { it.visible && !it.klean }.map { it.cal }

    override fun instances(fromMs: Long, toMs: Long): List<DeviceInstance> {
        val shown = calendars().map { it.id }.toSet()
        if (shown.isEmpty()) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()
        return cr.query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
                Instances.ALL_DAY, Instances.CALENDAR_DISPLAY_NAME, Instances.DISPLAY_COLOR, Instances.EVENT_LOCATION,
            ),
            null, null, Instances.BEGIN,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val calId = c.getLong(1)
                    if (calId !in shown) continue
                    add(
                        DeviceInstance(
                            eventId = c.getLong(0), calendarId = calId, title = c.getString(2).orEmpty(),
                            begin = c.getLong(3), end = c.getLong(4), allDay = c.getInt(5) != 0,
                            calendarName = c.getString(6), color = if (c.isNull(7)) null else c.getInt(7),
                            location = c.getString(8)?.ifBlank { null },
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching { cr.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }.isSuccess
        awaitClose { if (registered) cr.unregisterContentObserver(observer) }
    }
}
