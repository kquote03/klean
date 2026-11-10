package app.klean.feature.workout.data

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.workout.model.TemplateText
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import app.klean.shared.id.UuidV7
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

object WorkoutSource {
    const val MANUAL = "MANUAL"
    const val CAPTURE = "CAPTURE"
}

/** A session with its live sets, in order. */
data class SessionWithSets(val session: WorkoutSessionEntity, val sets: List<WorkoutSetEntity>) {
    val isOpen: Boolean get() = session.endedAt == null
}

/** One set of an exercise in its history: when, and what. */
data class ExerciseHistoryRow(val set: WorkoutSetEntity, val sessionId: String, val startedAt: Long)

/** Told after every workout write (outside the transaction): status line, widget, Today, suggestions. */
fun interface WorkoutChangeListener {
    suspend fun onWorkoutsChanged()
}

interface WorkoutRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>
    suspend fun template(id: String): WorkoutTemplateEntity?
    /** Creates ([id] null) or edits a template from its text. Returns its id. */
    suspend fun saveTemplate(id: String?, name: String, text: String): String
    suspend fun deleteTemplate(id: String)

    /** Live sessions, newest first, with their sets. */
    fun observeSessions(): Flow<List<SessionWithSets>>
    fun observeSession(id: String): Flow<SessionWithSets?>
    suspend fun session(id: String): SessionWithSets?
    /** The session still running, if any. */
    suspend fun openSession(): WorkoutSessionEntity?

    /** Starts a session (from [templateId], or empty), or returns the one already running. */
    suspend fun start(templateId: String? = null): String
    /** Logs one set of [item] in [sessionId]. */
    suspend fun addSet(sessionId: String, item: WorkoutItem): String
    /** Logs every set of [items] ("3×15 pushups" = three sets of 15). */
    suspend fun logItems(sessionId: String, items: List<WorkoutItem>)
    suspend fun updateSet(id: String, reps: Int?, weightKg: Double?, durationS: Int?, distanceM: Int?)
    suspend fun deleteSet(id: String)
    suspend fun finish(sessionId: String)
    suspend fun updateSession(id: String, startedAt: Long, endedAt: Long?, notes: String?)
    /** Tombstones the session and its sets. */
    suspend fun delete(sessionId: String)
    /** Undo of [delete]. */
    suspend fun restore(sessionId: String)

    /** Freeform log: a finished session at [at] with what [text] says ("did 30 pushups"). Never fails. */
    suspend fun logText(text: String, at: Long, source: String = WorkoutSource.MANUAL): String

    fun observeExercises(): Flow<List<ExerciseDefinitionEntity>>
    /** Every set of [exerciseId], newest first. */
    fun observeHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>>
    suspend fun exercise(id: String): ExerciseDefinitionEntity?

    suspend fun lastSessionStart(): Long?
}

/**
 * Workout data. Exercise definitions are made from typed names as they're used (case-insensitive match,
 * no manual curation). Every write is stamped for sync; nothing is hard-deleted.
 */
@Singleton
class DefaultWorkoutRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards WorkoutChangeListener>,
) : WorkoutRepository {
    private val templates get() = db.workoutTemplateDao()
    private val sessions get() = db.workoutSessionDao()
    private val sets get() = db.workoutSetDao()
    private val exercises get() = db.exerciseDefinitionDao()

    private fun now() = clock.now().toEpochMilliseconds()

    override fun observeTemplates(): Flow<List<WorkoutTemplateEntity>> = templates.observeAll()
    override suspend fun template(id: String): WorkoutTemplateEntity? = templates.get(id)?.takeUnless { it.sync.deleted }

    override suspend fun saveTemplate(id: String?, name: String, text: String): String {
        val items = TemplateText.parse(text)
        val title = name.trim().ifEmpty { items.firstOrNull()?.exercise ?: "Workout" }
        val out = db.transact {
            val prev = id?.let { templates.get(it) }
            if (prev != null) {
                templates.upsert(prev.copy(name = title, items = WorkoutItem.encode(items), sync = stamper.stamp(prev.sync)))
                prev.id
            } else {
                UuidV7.generate(now()).also {
                    templates.upsert(WorkoutTemplateEntity(it, title, WorkoutItem.encode(items), position = 0, sync = stamper.stamp()))
                }
            }
        }
        items.forEach { exerciseFor(it) }
        notifyChanged()
        return out
    }

    override suspend fun deleteTemplate(id: String) {
        db.transact {
            val t = templates.get(id) ?: return@transact
            if (!t.sync.deleted) templates.upsert(t.copy(sync = stamper.stamp(t.sync, deleted = true)))
        }
        notifyChanged()
    }

    override fun observeSessions(): Flow<List<SessionWithSets>> = combine(sessions.observeAll(), sets.observeAll()) { s, all ->
        val bySession = all.groupBy { it.sessionId }
        s.map { SessionWithSets(it, bySession[it.id].orEmpty().sortedBy { x -> x.position }) }
    }

    override fun observeSession(id: String): Flow<SessionWithSets?> = combine(sessions.observeAll(), sets.observeForSession(id)) { s, l ->
        s.firstOrNull { it.id == id }?.let { SessionWithSets(it, l) }
    }

    override suspend fun session(id: String): SessionWithSets? =
        sessions.get(id)?.takeUnless { it.sync.deleted }?.let { SessionWithSets(it, sets.forSession(id)) }

    override suspend fun openSession(): WorkoutSessionEntity? = sessions.open()

    override suspend fun start(templateId: String?): String {
        openSession()?.let { return it.id }
        val id = db.transact {
            UuidV7.generate(now()).also {
                sessions.upsert(WorkoutSessionEntity(it, templateId = templateId, startedAt = now(), source = WorkoutSource.MANUAL, sync = stamper.stamp()))
            }
        }
        notifyChanged()
        return id
    }

    override suspend fun addSet(sessionId: String, item: WorkoutItem): String {
        val def = exerciseFor(item)
        val id = db.transact { insertSet(sessionId, item, def.id) }
        notifyChanged()
        return id
    }

    override suspend fun logItems(sessionId: String, items: List<WorkoutItem>) {
        val defs = items.map { exerciseFor(it) }
        db.transact {
            items.forEachIndexed { i, item -> repeat(item.sets.coerceIn(1, MAX_SETS)) { insertSet(sessionId, item, defs[i].id) } }
        }
        notifyChanged()
    }

    private suspend fun insertSet(sessionId: String, item: WorkoutItem, exerciseId: String): String {
        val position = (sets.forSession(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        val id = UuidV7.generate(now())
        sets.upsert(
            WorkoutSetEntity(
                id = id, sessionId = sessionId, exercise = item.exercise, kind = item.kind.name, position = position,
                reps = item.reps, weightKg = item.weightKg, durationS = item.durationS, distanceM = item.distanceM,
                exerciseId = exerciseId, sync = stamper.stamp(),
            ),
        )
        return id
    }

    override suspend fun updateSet(id: String, reps: Int?, weightKg: Double?, durationS: Int?, distanceM: Int?) {
        db.transact {
            val s = sets.get(id)?.takeUnless { it.sync.deleted } ?: return@transact
            sets.upsert(s.copy(reps = reps, weightKg = weightKg, durationS = durationS, distanceM = distanceM, sync = stamper.stamp(s.sync)))
        }
        notifyChanged()
    }

    override suspend fun deleteSet(id: String) {
        db.transact {
            val s = sets.get(id) ?: return@transact
            if (!s.sync.deleted) sets.upsert(s.copy(sync = stamper.stamp(s.sync, deleted = true)))
        }
        notifyChanged()
    }

    override suspend fun finish(sessionId: String) {
        db.transact {
            val s = sessions.get(sessionId)?.takeUnless { it.sync.deleted || it.endedAt != null } ?: return@transact
            sessions.upsert(s.copy(endedAt = now().coerceAtLeast(s.startedAt), sync = stamper.stamp(s.sync)))
        }
        notifyChanged()
    }

    override suspend fun updateSession(id: String, startedAt: Long, endedAt: Long?, notes: String?) {
        db.transact {
            val s = sessions.get(id)?.takeUnless { it.sync.deleted } ?: return@transact
            val end = endedAt?.coerceAtLeast(startedAt)
            sessions.upsert(s.copy(startedAt = startedAt, endedAt = end, notes = notes?.trim()?.ifEmpty { null }, sync = stamper.stamp(s.sync)))
        }
        notifyChanged()
    }

    override suspend fun delete(sessionId: String) = setDeleted(sessionId, true)

    override suspend fun restore(sessionId: String) = setDeleted(sessionId, false)

    private suspend fun setDeleted(sessionId: String, deleted: Boolean) {
        db.transact {
            val s = sessions.get(sessionId) ?: return@transact
            if (s.sync.deleted != deleted) sessions.upsert(s.copy(sync = stamper.stamp(s.sync, deleted = deleted)))
            sets.allForSession(sessionId).filter { it.sync.deleted != deleted && (deleted || it.sync.updatedAt >= s.sync.updatedAt) }
                .forEach { sets.upsert(it.copy(sync = stamper.stamp(it.sync, deleted = deleted))) }
        }
        notifyChanged()
    }

    override suspend fun logText(text: String, at: Long, source: String): String {
        val items = TemplateText.parseLog(text)
        val defs = items.map { exerciseFor(it) }
        val total = items.sumOf { (it.durationS ?: 0) * it.sets.coerceAtLeast(1) }
        val id = db.transact {
            val id = UuidV7.generate(now())
            sessions.upsert(
                WorkoutSessionEntity(
                    id = id, startedAt = at - total * 1000L, endedAt = at, source = source,
                    // Nothing parsed ("gym", "climbing with Sam"): the words are kept as the session's note.
                    notes = text.trim().takeIf { items.isEmpty() && it.isNotEmpty() },
                    sync = stamper.stamp(),
                ),
            )
            items.forEachIndexed { i, item -> repeat(item.sets.coerceIn(1, MAX_SETS)) { insertSet(id, item, defs[i].id) } }
            id
        }
        notifyChanged()
        return id
    }

    override fun observeExercises(): Flow<List<ExerciseDefinitionEntity>> = exercises.observeAll()

    override suspend fun exercise(id: String): ExerciseDefinitionEntity? = exercises.get(id)

    override fun observeHistory(exerciseId: String): Flow<List<ExerciseHistoryRow>> =
        combine(sessions.observeAll(), sets.observeAll()) { s, all ->
            val starts = s.associate { it.id to it.startedAt }
            all.filter { it.exerciseId == exerciseId }
                .mapNotNull { set -> starts[set.sessionId]?.let { ExerciseHistoryRow(set, set.sessionId, it) } }
                .sortedWith(compareByDescending<ExerciseHistoryRow> { it.startedAt }.thenBy { it.set.position })
        }

    override suspend fun lastSessionStart(): Long? = sessions.lastSessionStart()

    /** The definition for [item]'s name, made the first time the name is typed. */
    private suspend fun exerciseFor(item: WorkoutItem): ExerciseDefinitionEntity {
        val name = item.exercise.trim()
        exercises.byName(name)?.let { return it }
        return db.transact {
            exercises.byName(name) ?: ExerciseDefinitionEntity(
                id = UuidV7.generate(now()), name = name, kind = item.kind.name,
                defaultReps = item.reps, defaultDurationS = item.durationS, sync = stamper.stamp(),
            ).also { exercises.upsert(it) }
        }
    }

    private suspend fun notifyChanged() = listeners.forEach { it.onWorkoutsChanged() }

    companion object {
        /** A typo like "300x10" shouldn't create hundreds of rows. */
        const val MAX_SETS = 20
    }
}

/** Kind of a stored set, tolerant of unknown values from newer versions. */
fun WorkoutSetEntity.workoutKind(): WorkoutKind = runCatching { WorkoutKind.valueOf(kind) }.getOrDefault(WorkoutKind.STRENGTH)

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutDataModule {
    @Binds abstract fun repo(impl: DefaultWorkoutRepository): WorkoutRepository
    @Multibinds abstract fun listeners(): Set<WorkoutChangeListener>
}
