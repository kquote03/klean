package app.klean.shared.capture

import app.klean.shared.module.ModuleId
import app.klean.shared.time.NaturalTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the shared capture fixture (`commonTest/resources/capture-cases.json`) against the Kotlin
 * [RuleCaptureClassifier] and [NaturalTime]. The web's TypeScript port runs the same file
 * (`web/src/capture/rules.test.ts`), so the two cannot drift: change the fixture, and both sides must agree.
 */
class CaptureCasesFixtureTest {
    private val fixture: JsonObject = Json.parseToJsonElement(load()).jsonObject
    private val now = LocalDateTime.parse(fixture.str("now"))

    private fun load(): String =
        javaClass.classLoader.getResource("capture-cases.json")?.readText()
            ?: File("src/commonTest/resources/capture-cases.json").readText()

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun options(spec: kotlinx.serialization.json.JsonElement): List<CaptureOption> {
        val ids = when (spec) {
            is JsonPrimitive -> fixture["optionSets"]!!.jsonObject[spec.content]!!.jsonArray
            is JsonArray -> spec
            else -> fail("bad options $spec")
        }.map { it.jsonPrimitive.content }
        return ids.map { CaptureOption(ModuleId(it), ActionSpec(it, it)) }
    }

    @Test fun captureCases() = runTest {
        val cases = fixture["capture"]!!.jsonArray
        assertTrue(cases.size >= 30)
        val rules = RuleCaptureClassifier { now }
        for (c in cases.map { it.jsonObject }) {
            val text = c.str("text")
            val got = rules.classify(text, options(c["options"]!!))
            val want = c["expect"]
            if (want == null || want is JsonNull) {
                assertEquals(null, got, "for \"$text\"")
                continue
            }
            val rows = want.jsonArray.map { it.jsonArray }
            assertEquals(rows.map { it[0].jsonPrimitive.content to it[1].jsonPrimitive.content },
                got?.map { it.moduleId.value to it.actionType }, "for \"$text\"")
            rows.zip(got!!).forEach { (w, g) ->
                assertTrue(abs(w[2].jsonPrimitive.float - g.confidence) < 1e-4f, "confidence for \"$text\": ${g.confidence}")
            }
        }
    }

    @Test fun timeCases() {
        val cases = fixture["time"]!!.jsonArray
        assertTrue(cases.size >= 30)
        for (c in cases.map { it.jsonObject }) {
            val text = c.str("text")
            val at = c["now"]?.let { LocalDateTime.parse(it.jsonPrimitive.content) } ?: now
            val p = NaturalTime.parse(text, at)
            val date = c["date"]!!.let { if (it is JsonNull) null else LocalDate.parse(it.jsonPrimitive.content) }
            val minutes = c["minutes"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.int }
            assertEquals(Triple(date, minutes, c.str("rest")), Triple(p.date, p.minutes, p.rest), "for \"$text\"")
        }
    }
}
