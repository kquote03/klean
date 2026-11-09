package app.klean.feature.diet.data

import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.transact
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.QuickRepeatRanker
import app.klean.shared.diet.RepeatCandidate
import app.klean.shared.id.UuidV7
import app.klean.shared.time.DayWindow
import app.klean.shared.time.DayWindows
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.QuietHours
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class TodaySummary(val count: Int, val lastText: String?, val lastAt: Long?)

/** Settings the diet screens render with. */
data class DietViewPrefs(val quietHours: QuietHours, val gapThresholdMinutes: Int, val showCalories: Boolean)

/**
 * The only way diet data is written. Every write stamps sync columns inside one transaction and then
 * fans out through [DietChangeDispatcher]. An interface so view models can be tested with fakes.
 */
interface DietRepository {
    /** Last known sleep window; drives the logical day without suspending. */
    val quietHours: StateFlow<QuietHours>

    suspend fun logEntry(
        text: String,
        source: CaptureSource,
        eatenAt: Long? = null,
        photoFile: String? = null,
        viaVoice: Boolean = false,
    ): String

    /** Changes only the given fields. `raw_text` changes only when [text] is passed. */
    suspend fun edit(id: String, text: String? = null, eatenAt: Long? = null, photoFile: String? = null, clearPhoto: Boolean = false)

    /** Tombstones the entry and its items. */
    suspend fun delete(id: String)

    /** Undoes [delete]: clears the tombstone on the entry and on the items that delete tombstoned. */
    suspend fun restore(id: String)

    /** The user corrects an AI item. It becomes EDITED and keeps its provider id. */
    suspend fun editItem(itemId: String, name: String, portion: String?, calories: Int?)

    /** "Not right": hides one AI item (REJECTED). */
    suspend fun rejectItem(itemId: String)

    /** "Remove AI details": hides every item of the entry. The entry and its text stay as they are. */
    suspend fun rejectAllItems(entryId: String)

    suspend fun get(id: String): FoodEntryEntity?
    fun observeEntry(id: String): Flow<FoodEntryEntity?>
    fun observeDay(date: LocalDate): Flow<List<FoodEntryEntity>>
    fun observeItems(entryIds: List<String>): Flow<List<FoodItemEntity>>

    /** `eaten_at` of live entries in [fromMs, toMs). History groups them by logical day. */
    fun observeEatenTimes(fromMs: Long, toMs: Long): Flow<List<Long>>
    fun observeViewPrefs(): Flow<DietViewPrefs>
    fun windowFor(date: LocalDate): DayWindow
    suspend fun today(): List<FoodEntryEntity>
    suspend fun todaySummary(): TodaySummary
    fun todayDate(): LocalDate
    suspend fun quickRepeats(): List<String>
}

@Singleton
class DefaultDietRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val settings: SettingsRepository,
    private val dispatcher: DietChangeDispatcher,
    private val enrichmentPolicy: EnrichmentPolicy,
    @ApplicationScope scope: CoroutineScope,
) : DietRepository {

    override val quietHours: StateFlow<QuietHours> =
        settings.observe(SyncedKeys.QuietHoursKey).stateIn(scope, SharingStarted.Eagerly, QuietHours.DEFAULT)

    private fun now() = clock.now().toEpochMilliseconds()

    private suspend fun initialEnrichment() =
        if (enrichmentPolicy.hasProviderFor(AiTask.ENTRY_PARSE)) EnrichmentStatus.PENDING else EnrichmentStatus.NOT_REQUESTED

    override suspend fun logEntry(text: String, source: CaptureSource, eatenAt: Long?, photoFile: String?, viaVoice: Boolean): String {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "text required" }
        val now = now()
        val id = UuidV7.generate(now)
        val enrichment = initialEnrichment()
        db.transact {
            db.foodEntryDao().upsert(
                FoodEntryEntity(
                    id = id, rawText = clean, eatenAt = eatenAt ?: now, eatenAtZone = zone.current().id,
                    photoFile = photoFile, source = source, viaVoice = viaVoice, mealType = null,
                    mealTypeProvider = null, enrichment = enrichment, sync = stamper.stamp(),
                ),
            )
        }
        dispatcher.dispatch(DietChange.Logged(id, clean))
        return id
    }

    override suspend fun edit(id: String, text: String?, eatenAt: Long?, photoFile: String?, clearPhoto: Boolean) {
        val newText = text?.trim()
        require(newText == null || newText.isNotEmpty()) { "text required" }
        val reEnrich = initialEnrichment() == EnrichmentStatus.PENDING
        val changed = db.transact {
            val prev = db.foodEntryDao().get(id) ?: return@transact false
            val textChanged = newText != null && newText != prev.rawText
            val next = prev.copy(
                rawText = if (textChanged && newText != null) newText else prev.rawText,
                eatenAt = eatenAt ?: prev.eatenAt,
                photoFile = when {
                    clearPhoto -> null
                    photoFile != null -> photoFile
                    else -> prev.photoFile
                },
                enrichment = if (textChanged && reEnrich) EnrichmentStatus.PENDING else prev.enrichment,
            )
            if (next == prev) return@transact false
            db.foodEntryDao().upsert(next.copy(sync = stamper.stamp(prev.sync)))
            true
        }
        if (changed) dispatcher.dispatch(DietChange.Edited(id))
    }

    override suspend fun delete(id: String) {
        val deleted = db.transact {
            val prev = db.foodEntryDao().get(id) ?: return@transact false
            if (prev.sync.deleted) return@transact false
            db.foodEntryDao().upsert(prev.copy(sync = stamper.stamp(prev.sync, deleted = true)))
            val items = db.foodItemDao().forEntry(id).filterNot { it.sync.deleted }
            if (items.isNotEmpty()) {
                db.foodItemDao().upsertAll(items.map { it.copy(sync = stamper.stamp(it.sync, deleted = true)) })
            }
            true
        }
        if (deleted) dispatcher.dispatch(DietChange.Deleted(id))
    }

    override suspend fun restore(id: String) {
        val restored = db.transact {
            val prev = db.foodEntryDao().get(id) ?: return@transact false
            if (!prev.sync.deleted) return@transact false
            // Items tombstoned by delete() were stamped right after the entry; older tombstones stay.
            val items = db.foodItemDao().forEntry(id).filter { it.sync.deleted && it.sync.updatedAt >= prev.sync.updatedAt }
            db.foodEntryDao().upsert(prev.copy(sync = stamper.stamp(prev.sync, deleted = false)))
            if (items.isNotEmpty()) {
                db.foodItemDao().upsertAll(items.map { it.copy(sync = stamper.stamp(it.sync, deleted = false)) })
            }
            true
        }
        if (restored) dispatcher.dispatch(DietChange.Edited(id))
    }

    override suspend fun editItem(itemId: String, name: String, portion: String?, calories: Int?) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "name required" }
        val entryId = db.transact {
            val prev = db.foodItemDao().get(itemId)?.takeIf { !it.sync.deleted } ?: return@transact null
            val next = prev.copy(
                name = cleanName.take(80),
                portion = portion?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
                calories = calories?.takeIf { it in 0..5000 },
                state = FoodItemState.EDITED,
            )
            if (next == prev) return@transact null
            db.foodItemDao().upsertAll(listOf(next.copy(sync = stamper.stamp(prev.sync))))
            prev.entryId
        }
        if (entryId != null) dispatcher.dispatch(DietChange.Edited(entryId))
    }

    override suspend fun rejectItem(itemId: String) {
        val entryId = db.transact {
            val prev = db.foodItemDao().get(itemId)?.takeIf { !it.sync.deleted && it.state != FoodItemState.REJECTED }
                ?: return@transact null
            db.foodItemDao().upsertAll(listOf(prev.copy(state = FoodItemState.REJECTED, sync = stamper.stamp(prev.sync))))
            prev.entryId
        }
        if (entryId != null) dispatcher.dispatch(DietChange.Edited(entryId))
    }

    override suspend fun rejectAllItems(entryId: String) {
        val changed = db.transact {
            val live = db.foodItemDao().forEntry(entryId).filter { !it.sync.deleted && it.state != FoodItemState.REJECTED }
            if (live.isEmpty()) return@transact false
            db.foodItemDao().upsertAll(live.map { it.copy(state = FoodItemState.REJECTED, sync = stamper.stamp(it.sync)) })
            true
        }
        if (changed) dispatcher.dispatch(DietChange.Edited(entryId))
    }

    override suspend fun get(id: String): FoodEntryEntity? = db.foodEntryDao().get(id)

    override fun observeEntry(id: String): Flow<FoodEntryEntity?> = db.foodEntryDao().observe(id)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDay(date: LocalDate): Flow<List<FoodEntryEntity>> =
        settings.observe(SyncedKeys.QuietHoursKey).flatMapLatest { q ->
            val w = DayWindows.forDate(date, q, zone.current())
            db.foodEntryDao().observeRange(w.start, w.end)
        }

    override fun observeItems(entryIds: List<String>): Flow<List<FoodItemEntity>> =
        if (entryIds.isEmpty()) flowOf(emptyList()) else db.foodItemDao().observeForEntries(entryIds)

    override fun observeEatenTimes(fromMs: Long, toMs: Long): Flow<List<Long>> =
        db.foodEntryDao().observeEatenBetween(fromMs, toMs)

    override fun observeViewPrefs(): Flow<DietViewPrefs> = combine(
        settings.observe(SyncedKeys.QuietHoursKey),
        settings.observe(SyncedKeys.DietGapThresholdMinutes),
        settings.observe(SyncedKeys.DietShowCalories),
        ::DietViewPrefs,
    )

    override fun windowFor(date: LocalDate): DayWindow = DayWindows.forDate(date, quietHours.value, zone.current())

    override suspend fun today(): List<FoodEntryEntity> {
        val q = settings.get(SyncedKeys.QuietHoursKey)
        val tz = zone.current()
        val w = DayWindows.forDate(DayWindows.logicalDate(now(), q, tz), q, tz)
        return db.foodEntryDao().range(w.start, w.end)
    }

    override suspend fun todaySummary(): TodaySummary {
        val now = now()
        val entries = today().filter { it.eatenAt <= now }
        val last = entries.maxByOrNull { it.eatenAt }
        return TodaySummary(entries.size, last?.rawText, last?.eatenAt)
    }

    override fun todayDate(): LocalDate = DayWindows.logicalDate(now(), quietHours.value, zone.current())

    override suspend fun quickRepeats(): List<String> {
        val now = now()
        val since = now - REPEAT_LOOKBACK_MS
        val rows = db.foodEntryDao().repeatRows(since) + db.foodItemDao().acceptedNamesSince(since)
        return QuickRepeatRanker.rank(rows.map { RepeatCandidate(it.text, it.loggedAt) }, now)
    }

    private companion object {
        const val REPEAT_LOOKBACK_MS = 30 * 24 * HOUR_MS
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietDataModule {
    @Binds abstract fun repository(impl: DefaultDietRepository): DietRepository
}
