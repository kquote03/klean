package app.klean.shared.ai

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** JSON schema for [ParsedEntryDto], used for structured output. */
val ENTRY_PARSE_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("items") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "string") }
                    putJsonObject("portion") { putJsonArray("type") { add("string"); add("null") } }
                    putJsonObject("calories") { putJsonArray("type") { add("integer"); add("null") } }
                }
                putJsonArray("required") { add("name"); add("portion"); add("calories") }
                put("additionalProperties", false)
            }
        }
        putJsonObject("mealType") {
            putJsonArray("type") { add("string"); add("null") }
            putJsonArray("enum") {
                listOf("breakfast", "lunch", "dinner", "snack", "drink").forEach { add(it) }
                add(JsonNull)
            }
        }
    }
    putJsonArray("required") { add("items"); add("mealType") }
    put("additionalProperties", false)
}

const val ENTRY_PARSE_SYSTEM: String =
    "You turn a short food log into structured data. Split it into separate foods. " +
        "Estimate a typical portion if none is given. Estimate calories per item for the whole portion as an integer. " +
        "Choose mealType from breakfast, lunch, dinner, snack, drink using the foods and the local time. " +
        "Never add foods that are not mentioned. Reply with JSON only."
