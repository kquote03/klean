package app.klean.core.notifications

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Which phone a prompt is for when several devices sync (M25): the phone used most recently
 * (`device.last_seen_at`, refreshed when Klean comes to the foreground). The others still post it, silently,
 * so it can be answered anywhere. Alarms are separate: they ring only on the main phone.
 */
class PromptAudience @Inject constructor(
    private val db: KleanDatabase,
    private val prefs: DevicePrefs,
) {
    /** True when this phone should make a sound or vibrate. With no other phones, or on any error, it is. */
    suspend fun isLoudHere(): Boolean = try {
        val me = prefs.deviceId()
        val latest = db.deviceDao().observeAll().first().firstOrNull { it.platform == PHONE }
        latest == null || latest.id == me
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    private companion object {
        const val PHONE = "ANDROID"
    }
}
