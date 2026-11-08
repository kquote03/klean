package app.klean.feature.diet

import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.data.DietViewPrefs
import app.klean.feature.diet.data.TodaySummary
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.time.DayWindow
import app.klean.shared.time.DayWindows
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** In-memory [DietRepository] that records calls. */
class FakeDietRepository(private val clock: FakeClock) : DietRepository {
    data class LogCall(val text: String, val source: CaptureSource, val eatenAt: Long?)
    data class EditCall(val id: String, val text: String?, val eatenAt: Long?, val photoFile: String?, val clearPhoto: Boolean)

    val entries = MutableStateFlow<List<FoodEntryEntity>>(emptyList())
    val logs = mutableListOf<LogCall>()
    val edits = mutableListOf<EditCall>()
    val deletes = mutableListOf<String>()
    val restores = mutableListOf<String>()
    var repeats = listOf<String>()
    var prefs = DietViewPrefs(QuietHours.DEFAULT, 240, false)
    override val quietHours: StateFlow<QuietHours> = MutableStateFlow(QuietHours.DEFAULT)

    fun entry(id: String, text: String, eatenAt: Long, photo: String? = null) = FoodEntryEntity(
        id, text, eatenAt, "Asia/Dubai", photo, CaptureSource.MODULE_SCREEN, false, null, null,
        EnrichmentStatus.NOT_REQUESTED, SyncColumns(eatenAt, eatenAt, "dev", false, 1),
    )

    override suspend fun logEntry(text: String, source: CaptureSource, eatenAt: Long?, photoFile: String?, viaVoice: Boolean): String {
        logs += LogCall(text, source, eatenAt)
        val id = "id${logs.size}"
        entries.value = (entries.value + entry(id, text.trim(), eatenAt ?: clock.nowMs)).sortedBy { it.eatenAt }
        return id
    }

    override suspend fun edit(id: String, text: String?, eatenAt: Long?, photoFile: String?, clearPhoto: Boolean) {
        edits += EditCall(id, text, eatenAt, photoFile, clearPhoto)
        entries.value = entries.value.map {
            if (it.id != id) it else it.copy(
                rawText = text ?: it.rawText, eatenAt = eatenAt ?: it.eatenAt,
                photoFile = if (clearPhoto) null else photoFile ?: it.photoFile,
            )
        }
    }

    override suspend fun delete(id: String) {
        deletes += id
        entries.value = entries.value.map { if (it.id == id) it.copy(sync = it.sync.copy(deleted = true)) else it }
    }

    override suspend fun restore(id: String) {
        restores += id
        entries.value = entries.value.map { if (it.id == id) it.copy(sync = it.sync.copy(deleted = false)) else it }
    }

    override suspend fun get(id: String) = entries.value.firstOrNull { it.id == id }
    override fun observeEntry(id: String): Flow<FoodEntryEntity?> = entries.map { l -> l.firstOrNull { it.id == id } }
    override fun observeDay(date: LocalDate): Flow<List<FoodEntryEntity>> {
        val w = windowFor(date)
        return entries.map { l -> l.filter { !it.sync.deleted && it.eatenAt >= w.start && it.eatenAt < w.end } }
    }
    override fun observeItems(entryIds: List<String>): Flow<List<FoodItemEntity>> = flowOf(emptyList())
    override fun observeViewPrefs(): Flow<DietViewPrefs> = flowOf(prefs)
    override fun windowFor(date: LocalDate): DayWindow = DayWindows.forDate(date, QuietHours.DEFAULT, Dubai)
    override suspend fun today() = entries.value.filter { !it.sync.deleted && it.eatenAt in windowFor(todayDate()).let { w -> w.start until w.end } }
    override suspend fun todaySummary() = today().let { TodaySummary(it.size, it.lastOrNull()?.rawText, it.lastOrNull()?.eatenAt) }
    override fun todayDate(): LocalDate = DayWindows.logicalDate(clock.nowMs, QuietHours.DEFAULT, Dubai)
    override suspend fun quickRepeats(): List<String> = repeats
}
