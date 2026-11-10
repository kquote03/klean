package app.klean.core.sync

import app.klean.core.data.db.entity.ApprovalRequestEntity
import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.db.entity.DelegationEntity
import app.klean.core.data.db.entity.DelegationMessageEntity
import app.klean.core.data.db.entity.DeviceEntity
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.entity.GeofenceEntity
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SettingEntity
import app.klean.core.data.db.entity.SleepScheduleEntity
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The table list must follow Room's latest exported schema and the entity classes' Kotlin types. */
class SyncTablesTest {
    private val entities = listOf(
        FoodEntryEntity::class, FoodItemEntity::class, PromptLogEntity::class, SettingEntity::class, GeofenceEntity::class,
        TaskListEntity::class, TaskEntity::class, ReminderEntity::class, EventEntity::class, ContactEntity::class,
        WorkoutTemplateEntity::class, WorkoutSessionEntity::class, WorkoutSetEntity::class, SleepSessionEntity::class,
        DelegationEntity::class, DelegationMessageEntity::class, ApprovalRequestEntity::class, CalendarSourceEntity::class,
        ExerciseDefinitionEntity::class, SleepScheduleEntity::class, DeviceEntity::class,
    ).map { it.java }

    private fun latestSchema(): JsonObject {
        val dir = listOf(File("../data/schemas/app.klean.core.data.db.KleanDatabase"), File("core/data/schemas/app.klean.core.data.db.KleanDatabase"))
            .first { it.isDirectory }
        val f = dir.listFiles()!!.maxBy { it.nameWithoutExtension.toInt() }
        return Json.parseToJsonElement(f.readText()).jsonObject["database"]!!.jsonObject
    }

    @Test fun everySyncableTableIsListedAndLocalOnesAreNot() {
        val tables = latestSchema()["entities"]!!.jsonArray.map { it.jsonObject }
        val synced = tables.filter { t -> (t["fields"] as JsonArray).any { it.jsonObject["columnName"]!!.jsonPrimitive.content == "change_seq" } }
            .map { it["tableName"]!!.jsonPrimitive.content }.toSet()
        val local = tables.map { it["tableName"]!!.jsonPrimitive.content }.toSet() - synced
        assertEquals(synced, SyncTables.ALL.map { it.name }.toSet())
        assertEquals(SyncTables.LOCAL, local)
    }

    @Test fun booleanColumnsMatchTheEntityTypes() {
        val tables = latestSchema()["entities"]!!.jsonArray.map { it.jsonObject }
        for (t in tables) {
            val name = t["tableName"]!!.jsonPrimitive.content
            val spec = SyncTables.byName[name] ?: continue
            val entity = entities.single { it.getAnnotation(androidx.room.Entity::class.java)?.tableName == name || tableOf(it) == name }
            val booleans = (t["fields"] as JsonArray).map { it.jsonObject }.filter { f ->
                val type = fieldType(entity, f["fieldPath"]!!.jsonPrimitive.content)
                type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java
            }.map { it["columnName"]!!.jsonPrimitive.content }.toSet()
            assertEquals("booleans of $name", booleans, spec.booleans + "deleted")
        }
    }

    @Test fun localAndBlobColumnsExist() {
        val tables = latestSchema()["entities"]!!.jsonArray.map { it.jsonObject }.associateBy { it["tableName"]!!.jsonPrimitive.content }
        for (spec in SyncTables.ALL) {
            val cols = (tables.getValue(spec.name)["fields"] as JsonArray).map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet()
            (spec.localColumns + spec.blobs.keys + spec.booleans).forEach { c -> assert(c in cols) { "${spec.name}.$c" } }
        }
    }

    /** Room's `@Entity` has CLASS retention; match by the class name instead (FooBarEntity → foo_bar). */
    private fun tableOf(c: Class<*>): String {
        val base = c.simpleName.removeSuffix("Entity")
        val snake = base.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        return when (snake) {
            "sleep_session" -> "sleep_session"
            else -> snake
        }
    }

    private fun fieldType(c: Class<*>, path: String): Class<*> {
        var cls = c
        var type: Class<*> = c
        for (part in path.split('.')) {
            type = cls.getDeclaredField(part).type
            cls = type
        }
        return type
    }
}
