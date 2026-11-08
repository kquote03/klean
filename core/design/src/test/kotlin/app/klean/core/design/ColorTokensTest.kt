package app.klean.core.design

import androidx.compose.ui.graphics.Color
import app.klean.core.design.theme.KleanColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ColorTokensTest {
    // Module unit tests run with the module directory as the working dir.
    private val tokens = Json.parseToJsonElement(File("tokens/klean-color-tokens.json").readText()).jsonObject

    private fun parseHex(hex: String): Long = 0xFF000000L or hex.removePrefix("#").toLong(16)

    private fun check(mode: String, actual: Map<String, Color>) {
        val expected = tokens.getValue(mode).jsonObject
        assertEquals("$mode token count", 35, expected.size)
        assertEquals("$mode keys", expected.keys, actual.keys)
        for ((key, value) in expected) {
            assertEquals("$mode.$key", Color(parseHex(value.jsonPrimitive.content)), actual[key])
        }
    }

    @Test fun lightTokensMatchJson() = check("light", KleanColors.light)

    @Test fun darkTokensMatchJson() = check("dark", KleanColors.dark)

    @Test fun schemesUseTokens() {
        assertEquals(KleanColors.light.getValue("primary"), KleanColors.lightScheme.primary)
        assertEquals(KleanColors.dark.getValue("surfaceContainerHighest"), KleanColors.darkScheme.surfaceContainerHighest)
        assertEquals(KleanColors.dark.getValue("scrim"), KleanColors.darkScheme.scrim)
    }
}
