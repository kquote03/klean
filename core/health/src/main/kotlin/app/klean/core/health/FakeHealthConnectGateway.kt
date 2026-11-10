package app.klean.core.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * An in-memory Health Connect for tests (and for any build without the real one): records live in lists,
 * [availability] and [granted] are set directly. Writes with the same client id replace the earlier one.
 */
class FakeHealthConnectGateway(
    var available: HealthAvailability = HealthAvailability.AVAILABLE,
    grantedAccess: Set<HealthAccess> = HealthConnectGateway.ALL,
    override val ownPackage: String = "app.klean.test",
) : HealthConnectGateway {
    val sleep = mutableListOf<HcSleep>()
    val exercise = mutableListOf<HcExercise>()
    private val clientIds = mutableMapOf<String, String>()
    private var next = 1
    override val granted = MutableStateFlow(grantedAccess)

    override fun availability(): HealthAvailability = available
    override suspend fun refresh() {}
    override fun permissionsFor(access: Set<HealthAccess>): Set<String> = access.map { "fake.${it.name}" }.toSet()

    override suspend fun readSleep(from: Long, to: Long): List<HcSleep> =
        if (!canUse(HealthAccess.READ_SLEEP)) emptyList() else sleep.filter { it.end > from && it.start < to }

    override suspend fun readExercise(from: Long, to: Long): List<HcExercise> =
        if (!canUse(HealthAccess.READ_EXERCISE)) emptyList() else exercise.filter { it.end > from && it.start < to }

    override suspend fun writeExercise(clientId: String, start: Long, end: Long, title: String?, running: Boolean): String? {
        if (!canUse(HealthAccess.WRITE_EXERCISE)) return null
        val id = clientIds.getOrPut(clientId) { "hc-${next++}" }
        exercise.removeAll { it.id == id }
        exercise += HcExercise(id, start, end, title, ownPackage, running)
        return id
    }
}
