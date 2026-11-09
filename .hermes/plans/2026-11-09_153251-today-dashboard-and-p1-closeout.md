# Klean: finish the Today dashboard and close out Prototype 1 on Waydroid

## Goal

Turn Today into a real welcome dashboard (a greeting header, summary tiles, a week calendar overview, and the module cards). Then close every Prototype 1 item that can be checked on Waydroid, so the only thing left is the Pixel session (M16).

## Current context and assumptions

- **Repo:** `/home/kenan1099/Src/klean`, branch `main`, HEAD `a5bf39c`.
  - Tags `m0`–`m15` are all pushed to `github.com/kquote03/klean`, which is public.
  - CI runs on every push and is green, and every push to main refreshes the rolling `nightly` release.
- **Read first:**
  - `AGENTS.md`, for conventions and gotchas. Its "Current state" section is stale; this plan supersedes it.
  - `PLAN.md`, the original plan.
  - `TASKS.md`.
  - The shared subagent brief, `/home/kenan1099/.hermes/cache/scratch/klean-subagent-brief.md`, which covers toolchain, Waydroid, and the rules: flock, commit only your own paths, push after every commit.
- **What Today is now.** `app/src/main/kotlin/app/klean/today/TodayScreen.kt` (94 lines) shows only:
  - a "Today" top bar
  - the universal capture field
  - `registry.dashboardCards`, the Diet card
  - "Nothing here yet" when no module is on

  `TodayViewModel` (53 lines) only handles capture.
- **Wiring.** `ShellViewModel` (`app/src/main/kotlin/app/klean/nav/ShellViewModel.kt`) hands singletons to screens so the shell can be tested without Hilt. `KleanNavHost.kt:44` builds `TodayScreen(registry, capture, clock, onOpen)`.
- **Diet data the dashboard needs (all of it exists already):**
  - `DietRepository.observeDay(date)`, `observeEatenTimes(fromMs, toMs)`, `observeViewPrefs()` (which includes `quietHours` and `showCalories`), `todayDate()`, `windowFor(date)`.
  - `DevicePrefs.nextPromptAt: Flow<Long?>`.
  - `SettingsRepository.observe(SyncedKeys.DietPromptsEnabled)`.
  - `DietFormatter.time(ms)` / `date(d)` / `kcalSoFar(n)`.
  - `DietDayRoute(isoDate)` and `DietRoute`.
  - Logical-day counting is already done in `DietHistoryViewModel.month()` (`DayWindows.logicalDate`).
- **Testing devices.** "Test with just Waydroid": serial `192.168.240.112:5555`, Android 13, x86_64, 540×1212.
  - Waydroid has no camera (never trigger "Take photo"), no Play services, and no TalkBack.
  - `run-as` fails there. Use the debug `EXPORT_DB` broadcast instead.
  - The Pixel is off-limits until the user's testing session.
- **The user is AFK and can't answer questions.** Decided items in PLAN.md stay as they are, and everything else uses the defaults below.

### Decisions taken here, because the user is away. All are reversible.

1. **"Calendar overview" is a week strip on Today.** It shows the 7 days of the current week, with a neutral count under each day that has activity, and buttons for the previous and next week (never past the current week).
   - It is fed by a new module-agnostic `DayActivityProvider` contract. Diet provides entry counts today; the future calendar, tasks, workout and sleep modules plug in the same way.
   - This is **not** the calendar module, which is out of scope for P1. Tapping a day opens that day in the module that has activity on it (diet's day pager in P1).
2. **"Summary components" are tiles.** Modules contribute them through a new `DashboardSummaryProvider` contract.
   - Diet contributes:
     - "Today": the entry count
     - "Last entry": the text and time
     - "Next check-in": around HH:mm (only when check-ins are on)
     - "So far": ~kcal (only when Show calories is on and estimates exist)
   - The app contributes "Quiet hours · until 8:00 AM", but only while quiet hours are active.
3. **Header.** A greeting picked by time of day, and the date (e.g. "Good afternoon" / "Monday, 9 November").
   - Night (22:00–04:59) says "Hello", not "Good night", because the app shouldn't tell the user to sleep.
   - No exclamation marks. No streaks, scores, colour coding or "missed" wording; empty days are blank.
4. **Today stays in `:app/today`.** It moves into `:feature:today` only when a second consumer appears (YAGNI).
5. **The week starts on the locale's first day** (`WeekFields.of(Locale.getDefault()).firstDayOfWeek`), passed into the pure model so tests can fix it.
6. **Launcher icon.** A simple adaptive vector icon: a pastel lavender background with a rounded bowl and spoon glyph. No external assets.
7. **M16 is not run and `p1` is not tagged.** That needs the Pixel. We produce `docs/acceptance-p1.md` with a Waydroid column filled in and a Pixel column left pending.

## Architecture

Two new multibound contracts in `:core:ui` (`DashboardSummaryProvider`, `DayActivityProvider`) sit next to `KleanModule`. A `DashboardContributions` singleton filters them by `ModuleGate`, so a disabled module disappears from the dashboard. A new `TodayDashboardViewModel` combines them with a pure, unit-tested model (`TodayModel.kt`: greeting, week dates, merging counts) and renders stateless `:core:design` components (`SummaryTile`, `WeekOverview`). Diet implements both providers from data that already exists. The rest of the plan is a Waydroid verification and cleanup pass that ends with an acceptance document and a handover update.

## Ground rules for the implementer

- Every Gradle call: `cd /home/kenan1099/Src/klean && flock /home/kenan1099/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew <tasks> --console=plain`.
- `git add` new files before running Gradle (flakes only see tracked files).
- Commit only your own paths: `git commit -- <paths>`, never `git add -A`.
- After every commit, run `git push -q origin main`.
- Follow TDD for logic: write the test, watch it fail, implement, watch it pass, commit.
- Every Waydroid shell: `export ANDROID_SERIAL=192.168.240.112:5555`.
  - Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
  - Then run `adb shell pm grant app.klean.debug android.permission.POST_NOTIFICATIONS`.
- Copy rules: all copy goes in `strings.xml`. Keep it calm, with no `!`. Never use missed, streak, behind, forgot or overdue, and never mention ADHD or autism. `ForbiddenCopyTest` enforces this.
- Gate (it must end in `BUILD SUCCESSFUL`):
  ```
  flock /home/kenan1099/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest --console=plain
  ```
- Test count: `python3 ~/.hermes/skills/software-development/android-app-development/scripts/count-test-results.py .` must print `... 0 fail ...`.

---

## Part A — Dashboard contracts (`:core:ui`)

### A1. Contracts and the aggregator (test first)

Create `core/ui/src/test/kotlin/app/klean/core/ui/module/DashboardContributionsTest.kt`:

```kotlin
package app.klean.core.ui.module

import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardContributionsTest {
    private class Gate(off: Set<ModuleId> = emptySet()) : ModuleGate {
        val ids = MutableStateFlow(off)
        override val disabledIds: Flow<Set<ModuleId>> = ids
        override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
            ids.value = if (enabled) ids.value - id else ids.value + id
        }
    }
    private val d1 = LocalDate(2026, 11, 9)

    private fun summary(id: ModuleId, key: String) = object : DashboardSummaryProvider {
        override val moduleId = id
        override fun summaries() = flowOf(listOf(SummaryItem(key = key, label = key, value = "v")))
    }
    private fun activity(id: ModuleId, n: Int) = object : DayActivityProvider {
        override val moduleId = id
        override fun activity(from: LocalDate, to: LocalDate) = flowOf(mapOf(d1 to n))
        override fun dayRoute(date: LocalDate): Any = "route-${id.value}"
    }

    @Test fun summariesFromDisabledModulesAreHidden() = runTest {
        val gate = Gate()
        val c = DashboardContributions(
            setOf(summary(ModuleId.DIET, "diet"), summary(ModuleId.CORE, "core")), emptySet(), gate,
        )
        assertEquals(setOf("diet", "core"), c.summaries().first().map { it.key }.toSet())
        gate.setEnabled(ModuleId.DIET, false)
        assertEquals(listOf("core"), c.summaries().first().map { it.key })
    }

    @Test fun activityIsSummedPerDayAndRoutedToTheFirstModuleWithActivity() = runTest {
        val c = DashboardContributions(emptySet(), setOf(activity(ModuleId.DIET, 3)), Gate())
        val week = c.activity(d1, d1).first()
        assertEquals(3, week.getValue(d1).count)
        assertEquals("route-diet", week.getValue(d1).route)
    }
}
```

Check that `ModuleId.CORE` exists in `shared/src/commonMain/kotlin/app/klean/shared/module/ModuleId.kt` (`grep -n "CORE\|val " shared/src/commonMain/kotlin/app/klean/shared/module/ModuleId.kt`). If it doesn't, add `val CORE = ModuleId("core")` beside `DIET`, in the same style, with a KDoc line: "Contributions owned by the app itself; never disabled." Also check that `ModuleGate` (`core/data/src/main/kotlin/app/klean/core/data/module/`) has exactly the members the fake overrides, and adapt the fake if not.

Run:
```
flock ... ./gradlew :core:ui:testDebugUnitTest --tests '*DashboardContributionsTest' --console=plain
```
Expected: compilation fails (`Unresolved reference 'DashboardContributions'`).

Create `core/ui/src/main/kotlin/app/klean/core/ui/module/Dashboard.kt`:

```kotlin
package app.klean.core.ui.module

import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/** One tile on Today ("Today · 3 entries"). Values are already formatted for display. */
data class SummaryItem(
    val key: String,
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    /** Where a tap goes, or null for a tile that only informs. */
    val route: Any? = null,
)

/** A module's tiles on Today. Bind with @IntoSet. Emit an empty list to show nothing. */
interface DashboardSummaryProvider {
    val moduleId: ModuleId
    fun summaries(): Flow<List<SummaryItem>>
}

/**
 * A module's per-day activity for the week overview on Today. Counts are neutral facts: a day
 * without activity is simply blank. Future calendar/tasks/workout modules plug in here.
 */
interface DayActivityProvider {
    val moduleId: ModuleId
    /** Activity per logical day in [from]..[to], both inclusive. Days with nothing may be absent. */
    fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    /** The screen that shows this module's [date]. */
    fun dayRoute(date: LocalDate): Any
}

data class DayActivity(val count: Int, val route: Any?)

/** Everything the enabled modules contribute to Today, filtered by the module gate. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DashboardContributions @Inject constructor(
    private val summaryProviders: Set<@JvmSuppressWildcards DashboardSummaryProvider>,
    private val activityProviders: Set<@JvmSuppressWildcards DayActivityProvider>,
    private val gate: ModuleGate,
) {
    fun summaries(): Flow<List<SummaryItem>> = gate.disabledIds.flatMapLatest { off ->
        val on = summaryProviders.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
        if (on.isEmpty()) flowOf(emptyList()) else combine(on.map { it.summaries() }) { lists -> lists.flatMap { it } }
    }

    fun hasActivityProviders(): Flow<Boolean> = gate.disabledIds.flatMapLatest { off ->
        flowOf(activityProviders.any { it.moduleId !in off })
    }

    fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayActivity>> =
        gate.disabledIds.flatMapLatest { off ->
            val on = activityProviders.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
            if (on.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(on.map { p -> p.activity(from, to) }) { maps ->
                    val days = maps.flatMap { it.keys }.toSet()
                    days.associateWith { d ->
                        val total = maps.sumOf { it[d] ?: 0 }
                        val first = on.indices.firstOrNull { (maps[it][d] ?: 0) > 0 }
                        DayActivity(total, first?.let { on[it].dayRoute(d) })
                    }.filterValues { it.count > 0 }
                }
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardMultibindings {
    @Multibinds abstract fun summaries(): Set<DashboardSummaryProvider>
    @Multibinds abstract fun activity(): Set<DayActivityProvider>
}
```

Ordering note: `sortedBy { it.moduleId.value }` puts "core" before "diet". That is intentional: the quiet-hours tile comes first, because it explains why there are no check-ins.

Check `:core:ui/build.gradle.kts`:
- It must have `kotlinx-datetime`. If not, add `implementation(libs.kotlinx.datetime)`; check the alias with `grep -n datetime gradle/libs.versions.toml`.
- Its test dependencies must include `kotlinx-coroutines-test`. If missing, copy the line from `feature/settings/build.gradle.kts`.

Run the same test command. Expected: `BUILD SUCCESSFUL` and 2 tests passed.

Commit:
```
git add core/ui/src/main/kotlin/app/klean/core/ui/module/Dashboard.kt core/ui/src/test/kotlin/app/klean/core/ui/module/DashboardContributionsTest.kt
git commit -m "feat(ui): dashboard contracts — summary tiles and per-day activity, filtered by the module gate" -- core/ui shared
git push -q origin main
```

---

## Part B — Pure Today model (`:app`)

### B1. Greeting and week maths (test first)

Create `app/src/test/kotlin/app/klean/today/TodayModelTest.kt`:

```kotlin
package app.klean.today

import java.time.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayModelTest {
    @Test fun greetingByHour() {
        assertEquals(Greeting.NIGHT, greetingFor(4))
        assertEquals(Greeting.MORNING, greetingFor(5))
        assertEquals(Greeting.MORNING, greetingFor(11))
        assertEquals(Greeting.AFTERNOON, greetingFor(12))
        assertEquals(Greeting.AFTERNOON, greetingFor(16))
        assertEquals(Greeting.EVENING, greetingFor(17))
        assertEquals(Greeting.EVENING, greetingFor(21))
        assertEquals(Greeting.NIGHT, greetingFor(22))
    }

    @Test fun weekStartsOnTheGivenDay() {
        val mon = weekOf(LocalDate(2026, 11, 11), DayOfWeek.MONDAY) // a Wednesday
        assertEquals(LocalDate(2026, 11, 9), mon.first())
        assertEquals(7, mon.size)
        val sat = weekOf(LocalDate(2026, 11, 11), DayOfWeek.SATURDAY)
        assertEquals(LocalDate(2026, 11, 7), sat.first())
        assertEquals(LocalDate(2026, 11, 13), sat.last())
    }

    @Test fun weekShiftNeverPassesTheCurrentWeek() {
        val today = LocalDate(2026, 11, 11)
        assertEquals(0, clampWeekOffset(1))
        assertEquals(-3, clampWeekOffset(-3))
        assertEquals(LocalDate(2026, 11, 2), weekOf(today, DayOfWeek.MONDAY, offset = -1).first())
    }
}
```

Run `./gradlew :app:testDebugUnitTest --tests '*TodayModelTest'`. Expected: it fails to compile.

Create `app/src/main/kotlin/app/klean/today/TodayModel.kt`:

```kotlin
package app.klean.today

import java.time.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Calm, time-of-day greeting. Night says hello rather than telling anyone to sleep. */
enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

fun greetingFor(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.MORNING
    in 12..16 -> Greeting.AFTERNOON
    in 17..21 -> Greeting.EVENING
    else -> Greeting.NIGHT
}

/** The seven days of the week containing [day], shifted by [offset] weeks (0 = this week). */
fun weekOf(day: LocalDate, firstDay: DayOfWeek, offset: Int = 0): List<LocalDate> {
    val back = (day.dayOfWeek.ordinal - firstDay.ordinal + 7) % 7
    val start = day.minus(back, DateTimeUnit.DAY).plus(offset * 7, DateTimeUnit.DAY)
    return (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
}

/** The overview may look back but never ahead of the current week. */
fun clampWeekOffset(offset: Int): Int = minOf(0, offset)
```

`kotlinx.datetime.LocalDate.dayOfWeek` returns `java.time.DayOfWeek` on JVM in 0.8.0. If it returns `kotlinx.datetime.DayOfWeek` instead, the code still compiles if you change the import to `kotlinx.datetime.DayOfWeek`. Update the test import to match and pass `DayOfWeek.MONDAY` the same way.

Run it again. Expected: 3 tests pass. Commit with `feat(today): greeting and week model`, then push.

---

## Part C — Design components (`:core:design`)

### C1. `SummaryTile` and `WeekOverview`, with an a11y test first

Create `core/design/src/test/kotlin/app/klean/core/design/DashboardComponentsTest.kt`. Model it on `RepeatChipsTest.kt` in the same folder (Robolectric + `createComposeRule`):

```kotlin
package app.klean.core.design

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.SummaryTile
import app.klean.core.design.component.WeekDayCell
import app.klean.core.design.component.WeekOverview
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tileShowsLabelAndValueAndIsATallEnoughTarget() {
        var taps = 0
        compose.setContent { KleanTheme { SummaryTile("Today", "3 entries", null, onClick = { taps++ }) } }
        compose.onNodeWithText("3 entries").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }

    @Test fun emptyDaysAreBlankAndEveryDayHasALabel() {
        val days = listOf(
            WeekDayCell(key = "a", weekday = "M", dayNumber = "9", count = 0, isToday = false, isFuture = false, description = "Monday 9 November, nothing logged"),
            WeekDayCell(key = "b", weekday = "T", dayNumber = "10", count = 2, isToday = true, isFuture = false, description = "Tuesday 10 November, 2 things logged"),
        )
        var opened: String? = null
        compose.setContent {
            KleanTheme {
                WeekOverview(
                    title = "This week", days = days, onDay = { opened = it.key },
                    onPrevious = {}, onNext = {}, canGoNext = false,
                    previousDescription = "Previous week", nextDescription = "Next week",
                )
            }
        }
        compose.onNodeWithContentDescription("Monday 9 November, nothing logged").assertExists()
        compose.onNodeWithContentDescription("Tuesday 10 November, 2 things logged").performClick()
        assertEquals("b", opened)
        compose.onNodeWithText("0").assertDoesNotExist() // no zero badges, ever
    }
}
```

Check the real package names first: `grep -rn "^package\|fun KleanTheme" core/design/src/main/kotlin | head`. Fix the imports if `KleanTheme` lives elsewhere.

Run `./gradlew :core:design:testDebugUnitTest --tests '*DashboardComponentsTest'`. Expected: it fails to compile.

Create `core/design/src/main/kotlin/app/klean/core/design/component/SummaryTile.kt`:

```kotlin
package app.klean.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A small, calm fact on Today: a label above a value. Never coloured by "good" or "bad". */
@Composable
fun SummaryTile(label: String, value: String, icon: ImageVector?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val content: @Composable () -> Unit = {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { content() }
    else Card(modifier = modifier, colors = colors) { content() }
}
```

Create `core/design/src/main/kotlin/app/klean/core/design/component/WeekOverview.kt`:

```kotlin
package app.klean.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One day in the week overview. [count] 0 renders as blank, never as "0". */
data class WeekDayCell(
    val key: String,
    val weekday: String,
    val dayNumber: String,
    val count: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val description: String,
)

/** Seven days at a glance. Neutral: no colours by amount, no streaks, empty days stay empty. */
@Composable
fun WeekOverview(
    title: String,
    days: List<WeekDayCell>,
    onDay: (WeekDayCell) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
) {
    KleanCard(modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, previousDescription) }
                IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, nextDescription) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { day -> DayCell(day, onDay, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: WeekDayCell, onDay: (WeekDayCell) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val outline = if (day.isToday) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier
    val faded = if (day.isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .padding(2.dp)
            .heightIn(min = 72.dp)
            .clip(shape)
            .then(outline)
            .clickable(enabled = !day.isFuture, role = Role.Button) { onDay(day) }
            .clearAndSetSemantics { contentDescription = day.description }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day.weekday, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(day.dayNumber, style = MaterialTheme.typography.titleMedium, color = faded)
        Text(
            if (day.count > 0) day.count.toString() else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
```

- Check that `KleanCard` has the signature `KleanCard(modifier, content)`: run `grep -n "fun KleanCard" -A3 core/design/src/main/kotlin/app/klean/core/design/component/KleanCard.kt` and adapt the call if it differs.
- If `clickable(enabled=, role=, onClick=)` isn't available in this Compose BOM, use `clickable(enabled = !day.isFuture, onClickLabel = null, role = Role.Button, onClick = { onDay(day) })`.
- `material-icons-extended` is already used for `Icons.Rounded.Restaurant` in diet. If `:core:design` lacks it, add the same dependency line diet uses.

Run the test again. Expected: 2 tests pass. Commit with `feat(design): SummaryTile and WeekOverview (neutral, 48dp+ targets, labelled days)`, then push.

---

## Part D — Today dashboard view model and screen (`:app`)

### D1. `TodayDashboardViewModel` (test first)

Create `app/src/test/kotlin/app/klean/today/TodayDashboardViewModelTest.kt`:

```kotlin
package app.klean.today

import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.SummaryItem
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import java.time.DayOfWeek
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardViewModelTest {
    // 2026-11-11 (Wednesday) 10:00 in Dubai = 06:00 UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-11T06:00:00Z") }
    private val dubai = TimeZone.of("Asia/Dubai")
    private val gate = FakeModuleGate()
    private val dietSummaries = MutableStateFlow(listOf(SummaryItem("diet.today", "Today", "3 entries")))
    private val summary = object : DashboardSummaryProvider {
        override val moduleId = ModuleId.DIET
        override fun summaries() = dietSummaries
    }
    private val activity = object : DayActivityProvider {
        override val moduleId = ModuleId.DIET
        override fun activity(from: LocalDate, to: LocalDate) = flowOf(mapOf(LocalDate(2026, 11, 10) to 4))
        override fun dayRoute(date: LocalDate): Any = "day:$date"
    }
    private fun vm() = TodayDashboardViewModel(
        DashboardContributions(setOf(summary), setOf(activity), gate), clock, { dubai }, DayOfWeek.MONDAY,
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun headerSummariesAndWeek() = runTest {
        val s = vm().state.first { it.loaded }
        assertEquals(Greeting.MORNING, s.greeting)
        assertEquals(LocalDate(2026, 11, 11), s.today)
        assertEquals(listOf("diet.today"), s.summaries.map { it.key })
        assertEquals(LocalDate(2026, 11, 9), s.week.first().date)
        assertEquals(4, s.week.first { it.date == LocalDate(2026, 11, 10) }.count)
        assertEquals("day:2026-11-10", s.week.first { it.date == LocalDate(2026, 11, 10) }.route)
        assertTrue(s.week.first { it.date == LocalDate(2026, 11, 11) }.isToday)
        assertTrue(s.week.last().isFuture)
        assertEquals(false, s.canGoNext)
    }

    @Test fun previousWeekAndBackButNeverAhead() = runTest {
        val vm = vm()
        vm.previousWeek()
        assertEquals(LocalDate(2026, 11, 2), vm.state.first { it.loaded && it.weekOffset == -1 }.week.first().date)
        vm.nextWeek(); vm.nextWeek()
        assertEquals(0, vm.state.first { it.loaded }.weekOffset)
    }

    @Test fun disablingTheModuleClearsItsTilesAndWeek() = runTest {
        val vm = vm()
        vm.state.first { it.loaded && it.summaries.isNotEmpty() }
        gate.setEnabled(ModuleId.DIET, false)
        val s = vm.state.first { it.summaries.isEmpty() }
        assertEquals(false, s.showWeek)
    }
}
```

Check that `FakeModuleGate` (`app/src/test/kotlin/app/klean/nav/FakeModuleGate.kt`) has `setEnabled`. If its API differs, adapt the test call.

Run `./gradlew :app:testDebugUnitTest --tests '*TodayDashboardViewModelTest'`. Expected: it fails to compile.

Create `app/src/main/kotlin/app/klean/today/TodayDashboardViewModel.kt`:

```kotlin
package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.SummaryItem
import java.time.DayOfWeek
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

data class WeekDay(val date: LocalDate, val count: Int, val route: Any?, val isToday: Boolean, val isFuture: Boolean)

data class TodayDashboardState(
    val greeting: Greeting = Greeting.MORNING,
    val today: LocalDate = LocalDate(2026, 1, 1),
    val summaries: List<SummaryItem> = emptyList(),
    val showWeek: Boolean = false,
    val week: List<WeekDay> = emptyList(),
    val weekOffset: Int = 0,
    val canGoNext: Boolean = false,
    val loaded: Boolean = false,
)

/** Header, tiles and week overview on Today. Capture stays in [TodayViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardViewModel(
    private val contributions: DashboardContributions,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val firstDay: DayOfWeek,
) : ViewModel() {
    private val offset = MutableStateFlow(0)

    val state: StateFlow<TodayDashboardState> = offset.flatMapLatest { off ->
        val now = clock.now().toLocalDateTime(zone.current())
        val today = now.date
        val days = weekOf(today, firstDay, off)
        combine(
            contributions.summaries(),
            contributions.hasActivityProviders(),
            contributions.activity(days.first(), days.last()),
        ) { tiles, hasWeek, activity ->
            TodayDashboardState(
                greeting = greetingFor(now.hour),
                today = today,
                summaries = tiles,
                showWeek = hasWeek,
                week = days.map { d -> WeekDay(d, activity[d]?.count ?: 0, activity[d]?.route, d == today, d > today) },
                weekOffset = off,
                canGoNext = off < 0,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayDashboardState())

    fun previousWeek() = offset.update { it - 1 }
    fun nextWeek() = offset.update { clampWeekOffset(it + 1) }
}
```

Note: "today" here is the calendar date in the device zone, and the diet provider counts by logical day. They differ only between midnight and 04:00, which is acceptable. Write it as a KDoc line on `today`.

The `{ dubai }` lambda in the test needs `ZoneProvider` to be a `fun interface`. If it isn't (`core/common/.../ZoneProvider`), use `object : ZoneProvider { override fun current() = dubai }` in the test instead. Do not change `ZoneProvider`.

Run it again. Expected: 3 tests pass. Commit with `feat(today): dashboard view model (greeting, tiles, week overview)`, then push.

### D2. Wire the contributions into the shell

1. In `app/src/main/kotlin/app/klean/nav/ShellViewModel.kt`, add two constructor parameters **with defaults**, so `ShellSmokeTest` and `OnboardingFlowTest` keep compiling:
   ```kotlin
       val quietHours: QuietHoursStore,
       val dashboard: DashboardContributions = DashboardContributions(emptySet(), emptySet(), NoGate),
       val zone: ZoneProvider = ZoneProvider { kotlinx.datetime.TimeZone.currentSystemDefault() },
   ```
   - If `ZoneProvider` isn't a fun interface, use an `object :` expression.
   - Define `private object NoGate : ModuleGate` in the same file, with `disabledIds = flowOf(emptySet())` and a no-op `setEnabled`. Match `ModuleGate`'s real members.
   - Hilt uses the full constructor, so the defaults are only for tests.
2. In `app/src/main/kotlin/app/klean/nav/KleanNavHost.kt:44`, pass `dashboard = shell.dashboard, zone = shell.zone` to `TodayScreen`.

### D3. Rebuild `TodayScreen`

Replace the body of `app/src/main/kotlin/app/klean/today/TodayScreen.kt`. Keep the capture logic exactly as it is.

- **Signature:** add `dashboard: DashboardContributions` and `zone: ZoneProvider` parameters, plus
  ```kotlin
  dash: TodayDashboardViewModel = viewModel {
      TodayDashboardViewModel(dashboard, clock, zone, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
  }
  ```
- **Top bar:** remove the `TopAppBar`. The header item replaces it. Keep `Scaffold` for the insets: `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`.
- **LazyColumn items, in this order:**
  1. **Header item.**
     - `Text(greetingText(state.greeting), style = MaterialTheme.typography.headlineMedium)`.
     - Below it, `Text(longDate(state.today), style = bodyLarge, color = onSurfaceVariant)`.
     - `greetingText` maps to `R.string.today_greeting_morning`, `..._afternoon`, `..._evening` and `..._night`.
     - `longDate` uses `java.time.format.DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)` on `java.time.LocalDate.of(y, m, d)`.
     - Give the header Column `Modifier.semantics(mergeDescendants = true) { heading() }`.
  2. **The existing capture item,** unchanged.
  3. **Tiles item,** only when `state.summaries.isNotEmpty()`. Use a `FlowRow(horizontalArrangement = spacedBy(8.dp), verticalArrangement = spacedBy(8.dp))` of `SummaryTile(it.label, it.value, it.icon, onClick = it.route?.let { r -> { onOpen(r) } }, Modifier.widthIn(min = 150.dp).weight(1f))`. Use `@OptIn(ExperimentalLayoutApi::class)` if FlowRow requires it.
  4. **Week item,** only when `state.showWeek`: `WeekOverview(...)` with
     - `title`: `R.string.today_week_this` when the offset is 0, otherwise `R.string.today_week_of` formatted with the first day as "9 Nov".
     - `days`: mapped to `WeekDayCell(key = it.date.toString(), weekday = narrow weekday via java.time.format.TextStyle.NARROW, dayNumber = it.date.day.toString(), count, isToday, isFuture, description = pluralStringResource(R.plurals.today_day_description, count, longDate(it.date), count))`.
     - `onDay`: look up the `WeekDay` by key, then `route?.let(onOpen)`.
     - `onPrevious = dash::previousWeek`, `onNext = dash::nextWeek`, `canGoNext = state.canGoNext`.
     - The two descriptions from strings.
  5. **The existing cards / NeutralEmpty items,** unchanged.

`LocalDate.day` vs `dayOfMonth`: kotlinx-datetime 0.8.0 renamed it to `day`. Use whichever compiles; check with `grep -rn "\.day\b\|dayOfMonth" feature/diet/src/main | head -3`.

### D4. Strings (`app/src/main/res/values/strings.xml`)

Add:
```xml
<string name="today_greeting_morning">Good morning</string>
<string name="today_greeting_afternoon">Good afternoon</string>
<string name="today_greeting_evening">Good evening</string>
<string name="today_greeting_night">Hello</string>
<string name="today_week_this">This week</string>
<string name="today_week_of">Week of %1$s</string>
<string name="today_week_previous">Previous week</string>
<string name="today_week_next">Next week</string>
<plurals name="today_day_description">
    <item quantity="zero">%1$s, nothing logged</item>
    <item quantity="one">%1$s, 1 thing logged</item>
    <item quantity="other">%1$s, %2$d things logged</item>
</plurals>
```

English ignores `quantity="zero"`, so the description for 0 would say "0 things logged". Instead, branch in code: when count == 0, use `stringResource(R.string.today_day_nothing, date)`, and add `<string name="today_day_nothing">%1$s, nothing logged</string>`. Keep only the `one`/`other` items in the plural.

### D5. Screen test (Robolectric)

Add to `app/src/test/kotlin/app/klean/nav/ShellSmokeTest.kt` a test, `todayShowsGreetingTilesAndWeek`:
- Build the shell with `dashboard = DashboardContributions(setOf(fakeSummary), setOf(fakeActivity), gate)`.
- Assert `onNodeWithText("3 entries")` exists, and a node whose content description contains "things logged" exists.
- Click the week day that has activity and assert the navigation landed on the diet route. Reuse how the existing tests assert routes; `grep -n "currentDestination\|assert" app/src/test/kotlin/app/klean/nav/ShellSmokeTest.kt | head`.

Run:
```
./gradlew :app:testDebugUnitTest --console=plain
```
Expected: `BUILD SUCCESSFUL` (all app tests, including ForbiddenCopyTest).

Commit with `feat(today): welcome header, summary tiles and week overview on Today`, then push.

---

## Part E — Diet contributes tiles and week activity (`:feature:diet`)

### E1. `DietDashboardProviders` (test first)

Create `feature/diet/src/test/kotlin/app/klean/feature/diet/ui/dashboard/DietDashboardProvidersTest.kt`. Use `FakeDietRepository`, `FakeClock` and the `Dubai` helpers from `feature/diet/src/test/kotlin/app/klean/feature/diet/` (read `FakeDietRepository.kt` lines 1–100 first). Prompt settings come from small lambdas, so no DataStore or Room is needed.

```kotlin
// shape of the class under test (constructor):
// DietDashboardProviders(repo: DietRepository, fmt: DietFormatter, text: DietSummaryText,
//                        nextCheckIn: () -> Flow<Long?>, promptsOn: () -> Flow<Boolean>)
```

Tests (each one is `runTest` and collects `.first { ... }`):
1. `noEntriesShowsTodayTileOnly`: no entries, check-ins off → keys `["diet.today"]`, value `"No entries yet"`.
2. `todayCountAndLastEntry`: two entries today (08:10 "toast", 13:15 "shawarma") → keys `["diet.today", "diet.last"]`, values `"2 entries"` and `"shawarma · 1:15 PM"` (through a fake formatter, so use whatever its `time()` returns).
3. `nextCheckInOnlyWhenOn`: `nextCheckIn = flowOf(t)`. With prompts on, a `"diet.next"` tile appears; with prompts off, it doesn't.
4. `caloriesOnlyWhenShown`: entries whose items have calories → the `"diet.kcal"` tile appears only when `observeViewPrefs().showCalories` is true. Check how `FakeDietRepository` exposes prefs and items; extend the fake with a settable `prefs` var if needed.
5. `weekActivityCountsByLogicalDay`: an entry at 01:30 on Nov 11 (Dubai) counts toward Nov 10, and an entry at 10:00 on Nov 11 counts toward Nov 11 → `{Nov10: 1, Nov11: 1}`. `dayRoute(today)` is `DietRoute`; `dayRoute(Nov 10)` is `DietDayRoute("2026-11-10")`.

`DietSummaryText` is a tiny interface, so tests don't need Android resources:
```kotlin
interface DietSummaryText {
    fun todayLabel(): String
    fun count(n: Int): String          // "No entries yet" / "1 entry" / "%d entries"
    fun lastLabel(): String
    fun last(text: String, time: String): String   // "%1$s · %2$s"
    fun nextLabel(): String
    fun next(time: String): String     // "around %1$s"
    fun kcalLabel(): String
}
```
The test's fake returns literal English.

Run `./gradlew :feature:diet:testDebugUnitTest --tests '*DietDashboardProvidersTest'`. Expected: it fails to compile.

Create `feature/diet/src/main/kotlin/app/klean/feature/diet/ui/dashboard/DietDashboardProviders.kt`:
- The class implements both `DashboardSummaryProvider` and `DayActivityProvider` with `moduleId = ModuleId.DIET`.
- `summaries()` = `combine(repo.observeDay(repo.todayDate()), repo.observeViewPrefs(), promptsOn(), nextCheckIn(), itemsFlow)`:
  - The tiles are listed in the order given in decision 2.
  - Each tile's `route` is `DietRoute`.
  - For the last-entry tile, use the entry with the greatest `eatenAt` and `fmt.time(eatenAt)`. Truncate the text to 40 characters with "…".
  - The kcal tile sums the accepted/edited items' calories for today's entries.
    - Reuse the query `DietViewModel` uses for "~1,850 kcal so far"; `grep -n "kcalSoFar" -B10 feature/diet/src/main/kotlin/app/klean/feature/diet/ui/today/DietViewModel.kt`.
    - Extract that summing into an internal top-level function in the same package if it's inline, so both callers share it (DRY).
  - The day can roll over while the dashboard is open. Mirror DietViewModel's approach of mapping a ticker to `todayDate()` with `distinctUntilChanged()` and `flatMapLatest`. Inject `MinuteTicker` the same way.
- `activity(from, to)`: copy the logic from `DietHistoryViewModel.month()`:
  - `observeViewPrefs().flatMapLatest { prefs -> repo.observeEatenTimes(DayWindows.forDate(from, ..).start, DayWindows.forDate(to, ..).end).map { times -> times.groupingBy { DayWindows.logicalDate(it, prefs.quietHours, tz) }.eachCount() } }`.
  - Then refactor `DietHistoryViewModel` to call a shared internal `countByLogicalDay(times, quietHours, tz)` (DRY), and keep its tests green.
- `dayRoute(date)` = `if (date == repo.todayDate()) DietRoute else DietDayRoute(date.toString())`.
- Constructor: `@Inject constructor(repo, fmt, text: DietSummaryText, ticker: MinuteTicker, prefs: DevicePrefs, settings: SettingsRepository)`, with the two lambdas built from `prefs.nextPromptAt` and `settings.observe(SyncedKeys.DietPromptsEnabled)`.
  - That makes the constructor differ from the test shape. Resolve it with a primary constructor that takes the lambdas, plus a secondary `@Inject constructor` that builds them.
  - Hilt can't inject a class that has two constructors where the `@Inject` one is secondary, so use a `@Provides` function in `DietModule` (the Hilt module) instead, with a primary constructor that takes the lambdas.

Create `AndroidDietSummaryText` (`@Inject constructor(@ApplicationContext ctx)`) in the same file, backed by these strings in `feature/diet/src/main/res/values/strings.xml`:
```xml
<string name="diet_summary_today">Today</string>
<string name="diet_summary_none">No entries yet</string>
<plurals name="diet_summary_count"><item quantity="one">1 entry</item><item quantity="other">%d entries</item></plurals>
<string name="diet_summary_last">Last entry</string>
<string name="diet_summary_last_value">%1$s · %2$s</string>
<string name="diet_summary_next">Next check-in</string>
<string name="diet_summary_next_value">around %1$s</string>
<string name="diet_summary_kcal">So far</string>
```
For the kcal value, reuse `fmt.kcal(n)` ("~450 kcal").

Bindings, in the existing Hilt module where `DietKleanModule` is bound (grep `@IntoSet` in `feature/diet/src/main/kotlin/app/klean/feature/diet/DietModule.kt`):
```kotlin
@Provides @Singleton fun dashboardProviders(...): DietDashboardProviders = DietDashboardProviders(...)
@Provides @IntoSet fun dietSummaries(p: DietDashboardProviders): DashboardSummaryProvider = p
@Provides @IntoSet fun dietActivity(p: DietDashboardProviders): DayActivityProvider = p
@Binds abstract fun summaryText(impl: AndroidDietSummaryText): DietSummaryText
```
If the module is an `abstract class` with `@Binds`, put the `@Provides` functions in its `companion object` and annotate them `@JvmStatic` (the pattern Hilt documents).

Run the diet tests, then the whole gate. Expected: `BUILD SUCCESSFUL`.

Commit with `feat(diet): Today tiles (count, last entry, next check-in, calories when shown) and week activity`, then push.

### E2. Slim down the diet dashboard card

Today now shows the count and the last entry as tiles, so the Diet card (`DietDashboardCard.kt`) shouldn't repeat them. Keep the card as it is (the last 5 entries and the inline log field), but drop any "N today" header line if one exists, to avoid duplication: `grep -n "count" feature/diet/src/main/kotlin/app/klean/feature/diet/ui/dashboard/DietDashboardCard.kt`. If the card has no such line, skip this task. Commit only if something changed.

### E3. Quiet-hours tile (app-owned)

Create `app/src/main/kotlin/app/klean/today/QuietHoursSummary.kt`:
- `class QuietHoursSummary @Inject constructor(quiet: QuietHoursService, fmt: ..., @ApplicationContext ctx, ticker)`, implementing `DashboardSummaryProvider` with `moduleId = ModuleId.CORE`.
- It emits one tile, `"Quiet hours"` / `"until %1$s"`, only while `quiet.isQuiet(now)`, using `quiet.nextAllowed(now)`. Otherwise it emits an empty list. It re-evaluates on each minute tick.
- `MinuteTicker` lives in feature/diet. If `:app` can't see it, use a 60-second `flow { while (true) { emit(Unit); delay(60_000) } }`.
- Add the strings to app strings.xml: `today_quiet_label` "Quiet hours" and `today_quiet_value` "until %1$s".
- Format the time with `java.time.format.DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)` in the zone from `ZoneProvider`.
- Bind it with `@IntoSet` in `app/src/main/kotlin/app/klean/AppBindings.kt`.
- Test: `app/src/test/kotlin/app/klean/today/QuietHoursSummaryTest.kt` covers inside quiet hours → one tile with the right key, and outside → empty. Make the decision testable by extracting `fun quietTile(isQuiet: Boolean, until: String): List<SummaryItem>` and testing that function.

Commit with `feat(today): quiet-hours tile while quiet hours are on`, then push.

### E4. Waydroid check of the dashboard

```
export ANDROID_SERIAL=192.168.240.112:5555
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell pm grant app.klean.debug android.permission.POST_NOTIFICATIONS
adb shell am start -n app.klean.debug/app.klean.MainActivity
python3 ~/.hermes/cache/scratch/bin/ui.py dump
```

Expected in the dump:
- a greeting ("Good …" or "Hello") and a full date
- the capture field
- the tiles "Today" and "Last entry" (log something first if there are none)
- "This week"
- 7 day cells whose content descriptions end in "logged"
- the Diet card

Then:
- Tap a day that has activity (`ui.py tap` won't match a content description, so use the bounds from the dump with `adb shell input tap X Y`). It should open the diet day screen, or Diet itself for today.
- Tap the previous-week arrow: the title becomes "Week of …" and the next arrow becomes enabled.
- Settings → Modules → switch Diet off. Today should show only the greeting, an empty capture state and "Nothing here yet …", with no week strip. Switch it back on.
- Dark mode: run `adb shell cmd uimode night yes`, then take a screenshot with `~/.hermes/cache/scratch/bin/wshot today-dark.png` and check the colours; then `night no`.
- Font scale 2.0 (`adb shell settings put system font_scale 2.0`): take a screenshot, check that tiles wrap and day cells don't clip, then reset to 1.0. If the day numbers clip at 2.0, reduce them to `titleSmall` when `LocalDensity.current.fontScale > 1.5`.

Record the results under "Device checks" in TASKS.md, as a new "Today dashboard (Waydroid)" block. Commit with `docs(tasks): Today dashboard checks on Waydroid`, then push.

---

## Part F — Close out everything checkable on Waydroid

Do each item, record it in TASKS.md under a new heading "Waydroid close-out (Android 13)", and tick it when it passes. Fix any bug found test-first, each in its own `fix(...)` commit.

### F1. Reboot rescheduling (5.2)

```
export ANDROID_SERIAL=192.168.240.112:5555
adb shell dumpsys alarm | grep -A3 app.klean.debug | grep -E "origWhen|tag" | head
waydroid session stop; sleep 3
# then restart in the background so it survives this shell:
#   terminal(background=true, persist_on_release=true): waydroid session start
sleep 25; adb connect 192.168.240.112:5555; adb wait-for-device
adb shell dumpsys alarm | grep -A3 app.klean.debug | grep -E "origWhen|tag" | head
```

Expected: after the restart the check-in alarm (`PromptAlarmReceiver`) and the day-rollover alarm are pending again. The app was not opened, so BOOT_COMPLETED rescheduled them.

### F2. Quiet hours suppress a scheduled check-in (5.7)

- In the app, set Settings → Quiet hours to start 5 minutes ago and end 2 hours from now.
- Then run `adb shell am broadcast -a app.klean.debug.RESCHEDULE -p app.klean.debug`, followed by `adb shell dumpsys alarm | grep -A3 app.klean.debug`.
- Expected: the check-in `origWhen` is at or after the quiet end.
- Then restore 00:00–08:00.

### F3. Swipe a check-in, then the format changes (5.7)

- Diet settings → Debug → "Fire a check-in now".
- Open the shade with `cmd statusbar expand-notifications`, then swipe the check-in away: `input swipe` across its bounds, which you take from `ui.py dump`.
- Fire again, then run `adb shell "dumpsys notification --noredact" | grep -E "channel=diet_prompt"`.
- Expected: a different channel or title from the previous one, and the debug "prompt log" shows DISMISSED. If the log isn't visible, use EXPORT_DB:
  ```
  nix develop -c sqlite3 klean-debug.db "select format,outcome from prompt_log order by rowid desc limit 3"
  ```

### F4. Backup export and restore through the picker (9.4)

1. Log "backup probe" in the app. Settings → Backup → Export, then save to Downloads in DocumentsUI.
2. Delete "backup probe" in the app.
3. Settings → Backup → Restore, then pick the file.
   - The confirmation text must state exactly what will be overwritten; compare it with PLAN.md §M9 Task 9.4.
   - Confirm. The app restarts.
4. Expected: "backup probe" is back, and settings such as quiet hours are restored.
5. Fresh-install variant:
   ```
   adb uninstall app.klean.debug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Skip the wizard and restore from Downloads. Expected: the entries and photos are back. Waydroid has no camera, so first attach a photo from the gallery using a Waydroid image. If the gallery is empty, push one with `adb push some.jpg /sdcard/Pictures/` and run `am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/some.jpg`.

### F5. Compact widget (4.7)

Pin the widget from Settings → Run setup again → Widget page → "Add widget" (Waydroid supports pinning). Then resize it to one row by long-pressing it in the launcher (`input swipe X Y X Y 1500`) and dragging the resize handle.

Expected: the compact layout shows the add button and the latest entry with no clipping. Take a screenshot as evidence. If resizing by input proves too fiddly, add a Glance unit test instead: `androidx.glance:glance-appwidget-testing`, `runGlanceAppWidgetUnitTest { setAppWidgetSize(DpSize(120.dp, 60.dp)); provideComposable { … } ; onNode(hasContentDescription("…")).assertExists() }`. Check the artifact version against the Glance version in `gradle/libs.versions.toml`.

### F6. Inline reply in airplane mode (4.3), with a Waydroid stand-in

Airplane mode on Waydroid may drop adb, because adb runs over the bridge. Recovery doesn't need the network, because `waydroid shell` uses lxc:

```
adb shell cmd connectivity airplane-mode enable
# if adb drops: run everything below via `waydroid shell -- <cmd>` instead of `adb shell`
```

- Reply "airplane probe" from the status notification's "Log food" action (`ui.py tap "Log food"`, `input text`, `ui.py tap Send`).
- Measure the time from Send to the row being saved with `logcat -v epoch` (the `Klean` tag logs the save).
- Expected: under 5 s, and `dumpsys window | grep mCurrentFocus` shows that the launcher stayed focused.
- Afterwards run `cmd connectivity airplane-mode disable`.

This doesn't replace the lock-screen check on the Pixel; note that in acceptance.

### F7. Launcher icon (polish)

- Create `app/src/main/res/drawable/ic_launcher_foreground.xml`. It is a 108dp vector, with the glyph inside the central 66dp safe zone: a lavender (`#4C3F8B`) rounded bowl (an arc path) and a spoon (a rounded rect plus an oval).
- Create `app/src/main/res/drawable/ic_launcher_background.xml` as a solid `#E6DEFF` (the light `primaryContainer` token).
- Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`. Each is an `<adaptive-icon>` with `<background>` / `<foreground>` / `<monochrome android:drawable="@drawable/ic_launcher_foreground"/>`.
- minSdk is 28, so the anydpi-v26 resources cover every supported device and no PNG fallback is needed.
- In `app/src/main/AndroidManifest.xml`, set `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`. Check what's there now with `grep -n "android:icon\|roundIcon" app/src/main/AndroidManifest.xml`.
- Verify with `assembleDebug`, install, then take a screenshot of the Waydroid launcher with `wshot`. The icon should show a bowl on lavender.
- Commit with `feat(app): adaptive launcher icon`.

### F8. Test hygiene follow-up (from the last CI flake)

Four diet test classes create view models on a real DataStore/Room without clearing them, which is the same pattern that flaked `feature:settings` on CI:
- `DeliveryAppsViewModelTest`
- `PlaceEditViewModelTest`
- `DietDashboardViewModelTest`
- `DietSettingsViewModelTest`

Apply the same fix as `AiSettingsViewModelTest` (commit `a5bf39c`) to each:
- add a `ViewModelStore` and a `.tracked()` helper;
- call `vms.clear()` in `@After`, before `resetMain()` and before `db.close()`.

Run `:feature:diet:testDebugUnitTest` 3 times in a row. Expected: all three are `BUILD SUCCESSFUL`. Pass `--rerun` to force a re-run.

Commit with `test(diet): clear view models before teardown`.

### F9. Full device test suite on Waydroid

```
export ANDROID_SERIAL=192.168.240.112:5555
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, with all instrumented tests passing (6 at the time of writing). Then reinstall the app and re-grant notifications.

---

## Part G — Acceptance document and handover

### G1. `docs/acceptance-p1.md`

Create a table with one row for each of the 16 criteria in PLAN.md "### M16" and these columns: `# | Criterion | Waydroid (Android 13) | Evidence | Pixel (pending)`.
- Fill in the Waydroid column from Parts E and F and the earlier TASKS.md records: PASS, PARTIAL (with a reason), or N/A (no camera, mic, delivery apps or lock screen).
- Leave the Pixel column as "pending" in every row.
- Add a short "Pixel session checklist" section that copies the open `- [ ]` lines from TASKS.md, so the user's session has one list.

Commit with `docs: acceptance-p1 with Waydroid results; Pixel column pending`.

Do NOT `git tag p1`.

### G2. Handover update

AGENTS.md is a protected file; writes need the user's approval through a prompt.
- Try to update AGENTS.md once: current state, milestones m0–m15, the Today dashboard contracts (`core/ui/module/Dashboard.kt`), the CI/nightly setup, the flock rule, and the KSP-cache and VM-teardown gotchas.
- If the write is blocked, do not retry. Write the same content to `docs/handover-2026-11-09.md` and commit that instead.
- Either way, update TASKS.md:
  - add a "Today dashboard" section with its tasks ticked;
  - mark every item that was closed on Waydroid.

### G3. Final gate, CI and nightly

```
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest --console=plain
python3 ~/.hermes/skills/software-development/android-app-development/scripts/count-test-results.py .
git push -q origin main
gh run list -R kquote03/klean --limit 1      # wait until: completed success
gh release view nightly -R kquote03/klean --json name --jq .name   # "Latest main (0.1.0-<count>-<sha>)" matching HEAD
```

Expected:
- the gate prints `BUILD SUCCESSFUL`;
- the counter shows `0 fail`;
- the last CI run is `completed success`;
- the nightly name contains `git rev-parse --short HEAD`.

---

## Execution order and parallelism

Run it sequentially, as one subagent per part group, because builds share one 14 GB machine:

1. A + B + C + D + E (the dashboard). One subagent; it ends with E4.
2. F1–F9. One subagent. F7 and F8 can be done first, while nothing else is building.
3. G. The parent agent, after reviewing both reports and re-running the gate itself.

Each subagent gets the shared brief plus this plan file path. The parent verifies:
- a clean `git status`;
- the gate;
- CI green;
- the Waydroid dashboard screenshot.

## Risks, tradeoffs, open questions

- **"Calendar overview" interpretation.** It is built as a module-agnostic week strip, not the calendar module, which would break P1 scope. If the user wanted a month grid on Today, `WeekOverview` can grow a month mode later. The provider contract stays the same.
- **Greeting at night ("Hello")** and the **week start from the locale** are taste calls. Both are one-line changes.
- **Logical day vs calendar day:** the week strip highlights the calendar date, while diet counts use logical days (they turn at 04:00). A 01:30 snack shows on the previous day's cell, which is consistent with History.
- **Waydroid airplane mode** may drop adb. The `waydroid shell` fallback is documented in F6. Lock-screen inline reply and 3-hour unlock behaviour remain Pixel-only.
- **Glance widget resize via input** may be flaky. The fallback is a Glance unit test (F5).
- **AGENTS.md approval** can't happen while the user is AFK, so `docs/handover-2026-11-09.md` is the fallback.
- **Not done in this plan:** M16 on the Pixel and the `p1` tag. Patterns ("most entries between 22:00 and 01:00") stay a placeholder, as PLAN.md specifies.
