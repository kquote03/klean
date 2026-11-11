package app.klean.widget

import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.KleanTypography
import app.klean.core.design.theme.Space
import app.klean.core.design.theme.WidgetTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Klean Today and Diet widgets mirror the app's Soft cards: tokens only, no stray sizes (UI Phase 3). */
class WidgetTokensTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val widgets = listOf(
        "app/src/main/kotlin/app/klean/widget/TodayWidget.kt",
        "feature/diet/src/main/kotlin/app/klean/feature/diet/widget/DietWidget.kt",
    )
    private val raw = Regex("""\b\d+(\.\d+)?\.(dp|sp)\b""")

    @Test fun widgetsUseTheTokensOnly() {
        val found = widgets.flatMap { path ->
            File(root, path).readLines().mapIndexedNotNull { i, line ->
                // Size buckets (DpSize) are the launcher's grid, not layout.
                if (raw.containsMatchIn(line) && "DpSize(" !in line) "$path:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test fun tokensAreTheAppsScales() {
        assertEquals(24.dp, WidgetTokens.cardRadius)
        assertEquals(Space.touchTarget, WidgetTokens.pillHeight)
        assertEquals(KleanTypography.bodyMedium.fontSize, WidgetTokens.meta)
        assertEquals(KleanTypography.titleMedium.fontSize, WidgetTokens.title)
        assertEquals("surfaceContainerLow", WidgetTokens.SURFACE)
    }
}
