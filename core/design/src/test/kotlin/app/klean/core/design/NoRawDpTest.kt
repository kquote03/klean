package app.klean.core.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screens space themselves with `Space` (docs/ui/design-system.md), never with raw `NN.dp` paddings. */
class NoRawDpTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val raw = Regex("""\.padding\([^)]*\b(?!0\.dp)\d+(\.\d+)?\.dp""")

    private fun offenders(files: Sequence<File>) = files.flatMap { f ->
        f.readLines().mapIndexedNotNull { i, line -> if (raw.containsMatchIn(line)) "${f.relativeTo(root)}:${i + 1}: ${line.trim()}" else null }
    }.toList()

    /** The screens redesigned so far (UI overhaul Phase 3). Each new one joins this list. */
    private val converted = listOf(
        "app/src/main/kotlin/app/klean/today/TodayScreen.kt",
        "feature/tasks/src/main/kotlin/app/klean/feature/tasks/ui/TasksScreen.kt",
        "feature/calendar/src/main/kotlin/app/klean/feature/calendar/ui/CalendarScreen.kt",
        "app/src/main/kotlin/app/klean/track/TrackWeekScreen.kt",
        "feature/workout/src/main/kotlin/app/klean/feature/workout/ui/WorkoutScreens.kt",
        "feature/sleep/src/main/kotlin/app/klean/feature/sleep/ui/SleepScreens.kt",
        "feature/diet/src/main/kotlin/app/klean/feature/diet/ui/today/DietScreen.kt",
        "feature/contacts/src/main/kotlin/app/klean/feature/contacts/ui/ContactsScreen.kt",
        "feature/contacts/src/main/kotlin/app/klean/feature/contacts/ui/ContactDetailScreen.kt",
        "feature/settings/src/main/kotlin/app/klean/feature/settings/SettingsScreen.kt",
        "feature/onboarding/src/main/kotlin/app/klean/feature/onboarding/OnboardingScreen.kt",
        "app/src/main/kotlin/app/klean/widget/TodayWidget.kt",
        "feature/diet/src/main/kotlin/app/klean/feature/diet/widget/DietWidget.kt",
    )

    @Test fun redesignedScreensUseTheSpacingScale() {
        val found = offenders(converted.asSequence().map { File(root, it) }.filter { it.exists() })
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test fun matcherSeesRawPaddingButNotSpaceOrZero() {
        assertTrue(raw.containsMatchIn("Modifier.padding(16.dp)"))
        assertTrue(raw.containsMatchIn("Modifier.padding(start = Space.md, top = 4.dp)"))
        assertFalse(raw.containsMatchIn("Modifier.padding(Space.md)"))
        assertFalse(raw.containsMatchIn("Modifier.padding(0.dp)"))
    }

    /** Every feature screen (UI Phase 3 is done): no raw `NN.dp` padding anywhere under a feature's `ui/`. */
    @Test fun noRawPaddingInFeatureScreens() {
        val files = File(root, "feature").walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && "/ui/" in it.path }
        val found = offenders(files)
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }
}
