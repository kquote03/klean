# Plan: UI/UX overhaul + finishing the remaining milestones

> **Status: DRAFT, waiting for the user's approval.** Approve by replying "approve the UI plan", optionally with your picks for the decisions in §0. Anything you leave unanswered uses the default marked **(default)**.

## Goal

Make Klean feel like one calm, coherent app rather than "a panel of buttons and input boxes".
- Every module is reachable in one tap, with no "More" dead end.
- Screens show content first and controls second.
- One design system is applied everywhere.

Alongside this, M21, M27 and M28 are finished, and M29 (the acceptance session with the user) is prepared.

## Current context / assumptions

### Repo
- `/home/kenan1099/Src/klean`, `main`; public on GitHub as `kquote03/klean`.
- Android app in Kotlin + Jetpack Compose + Material 3 Expressive (material3 1.5.0-rc01), Hilt, Room.
- Web client in `web/` (Vite + TypeScript + `@m3e/web`), served by the server image.

### Navigation today
- `app/src/main/kotlin/app/klean/nav/NavLayout.kt` builds the bar as Today + the first **3** enabled modules by `navOrder` + "More".
- `navOrder` values: Tasks 10, Reminders 15 (inside Tasks), Calendar 20, Contacts 30, Birthdays 35 (inside Contacts), Workout 40, Sleep 50, Diet 60.
- Workout, Sleep, Diet and Settings sit behind `MoreScreen.kt`, a bare list.
- The user's screenshots, 2026-11-10:
  - The Workout screen is a pill button, an outlined text field, small section labels, empty-state sentences and an empty "Exercises" divider list.
  - More is four plain rows.

### Today (`app/src/main/kotlin/app/klean/today/`)
- Module cards in `DashboardSlot` order (APPROVALS, TASKS, AGENDA, BIRTHDAYS, SLEEP, WORKOUT, DIET).
- An "Arrange Today" screen.
- Summary tiles and a week overview.

### Design system (`core/design/`)
- Tokens: `klean-color-tokens.json`, pastel, light and dark, AA contrast tested.
- Font: Cantarell.
- Components: `CaptureField`, `EntryRow`, `KleanCard`, `SummaryTile`, `WeekOverview`, `RepeatChips`, `NeutralEmpty`, `SwitchListItem`, `GapMarker`, `ProviderChip`, `PhotoThumbnail`, `FabClearance`.
- There is no spacing scale, shape scale or screen scaffold, so every feature screen lays itself out ad hoc. That is the root cause of the "slapped together" feel.

### Lane NEXT
- A background agent works in `../klean-next`, branch `next`, from `~/.hermes/cache/scratch/lane-next.md`.
- Done so far:
  - UI polish: calendar view picker, week titles, Tasks tabs.
  - M22: Health Connect + GPS.
  - M23: delegation + Hermes, in progress at `4cdf0b2`.
- Still to do: M21 Google, M28 capture everywhere, M27 Wear, and preparing M29.

### Pending on `main`, from the parent
- The `klean-server` Dockerfile and GHCR pipeline: committed locally in pieces, final gate running, not yet pushed.
- The user must set the GHCR package to Public once after the first push.

### Constraints
- 14 GB of RAM: one Gradle build at a time (`flock ~/.hermes/cache/scratch/gradle.lock`), Waydroid about 2 GB, Android Studio not used.
- Nix for every tool. Commit after every task (Conventional Commits) and push. Write the tests first for logic.
- Copy is calm: no `!`, no guilt words, never ADHD or autism. Diet is a module among equals.
- Test on Waydroid only. The Pixel is kept for the M29 session.

### Installed design skills
- `ui-ux-pro-max`, at `~/.hermes/skills/creative/ui-ux-pro-max`. It includes a `jetpack-compose` stack.
- `frontend-design` (Anthropic). Research notes are in `.hermes/plans/ui-overhaul-research.md`.

## §0 Decisions for the user (defaults apply if unanswered)

1. **Navigation structure** (all of these remove "More"):
   - **A (default): grouped tabs.** Four tabs: **Today · Plan · Track · People**.
     - Plan = Tasks + Reminders + Calendar.
     - Track = Workout + Sleep + Diet.
     - People = Contacts + Birthdays.
     - Inside a group, a segmented control at the top switches modules, and the app remembers the last one used.
     - Settings moves behind an avatar/gear button in Today's top bar.
     - Disabled modules disappear from their group, and a group with no enabled modules disappears from the bar.
     - This follows MindKit (grouping by how a module is used), Material 3 (3–5 fixed destinations) and Apple's "avoid a More tab".
   - **B: customizable bar** (TickTick/Taskito). Today is fixed, the user chooses 3 modules, and the rest open from Today's cards and a module launcher sheet.
   - **C: Today as the directory** (Mint/Samsung Health). The bar holds Today · Add · Search, and every module opens from its Today card.
2. **Visual direction.** The user picks from 3 HTML prototypes in Phase 1:
   - **Soft cards (default):** pastel tonal surfaces, large rounded cards, generous spacing.
   - **Airy list:** Things 3-like, almost no containers, strong typography.
   - **Bento:** Today as a grid of mixed-size tiles.
3. **What to do if the user is away at checkpoint 1** (when the prototypes are ready):
   - **(default)** continue with the recommended combination (A + Soft cards) and record that choice in TASKS.md, so it can be revisited cheaply. Implementing a change costs about a day.
   - Or: stop and wait.
4. **Hot-reload tool trial** (`xception-hash/compose-hot-reload`, unofficial, 0★, debug builds only): **(default) yes, 1-hour timebox.**

## Architecture / approach

**Three phases, so iteration is cheap:**
1. **Prototype in HTML.** No compile, near-zero RAM, viewable on the phone. The user picks a direction.
2. **Build the design system in `core/design`.** Spacing, shape and type scales, a screen scaffold, and the shared components every screen needs. Verify with Roborazzi screenshot tests (JVM, no device) and, if the trial works, the Android hot-reload watcher.
3. **Re-skin screen by screen,** using only those components. Each screen gets a light and dark screenshot test plus a Waydroid check.

**Navigation** moves from "first 3 by `navOrder` + More" to a `NavGroup` model.

**The web client** gets the same structure afterwards (Phase 4). It mirrors the tokens already.

**Ordering with lane NEXT:**
- Phase 1 and the hot-reload trial don't touch feature Kotlin, so they start now, in parallel.
- Phase 2 (design system in `core/design` + the new navigation in `app/`) starts after lane NEXT merges M28. M28 touches capture UI across modules.
- Phase 3 can run alongside M27 (Wear lives in its own module).

---

## Phase 0 — finish what is in flight (parent, about 30 min)

### 0.1 Push the server image work

When `~/.hermes/cache/scratch/gate-main.log` ends with `BUILD SUCCESSFUL`:

```bash
cd /home/kenan1099/Src/klean
git add Dockerfile .dockerignore
git commit -m "build: klean-server Dockerfile (web + server stages, JRE runtime, uid 10001), server-only Gradle mode, Jib removed; GHCR workflow" -- Dockerfile .dockerignore settings.gradle.kts backend/build.gradle.kts build.gradle.kts gradle/libs.versions.toml .github/workflows/docker.yml .github/workflows/ci.yml scripts/image-smoke.sh scripts/image-repro.sh deploy/docker-compose.yml docs/deploy.md web/e2e/run.sh feature/reminders/src/test/kotlin/app/klean/feature/reminders/ReminderSurfacesTest.kt TASKS.md docs/handover-2026-11-10.md
git push -q origin main
gh run watch -R kquote03/klean $(gh run list -R kquote03/klean --limit 1 --json databaseId --jq '.[0].databaseId') --exit-status
```

**Expected:**
- Exit 0.
- Job `docker / image` shows "Smoke test the image", "Reproducible build check" and "Push" in green.
- The step summary lists `ghcr.io/kquote03/klean-server:latest`.

**Then:**
- Tell the user to set the package to Public at https://github.com/users/kquote03/packages/container/klean-server/settings.
- Steer lane NEXT: "main has the Dockerfile; run `git merge origin/main` at your next milestone start".

### 0.2 Merge lane NEXT's finished milestones (M22, M23 once ticked)

```bash
cd /home/kenan1099/Src/klean
git fetch -q origin
git merge --no-edit origin/next
```

Then:
- Run the gate.
- Tag `m22` and `m23`, and push them. The tags also publish the image tags.

**Expected merge conflicts:** `settings.gradle.kts` (keep the `serverOnly` block and add the lane's new includes inside the `else` list) and `TASKS.md` (keep both).

---

## Phase 1 — research → HTML prototypes → user choice (parent, about 3 h, no Gradle)

### 1.1 Audit the current UI from real screens

- Take Waydroid screenshots, holding `waydroid.lock` once, of: Today, Tasks (each tab), Calendar (agenda/week/month), Contacts, Birthdays, Workout, Sleep, Diet, More, Settings, the capture sheet and onboarding. Light and dark. Save to `~/.hermes/cache/scratch/ui-audit/`.
- Write `docs/ui/audit.md`. One section per screen, each listing:
  - what the screen is for, in one sentence;
  - its primary action;
  - its problems, each as a rule broken (from `ui-ux-pro-max --domain ux` results and Nielsen heuristics);
  - a screenshot path.
- Run, and paste the relevant rows into the audit:

  ```bash
  S=~/.hermes/skills/creative/ui-ux-pro-max/scripts/search.py
  nix shell nixpkgs#python3 -c python3 $S "navigation bottom bar overflow tabs" --domain ux
  nix shell nixpkgs#python3 -c python3 $S "empty state form input hierarchy" --domain ux
  nix shell nixpkgs#python3 -c python3 $S "card list spacing" --stack jetpack-compose
  ```

- Commit: `docs(ui): audit of every screen with the rules each breaks`.

### 1.2 Study reference apps

- For each of TickTick, Things 3, Todoist, Google Calendar, Samsung Health, Bevel, Structured and Notion Calendar, capture 2–3 store screenshots or official help-page images with `web_extract` or the browser tool.
- Note in `docs/ui/references.md`:
  - navigation structure;
  - how the home screen summarises modules;
  - how each app makes "add" a single action;
  - how empty states look.
- Link the sources. Don't copy assets into the repo.

### 1.3 Set up the prototype workspace `prototypes/ui-2026-11/`

Plain HTML/CSS, no build step:

```
prototypes/ui-2026-11/
  index.html             # links to every direction and screen; phone-sized frames side by side
  tokens.css             # copied from web/src/generated/tokens.css (same pastel tokens as the app)
  fonts/                 # Cantarell, copied from core/design/src/main/res/font/
  a-soft-cards/{today,plan,track,people,workout,sleep,diet,add-sheet}.html
  b-airy-list/…          # same screens
  c-bento/…              # same screens
```

- Each screen is a 412×915 frame (a Pixel-class phone) with realistic seeded content from Klean's own domain:
  - 3 tasks, one of them past due, under "Still want these?";
  - 2 events, a birthday in 3 days, last night's sleep 7h10, today's food "oatmeal, banana";
  - a saved workout, "Push day".
- Prototypes must follow the house rules too: pastel tokens only, Cantarell, 48 dp targets, calm copy.

**Serve them to the user's phone over Tailscale:**

```bash
nix shell nixpkgs#python3 -c python3 -m http.server 8787 --directory prototypes/ui-2026-11
```

The user opens `http://<host tailscale name>:8787/`. Check the name with `tailscale status | head -1`.

### 1.4 Build the three directions

For each of the three directions, build the 8 screens.

**Every direction must solve:**
- every module one tap from anywhere;
- one "+" that opens an add menu (Material 3 FAB menu: Task, Event, Reminder, Log food, Log workout, Log sleep), with text capture at the top of that menu;
- module screens that lead with content and history, with the capture field reached from the "+".

**Screenshots:** take them with headless Chromium:

```bash
nix shell nixpkgs#chromium -c chromium --headless --window-size=412,915 --screenshot=out.png file://$PWD/prototypes/ui-2026-11/a-soft-cards/today.html
```

Look at each one yourself, and fix it until it passes the `frontend-design` and `ui-ux-pro-max` checklists.

**Commit:** `feat(prototypes): three UI directions for the overhaul (HTML, no build)`.

### 1.5 CHECKPOINT 1: the user chooses

- Send the user the link, plus one side-by-side image per direction made with `index.html`, in Telegram.
- Ask, in one message:
  - which navigation structure (A/B/C);
  - which visual direction;
  - anything they dislike.
- If there's no answer within 2 hours of sending: follow decision 3 (default: A + Soft cards). Record the choice in `docs/ui/decision.md` with the reasons.

### 1.6 Write the design spec `docs/ui/design-system.md`

Derived from the chosen prototype:

- **Spacing scale:** 4, 8, 12, 16, 24, 32, 48. Screen side margin 16; card padding 16; gap between cards 12; gap between sections 24.
- **Shapes:** small 8, medium 16, large 24, full (pill); and which component uses which.
- **Type scale:** Cantarell. `displaySmall` for screen titles only on Today; `titleLarge` for module screen titles; `titleMedium` for card titles; `bodyLarge` for list items; `labelLarge` for chips and buttons.
- **Surfaces:** background = `surface`; cards = `surfaceContainerLow`; a card's hero area = the module's tonal container. Each module gets one pastel accent from the existing tokens:
  - Tasks: primary;
  - Calendar: secondary;
  - People: tertiary;
  - Track: secondary or tertiary containers.
- **Motion:** shared-axis transitions between tabs and container transforms from Today cards into modules, 250 ms, honouring reduced motion.
- **Components list:** the targets of Phase 2.

---

## Phase 1b — trial the hot-reload tool (parent, 1-hour timebox, after lane NEXT releases Gradle and Waydroid)

Work on a throwaway branch: `git switch -c spike/hot-reload`.

1. Read `https://github.com/xception-hash/compose-hot-reload/blob/main/docs/ai-project-setup.md` in full.
2. Apply the `dev.hotreload` Gradle plugin to `app/build.gradle.kts`, **debug variant only**, as the docs show.
3. Run its `prepare` and `doctor` against `ANDROID_SERIAL=192.168.240.112:5555` with the Nix JDK 21 (`--project-java-home $(dirname $(dirname $(readlink -f $(nix develop -c which javac))))`).

   Expected: `doctor` reports ready.
4. Start `watch`. Edit a literal in `TodayScreen.kt`, for example the greeting's padding from 16.dp to 24.dp, and save.

   Expected: the Waydroid screen changes within about 2 s without the app restarting. Capture before and after with `wshot`.
5. Measure peak RSS during watch with `ps -o rss= -p <watcher pid>` and record it.
6. Decide:
   - **It worked:** keep it as a debug-only, opt-in Gradle property (`-Pklean.hotreload=true`) so CI and release builds never apply it. Document the loop in `docs/ui/iteration.md`. Commit to `main`: `build: opt-in Compose hot reload for debug builds (UI iteration)`.
   - **It failed within the hour:** delete the branch, and note what failed in `docs/ui/iteration.md`.

---

## Phase 2 — design system and navigation (after lane NEXT merges M28; parent or one UI lane)

### 2.1 Roborazzi screenshot tests (iteration loop + regression tests)

1. Find the current versions:

   ```bash
   scripts/latest-versions.sh io.github.takahirom.roborazzi:roborazzi
   ```

   Add `roborazzi`, `roborazzi-compose` and `roborazzi-junit-rule` to `gradle/libs.versions.toml`, and the `io.github.takahirom.roborazzi` plugin.
2. In `build-logic/convention/src/main/kotlin/AndroidLibraryComposeConventionPlugin.kt`:
   - apply the plugin;
   - add the test dependencies;
   - set `testOptions.unitTests.isIncludeAndroidResources = true`;
   - add the system property `robolectric.graphicsMode = NATIVE`.
3. Create `core/design/src/test/kotlin/app/klean/core/design/shots/Shots.kt`:

   ```kotlin
   package app.klean.core.design.shots

   import androidx.compose.runtime.Composable
   import androidx.compose.ui.test.junit4.v2.createComposeRule
   import app.klean.core.design.theme.KleanTheme
   import com.github.takahirom.roborazzi.captureRoboImage

   /** Renders [content] in light and dark at phone size into build/outputs/roborazzi/<name>-{light,dark}.png. */
   fun shot(name: String, content: @Composable () -> Unit) {
       for (dark in listOf(false, true)) {
           captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png") {
               KleanTheme(darkTheme = dark) { content() }
           }
       }
   }
   ```

   Check `KleanTheme`'s real parameter name in `core/design/src/main/kotlin/app/klean/core/design/theme/` and use it.
4. Test first: `core/design/src/test/kotlin/app/klean/core/design/shots/ComponentShotsTest.kt`, with `@RunWith(AndroidJUnit4::class) @Config(qualifiers = "w412dp-h915dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)` and one test per component calling `shot("summary-tile") { SummaryTile(…) }`.
5. Record and verify:

   ```bash
   flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew :core:design:recordRoborazziDebug --console=plain
   ls core/design/build/outputs/roborazzi/
   ```

   Expected: `*-light.png` and `*-dark.png` per component. Look at them. Commit the reference images under `core/design/src/test/snapshots/` (configure `roborazzi.outputDir`).
6. Add `verifyRoborazziDebug` to the gate command in AGENTS.md (with approval) and to the CI gate step.

   Commit: `test(design): Roborazzi screenshot tests in light and dark (JVM, no device)`.

### 2.2 Design tokens in Kotlin: `core/design/src/main/kotlin/app/klean/core/design/theme/Dimens.kt`

```kotlin
package app.klean.core.design.theme

import androidx.compose.ui.unit.dp

/** The only spacing values screens may use (docs/ui/design-system.md). */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val screenEdge = md
    val cardPadding = md
    val cardGap = sm
    val sectionGap = lg
}
```

- Add `KleanShapes` (small 8, medium 16, large 24) to `KleanTheme`'s `MaterialTheme(shapes = …)`.
- Add a lint-style unit test, `core/design/src/test/.../NoRawDpTest.kt`, modelled on the existing `NoRawColorsTest`. It fails when any `feature/**/ui/**/*.kt` file uses `.padding(` with a raw `NN.dp` other than `0.dp`.
  - Start it as `@Ignore` with a TODO.
  - Remove the `@Ignore` at the end of Phase 3, when every screen has been converted.

### 2.3 Core components (each: Roborazzi test first → implement → record → look → commit)

All live in `core/design/src/main/kotlin/app/klean/core/design/component/`:

1. **`KleanScreen`:** the one screen scaffold.
   - Top bar with a `titleLarge` title, optional subtitle and optional trailing actions.
   - A content slot with `Space.screenEdge` side margins.
   - The FAB slot (`KleanAddButton`), and a snackbar host.
   - Every feature screen uses it.
2. **`SectionHeader`:** `titleSmall` in onSurfaceVariant, an optional trailing "See all" text button, `Space.sectionGap` above.
3. **`ModuleCard`:** the card used on Today and at the top of module screens.
   - A tonal header with the module icon and title.
   - A body slot.
   - An optional single primary action, as a tonal button.
   - The whole card is clickable to open the module.
4. **`ListRow`:** replaces ad-hoc rows. Leading icon or avatar, title, supporting text, trailing meta or checkbox, 56–72 dp tall.
5. **`EmptyState`:** an illustration-free tonal icon, one calm sentence and an optional single action. It replaces bare grey sentences like "Workouts you log show up here."
6. **`KleanAddButton` + `AddMenu`:** a Material 3 Expressive `FloatingActionButtonMenu` with the items Task, Event, Reminder, Log food, Log workout, Log sleep. Disabled modules are hidden.
   - The top item is "Type or speak…", which opens the existing capture sheet.
   - Each menu item opens the capture sheet targeted at that module (`KleanModule.capturePlaceholderRes` already exists).
7. **`SegmentedTabs`:** a Material 3 `SingleChoiceSegmentedButtonRow` for switching modules inside a group. It remembers the last choice per group in DataStore.

Commit each one separately, e.g. `feat(design): KleanScreen scaffold`.

### 2.4 Navigation: `NavGroup` (decision A)

**Test first:** `app/src/test/kotlin/app/klean/nav/NavGroupsTest.kt`.

```kotlin
class NavGroupsTest {
    private fun ids(vararg m: ModuleId) = m.toList()

    @Test fun allModulesOnGiveFourGroupsInOrder() {
        val groups = navGroups(ids(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR, ModuleId.CONTACTS,
            ModuleId.BIRTHDAYS, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET))
        assertEquals(listOf(NavGroup.TODAY, NavGroup.PLAN, NavGroup.TRACK, NavGroup.PEOPLE), groups.map { it.group })
        assertEquals(ids(ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET), groups.single { it.group == NavGroup.TRACK }.modules)
    }

    @Test fun aGroupWithNothingEnabledDisappears() {
        val groups = navGroups(ids(ModuleId.TASKS, ModuleId.CALENDAR))
        assertEquals(listOf(NavGroup.TODAY, NavGroup.PLAN), groups.map { it.group })
    }

    @Test fun noMoreDestinationEverExists() {
        assertTrue(NavGroup.entries.none { it.name == "MORE" })
    }
}
```

Use the real `ModuleId` constant names from `shared/src/commonMain/kotlin/app/klean/shared/ModuleId.kt`, including `DELEGATION` if M23 added it. Delegation shows in Today's APPROVALS card and in Plan, not as a tab.

**Implementation:** `app/src/main/kotlin/app/klean/nav/NavGroups.kt`.

```kotlin
package app.klean.nav

import app.klean.shared.ModuleId

/** Bottom-bar destinations (holistic UI plan, decision A). Modules are grouped by how they're used; no "More". */
enum class NavGroup(val members: List<ModuleId>) {
    TODAY(emptyList()),
    PLAN(listOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR)),
    TRACK(listOf(ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DIET)),
    PEOPLE(listOf(ModuleId.CONTACTS, ModuleId.BIRTHDAYS)),
}

data class NavGroupEntry(val group: NavGroup, val modules: List<ModuleId>)

/** Today always; every other group only when at least one of its modules is enabled, keeping [enabled]'s members in group order. */
fun navGroups(enabled: List<ModuleId>): List<NavGroupEntry> = NavGroup.entries.mapNotNull { g ->
    if (g == NavGroup.TODAY) NavGroupEntry(g, emptyList())
    else g.members.filter { it in enabled }.takeIf { it.isNotEmpty() }?.let { NavGroupEntry(g, it) }
}
```

**Then:**
- In `app/src/main/kotlin/app/klean/nav/KleanAppUi.kt`, replace the `navLayout(...)` bar with one item per `navGroups(...)` entry. Labels are new strings `nav_today` / `nav_plan` / `nav_track` / `nav_people`. Icons: rounded `Today`, `EventNote`, `Insights`, `People`.
- Each group route shows `SegmentedTabs` across its modules plus that module's existing start screen below.
  - A group with only one module shows no segmented control.
  - Reminders and Birthdays stay as tabs inside Tasks and Contacts, as they are now, so the Plan segments are Tasks · Calendar and the People group has a single member.
- **Delete** `MoreScreen.kt`, `MoreRoute` and `NavLayout.kt`, along with their tests.
- Settings becomes an icon button in Today's `KleanScreen` top bar, and in each group's overflow menu.
- Keep the "Arrange Today" entry where it is, in Today's top-bar menu.

**Verify:**
- `:app:testDebugUnitTest` passes.
- Waydroid: the bar shows 4 items. Track → Workout/Sleep/Diet switch with one tap. Settings opens from Today.
- With Diet, Workout and Sleep all turned off, Track disappears.
- Screenshot all of the above.

**Commit:** `feat(nav): grouped tabs (Today, Plan, Track, People) replace the More overflow`.

---

## Phase 3 — screen-by-screen redesign (one commit per screen; Roborazzi light/dark + Waydroid check each)

**Order** (highest traffic first):
1. Today
2. the Add menu and capture sheet
3. Tasks
4. Calendar
5. Track: Workout
6. Track: Sleep
7. Track: Diet
8. People: Contacts and Birthdays
9. Settings
10. Onboarding
11. Widgets: Glance, using the same tokens and shapes

**Per screen:**
1. Write `ScreenShotsTest` in the feature module (`feature/<m>/src/test/.../ui/<Screen>ShotsTest.kt`) rendering the screen's stateless `…Content` composable with seeded fake state, in empty, typical and long-content cases. Record the "before" images.
2. Rebuild the screen with `KleanScreen`, `ModuleCard`, `SectionHeader`, `ListRow` and `EmptyState` only:
   - no raw `dp` outside `Space`;
   - no ad-hoc `Text(style = …)` sizes outside the type scale;
   - **the capture field leaves the module screen.** "Add" is the FAB menu; the screen opens with content.
3. Record the "after" images and compare them with the prototype side by side. Fix until they match the chosen direction.
4. Install on Waydroid in one `waydroid.lock` hold:
   - tap through the screen's primary flow;
   - check font scale 1.5;
   - take screenshots.
5. Copy check: `./gradlew :app:testDebugUnitTest --tests '*ForbiddenCopyTest'` passes.
6. Commit, e.g. `feat(workout): redesigned around recent sessions and saved workouts; logging moves to the Add menu`.

**Workout, as the worked example of what "content first" means** (from the user's screenshot):
- **Top:** a `ModuleCard` "This week", showing sessions and minutes, with a mini bar for each day.
- **Next:** `SectionHeader("Saved workouts")`, with horizontally scrolling tonal cards, one per template. Each has a big "Start" affordance, plus a "New" card at the end.
- **Then:** `SectionHeader("Recent")`, a `ListRow` per session (date, exercises summary, duration).
- **With no data:** a single `EmptyState` ("Your workouts will show up here") with one "Start a workout" action. Not three separate grey sentences.
- **Removed:** the in-page text field (logging moves to the Add menu), and the empty "Exercises" divider list (it moves into a session's detail).

**At the end of Phase 3:**
- Remove `@Ignore` from `NoRawDpTest`.
- Run the full gate plus `verifyRoborazziDebug`.
- Merge, and tag `ui1`.

## Phase 4 — the web client follows (one lane, pnpm only, no Gradle lock)

- Port the chosen structure to `web/`:
  - the same four groups (a rail on a laptop, a bar on a phone);
  - the same Add menu;
  - the same components as Lit templates.
- Update `web/e2e/visual.spec.ts` baselines.
- Visual review at 1440×900 and 390×844, light and dark.
- Commit: `feat(web): grouped navigation and the redesigned screens`.

---

## Remaining milestones (lane NEXT keeps going; the parent merges)

| Milestone | Owner | When | Notes |
|---|---|---|---|
| M23 Delegation + Hermes | lane NEXT | in progress | Merge and tag `m23` in Phase 0.2 |
| M21 Google Tasks + Assistant | lane NEXT | next | Fakes only; `docs/google-oauth.md`; user's OAuth IDs needed for real use |
| M28 Capture everywhere | lane NEXT | after M21 | **Phase 2 waits for this merge**, because both touch capture UI |
| M27 Wear OS | lane NEXT | after M28 | Separate `wear/` module, so it can overlap Phase 3. Its tiles use the new tokens. |
| M29 prep | lane NEXT | last | `docs/acceptance-p1.md` with the Waydroid and Backend+Browser columns pre-filled |
| UI Phase 1 + 1b | parent | now (1) / when Gradle is free (1b) | No conflicts with lane NEXT |
| UI Phase 2–3 | parent, or one UI lane in `../klean-ui` | after `m28` | Lane NEXT must not edit feature screens after M28. I'll steer it. |
| UI Phase 4 | one web lane | after Phase 3 Today + Plan land | pnpm only |
| M29 acceptance | user + parent | after `ui1` | On the Pixel, watch, Docker server and browser; record in `docs/acceptance-p1.md`; tag `p1` |

**RAM rule for this period:**
- At most **one** Gradle-heavy agent at a time.
- HTML prototyping, web/pnpm work and design research don't count against that.
- The hot-reload watcher replaces full builds during Phase 3 if the trial works.

## Tests / validation summary

| Check | Command | Expected |
|---|---|---|
| Prototypes render | headless Chromium screenshot per screen | 24 PNGs (3 directions × 8 screens), reviewed |
| Navigation logic | `./gradlew :app:testDebugUnitTest --tests '*NavGroupsTest'` | 3 passing tests |
| Components | `./gradlew :core:design:verifyRoborazziDebug` | no image diffs |
| Screens | `./gradlew verifyRoborazziDebug` | no image diffs |
| No ad-hoc spacing | `NoRawDpTest` (un-ignored at the end of Phase 3) | passes |
| Copy | `ForbiddenCopyTest`, `web/scripts/check-copy.mjs` | pass |
| Whole app | the gate + `connectedDebugAndroidTest` on Waydroid | BUILD SUCCESSFUL, all instrumented tests pass |
| Web | `pnpm -C web test && pnpm -C web build && pnpm -C web visual` | pass, with new baselines |

## Risks, tradeoffs, open questions

1. **Taste is the user's call.** Prototypes front-load that decision, which is the cheap place to change course. The default path (A + Soft cards) is reversible, but redoing Phase 3 costs about a day.
2. **Grouped tabs add one tap within a group** (Track → Sleep when Workout was last used). This is mitigated by remembering the last segment, by Today's cards (one tap into any module), and by the Add menu (one tap to log anything).
3. **Merge friction with lane NEXT.** Phase 2–3 rewrites feature screens, so the lane must be done with screen edits (after M28) first. I'll steer it to stay in `wear/` and docs once M28 merges.
4. **The hot-reload tool is unofficial and new.** It's debug-only and opt-in, timeboxed to one hour, with Roborazzi as the fallback.
5. **Roborazzi on Robolectric NATIVE graphics** can differ slightly from a device: shadows, font hinting. Waydroid stays the final check for each screen.
6. **Material 3 Expressive APIs are still RC** (`FloatingActionButtonMenu`, `SingleChoiceSegmentedButtonRow`, short navigation bar). If one doesn't resolve with material3 1.5.0-rc01, build the component from stable parts and note it in AGENTS.md deviations.
7. **Usage limits.** Each phase commits per task, so any agent can resume from TASKS.md.
