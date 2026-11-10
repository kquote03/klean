package app.klean

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colours come from the theme tokens (`core/design`); screens never hard-code them. */
class NoRawColorsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val raw = Regex("""Color\(0x|Color\.(Red|Green|Blue|Yellow|Magenta|Cyan)\b|android\.graphics\.Color\.(RED|GREEN|BLUE|parseColor)""")

    @Test fun noRawColoursOutsideCoreDesign() {
        val offenders = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "node_modules" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && "/core/design/" !in it.path }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (raw.containsMatchIn(line)) "${f.relativeTo(root)}:${i + 1}: ${line.trim()}" else null } }
            .toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
