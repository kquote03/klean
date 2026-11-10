package app.klean.core.health

import kotlinx.coroutines.flow.StateFlow

/** Whether Health Connect can be used on this phone. */
enum class HealthAvailability {
    /** Installed and ready (permissions may still be missing). */
    AVAILABLE,
    /** The provider app is there but too old. */
    NEEDS_UPDATE,
    /** Not on this phone (no Play services, older Android without the app, Waydroid). */
    UNAVAILABLE,
}

/** The record types Klean uses, each with the access it needs. */
enum class HealthAccess { READ_SLEEP, READ_EXERCISE, WRITE_EXERCISE }

/** A sleep session read from Health Connect (UTC millis). */
data class HcSleep(val id: String, val start: Long, val end: Long, val origin: String)

/** An exercise session in Health Connect (UTC millis). [running] = a run/jog type. */
data class HcExercise(
    val id: String,
    val start: Long,
    val end: Long,
    val title: String?,
    val origin: String,
    val running: Boolean = false,
)

/**
 * Klean's only door to Health Connect (M22). Everything is best-effort: when Health Connect is missing or a
 * permission isn't granted, reads return nothing and writes return null, so callers never need to branch on
 * the platform. Records are converted to plain millis so the rest of the app never sees `java.time`.
 */
interface HealthConnectGateway {
    fun availability(): HealthAvailability

    /** Granted access, refreshed by [refresh]; empty when unavailable. */
    val granted: StateFlow<Set<HealthAccess>>

    suspend fun refresh()

    /** Health Connect permission strings for [access] (for the request contract). */
    fun permissionsFor(access: Set<HealthAccess>): Set<String>

    suspend fun readSleep(from: Long, to: Long): List<HcSleep>

    suspend fun readExercise(from: Long, to: Long): List<HcExercise>

    /**
     * Writes a finished session. [clientId] is Klean's session id, so a second write of the same session
     * updates instead of duplicating. Returns the Health Connect record id, or null when it couldn't be written.
     */
    suspend fun writeExercise(clientId: String, start: Long, end: Long, title: String?, running: Boolean): String?

    /** This app's package, to tell Klean's own records from other apps'. */
    val ownPackage: String

    companion object {
        /** Every access Klean asks for, in one request. */
        val ALL: Set<HealthAccess> = HealthAccess.entries.toSet()
    }
}

fun HealthConnectGateway.canUse(access: HealthAccess): Boolean =
    availability() == HealthAvailability.AVAILABLE && access in granted.value
