package app.klean

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Internal design context (AuDHD traits) may appear in code and docs, never in user-facing copy.
 * Scans every res/ XML and manifest under src/main in the repo.
 */
class ForbiddenCopyTest {
    // "ADD" is matched case-sensitively only, so the everyday verb "Add" stays allowed.
    private val forbidden = Regex(
        """(?i)\b(adhd|(?-i:ADD)\b|autis\w*|audhd|neurodiver\w*|neurotypical|executive dysfunction|asperger\w*|spectrum disorder)""",
    )
    private val skipDirs = setOf("build", ".gradle", ".git", ".idea", ".hermes")

    @Test fun noForbiddenWordsInUserFacingResources() {
        val root = File("..").canonicalFile
        val offenders = root.walkTopDown()
            .onEnter { it.name !in skipDirs }
            .filter { it.isFile && it.path.contains("/src/main/") && (it.extension == "xml" && it.path.contains("/res/") || it.name == "AndroidManifest.xml") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (forbidden.containsMatchIn(l)) "${f.relativeTo(root)}:${i + 1}: $l" else null } }
            .toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Zero guilt (Task 13.3): no exclamation marks and no guilt words in any string's text (resource names don't count). */
    private val guilt = Regex("""(?i)\b(missed|streaks?|behind|forgot\w*|overdue|failed to log)\b""")
    private val stringText = Regex(""">([^<]*)<""")

    @Test fun userCopyIsCalm() {
        val root = File("..").canonicalFile
        val offenders = root.walkTopDown()
            .onEnter { it.name !in skipDirs }
            .filter { it.isFile && it.path.contains("/src/main/res/values") && it.name == "strings.xml" }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, l ->
                    if (l.trimStart().startsWith("<!--")) return@mapIndexedNotNull null
                    val text = stringText.findAll(l).joinToString(" ") { it.groupValues[1] }
                    if ('!' in text || guilt.containsMatchIn(text)) "${f.relativeTo(root)}:${i + 1}: $l" else null
                }
            }
            .toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test fun calmPatternCatchesGuiltWords() {
        listOf("You missed lunch", "3-day streak", "You're behind", "Forgot to log?", "Overdue").forEach {
            assertTrue(it, guilt.containsMatchIn(it))
        }
        listOf("Backup couldn't be saved", "Behold", "Nothing yet today").forEach {
            assertTrue(it, !guilt.containsMatchIn(it))
        }
    }

    @Test fun patternCatchesTermsButAllowsTheVerbAdd() {
        listOf("ADHD", "adhd tips", "Autistic", "AuDHD", "neurodivergent", "ADD").forEach {
            assertTrue(it, forbidden.containsMatchIn(it))
        }
        listOf("Add something", "add a note", "Address", "Spectrum").forEach {
            assertTrue(it, !forbidden.containsMatchIn(it))
        }
    }
}
