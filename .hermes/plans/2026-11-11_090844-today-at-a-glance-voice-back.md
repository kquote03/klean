# Plan: Today at a glance, one-tap logging, a real voice screen, working Back, clean transitions

## Goal

Make Today a single "whiteboard" for the day:
- what needs attention;
- what's coming up;
- what has been done so far, including the actual foods eaten;
- a one-tap action for every module.

Also:
- add a dedicated voice button and voice screen that use the phone's own speech recognition;
- make Back work everywhere, without the zoom-out animation;
- put "New contact" at the bottom of the Add menu when you're in People.

## The user's feedback, verbatim

- "not showing currently consumed foods is a huge miss … 'X entries' is simply not enough."
- "Having the widget be the 'sleep widget' also seems counter-intuitive, as all these features are equally important. Maybe having a cards interface isn't the best idea here."
- "We need more of a 'Today at a Glance', similar to a billboard or a whiteboard view of what's upcoming (what needs focus / who needs attention), what was done so far throughout the day (tasks, food items consumed, how much time slept, etc), and … a button / interaction for each module from within the today view."
- "if I want to log a food item … that menu is now 2 taps away instead of 1 … a huge massive wall … make everything accessible from that view."
- "The back button doesnt work on some menus, and on others it does a weird zoom out animation … clean up transitions between pages."
- "For voice, have a dedicated voice button and voice interface. I also always get 'voice isn't available offline on this phone' on my pixel 9a, why? Shouldn't it be using the system's voice to text?"
- "People: the plus button is visually confusing … give the option to add a contact all the way at the bottom of the plus list (nearest to finger) when in contacts."

## Current context

Read these before starting. Line numbers are as of `main` at `dc0ecd7`.

**Repo and conventions**
- Repo: `/home/kenan1099/Src/klean`, a Kotlin, Compose, Material 3 Expressive, Hilt and Room app. Read `AGENTS.md` first for the gate, conventions and devices.
- Device testing uses **Waydroid only** (`adb -s 192.168.240.112:5555`, Android 13, with Play services). The user's Pixel 9a is off-limits except for the one read-only voice diagnostic in task V0, which the user runs.

**Today**
- `app/src/main/kotlin/app/klean/today/TodayScreen.kt` draws the weekday header, the avatar and ⋮ menu, then `ApprovalsCard`s and one `GroupCard` per group (Plan, Track, People).
- `app/src/main/kotlin/app/klean/today/TodayCards.kt` builds those cards. Line 39 hard-codes Track's order as `SLEEP, DIET, WORKOUT`, so **Sleep is always the hero**. This is the "sleep widget" complaint. Diet contributes only `SummaryItem`s such as "Today · 3 entries" and "Last · shawarma · 1:15 PM".
- `app/src/main/kotlin/app/klean/today/TodayDashboardViewModel.kt` combines the date, visible modules, `DashboardContributions.summariesByModule()` and `TodayItems.itemsByModule()`.
- `core/ui/src/main/kotlin/app/klean/core/ui/module/Dashboard.kt` defines the provider interfaces:
  - `DashboardSummaryProvider` (tiles);
  - `TodayItemsProvider` (dated upcoming items with deep-link `route` strings);
  - `DayActivityProvider`, `StatusLineProvider` and `WeekSummaryProvider`.

  Modules bind them `@IntoSet`. Providers live in:
  - `feature/diet/.../ui/dashboard/DietDashboardProviders.kt` (has `repo.observeDay(date)`);
  - `feature/tasks/.../TasksModule.kt`;
  - `feature/reminders/.../RemindersModule.kt`;
  - `feature/calendar/.../today/CalendarToday.kt`;
  - `feature/contacts/.../today/BirthdaySurfaces.kt`;
  - `feature/workout/.../WorkoutModule.kt`;
  - `feature/sleep/.../SleepModule.kt`.

**Adding things**
- `app/src/main/kotlin/app/klean/nav/AddMenuEntries.kt` lists the Add menu lines top to bottom: Sleep, Workout, Food, Reminder, Event, Task. The bottom line sits nearest the FAB.
- `app/src/main/kotlin/app/klean/nav/KleanAppUi.kt` lines 144–153 build the `AddMenuSpec`. **Every Add item starts `CaptureActivity`.**
- `CaptureActivity` (`core/capture/src/main/AndroidManifest.xml` lines 16–22) is `launchMode="singleInstance"` with its own `taskAffinity="app.klean.capture"`. Pressing Back on it is therefore a cross-task return, which Android's predictive back draws as the shrink/zoom-out animation. **This is the "weird zoom out".**
- `core/capture/src/main/kotlin/app/klean/core/capture/CaptureSheet.kt` (`CaptureSheet(vm, onDismiss)`) is a plain composable over `CaptureViewModel`, so it can be shown in-app without the activity.
- The FAB menu is in `core/design/.../component/KleanScreen.kt`. It already has `BackHandler { addOpen = false }` at line 144.

**Navigation and motion**
- `app/src/main/kotlin/app/klean/nav/Motion.kt` applies shared-axis X to **every** navigation, including switching bottom-bar groups. Material's rule is **fade-through** for top-level destinations and **shared axis X** only for parent → child.
- `app/src/main/kotlin/app/klean/nav/KleanNavHost.kt` and `KleanAppUi.kt` define the routes and `navigateTopLevel` (`popUpTo(TodayRoute){saveState}`, `restoreState`).
- Navigation is 2.10.2, which supports predictive back with seekable Compose transitions. The app targets SDK 37, so predictive back is on by default.

**Voice**
- `core/capture/src/main/kotlin/app/klean/core/capture/voice/AndroidVoiceRecognizer.kt`:
  - It prefers `createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable` is true. That's true on Pixels, where the on-device engine is Google's "Speech Recognition & Synthesis".
  - **It never sets `EXTRA_LANGUAGE`, and never checks or downloads the on-device language pack.**
  - `mapError` maps NETWORK, SERVER, `LANGUAGE_NOT_SUPPORTED` (12) and `LANGUAGE_UNAVAILABLE` (13) all to `UNAVAILABLE_OFFLINE`. The UI shows that as "Voice isn't available offline on this phone" (`core/capture/src/main/res/values/strings.xml:22`).
  - The fallback to the default recogniser runs only if the on-device one fails *before* `onReadyForSpeech`.
- **Hypothesis for the Pixel 9a:** the on-device engine reports error 13 or 12 for the phone's locale (e.g. `en-AE`, or a language whose offline pack isn't downloaded), and that is shown as "not available offline". Task V0 confirms this with logcat before anything is changed.

**People**
- `ContactEditRoute()` (`feature/contacts/.../ContactsRoutes.kt:11`) is the new-contact editor. The contacts screen's ⋮ currently has "New contact" (`ContactsModule.kt:91`).

**Testing**
- `core/testing/.../ClutterBudget.kt` defines `assertClutterBudget(maxInteractive = 7)`. Subtrees tagged `CLUTTER_EXEMPT` are skipped, and a tagged list counts once.

## Decisions this plan makes (the user's feedback overrides earlier rules)

1. **Today stops being group cards.** It becomes one scrolling "whiteboard" with fixed sections in this order:
   - **Quick actions:** one button per enabled module, plus Voice and Type.
   - **Needs attention.**
   - **Coming up.**
   - **So far today.**

   No module is a "hero". Inside each section, rows are ordered by time, never by module.
2. **Clutter budget change, for Today only.** The quick-action grid is one tagged region (`CLUTTER_EXEMPT`, counted once). It's capped at **8 buttons in at most 2 rows of 4**. The rest of the first viewport keeps the ≤7 budget.
   - Record this in `docs/ui/design-system.md` §"Clutter budget" as **"Today quick actions (user feedback 2026-11-11)"**.
   - This changes a Decided rule. The user's message is the approval.
3. **One tap per module from Today:**
   - **Food:** opens the capture sheet targeted at Diet, with "Log again" chips. Tapping a chip logs it, so a repeat food is still a single tap.
   - **Task, Reminder, Event:** open the capture sheet targeted at that module.
   - **Sleep:** **acts at once.** Calls `SleepRepository.toggle()`, then shows a snackbar "Good night · Undo" or "Morning · slept 7 h 20 min · Undo".
   - **Workout:** opens the Workout start sheet (saved workouts, Run, Empty).
   - **Contact:** opens `ContactEditRoute()`.
   - **Voice:** opens the new voice screen.
   - **Type:** opens the capture sheet with no target.
4. **Today drops its FAB.** The quick actions replace it. Every other screen keeps the "+".
5. **In-app adding never leaves the activity.** The shell shows `CaptureSheet` in-process. `CaptureActivity` stays only for outside entry points: QS tile, widget, notification reply, shortcuts, the `klean://capture` deep link and the watch.
6. **Motion:**
   - bottom-bar group switch → **fade-through** (90 ms out, 210 ms in, slight 0.92 → 1 scale on enter);
   - parent → child and Back → **shared axis X** (as now);
   - full-screen menus (account menu, voice screen) → **slide up from bottom / down on exit**;
   - with reduced motion → 100 ms cross-fade for all.

   Every in-app Back uses the NavHost's predictive back, so it plays our pop transition, never a cross-task zoom.
7. **Voice uses the system's recogniser first.** Order:
   1. **(a)** the default `SpeechRecognizer` (the system's voice-to-text service, which on Pixels is Google's) with `EXTRA_LANGUAGE` set to the device's locale and **no** prefer-offline. It works offline on its own when the pack exists.
   2. **(b)** only when offline, the on-device recogniser, after `checkRecognitionSupport`, offering `triggerModelDownload` when the pack is missing (API 33+).
   3. **(c)** if both fail, the system voice dialog `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` via an activity result.

   Error copy says the real cause ("Voice needs the English pack · Download", "No connection and no offline voice pack").
8. **The Add menu puts the current screen's own item nearest the finger.** It's always last. On People, the last line is **"New contact"**. The same rule moves "Event" last on Calendar, "Food" last on Diet, and so on.

## Architecture

- **Glance providers.** A new `TodayGlanceProvider` interface in `core/ui` (each module binds one `@IntoSet`) returns that module's attention, upcoming and done rows plus its quick action. A pure function `buildGlance(...)` in `app/today` merges them by time.
- **Rendering.** `TodayScreen` renders the result with four components in `core/design`: `QuickActionGrid`, `GlanceSection`, `GlanceRow` and `TimelineRow`.
- **Sheets and navigation.** The shell gains a `ShellSheets` state that shows `CaptureSheet` and the workout start sheet in-process. Motion becomes route-aware (top-level vs child vs full-screen).
- **Voice.** It gets a `VoiceEngine` chain (system → on-device → dialog) and a full-screen `VoiceCaptureScreen`.

---

## Tasks

Each task is a commit: `git commit -m "<type>(<scope>): …" -- <paths>`. Never `git add -A`.

Module-scoped test runs use:
```
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew <tasks> --console=plain -Pkotlin.compiler.execution.strategy=in-process
```

### Phase V0: diagnose the Pixel voice error (user-run, read-only, 5 minutes)

**V0.1** Ask the user, in chat: "Open Klean on your Pixel and tap the mic once. I'll read the voice log only, nothing else." With their OK, run:
```
adb -s 100.97.189.24:39307 logcat -c && sleep 20 && adb -s 100.97.189.24:39307 logcat -d -s KleanVoice:I SpeechRecognizer:* | tail -20
adb -s 100.97.189.24:39307 shell getprop persist.sys.locale
adb -s 100.97.189.24:39307 shell cmd package resolve-activity -a android.speech.action.RECOGNIZE_SPEECH | head -3
```
Expected output is lines like `voice start mode=ON_DEVICE` followed by `voice error=13`, or `=12`.

Record the numbers in `docs/voice.md` (new file) under "Pixel 9a diagnosis". If it's not 12 or 13, adjust task V2's mapping to the observed code before continuing.

### Phase A: glance model (pure Kotlin, no UI)

**A1. Provider interface.** Create `core/ui/src/main/kotlin/app/klean/core/ui/module/TodayGlance.kt`:
```kotlin
package app.klean.core.ui.module

import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow

/** One line on Today's whiteboard. [at] is epoch millis (for ordering and the time column); null = no time. */
data class GlanceRow(
    val key: String,
    val module: ModuleId,
    val text: String,
    val at: Long?,
    /** "7 h 20 min", "3 sets", "with milk"; shown under or beside [text]. */
    val detail: String? = null,
    val icon: ImageVector? = null,
    /** Deep link (klean://…) opened on tap, or null. */
    val route: String? = null,
    /** A task-like row that can be ticked from Today. */
    val checkable: Boolean = false,
)

/** What a module's one-tap button on Today does. */
sealed interface QuickAction {
    val module: ModuleId
    val label: String
    val icon: ImageVector
    /** Open the capture sheet aimed at [module]. */
    data class Capture(override val module: ModuleId, override val label: String, override val icon: ImageVector) : QuickAction
    /** Do it now (Sleep's toggle); returns the snackbar text and an undo, or null on failure. */
    data class Instant(
        override val module: ModuleId, override val label: String, override val icon: ImageVector,
        val run: suspend () -> InstantResult?,
    ) : QuickAction
    /** Open a route (Workout's start sheet, New contact). */
    data class Open(override val module: ModuleId, override val label: String, override val icon: ImageVector, val route: Any) : QuickAction
}

data class InstantResult(val message: String, val undo: (suspend () -> Unit)?)

/** A module's part of Today. Bind with @IntoSet. Each list is today's only (logical day, user's zone). */
data class GlanceContribution(
    val attention: List<GlanceRow> = emptyList(),
    val upcoming: List<GlanceRow> = emptyList(),
    val done: List<GlanceRow> = emptyList(),
    val quickAction: QuickAction? = null,
)

interface TodayGlanceProvider {
    val moduleId: ModuleId
    fun glance(): Flow<GlanceContribution>
}
```
Also:
- add `@Multibinds abstract fun glance(): Set<TodayGlanceProvider>` to `DashboardMultibindings` in `Dashboard.kt`;
- add a gate-filtered aggregator `class TodayGlance @Inject constructor(providers, gate)` with `fun byModule(): Flow<Map<ModuleId, GlanceContribution>>`, copying the pattern of `TodayItems.itemsByModule()`.

Verify: `:core:ui:compileDebugKotlin` → BUILD SUCCESSFUL. Commit `feat(core-ui): TodayGlanceProvider for Today's whiteboard`.

**A2. Test first: `buildGlance`.** Create `app/src/test/kotlin/app/klean/today/GlanceTest.kt` with these cases:
1. `doneIsTimeOrderedAcrossModules`: food 08:10, task done 09:00 and sleep wake 07:00 come out as sleep, food, task.
2. `noModuleIsAHero`: with only Diet and Workout visible, the output has no "lead" field. The sections are lists and the first `done` row is the earliest.
3. `upcomingDropsPastItems`: at 12:00, an event at 10:00 isn't in `upcoming`.
4. `quickActionsFollowArrangeOrderAndCapAtEight`: 9 modules give 8 actions, plus Voice and Type always first. Spec: Voice, Type, then modules in Arrange-Today order, capped so the total is ≤ 8. Modules dropped by the cap stay reachable through Type and the module tabs.
5. `hiddenModulesContributeNothing`.
6. `attentionKeepsAtMostFive`: overflow becomes "+N more" (a `moreCount` field).

Run `:app:testDebugUnitTest --tests '*GlanceTest'` and expect a compile FAIL, since `buildGlance` doesn't exist yet.

**A3. Implement.** Create `app/src/main/kotlin/app/klean/today/Glance.kt`:
```kotlin
package app.klean.today

import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.shared.module.ModuleId

data class GlanceSection(val rows: List<GlanceRow>, val moreCount: Int = 0)

data class Glance(
    val quickActions: List<QuickAction>,
    val attention: GlanceSection,
    val upcoming: GlanceSection,
    val done: GlanceSection,
)

const val MAX_QUICK_MODULE_ACTIONS = 6 // + Voice + Type = 8, two rows of four
const val MAX_ATTENTION = 5
const val MAX_UPCOMING = 6

fun buildGlance(visible: List<ModuleId>, parts: Map<ModuleId, GlanceContribution>, now: Long): Glance {
    val shown = visible.mapNotNull { id -> parts[id]?.let { id to it } }
    fun section(rows: List<GlanceRow>, max: Int) = GlanceSection(rows.take(max), (rows.size - max).coerceAtLeast(0))
    val upcoming = shown.flatMap { it.second.upcoming }.filter { (it.at ?: Long.MAX_VALUE) >= now }.sortedBy { it.at ?: Long.MAX_VALUE }
    val done = shown.flatMap { it.second.done }.sortedBy { it.at ?: Long.MIN_VALUE }
    val attention = shown.flatMap { it.second.attention }.sortedBy { it.at ?: Long.MIN_VALUE }
    return Glance(
        quickActions = shown.mapNotNull { it.second.quickAction }.take(MAX_QUICK_MODULE_ACTIONS),
        attention = section(attention, MAX_ATTENTION),
        upcoming = section(upcoming, MAX_UPCOMING),
        done = GlanceSection(done), // the day so far is never truncated: it is the point of the screen
    )
}
```
Voice and Type are added by the screen, not here, because they aren't modules. Adjust test 4 to check `quickActions.size <= 6` here and 8 on screen (task C3).

Verify: `--tests '*GlanceTest'` → 6 tests, 0 failures. Commit `feat(today): buildGlance merges modules by time, no hero`.

### Phase B: each module contributes (one commit per module, test first)

For each module:
1. Write `<Module>GlanceProviderTest` in that module's `src/test` with fakes (repositories already have in-memory fakes in tests; reuse them).
2. Implement `<Module>GlanceProvider : TodayGlanceProvider`.
3. Bind it `@IntoSet` in the module's Hilt module.

Use the user's zone through `ZoneProvider`, and the logical day where the module already has one (Diet's `observeDay`).

| Task | Module, file | attention | upcoming | done | quickAction |
|---|---|---|---|---|---|
| B1 | Diet, `feature/diet/.../ui/dashboard/DietGlanceProvider.kt` | none | the next check-in time when check-ins are on ("Check-in around 5:00 PM") | **every food entry today**: `text` = entry text (e.g. "shawarma"), `at` = eatenAt, `detail` = items joined ("chicken, garlic sauce") or kcal when Show calories is on; route `klean://diet/day/<date>` | `Capture(DIET, "Food", Restaurant)` |
| B2 | Tasks, `feature/tasks/.../today/TasksGlanceProvider.kt` | overdue / "Still want these?" count as ONE row ("3 from earlier days"), route to Tasks | tasks due today, with time if set, `checkable = true` | tasks completed today (completedAt today) | `Capture(TASKS, "Task", CheckCircleOutline)` |
| B3 | Reminders, `feature/reminders/.../today/RemindersGlanceProvider.kt` | missed reminders today | remaining reminders today | reminders done today | `Capture(REMINDERS, "Reminder", NotificationsNone)` |
| B4 | Calendar, `feature/calendar/.../today/CalendarGlanceProvider.kt` | an event starting in ≤ 30 min ("Starts in 20 min") | today's remaining events with start–end | events that ended today | `Capture(CALENDAR, "Event", EventAvailable)` |
| B5 | Contacts + Birthdays, `feature/contacts/.../today/PeopleGlanceProvider.kt` | birthdays **today** ("Sam's birthday · 30"), plus a birthday within 3 days | none | none | `Open(CONTACTS, "Contact", PersonAdd, ContactEditRoute())` |
| B6 | Sleep, `feature/sleep/.../today/SleepGlanceProvider.kt` | none | none | last night: `text` = "Slept 7 h 20 min", `detail` = "11:40 PM – 7:00 AM", `at` = wakeAt; while asleep: none | `Instant(SLEEP, "Sleep"/"I'm up", Bedtime) { repo.toggle(now) → InstantResult("Good night" / "Morning · slept 7 h 20 min", undo = { repo.delete(id) or repo.reopen }) }`. The label follows the open session: "Going to bed" when none is open, "I'm up" when one is |
| B7 | Workout, `feature/workout/.../today/WorkoutGlanceProvider.kt` | a running session ("Workout in progress · 24 min"), route to the session | none | sessions finished today: name, duration, sets | `Open(WORKOUT, "Workout", FitnessCenter, WorkoutStartRoute)`. If there's no start sheet route, add `@Serializable data object WorkoutStartRoute` showing the existing start choices (saved workouts, Run, Empty) as a bottom sheet destination |
| B8 | Delegation, `feature/delegation/...` | pending approvals and questions as rows ("Hermes asks: which venue?"), route to the approval | none | delegations finished today | none |

Each test checks, for its module:
- rows appear and are today's only (yesterday's items excluded at 00:30);
- the copy has no `!`;
- turning the module off gives nothing. That one is checked once, in A2 test 5, through the gate.

Diet's test must check that **the food text itself** appears (`"shawarma"`), not a count.

Verify each with `:feature:<m>:testDebugUnitTest --tests '*GlanceProviderTest'`, then commit `feat(<m>): Today glance rows and quick action`.

### Phase C: the new Today screen

**C1. Components** in `core/design/src/main/kotlin/app/klean/core/design/component/Glance.kt`, test first with Roborazzi in `core/design/src/test/.../GlanceShotsTest.kt` (light and dark, font scale 1.5):

- **`QuickActionGrid(actions: List<QuickActionUi>, modifier)`**
  - 4 columns of tonal tiles, each 72dp tall and at least 48dp wide, with the icon above a one-line label (autosize like `NavLabel`).
  - The whole grid has `Modifier.testTag(CLUTTER_EXEMPT)`.
  - `QuickActionUi(label, icon, onClick, contentDescription)`.
  - Voice is the first tile and uses the primary container; the others use the surface container high.
- **`GlanceSection(title: String, empty: String?, moreCount: Int, onMore: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit)`**
  - a `SectionHeader`, then the rows on one Soft surface (shape `large`), then "+N more" as a text button when `moreCount > 0`;
  - if `empty` is non-null and there are no rows, one calm line in onSurfaceVariant.
- **`TimelineRow(time: String?, title: String, detail: String?, icon, dimmed: Boolean, checkable: Boolean, onCheck, onClick)`**
  - a 64dp time gutter on the left (Tiimo-style), then the icon, then the title and detail;
  - `dimmed` rows (done) use onSurfaceVariant text;
  - the title takes at most 2 lines and ends in an ellipsis.

Record the references with `:core:design:recordRoborazziDebug`. Verify with `:core:design:verifyRoborazziDebug` → 0 failures. Commit `feat(design): Today whiteboard components`.

**C2. Test first: the Today screen.** In `app/src/test/kotlin/app/klean/today/TodayGlanceScreenTest.kt` (Robolectric Compose, using `TodayContent` fed with a fixed `Glance`):
1. `showsFoodNamesNotCounts`: "shawarma" and "coffee with milk" are displayed, and no node text matches `Regex("\\d+ entr")`.
2. `oneTapFoodOpensTargetedCapture`: clicking the "Food" tile calls `onQuickAction(DIET)` exactly once.
3. `sleepIsInstant`: clicking "Going to bed" runs the `Instant` lambda, and the snackbar text "Good night" with "Undo" appears.
4. `sectionsInOrder`: the tops of Quick actions, Needs attention, Coming up and So far today increase in that order.
5. `noFabOnToday`: there's no node with content description "Add".
6. `clutterBudget`: `assertClutterBudget()`, with the grid exempt.
7. `emptyDayIsCalm`: with no data, each empty section shows its calm line ("Nothing yet", "Nothing else today") and nothing says "no" or "missed".

Run `:app:testDebugUnitTest --tests '*TodayGlanceScreenTest'` and expect FAIL.

**C3. Implement Today.**
1. Rewrite `TodayContent` in `app/src/main/kotlin/app/klean/today/TodayScreen.kt` to take `glance: Glance` and `onQuickAction: (QuickAction) -> Unit`, plus `onVoice`, `onType` and `onOpen(route: String)`.
2. Layout in one `LazyColumn`:
   - the header (unchanged: weekday, date, avatar, ⋮ with Arrange Today);
   - `QuickActionGrid` with Voice, Type, then the module actions;
   - approvals, still via `ApprovalsCard` but placed inside "Needs attention";
   - **Needs attention**, **Coming up** and **So far today**, using `TimelineRow` with times formatted in the device zone and locale (`DateFormat.getTimeFormat`).
3. Delete `TodayCards.kt` and its test. Keep `ArrangeToday*`: it now orders the quick actions and which modules appear.
4. Replace `TodayDashboardViewModel` with `TodayGlanceViewModel(glance: TodayGlance, visible, clock, zone, ticks)`, which emits `TodayState(today, glance, loaded)`.
5. Pass `addSpec = null` for Today so `KleanScreen` hides the FAB. Add a `showAdd: Boolean = true` parameter to `KleanScreen` if one isn't there.
6. Strings go in `app/src/main/res/values/strings.xml`: `today_quick_voice` "Speak", `today_quick_type` "Type", `today_attention` "Needs attention", `today_upcoming` "Coming up", `today_done` "So far today", `today_done_empty` "Nothing yet", `today_upcoming_empty` "Nothing else today", `today_more` "%d more".

Run C2 and expect 7 passing. Re-record the `today-*` Roborazzi references with `:app:recordRoborazziDebug`. Commit `feat(today): Today at a glance — one tap per module, foods eaten, no hero`.

**C4. Track › Week.** Check that it no longer depends on `TodayCards`. It uses `WeekSummaryProvider` and shouldn't. Run `:app:testDebugUnitTest` → 0 failures.

**C5. The Today home-screen widget** (`app/src/main/kotlin/app/klean/widget/TodayWidget.kt`). Make it a mini whiteboard:
- a row of up to 4 quick-action buttons (Voice, Food, Task, Sleep) that use the same actions through `CaptureActivity` intents (the widget is outside the app, so the activity is right here) and `SleepToggleReceiver` for Sleep;
- the next 2 "Coming up" rows;
- the last 3 "So far today" rows, including foods.

Test first: `TodayWidgetStateTest` builds the state from a `Glance` and asserts that "shawarma" is present. Commit `feat(widget): Today widget shows the day so far and one-tap actions`.

### Phase D: Add menu and in-app capture

**D1. Test first: ordering.** Create `app/src/test/kotlin/app/klean/nav/AddMenuEntriesTest.kt`:
```kotlin
@Test fun currentModuleIsLast() {
    val e = addMenuEntries(setOf(ModuleId.TASKS, ModuleId.CALENDAR, ModuleId.DIET), current = ModuleId.CALENDAR)
    assertEquals(ModuleId.CALENDAR, e.last().module)
}
@Test fun contactsAddsNewContactLastOnlyInPeople() {
    val on = setOf(ModuleId.TASKS, ModuleId.CONTACTS)
    assertEquals(ModuleId.CONTACTS, addMenuEntries(on, current = ModuleId.CONTACTS).last().module)
    assertTrue(addMenuEntries(on, current = ModuleId.TASKS).none { it.module == ModuleId.CONTACTS })
}
@Test fun birthdaysCountAsPeople() {
    assertEquals(ModuleId.CONTACTS, addMenuEntries(setOf(ModuleId.CONTACTS, ModuleId.BIRTHDAYS), current = ModuleId.BIRTHDAYS).last().module)
}
```
Expect FAIL.

**D2. Implement** in `AddMenuEntries.kt`:
```kotlin
data class AddEntry(val module: ModuleId, @StringRes val label: Int, val icon: ImageVector, val opensContactEditor: Boolean = false)

private val CONTACT = AddEntry(ModuleId.CONTACTS, R.string.add_contact, Icons.Rounded.PersonAdd, opensContactEditor = true)

fun addMenuEntries(enabled: Set<ModuleId>, current: ModuleId? = null): List<AddEntry> {
    val here = if (current == ModuleId.BIRTHDAYS) ModuleId.CONTACTS else current
    val base = ALL.filter { it.module in enabled } +
        (if (here == ModuleId.CONTACTS && ModuleId.CONTACTS in enabled) listOf(CONTACT) else emptyList())
    val mine = base.filter { it.module == here }
    return base - mine.toSet() + mine // the screen's own item sits nearest the button
}
```
Add the string `add_contact` = "New contact" to `app/src/main/res/values/strings.xml`.

In `KleanAppUi.kt`, compute `current` from the back stack: the module whose `graphRoute` contains `current`. Pass it in. For `opensContactEditor`, call `nav.navigate(ContactEditRoute())`.

Remove "New contact" from the People ⋮ menu (`ContactsModule.kt:91`), so it lives in exactly one place. List the change in `docs/ui/audit.md`.

Verify D1 → 3 passing. Commit `feat(nav): the Add menu's own item sits nearest the button; New contact in People`.

**D3. In-process capture sheet (fixes the zoom).**
- Create `app/src/main/kotlin/app/klean/nav/ShellSheets.kt`:
  - `class ShellSheets { var capture by mutableStateOf<CaptureRequest?>(null) }` with `data class CaptureRequest(val target: ModuleId?, val voice: Boolean = false)`;
  - in `KleanAppUi`, `ShellSheets` is held in `remember`;
  - when `capture != null`, render `CaptureSheet(vm = hiltViewModel<CaptureViewModel>(key = "cap-${req.hashCode()}"), onDismiss = { sheets.capture = null })`.
- `CaptureViewModel` currently reads its target and voice from the Activity intent (`SavedStateHandle`). Add `fun start(target: ModuleId?, source: CaptureSource, voice: Boolean)` and call it from a `LaunchedEffect(req)`. `CaptureActivity` calls the same method from its intent, so there's one code path.
- Replace the `ctx.startActivity(CaptureActivity.intent(...))` calls in `KleanAppUi.kt` lines 148–152 with `sheets.capture = CaptureRequest(e.module)`.

Test first, in `app/src/test/kotlin/app/klean/nav/InAppCaptureTest.kt` (Robolectric):
1. Clicking an Add item doesn't start an activity: `shadowOf(activity).nextStartedActivity` is null.
2. The sheet node "Add something" is displayed.
3. Pressing Back (`activity.onBackPressedDispatcher.onBackPressed()`) dismisses the sheet, and the activity isn't finishing.

Commit `fix(capture): in-app adding opens the sheet in place, so Back doesn't zoom out`.

### Phase E: Back and transitions

**E1. Back audit test** (Waydroid instrumented). Create `app/src/androidTest/kotlin/app/klean/BackNavigationTest.kt`, using the Hilt runner, `createAndroidComposeRule<MainActivity>()` and the test database module already in `app/src/androidTest`.
- For each path below, drive the UI with `onNodeWithText`/`onNodeWithContentDescription` clicks. Then call `Espresso.pressBack()` and assert that the expected previous screen's title node is displayed and `activity.isFinishing == false`.
- Paths:
  1. Today → account menu → Back = Today
  2. Today → account menu → Settings → Back = Today
  3. Settings → each sub-screen in the hub, one by one → Back = the Settings hub. Iterate the hub's row labels.
  4. Plan › Tasks → a task's detail → Back = Tasks
  5. Plan › Calendar → an event → Back = Calendar
  6. Calendar ⋮ → week view → Back = the agenda (or the previous view) — **decide: views are tabs, Back leaves Plan to Today**
  7. Track › Diet → History → a day → an entry → Back ×3, in order
  8. Track › Workout → start sheet → Back closes the sheet only
  9. People → contact → edit → Back = the contact (with a discard dialog if edited)
  10. Any screen with the Add menu open → Back closes the menu only
  11. Any screen with a ⋮ menu open → Back closes the menu only
  12. Any `ModalBottomSheet` (capture, workout start, Move to…) → Back closes the sheet only
  13. Plan (top level) → Back = Today; Today → Back exits (the only place the system zoom is correct)
  14. Arrange Today → Back = Today
  15. Onboarding re-run from Settings → Back = the previous page, then Settings

Run it on Waydroid inside the lock:
```
flock -w 900 ~/.hermes/cache/scratch/waydroid.lock flock ~/.hermes/cache/scratch/gradle.lock nix develop -c env ANDROID_SERIAL=192.168.240.112:5555 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.klean.BackNavigationTest --console=plain
```
Expected first run: failures listing the broken menus. Commit the test, even while red, as `test(nav): Back audit across every screen and overlay`.

**E2. Fix each failure.** Typical fixes:
- a screen whose `onBack = {}` → `nav.popBackStack()`;
- an overlay without `BackHandler(enabled = open) { open = false }`;
- a sub-screen navigated with `popUpTo` that removes its parent.

One commit per screen: `fix(<m>): Back from <screen> returns to <parent>`. Re-run E1 until it's green.

**E3. Route-aware motion.** Test first, in `app/src/test/kotlin/app/klean/nav/MotionKindTest.kt`:
```kotlin
@Test fun groupSwitchIsFadeThrough() = assertEquals(MotionKind.FADE_THROUGH, motionKind(from = TodayRoute::class, to = TasksRoute::class, topLevel = topLevelRoutes))
@Test fun childIsSharedAxis() = assertEquals(MotionKind.AXIS_X, motionKind(from = TasksRoute::class, to = TaskDetailRoute::class, topLevel = topLevelRoutes))
@Test fun accountAndVoiceSlideUp() = assertEquals(MotionKind.SLIDE_UP, motionKind(from = TodayRoute::class, to = AccountRoute::class, topLevel = topLevelRoutes))
```
Implement it in `Motion.kt`:
- `enum class MotionKind { FADE_THROUGH, AXIS_X, SLIDE_UP }` plus `fun motionKind(...)`:
  - top-level means Today, Track Week and every module's `startRoute`;
  - full-screen means `AccountRoute` and `VoiceRoute`.
- `enterTransition` etc. read `initialState.destination` and `targetState.destination`, then pick the kind.
- Fade-through:
  - enter: `fadeIn(tween(210, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(210, delayMillis = 90))`
  - exit: `fadeOut(tween(90))`
- Slide-up:
  - enter: `slideInVertically { it } + fadeIn()`
  - exit: `slideOutVertically { it } + fadeOut()`, used as popExit
- Reduced motion keeps `fadeIn`/`fadeOut(100)` for all.

Commit `feat(nav): fade-through between groups, shared axis into detail, slide up for full-screen menus`.

**E4. Predictive back check.**
- Make sure `android:enableOnBackInvokedCallback="true"` is set on `<application>` in `app/src/main/AndroidManifest.xml`. The default on SDK 37 is on, but being explicit documents it.
- Make sure no code calls `activity.onBackPressed()` directly. Check with `grep -rn "onBackPressed()" --include=*.kt app core feature`; expect only tests.
- Waydroid check: do a back *gesture* rather than a keyevent, with `adb shell input swipe 2 600 300 600 400`, from Plan › Tasks › detail. Screenshot mid-swipe with `adb shell input swipe 2 600 200 600 1500 &` then `wshot`. It should show the Tasks list peeking behind (our pop transition), not a shrunken window. Save the shots to `~/.hermes/cache/scratch/back-shots/`.

Commit `chore(nav): predictive back declared; no direct onBackPressed`.

### Phase V: voice

**V1. Test first: engine order and error mapping.** Create `core/capture/src/test/kotlin/app/klean/core/capture/voice/VoiceEngineTest.kt` with fakes for the system recogniser, the on-device recogniser and the dialog launcher:
1. `systemFirst`: when the system recogniser is available, it's started with `EXTRA_LANGUAGE` = `Locale.getDefault().toLanguageTag()` and **without** `EXTRA_PREFER_OFFLINE`.
2. `languageUnavailableOffersDownload`: system error 13 (or 12) while offline → `VoiceError.NEEDS_LANGUAGE_PACK(languageTag)`.
3. `networkErrorOfflineTriesOnDevice`: system `ERROR_NETWORK` → on-device started, but only if `checkRecognitionSupport` lists the tag as installed.
4. `bothFailFallsBackToDialog`: → `VoiceStep.LaunchSystemDialog(intent)` with `ACTION_RECOGNIZE_SPEECH`, `EXTRA_LANGUAGE_MODEL_FREE_FORM` and the language.
5. `noRecognizerAtAll`: no service, and the dialog intent resolves to nothing (Waydroid without Google) → `VoiceError.NO_SERVICE`, shown as "This phone has no speech recognition."
6. `errorsAreNotAllOffline`: `mapError(ERROR_NO_MATCH)` → `NO_MATCH`, and `ERROR_INSUFFICIENT_PERMISSIONS` → `NO_PERMISSION`. NETWORK while online → `NETWORK` ("Couldn't reach the voice service. Try again"). Only a real offline pack problem says offline.

Expect FAIL.

**V2. Implement.**
1. Split `AndroidVoiceRecognizer.kt` into:
   - `SystemRecognizer`: `SpeechRecognizer.createSpeechRecognizer(ctx)` with the language extra;
   - `OnDeviceRecognizer`: API 31+. On API 33+ it first calls `checkRecognitionSupport(intent, executor, callback)`. When the tag is in `onlineLanguages` but not in `installedOnDeviceLanguages`, it returns `NEEDS_LANGUAGE_PACK`. The UI's "Download" button calls `triggerModelDownload(intent)`.
2. Add `VoiceEngine` to chain them, plus `SystemDialogLauncher`, which uses `rememberLauncherForActivityResult(StartActivityForResult)` and reads `RecognizerIntent.EXTRA_RESULTS`.
3. Connectivity comes from `ConnectivityManager.activeNetwork` + `NET_CAPABILITY_VALIDATED`, behind an interface so tests can fake it.
4. New strings in `core/capture/src/main/res/values/strings.xml`:
   - `voice_needs_pack` "Voice needs the %1$s pack to work offline"
   - `voice_download` "Download"
   - `voice_network` "Couldn't reach the voice service. Try again"
   - `voice_no_service` "This phone has no speech recognition"
   - delete `voice_unavailable_offline`

Run V1 → 6 passing. Commit `fix(voice): use the phone's own recogniser first, say the real reason, offer the language pack`.

**V3. The voice screen.** Add `VoiceRoute` (full screen, slide up) with `core/capture/src/main/kotlin/app/klean/core/capture/voice/VoiceCaptureScreen.kt`:
- **Layout:** a large round mic button (96dp) at the bottom centre, a live level ring driven by `onRmsChanged` (scale 1.0–1.25, still with reduced motion), and the live partial transcript in `headlineSmall` in the middle.
- **Listening state:** shows "Listening" with Stop (✕) top-left and Done.
- **After the final result:** the text stays editable (one field, the only input on the screen). Below it, the classifier's destination chips ("Food", "Task", "Reminder" …, from `RuleCaptureClassifier` via `CaptureViewModel`) with the predicted one selected, then **Save**.
- Saving shows the usual "Saved to Food · Undo · Move to…" and returns to the previous screen.
- **"Speak again"** restarts listening.
- **Errors** from V2 show inline with their one action: Download, Try again, or "Use Google voice", which launches the system dialog.
- **Entry points:** Today's Voice tile, the capture sheet's mic (which now opens this screen instead of dictating into the field), the QS tile's long press (if supported), and a new shortcut `voice` in `app/src/main/res/xml/shortcuts.xml` (`klean://voice`).

Test first, with Roborazzi and Compose tests in `core/capture/src/test/.../VoiceCaptureScreenTest.kt`:
- the listening state shows the partial text;
- the final state shows the chips, with "Food" selected for "a banana and coffee";
- Save calls `onSave("a banana and coffee", DIET)`;
- the needs-pack error shows "Download";
- `assertClutterBudget(maxInteractive = 7, maxTextInputs = 1)`.

Commit `feat(voice): a dedicated voice screen with live transcript and where-it-goes chips`.

**V4. Waydroid voice check.** Waydroid has no real microphone. Check that:
- Today › Speak opens the screen;
- the error path shows a sensible message, not "offline", given there's no service or no mic;
- "Use Google voice" resolves or hides when unavailable;
- Back closes the screen with the slide-down.

Screenshots go to `~/.hermes/cache/scratch/voice-shots/`. Real speech is checked on the Pixel in M29; add a row V5 to `docs/acceptance-p1.md`: "Pixel: Speak → 'banana and coffee' → Food chip → Save; airplane mode with the English pack installed → works; without the pack → Download offered."

### Phase F: wrap up

**F1. Docs.**
- `docs/ui/design-system.md`: the Today whiteboard, the quick-actions budget exception, the motion table, and the Add-menu ordering rule.
- `docs/ui/audit.md`: moved actions (New contact; Today FAB removed, with its actions now on the grid).
- `docs/voice.md`: the engine order, error causes, and the Pixel diagnosis from V0.

Commit `docs: Today at a glance, motion, voice`.

**F2. Full gate**, then Waydroid end-to-end.

Gate:
```
timeout 3500 flock ~/.hermes/cache/scratch/gradle.lock nix develop -c env TZ=UTC ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test :wear:testDebugUnitTest verifyRoborazziDebug --max-workers=3 --console=plain -Pkotlin.compiler.execution.strategy=in-process
```
Expect `BUILD SUCCESSFUL`, then `count-test-results.py` → 0 fail. Run E1 on Waydroid → all pass.

Manual Waydroid script, one lock hold of at most 20 minutes, screenshots to `~/.hermes/cache/scratch/glance-shots/`:
1. **Food:** Today › Food → sheet opens in place → "banana" → Save. Today › So far today shows "banana" with its time.
2. **Sleep:** Today › Going to bed → snackbar "Good night · Undo" → Undo → the label is back to "Going to bed".
3. **People:** People › + → the last item is "New contact" → it opens the editor → Back returns to People.
4. **Back from the Add sheet:** Plan › + → Task → Back closes the sheet, with no zoom.
5. **Transitions:** switching between the 4 tabs fades through; opening a task slides on the X axis.
6. **Large text:** font scale 1.5 → Today's grid labels fit on one line and there's no horizontal scroll.

Run `./gradlew --stop` afterwards.

---

## Validation summary (what "done" means)

- `GlanceTest` (6), the per-module `*GlanceProviderTest` (8 modules), `TodayGlanceScreenTest` (7), `AddMenuEntriesTest` (3), `InAppCaptureTest` (3), `MotionKindTest` (3), `VoiceEngineTest` (6) and `VoiceCaptureScreenTest` all pass.
- `BackNavigationTest` passes on Waydroid, covering all 15 paths.
- The full gate passes with 0 failures and Roborazzi verifies.
- Waydroid screenshots exist for every step in F2.
- `ForbiddenCopyTest` passes. No new string contains `!`, ADHD, autism or guilt words.

## Parallelisation (for subagent lanes)

- **Lane GLANCE:** A → B → C (Today, widget). It's the biggest lane and touches `app/today`, `core/ui`, `core/design` and the providers in each feature.
- **Lane NAV:** D → E (Add menu, in-app sheet, Back audit, motion). It touches `app/nav`, `core/capture/CaptureActivity` + `CaptureViewModel.start`, and module Back fixes.
- **Lane VOICE:** V0 (needs the user) → V1–V4. It touches `core/capture/voice/**` and `VoiceRoute`.
- **Conflicts:**
  - NAV and VOICE both touch `CaptureViewModel`. NAV adds `start(...)` first, and VOICE rebases onto it.
  - GLANCE and NAV both touch `KleanScreen` (the `showAdd` flag), so GLANCE owns that change.
  - Merge order: NAV, then GLANCE, then VOICE, with the gate after each merge.

## Risks, trade-offs, open questions

1. **Clutter vs. one tap.** Eight quick buttons on Today goes against the budget, and the user chose it. Mitigations:
   - a fixed 2×4 grid that never grows;
   - Arrange Today decides which modules get a tile;
   - everything else on the screen still keeps the ≤7 budget.

   *Open:* should Voice and Type always take two of the eight? The plan says yes, because voice was asked for explicitly.
2. **Sleep acting instantly** risks an accidental tap. Mitigations: Undo in the snackbar, visible for 6 s, and the label always says what will happen ("Going to bed" / "I'm up").
3. **Which widget?** "Having the widget be the 'sleep widget'" is read as Today's Track card, which was led by Sleep. The plan also rebuilds the home-screen Today widget (C5) so it isn't biased either. *Open:* confirm with the user whether they meant the home-screen widget only.
4. **Removing Today's FAB** breaks the "+ on every screen" consistency. The grid's Type tile does the same job one tap away. *Open:* keep a small FAB anyway? The plan says no, to avoid two ways of doing the same thing on one screen.
5. **The voice diagnosis needs the Pixel.** V0 is read-only logcat while the user taps the mic. If the user declines, V2's design still covers the likely causes (language pack, network, and the lumped error mapping), but the exact code goes unconfirmed until M29.
6. **`checkRecognitionSupport` and `triggerModelDownload` are API 33+.** On API 28–32 there's no pack check: the on-device path is tried and, if it fails, the dialog fallback is used.
7. **The system voice dialog** depends on Google's app. On de-Googled phones it may not resolve, and the plan hides "Use Google voice" when `resolveActivity` returns null.
8. **Back audit on Waydroid** can't reproduce gesture-specific predictive animations exactly. E4's screenshot check is best-effort, and the Pixel confirms in M29 (add row B1 to `docs/acceptance-p1.md`).
9. **Web parity.** The web client (`web/`) should get the same whiteboard and Add ordering. That's out of scope here; add a follow-up in `TASKS.md` under "Follow-ups".
10. **Data volume in "So far today".** A heavy day (20 foods) makes a long section. That's intended, since it is the point of the screen. The plan never truncates `done`, and the time gutter keeps it scannable.
