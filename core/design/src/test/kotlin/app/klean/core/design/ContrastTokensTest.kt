package app.klean.core.design

import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA (4.5:1) for text: every on-X/X token pair, in both themes, plus the text colours used on surfaces. */
class ContrastTokensTest {
    private val tokens = Json.parseToJsonElement(File("tokens/klean-color-tokens.json").readText()).jsonObject

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#").toLong(16)
        fun ch(shift: Int): Double {
            val c = ((v shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun pairs(mode: Map<String, String>): List<Pair<String, String>> {
        val onX = mode.keys.filter { it.startsWith("on") }.mapNotNull { k ->
            val base = k.removePrefix("on").replaceFirstChar { it.lowercase() }
            if (base in mode) k to base else null
        }
        val surfaces = mode.keys.filter { it.startsWith("surface") && it != "surfaceVariant" || it == "background" }
        return onX + ("inverseOnSurface" to "inverseSurface") +
            surfaces.flatMap { s -> listOf("onSurface" to s, "onSurfaceVariant" to s) } +
            listOf("surface", "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh").map { "primary" to it }
    }

    @Test fun everyTextPairMeetsAaInBothThemes() {
        val failures = mutableListOf<String>()
        for (theme in listOf("light", "dark")) {
            val mode = tokens.getValue(theme).jsonObject.mapValues { it.value.jsonPrimitive.content }
            val list = pairs(mode)
            assertTrue("$theme has on-X pairs", list.count { it.first.startsWith("on") } >= 11)
            for ((fg, bg) in list) {
                val c = contrast(mode.getValue(fg), mode.getValue(bg))
                if (c < 4.5) failures += "$theme $fg on $bg = ${"%.2f".format(c)}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
