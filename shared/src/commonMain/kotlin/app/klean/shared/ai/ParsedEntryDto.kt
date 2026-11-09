package app.klean.shared.ai

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable data class ParsedItemDto(val name: String, val portion: String? = null, val calories: Int? = null)

@Serializable data class ParsedEntryDto(val items: List<ParsedItemDto>, val mealType: String? = null) {
    fun sanitized(): ParsedEntryDto? {
        val clean = items.filter { it.name.isNotBlank() }.map {
            it.copy(name = it.name.trim().take(80), portion = it.portion?.trim()?.take(60), calories = it.calories?.takeIf { c -> c in 0..5000 })
        }
        if (clean.isEmpty()) return null
        return ParsedEntryDto(clean, mealType?.lowercase()?.takeIf { it in setOf("breakfast", "lunch", "dinner", "snack", "drink") })
    }

    companion object {
        /** Reads a model's answer leniently: numbers may be decimals or strings, unknown fields are ignored. */
        fun fromJson(json: JsonElement): ParsedEntryDto? {
            val o = json as? JsonObject ?: return null
            val arr = o["items"] as? JsonArray ?: return null
            val items = arr.mapNotNull { el ->
                val it = el as? JsonObject ?: return@mapNotNull null
                val name = it["name"].stringOrNull() ?: return@mapNotNull null
                ParsedItemDto(name, it["portion"].stringOrNull(), caloriesOf(it["calories"]))
            }
            return ParsedEntryDto(items, o["mealType"].stringOrNull())
        }

        fun caloriesOf(el: JsonElement?): Int? {
            val p = el as? JsonPrimitive ?: return null
            val d = p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: return null
            return if (d.isFinite()) d.roundToInt() else null
        }

        private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
