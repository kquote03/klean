package app.klean.shared.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParsedEntryDtoTest {
    @Test fun sanitized_dropsBlanksClampsCaloriesLowercasesMeal() {
        val dto = ParsedEntryDto(
            listOf(
                ParsedItemDto("  Oats ", " 1 bowl ", 300),
                ParsedItemDto("   ", "x", 10),
                ParsedItemDto("Cake", null, 9000),
                ParsedItemDto("Water", null, -5),
            ),
            mealType = "Breakfast",
        )
        assertEquals(
            ParsedEntryDto(
                listOf(ParsedItemDto("Oats", "1 bowl", 300), ParsedItemDto("Cake", null, null), ParsedItemDto("Water", null, null)),
                "breakfast",
            ),
            dto.sanitized(),
        )
    }
    @Test fun sanitized_unknownMealTypeBecomesNull() =
        assertNull(ParsedEntryDto(listOf(ParsedItemDto("tea")), "brunch").sanitized()!!.mealType)
    @Test fun sanitized_allBlank_null() = assertNull(ParsedEntryDto(listOf(ParsedItemDto(" ")), "lunch").sanitized())
    @Test fun sanitized_truncatesLongNames() =
        assertEquals(80, ParsedEntryDto(listOf(ParsedItemDto("a".repeat(200)))).sanitized()!!.items.single().name.length)

    @Test fun decodesProviderJson() {
        val json = """{"items":[{"name":"shawarma","portion":"1 wrap","calories":550}],"mealType":"dinner"}"""
        assertEquals(
            ParsedEntryDto(listOf(ParsedItemDto("shawarma", "1 wrap", 550)), "dinner"),
            Json.decodeFromString(ParsedEntryDto.serializer(), json),
        )
    }

    @Test fun schemaShape() {
        val s = ENTRY_PARSE_SCHEMA
        assertEquals(JsonPrimitive("object"), s["type"])
        assertEquals(JsonPrimitive(false), s["additionalProperties"])
        assertEquals(setOf("items", "mealType"), s["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        val item = s["properties"]!!.jsonObject["items"]!!.jsonObject["items"] as JsonObject
        assertEquals(setOf("name", "portion", "calories"), item["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(JsonPrimitive(false), item["additionalProperties"])
        val meal = s["properties"]!!.jsonObject["mealType"]!!.jsonObject
        assertTrue((meal["enum"] as JsonArray).size == 6)
        assertTrue(ENTRY_PARSE_SYSTEM.startsWith("You turn a short food log"))
    }

    @Test fun fromJson_isLenientAboutNumbersAndNulls() {
        val json = Json.parseToJsonElement(
            """{"items":[{"name":"toast","portion":null,"calories":180.4},{"name":"butter","portion":"1 pat","calories":"35"},{"portion":"x"},5],"mealType":"Breakfast","extra":1}""",
        )
        assertEquals(
            ParsedEntryDto(listOf(ParsedItemDto("toast", null, 180), ParsedItemDto("butter", "1 pat", 35)), "Breakfast"),
            ParsedEntryDto.fromJson(json),
        )
        assertNull(ParsedEntryDto.fromJson(Json.parseToJsonElement("""{"foods":[]}""")))
        assertNull(ParsedEntryDto.caloriesOf(Json.parseToJsonElement("null")))
    }
}
