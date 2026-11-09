# Klean — re-plan: holistic life management, full product

Source brief: `/home/kenan1099/Downloads/Klean — Prototype 1 Build Prompt (1).md` (the revised one; Prototype 1 is now the whole product).
User direction (2026-11-09): **diet is what I need most, but it is not the app's focus. Prioritise the other features and steer away from diet; even the icon must not be about food.**

## Goal

Re-centre Klean on holistic life management, then build out the full product in this order: every remaining module, Google, Health Connect, AI delegation with Hermes, the sync backend, the web client, and the Wear OS app. Diet is kept as one finished module among equals.

## Current context and assumptions

### What exists (on `main`, all pushed to `github.com/kquote03/klean`; CI and nightly releases are green)

- **Milestones `m0`–`m15`** (see `TASKS.md`):
  - foundation: shell, module registry, design system, onboarding, quiet hours, backup/restore, AI layer (Gemini, OpenAI-compatible, on-device LiteRT-LM)
  - a **complete diet module**: status notification + inline reply, QS tile, Glance widget, check-ins with rotation and learning, voice, photos, history and gaps, unlock sheet, delivery listener, places
- **Today dashboard:** greeting, summary tiles and a week overview, fed by the `DashboardSummaryProvider` / `DayActivityProvider` contracts in `core/ui/src/main/kotlin/app/klean/core/ui/module/Dashboard.kt`.
- **Diet-centric things that must change:**
  - The launcher icon is a bowl and spoon (`app/src/main/res/drawable/ic_launcher_foreground.xml`, commit `f88ce17`).
  - The QS tile label is "Log food" (`core/capture/src/main/res/values/strings.xml: tile_label`).
  - The persistent notification is diet-only: "What did you eat?" / "Log food" (`feature/diet/.../notification/DietStatusNotifier.kt`, strings in `feature/diet/src/main/res/values/strings.xml`).
  - The only widget is the diet widget.
  - Universal capture defaults to diet.
  - `DashboardSlot` lists DIET first.

### In flight right now (do not disturb; fold their results in)

- **Part F close-out agent on `main`:** Waydroid checks, the launcher icon, test fixes, CI flake root cause.
- **P0 foundation agent** (worktree `/home/kenan1099/Src/klean-p2`, branch `p2-foundation`):
  - already committed: module skeletons for tasks, reminders, calendar, contacts, workout and sleep; schema v2 (AutoMigration 1→2) with `task_list`, `task`, `reminder`, `event`, `contact`, `workout_template`, `workout_session`, `workout_set` and `sleep_session`; a `ReminderRepository` contract in `:core:data`
  - next: seeding new modules off, the bottom-bar "More" overflow, the Klean Android account, TASKS sections
  - Spec: `.hermes/plans/2026-11-09_170500-parallel-life-modules.md`
- **P0 parsers agent** (worktree `/home/kenan1099/Src/klean-p2-parsers`, branch `p2-parsers`): `NaturalTime` and the offline `RuleCaptureClassifier` / `CompositeCaptureClassifier`.

### How this plan relates to earlier plans

- **This plan supersedes** the module scope and ordering in `.hermes/plans/2026-11-09_170500-parallel-life-modules.md`.
- **Reused from that plan:** its Phase 0, its per-stream checklists (S1–S6) and its merge procedure, with the amendments below.
- **No longer valid:** its "deferred" column. Google Tasks, Health Connect, DAVx5, ICS, Wear OS, the backend, web and Hermes are now **in scope**.
- **`PLAN.md`** remains the record of the diet build (M0–M15).

### Environment

- NixOS, with the toolchain from `flake.nix`.
- `docker` 29.8 and `podman` are installed on the host; `node` is on the host path. Add `nodejs_22` and `pnpm` to the flake for reproducibility.
- Device: Waydroid only (`192.168.240.112:5555`, Android 13, x86_64, no Play services, camera, GPS or Health Connect).
- Wear OS: no emulator in the flake. Wear UI is tested with Robolectric/Compose tests; real-watch checks go to the user's device session.
- The Pixel 9a is reserved for the user's acceptance session.

### Hermes facts (from the local `hermes-agent` skill docs)

- Hermes has an **API Server** platform adapter (`gateway/platforms/api_server.py`), an OpenAI-compatible endpoint that routes through the full agent. That is what Open WebUI uses.
- It also has **Webhooks** and **ntfy** adapters.

Klean's `DelegationAgent` targets the API Server first; see M23. The task-0 research step confirms the exact endpoint and auth on the user's version: `hermes --help`, the docs at https://hermes-agent.nousresearch.com/docs, and `hermes gateway` / API-server config.

## Re-prioritisation decisions (the user is around; flag any you disagree with)

**H1. Klean's identity is holistic.**
- **New icon:** a pastel lavender rounded square with a white "day ring". This is a circle arc with a small check mark inside, and a dot at the arc's end ("your day, handled"). No food imagery.
- **QS tile:** "Add to Klean".
- **Persistent notification:** "Klean · Today", a holistic status line with one inline reply, **"Add something"**, which goes to universal capture.
- **Widgets:** a new holistic **Today widget** (next items across modules plus a capture pill). The diet widget stays as a module widget.

**H2. Diet is feature-frozen.** It's finished and stays as it is. Its remaining brief items (watch tile, Health Connect `NutritionRecord`, web parity, cross-device prompt clearing) are built inside the platform milestones (M22, M25–M27), not as diet work.

**H3. The status notification is holistic.**
- **Text:** one ongoing low-importance notification. Title: "Today" plus a compact line from module status providers, e.g. "2 tasks · Dentist 14:00 · 3 food entries".
- **Inline reply:** "Add something" routes through universal capture. With no AI, the rule parser routes obvious phrases. Plain text with no other match still falls back to diet (the brief: "otherwise defaults to the diet module, with a one-tap 'move to…' chip"), so the user's under-5-second food log is preserved.
- **Diet check-ins:** they stay as their own prompts.
- **Ask-first item:** the brief describes the diet notification as diet-specific, so this is a deviation from it. It's listed under open questions; default to building it.

**H4. Today card order:**
1. Hermes approvals (shown prominently)
2. Tasks
3. Agenda (today's reminders + events)
4. Birthdays
5. Sleep
6. Workout
7. Diet

The user can reorder or hide cards once (brief), stored in DevicePrefs (device-local layout).

**H5. Bottom bar:**
- Today, Tasks, Calendar, then "More" (Contacts, Workout, Sleep, Diet, Settings). A navigation rail on large screens.
- `navOrder`: Tasks 10, Calendar 20, Contacts 30, Workout 40, Sleep 50, Diet 60.
- Reminders are a tab inside Tasks (the brief treats "Tasks and reminders" as one module). Birthdays are a tab inside Contacts. The `REMINDERS` and `BIRTHDAYS` ids remain separate gates, so their notifications can be turned off on their own.

**H6. New modules default ON** for a holistic app.
- **Fresh installs:** the wizard's module step shows all of them on.
- **Existing installs** (the user's Pixel): a one-time "New in Klean" sheet lists the new modules, switched on, with a single "Done". This replaces P0.4's "seed off" behaviour (see M16.6).

**H7. Universal capture fallback.** With no AI and no rule match, it still defaults to diet (brief §Universal capture), but always shows a "Move to…" chip row (Task, Reminder, Event, Contact, Workout, Sleep).

**H8. Milestone order:**
1. M16 Holistic re-centre
2. M17 Foundation 2
3. M18–M20, Android modules in parallel
4. M21 Google
5. M22 Health Connect + GPS
6. M23 Delegation + Hermes
7. M24 Backend, in parallel from M17 onwards
8. M25 Android sync
9. M26 Web, in parallel from when M24's API is frozen
10. M27 Wear OS
11. M28 Capture everywhere + multi-device
12. M29 Acceptance

Diet's milestone already happened (it was first, as the brief required).

**H9. Minimum SDK stays 28.** Health Connect requires API 28+ through its APK, and is built in from API 34. Wear OS minimum is API 30 (Wear OS 3).

## Architecture

The monorepo grows from an Android app into four products sharing `:shared` (KMP):
- **Android:** `app/`, `core/*`, `feature/*`
- **Wear OS:** `wear/`
- **Ktor backend:** `backend/`, a JVM Gradle module in the same build, packaged with Jib
- **Web client:** `web/`, TypeScript + Vite + `@m3e/web`, built with pnpm and served by the backend

Every syncable entity already follows UUIDv7, `SyncColumns`, tombstones and `change_seq`. M17 adds the remaining entities from the brief. M24/M25 add a cursor-based push/pull protocol (last-write-wins on `updatedAt`) with blob transfer. Holistic surfaces (status line, Today widget, Today cards, capture) are fed by registry-style multibindings, so each module contributes and none is special-cased.

### Repository layout (target)

```
klean/
  app/                 Android phone app (single activity)
  wear/                Wear OS app: Compose for Wear, Tiles, Complications, Data Layer
  shared/              KMP (jvm, android): models, sync protocol DTOs, merge, NaturalTime, rules, RRULE, vCard/iCal codecs
  core/{common,design,data,ui,notifications,ai,capture,sync,health,google}
  feature/{tasks,reminders,calendar,contacts,workout,sleep,diet,delegation,onboarding,settings}
  backend/             Ktor server (JVM): sync, pairing, blobs, DAV, Google, push, Hermes relay, AI proxy; Jib image
  web/                 Vite + TypeScript + @m3e/web PWA; served by backend at /
  deploy/              docker-compose.yml, Caddy/Traefik examples, Tailscale notes
  build-logic/         convention plugins (+ klean.jvm.backend)
```

### Sync protocol (M24/M25; DTOs live in `:shared`, `shared/src/commonMain/kotlin/app/klean/shared/sync/`)

```kotlin
@Serializable data class SyncRecord(
    val table: String,             // "task", "food_entry", …
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val json: JsonObject,          // the row's columns, snake_case, excluding change_seq
)
@Serializable data class PushRequest(val deviceId: String, val records: List<SyncRecord>)
@Serializable data class PushResponse(val accepted: Int, val serverCursor: Long)
@Serializable data class PullResponse(val records: List<SyncRecord>, val cursor: Long, val hasMore: Boolean)

/** Last-write-wins: higher updatedAt wins; ties broken by deviceId lexical order (deterministic everywhere). */
fun lww(local: SyncRecord?, remote: SyncRecord): SyncRecord =
    if (local == null || remote.updatedAt > local.updatedAt ||
        (remote.updatedAt == local.updatedAt && remote.deviceId > local.deviceId)) remote else local
```

API surface (all under `/api/v1`; bearer device token unless noted):

| Route | Purpose |
|---|---|
| `POST /pair/start` (admin session) → `{code, qr}` | Web shows a QR containing `klean://pair?url=…&code=…` |
| `POST /pair/claim {code, deviceName}` → `{deviceId, token}` | Phone scans the QR and claims a device token |
| `GET /devices`, `DELETE /devices/{id}` | List and revoke devices |
| `POST /sync/push`, `GET /sync/pull?cursor=&limit=` | Entity sync |
| `PUT /blobs/{sha256}`, `GET /blobs/{sha256}` | Photos and attachments |
| `/dav/contacts/…` (CardDAV), `/dav/calendars/…` (CalDAV) | DAVx5 and other DAV clients (basic auth with an app password) |
| `GET /google/oauth/start`, `/google/oauth/callback`, `GET /google/calendar/events`, `GET/PATCH /google/tasks/…` | Server-side Google for the web |
| `POST /push/subscribe` (VAPID), `POST /push/test` | Web push |
| `POST /hermes/tasks`, `GET /hermes/tasks/{id}/events` (SSE) | Hermes relay |
| `POST /ai/v1/chat/completions` | Optional proxy to a local OpenAI-compatible model |
| `GET /health`, `POST /admin/backup` | Ops |

## Execution model

- **Parallel lanes,** each in its own git worktree and branch:
  - A: Android modules
  - B: backend
  - C: web
  - D: Wear
  - E: Google / Health / Delegation
- **Shared locks:**
  - every Gradle call: `flock /home/kenan1099/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew … -Pkotlin.compiler.execution.strategy=in-process`
  - every Waydroid session: `flock /home/kenan1099/.hermes/cache/scratch/waydroid.lock`
  - web `pnpm` builds need no lock
- **Merging:** the parent merges each lane after it reports, runs the gate on `main`, pushes, waits for CI, and tags.
- **Subagent context:** each subagent gets the shared brief (`/home/kenan1099/.hermes/cache/scratch/klean-subagent-brief.md`), this plan, and its lane section.
- **Rules for every agent:**
  - TDD, Conventional Commits, commit only your own paths, push your branch;
  - every lane updates its own `TASKS.md` section;
  - copy rules: calm, no `!`, no guilt words, never ADHD/autism (`ForbiddenCopyTest` scans Android `res`; M26 adds `web/scripts/check-copy.mjs`).

---

## M16 — Holistic re-centre (one agent, on `main`, after Part F and P0 merge)

**Prerequisite:** the parent merges `p2-parsers` into `p2-foundation`, then `p2-foundation` into `main` after Part F finishes. The gate must pass, then tag `p2-foundation`.

### M16.1 New launcher icon (no food)

- Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` with this (a 108dp canvas; the glyph sits inside the 66dp safe zone centred at 54,54):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Klean mark: a day ring (open circle) with a check inside and a dot where the ring ends. No food imagery. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- ring: 300° arc, radius 22, centred (54,54), opening at the top right -->
    <path android:strokeColor="#4C3F8B" android:strokeWidth="6" android:strokeLineCap="round"
        android:fillColor="#00000000"
        android:pathData="M 65,34.95 A 22,22 0 1,0 74.05,45" />
    <!-- end dot -->
    <path android:fillColor="#4C3F8B" android:pathData="M 79,40 m -4,0 a 4,4 0 1,0 8,0 a 4,4 0 1,0 -8,0" />
    <!-- check -->
    <path android:strokeColor="#4C3F8B" android:strokeWidth="6" android:strokeLineCap="round"
        android:strokeLineJoin="round" android:fillColor="#00000000"
        android:pathData="M 44,55 L 51,62 L 65,47" />
</vector>
```

- Keep `ic_launcher_background.xml` (`#E6DEFF`) and the `mipmap-anydpi-v26` adaptive icons. The monochrome layer reuses the foreground.
- Verify:
  ```
  flock … ./gradlew :app:assembleDebug
  flock waydroid.lock bash -c 'adb install -r -d app/build/outputs/apk/debug/app-debug.apk && adb shell input keyevent HOME'
  ~/.hermes/cache/scratch/bin/wshot icon.png
  ```
  Expected: a lavender ring with a check on a pastel tile, and no bowl.
- Commit: `feat(app): holistic launcher icon (day ring with check)`.

### M16.2 QS tile and capture wording

In `core/capture/src/main/res/values/strings.xml`:
- `tile_label` → `Add to Klean`
- the capture sheet placeholder for universal mode (no target module) → `Add anything: a task, a time, a meal…`. Find its key with `grep -n placeholder core/capture/src/main/res/values/strings.xml`.

Module-targeted sheets (diet widget, diet screen) keep "What did you eat?". Update any tests that assert the old strings (`grep -rn "Log food" --include=*.kt .`).

Commit: `feat(capture): holistic tile and capture wording`.

### M16.3 Holistic status line contract (test first)

Add to `core/ui/src/main/kotlin/app/klean/core/ui/module/Dashboard.kt`:

```kotlin
/** One short fragment of the persistent "Today" status line, e.g. "2 tasks" or "Dentist 14:00". */
data class StatusFragment(val key: String, val text: String, val priority: Int)

/** A module's contribution to the persistent status notification. Bind with @IntoSet. */
interface StatusLineProvider {
    val moduleId: ModuleId
    fun fragments(): Flow<List<StatusFragment>>
}
```

In `DashboardContributions`:
- add `Set<@JvmSuppressWildcards StatusLineProvider>` to the constructor;
- add `@Multibinds abstract fun statusLine(): Set<StatusLineProvider>`;
- add this function:

```kotlin
/** Gate-filtered fragments, highest priority first, at most [max]. */
fun statusLine(max: Int = 3): Flow<List<StatusFragment>> = gate.disabledIds.flatMapLatest { off ->
    val on = statusProviders.filter { it.moduleId !in off }
    if (on.isEmpty()) flowOf(emptyList())
    else combine(on.map { it.fragments() }) { l -> l.flatMap { it }.sortedByDescending { it.priority }.take(max) }
}
```

Test in `DashboardContributionsTest`:
- three providers with priorities 1, 5 and 3 → `["b", "c", "a"]` order, capped by `max`;
- a disabled module's fragments are absent.

Update the existing call sites with `emptySet()` (`ShellViewModel` default, tests).

Commit: `feat(ui): status line contract for the holistic notification`.

### M16.4 `KleanStatusNotifier` replaces the diet-only status notification

**Move, don't fork.** Create `core/capture/src/main/kotlin/app/klean/core/capture/status/KleanStatusNotifier.kt`, built from `feature/diet/src/main/kotlin/app/klean/feature/diet/notification/DietStatusNotifier.kt`. Read that file and `DietStatusActions.kt`, `DietReplyReceiver.kt` and `StatusRepostReceiver.kt` first.

- **Same notification id** (1001) and channel (`diet_status_v2` → rename to `status_v1`: add it to `Channels.kt` and put `diet_status_v2` in `retired`), same ongoing, silent and public-version behaviour.
- **Title:** `Today`. **Text:** `statusLine().first().joinToString(" · ") { it.text }`, or `Nothing planned. Add anything here.` when empty.
- **Public (lock-screen) version:** `Today · N items`, where N is the fragment count. No content.
- **One action,** `Add something`, with a `RemoteInput`. The reply goes to a new `StatusReplyReceiver` (in `:core:capture`). It calls `UniversalCaptureService.submit(CaptureInput(text, CaptureSource.NOTIFICATION_REPLY, now))`, then re-posts the notification with a confirmation: subText `Saved to <module label>`, kept until the next refresh.
  - This keeps the no-UI, under-5-second save path.
  - Diet stays the fallback, so "shawarma and fries" still becomes a diet entry.
- **Content intent:** opens Today (`klean://today`).
- **Refresh:** on any `DietChangeListener` event plus a new core hook `StatusRefresh` that each module calls after writes. Start a `combine` over `statusLine()` in the app scope and repost when it changes; debounce 500 ms.

Diet becomes one provider. In `feature/diet/.../notification/`:
- delete `DietStatusNotifier`'s posting code;
- keep a `DietStatusLine : StatusLineProvider` (fragment `"N food entries"` priority 1; `"last: shawarma 13:15"` only when there is no other module);
- keep `DietReplyReceiver` only for **check-in** prompt replies (the source `PROMPT_REPLY` path). Its status-reply path moves to `StatusReplyReceiver`.

Tests:
- Move `DietReplyReceiverTest`'s status case to `StatusReplyReceiverTest` (Robolectric):
  - a reply saves through a fake capture service with source `NOTIFICATION_REPLY`;
  - no activity is started (`shadowOf(app).nextStartedActivity == null`).
- `KleanStatusNotifierTest` (pattern from `DietStatusNotifierTest`):
  - the text joins the fragments;
  - the empty state;
  - the public version has no content.
- Update `app/src/androidTest/.../InlineReplySaveTest.kt` to the new receiver. It must still pass on Waydroid: `connectedDebugAndroidTest`.

On Waydroid: the shade shows "Today · N food entries". Reply "dentist friday 9:30" with the calendar module present after M19, or "pay rent" after the Tasks merge. Before those merge, "toast" → diet entry and "Saved to Diet".

Commit: `feat(core): holistic Today status notification with Add something reply`.

### M16.5 Today card order, reorder and hide (test first)

- `core/ui/.../KleanModule.kt`: replace `enum class DashboardSlot` with
  ```kotlin
  enum class DashboardSlot { APPROVALS, TASKS, AGENDA, BIRTHDAYS, SLEEP, WORKOUT, DIET }
  ```
  Update the diet module's slot (`DIET`). Update the P0 skeletons, if they reference `EVENTS`/`REMINDERS`, to `AGENDA`.
- Device-local layout in `DevicePrefs`:
  - `cardOrder: Flow<List<String>>` (module ids; empty = default);
  - `hiddenCards: Flow<Set<String>>`;
  - their setters.
- Pure function in `core/ui/.../module/CardLayout.kt`:

```kotlin
/** Default slot order, then the user's saved order for the ids it mentions; hidden ids removed. */
fun <T> arrangeCards(cards: List<T>, id: (T) -> String, slot: (T) -> Int, saved: List<String>, hidden: Set<String>): List<T> {
    val visible = cards.filter { id(it) !in hidden }.sortedBy(slot)
    if (saved.isEmpty()) return visible
    val rank = saved.withIndex().associate { (i, v) -> v to i }
    return visible.sortedWith(compareBy({ rank[id(it)] ?: (saved.size + slot(it)) }, slot))
}
```

- `CardLayoutTest` cases:
  - default order follows the slots;
  - a saved order wins;
  - an unknown new module lands after the saved ones in slot order;
  - hidden cards are removed.
- `ModuleRegistry.dashboardCards` keeps the slot order. `TodayScreen` applies `arrangeCards` with the prefs (via `ShellViewModel` → a `DevicePrefs` flow param with an empty default for tests).
- UI: a Today overflow menu, "Arrange Today", opens `ArrangeTodayScreen` (`app/src/main/kotlin/app/klean/today/ArrangeTodayScreen.kt`). It shows a list of cards with up/down arrow buttons (48 dp, labelled "Move <name> up"), a visibility switch, and "Done". Use arrow buttons, not drag, for accessibility and simplicity.
- Strings: `today_arrange` = "Arrange Today", `today_arrange_up` = "Move %1$s up", `today_arrange_down` = "Move %1$s down", `today_arrange_show` = "Show %1$s".
- Tests: a ViewModel test for move up/down/hide persisting. Add to `ShellSmokeTest`: hide Diet → the Diet card is absent while the Diet nav item stays.

Commit: `feat(today): approvals-first card order; arrange and hide cards once`.

### M16.6 Navigation order and "New in Klean"

- Set `navOrder` per H5. Diet is 60.
- Replace P0.4's "seed new modules off" in `ModuleRegistry.seed()` with seeding **on**, recording them in `core.modules.known`, plus a DevicePrefs flag `pendingNewModules: Set<String>` for existing installs only (detected when `known` was non-empty before seeding).
- `NewModulesSheet` (`app/src/main/kotlin/app/klean/nav/NewModulesSheet.kt`): a modal bottom sheet shown once on Today when `pendingNewModules` is non-empty.
  - Title "New in Klean", one switch per new module (on), and "Done", which clears the flag. Switching one off calls `registry.setEnabled(id, false)`.
- Test the pure seeding logic:
  - fresh install → everything enabled, nothing pending;
  - existing install with {diet} known → the new ids are pending and enabled;
  - the second run is a no-op.

Commit: `feat(app): holistic nav order; new modules arrive on with a one-time sheet`.

### M16.7 Holistic Today widget (test the state builder first)

- `app/src/main/kotlin/app/klean/widget/TodayWidget.kt`, a Glance widget registered in the app manifest with `today_widget_info.xml`, label "Klean Today".
- **Content:** a header with the date. Up to 5 "next up" rows from a new contract, `TodayItemsProvider`, in `Dashboard.kt`:
  ```kotlin
  data class TodayItem(val key: String, val timeLabel: String?, val text: String, val sortKey: Long, val route: String /* deep link */)
  interface TodayItemsProvider { val moduleId: ModuleId; fun items(): Flow<List<TodayItem>> }
  ```
  These are gate-filtered and sorted by `sortKey`, plus a capture pill "Add anything" that opens `CaptureActivity` in universal mode.
- **Compact size:** pill plus the count.
- Diet contributes no rows (diet has its own widget). Tasks, Reminders, Calendar, Birthdays, Workout and Sleep contribute in their streams.
- **Updates:** `TodayWidgetRefresher` is triggered by the same `StatusRefresh` hook.
- Test: `TodayWidgetStateTest` covers sorting, cap 5, empty state "Nothing coming up", and disabled modules being absent.
- Waydroid: pin it through the wizard's widget page, which must now offer both widgets; extend the onboarding widget step to list every module `widgetReceiver` plus the Today widget first. Take a screenshot.

Commit: `feat(app): holistic Today widget`.

### M16.8 Onboarding re-centre

The wizard order:
1. modules (all on)
2. sleep schedule (the target bed/wake that drives quiet hours; renamed from "sleep window")
3. main phone ("Is this your main phone? Alarms ring only on your main phone.")
4. permissions: notifications, exact alarms, full-screen alarms, battery, contacts, calendar, microphone. Health Connect is added in M22.
5. widget
6. contacts import (VCF or device accounts; added by M19)
7. AI
8. Google (M21)
9. backend pairing (M25)
10. Hermes (M23)
11. watch (M27)
12. diet preferences, **last**

Steps owned by later milestones render only once their module or hook is present. Use the existing `KleanModule.onboardingTitleRes` / `OnboardingContent` hooks, plus a new `@IntoSet OnboardingStepProvider { val order: Int; val titleRes: Int; @Composable fun Content() }` for non-module steps.

- Move the diet onboarding page to order 900.
- Test: `OnboardingFlowTest` walks the new order; diet comes last.

Commit: `feat(onboarding): holistic step order; diet preferences last`.

### M16.9 Gate and device check, then tag `m16`

- Gate.
- `connectedDebugAndroidTest` on Waydroid.
- Waydroid walkthrough: icon, tile label, status line, Today order, "Arrange Today", "New in Klean" (simulate with `pm clear` after seeding `known={diet}` using a debug broadcast), Today widget.
- Update `TASKS.md` with a new `## M16 — Holistic re-centre` section.
- Push, wait for CI to pass, then `git tag m16 && git push origin m16`.

---

## M17 — Foundation 2: remaining entities, devices, secrets (one agent, on `main`)

Room `AutoMigration(2 → 3)`, adding only tables and columns. Write a `MigrationV3Test` that starts from the v2 fixture: export a v2 DB from a test into `core/data/src/test/resources/backup-v2.sqlite` once, using a small test helper.

| Entity / change | Columns (plus `id`, `SyncColumns`, `Index("change_seq")`) |
|---|---|
| `task` + columns | `parent_id` (subtasks, indexed), `status` (`OPEN`/`DONE`/`SOMEDAY`), `archived_at`, `source` (`KLEAN`/`GOOGLE`), `remote_id`, `remote_list_id`, `remote_etag` |
| `task_list` + columns | `source`, `remote_id`, `selected` (Google list shown) |
| `delegation` | `task_id`, `mode` (`PARTIAL`/`INTERACTIVE`/`AGENTIC`), `agent` (`AI:<provider>`/`HERMES`), `status` (`QUEUED`/`RUNNING`/`NEEDS_INPUT`/`NEEDS_APPROVAL`/`DONE`/`FAILED`), `remote_ref`, `result` (text), `auto_complete` (bool) |
| `delegation_message` | `delegation_id`, `role` (`AGENT`/`USER`), `text`, `at` |
| `approval_request` | `delegation_id`, `action` (e.g. `send_email`), `draft` (text), `recipients` (JSON array), `editable` (JSON), `decision` (`PENDING`/`APPROVED`/`EDITED`/`REJECTED`), `decided_at` |
| `contact` + columns | `name_json` (vCard N), `addresses` (JSON), `org`, `photo_blob`, `anniversary` (`--MM-DD` or a date), `custom` (JSON), `source_account` (`KLEAN`/`<account type:name>`), `remote_uid` |
| `event` + columns | `calendar_id`, `uid` (iCal), `rrule`, `exdates` (JSON), `alarm` (bool), `remote_etag` |
| `calendar_source` | `kind` (`KLEAN`/`DEVICE`/`GOOGLE`/`DAV`), `name`, `color`, `remote_id`, `read_only` (bool), `selected` (bool) |
| `exercise_definition` | `name`, `kind`, `default_reps`, `default_duration_s` |
| `workout_set` + columns | `exercise_id` |
| `workout_session` + columns | `route_blob`, `distance_m`, `hc_record_id` |
| `sleep_schedule` | `bed_minutes`, `wake_minutes`, `days` (bitmask), `reminder_minutes_before` (default 30) |
| `sleep_session` + columns | `hc_record_id` |
| `device` | `name`, `platform` (`ANDROID`/`WEAR`/`WEB`), `is_main_phone`, `last_seen_at` |
| `sync_cursor` (not synced) | `id` (`"server"`), `cursor`, `last_push_seq` |

Also:
- **`SecretStore` keys** (exist from M10): add `google.refresh`, `backend.token` and `hermes.token`. They're excluded from backups unless opted in, which is already enforced by `SecretBackup`.
- **`DevicePrefs.isMainPhone`** (device-local, default true), plus `device` row upsert at startup.
- **P0's `ReminderRepository`** gains `snooze(id, until)`, `markDone(id)` and `dismiss(id)`. Remember: dismiss ≠ done.

Commit each piece separately: the entity plus its DAO plus its test. Then gate, tag `m17` and push.

---

## M18–M20 — Android modules (Lane A: 4 parallel worktrees)

Use `.hermes/plans/2026-11-09_170500-parallel-life-modules.md` §Phase 1 (common checklist + S1–S6) **with these amendments:**

- **Streams merged by H5:**
  - **S1 + S2 → "Tasks & reminders"** (worktree `../klean-tasks`, branch `m18-tasks`). One nav item, tabs Today / Upcoming / Lists / Reminders / Done. Gates: `TASKS` and `REMINDERS`.
  - **S3 Calendar** (`../klean-calendar`, `m19-calendar`).
  - **S4 Contacts & birthdays** (`../klean-contacts`, `m19-contacts`).
  - **S5 + S6 → Workout and Sleep** (`../klean-body`, `m20-body`). Two modules, one worktree.
- **Every stream also implements** `StatusLineProvider`, `TodayItemsProvider` and calls `StatusRefresh` after writes (from M16).
- **Tasks additions from the brief:**
  - subtasks (`parent_id`);
  - views Today (due today + the review), Upcoming, per-list, Completed (collapsed, auto-archived after 30 days by a daily worker — no manual cleanup);
  - "Still want these?" actions: keep for today, pick a date, move to someday (`status=SOMEDAY`), done, delete;
  - recurrence includes "every N days/weeks" and a custom RRULE text field;
  - an in-app mic on the Tasks screen (reuse `rememberVoiceInput` from `:core:capture`).
- **Reminders additions:**
  - non-alarm reminders follow **format rotation** (reuse diet's `FormatRotator` from `:shared`, with a `reminder_prompt` log in `prompt_log` using `trigger=REMINDER`) and **respect quiet hours**;
  - alarms are exempt and ring only when `isMainPhone`;
  - alarm actions: snooze 10 min, 1 h, tomorrow, and custom (a time picker);
  - ring through DND with the user's permission (`ACCESS_NOTIFICATION_POLICY`, alarm channel `setBypassDnd(true)`).
- **Calendar additions:**
  - views: day, 3-day, week, month, agenda;
  - shows Klean events, birthdays, task due dates and reminders, plus device calendars read-only;
  - ICS import/export: a codec in `shared/src/commonMain/kotlin/app/klean/shared/ical/`, VEVENT with RRULE/EXDATE/VALARM, round-trip tests;
  - `calendar_source` rows for each device calendar, with a selection toggle.
- **Contacts additions:**
  - vCard 4 fields per M17;
  - VCF 3 and 4, single and bulk (codec in `shared/src/commonMain/kotlin/app/klean/shared/vcard/`);
  - **DAVx5 awareness**: detect accounts of type `bitfire.at.davdroid` via `AccountManager.getAccountsByType`. Read and edit contacts in any DAVx5 account through the provider. If one syncs the user's own Klean backend (its URL matches the paired backend, after M25), use it as the provider representation and stop mirroring under the Klean account.
  - **One-time duplicate merge offer**: same normalised name plus a shared phone or email across accounts gives one card, "These look like the same people", with merge per pair.
  - **Birthdays**: a yearly event on the Klean calendar (no-year and Feb 29 handled); reminders on the day and 3 days before by default, configurable per contact or off. **No AI** anywhere.
- **Workout additions:**
  - `exercise_definition` (auto-created from typed names; no manual curation);
  - freeform and quick start;
  - per-exercise history (a neutral list over time);
  - gentle suggestions follow format rotation and quiet hours.
- **Sleep additions:**
  - `sleep_schedule` drives quiet hours. Replace `QuietHoursStore`'s source with the schedule, keep the existing quiet-hours UI as an editor of the schedule, and migrate the existing synced key into the first schedule row once.
  - the bedtime reminder defaults to 30 min before, once, with no escalation;
  - taps from the dashboard, the notification and the Today widget.

**Order:** start all four worktrees as soon as `m17` is tagged. Merge each into `main` when it reports, running the gate after each, and tag `m18` (Tasks & reminders), `m19` (Calendar + Contacts) and `m20` (Workout & Sleep).

---

## M21 — Google Tasks overlay + Assistant (one agent, after `m18`)

- **Module `:core:google`:**
  - **Sign-in:** Credential Manager + Google Identity `AuthorizationClient` with incremental scopes (`https://www.googleapis.com/auth/tasks` first; calendar.readonly only if enabled).
  - **Tokens:** stored in `SecretStore` (`google.refresh` for server-side, access tokens in memory).
  - **No Play services on Waydroid,** so put this behind `GoogleAuth` with a `FakeGoogleAuth` for tests. Real sign-in is verified only in the Pixel session.
- **`GoogleTasksClient`** (Ktor + kotlinx.serialization; endpoints `tasklists`, `lists/{id}/tasks`):
  - read all lists (default all selected; deselect in settings);
  - complete, uncomplete and patch title, notes and due **date** only;
  - **never** a POST that creates a task. A unit test asserts that no `POST …/tasks` request is ever built (inspect MockEngine history across the whole suite's client usage).
- **Cache:** rows in `task` with `source=GOOGLE`, `remote_id` and etag.
- **Offline edits:** queued in a `google_outbox` (device-local DataStore JSON list) and replayed by WorkManager; conflicts are last-write-wins.
- **Time reminders on a Google task** stay local (a `reminder` row linked by `task_id`).
- **UI:** a small "G" source chip on Google tasks; Google lists appear under Lists, after Klean lists.
- **Self-hosted OAuth doc:** `docs/google-oauth.md` explains how the user creates their own OAuth client (Android + Web types), and why testing-mode refresh tokens expire after 7 days.
- **Assistant integration:**
  - **Research task first (timebox 30 min):** check Google's current support for third-party actions as of late 2026 (App Actions with built-in intents and `shortcuts.xml` capabilities, Gemini app extensions, and app shortcuts). Write the findings to `docs/assistant.md` with links.
  - **Implementation:** ship `shortcuts.xml` capabilities for `actions.intent.CREATE_REMINDER`, `actions.intent.CREATE_TASK` (or the current equivalents) and a "Log food" shortcut, all mapped to `CaptureActivity` deep links (`klean://capture?text=…&module=…`).
  - Klean never registers as the default assistant.
- Tag `m21`.

## M22 — Health Connect + GPS (one agent, after `m20`)

- **Module `:core:health`:**
  - `androidx.health.connect:connect-client` (pin a stable version);
  - `HealthConnectGateway` interface plus an implementation and a fake;
  - availability check (`SDK_UNAVAILABLE` on Waydroid → calm "Health Connect isn't on this phone" settings row);
  - permission contracts per type: `SleepSessionRecord` read, `ExerciseSessionRecord` read/write, `NutritionRecord` write; only the types in use.
- **Sleep:** read sessions in the last 30 days into `sleep_session` (source `HEALTH_CONNECT`, `hc_record_id`). Merge with manual sessions: an overlap above 50% means prefer Health Connect and tombstone the manual one with a note.
- **Workout:**
  - write finished sessions;
  - read other apps' sessions;
  - **duplicate detection:** a Klean GPS run and a watch run overlapping more than 70% in time → keep one (prefer the one with a route) and link the other by `hc_record_id`.
- **Diet (H2 parity):** optional write of `NutritionRecord` (calories only when estimated), plus reading meal-related data where available to sharpen `MealTimeLearner`. Off by default.
- **GPS jog:**
  - `JogTrackingService` is a foreground service of type `location`, with `ACCESS_FINE_LOCATION` and an `ACCESS_BACKGROUND_LOCATION`-free design (foreground only);
  - `LocationManager` with a fused fallback (no Play services);
  - the route is stored as a polyline blob;
  - distance and pace are computed in `:shared` (`RouteMath`, unit-tested with fixed coordinate tracks).
- **Waydroid checks:**
  - the HC-unavailable path;
  - the GPS jog with a mock provider (`cmd location providers add-test-provider gps` and set-test-location every second from a script);
  - distance within 5% of the expected value.
- Tag `m22`.

## M23 — AI delegation + Hermes (one agent, after `m18`; can run in parallel with M21/M22)

- **Module `:feature:delegation`:**
  - "Delegate" on the task detail screen, plus capture (`delegate` action: "ask Hermes to…").
  - **Partial mode:** `AiGateway` with a schema `{draft, subtasks[], notes}` attaches the result to the task (delegation `result`; subtasks created on accept).
- **`DelegationAgent` interface** (`feature/delegation/.../agent/DelegationAgent.kt`):
  ```kotlin
  interface DelegationAgent {
      suspend fun start(task: DelegationPayload): String            // remote ref
      fun events(remoteRef: String): Flow<AgentEvent>               // Question, Draft, ApprovalNeeded, Result, Failed
      suspend fun answer(remoteRef: String, text: String)
      suspend fun decide(remoteRef: String, approval: ApprovalDecision)
  }
  ```
- **Research task (timebox 45 min), using the local Hermes install and docs.** Confirm:
  - the API Server adapter's endpoint, auth token config and streaming (SSE);
  - whether tool calls / approvals can be surfaced to a client;
  - the Webhooks and ntfy options.

  Design: Klean opens a session through the API Server, with a system prompt instructing Hermes to call Klean-defined interaction conventions. Questions and approvals are encoded as structured JSON blocks in the assistant stream (`{"klean":"question",…}` / `{"klean":"approval",…}`), parsed by `HermesAgent`. Document the protocol in `docs/hermes.md` and keep it behind `DelegationAgent`. Config: URL + token (`SecretStore hermes.token`).
- **Approvals:**
  - an `approval_request` row → a high-priority notification (channel `approvals`) and the **APPROVALS** Today card (first, per H4);
  - `ApprovalScreen` shows the **full draft and full recipient list, both editable**, with Approve / Edit / Reject;
  - **more than 5 recipients always needs approval**, enforced in `ApprovalPolicy` (`:shared`, unit-tested), regardless of mode;
  - credentials never pass through Klean (assert by code review plus a test that `DelegationPayload` has no credential fields).
- **Questions:** a notification with inline reply → `answer()`, plus a thread on the task (`delegation_message`).
- **Completion:** the task is marked done only on user confirmation, or automatically when `auto_complete` is on.
- **Waydroid:** run a local Hermes API server on the host (`hermes gateway` with the API Server adapter, or a stub server that replays a scripted conversation if no real Hermes is available). Flow: delegate an interactive email task → a question notification → reply → approval with 6 recipients → edit → approve → result attached.
- Tag `m23`.

## M24 — Sync backend (Lane B: start right after `m17`, in parallel with M18–M23)

Worktree `../klean-backend`, branch `m24-backend`.
- **Module `backend/`** (`build.gradle.kts` with the `kotlin("jvm")`, `io.ktor.plugin` and Jib plugins), depending on `:shared`. Add `include(":backend")`.
- **Ktor 3.x** (pin the same Ktor version as `:core:ai`). Server engine CIO or Netty. Exposed or SQLDelight for storage: **choose Exposed + JDBC** (SQLite default via `org.xerial:sqlite-jdbc`; Postgres via `org.postgresql:postgresql`, selected by `KLEAN_DB_URL`).
- **Storage tables:**
  - one generic `records(table, id, updated_at, device_id, deleted, json, server_seq)` with a unique key (table, id) and an index on server_seq, so every client entity syncs without per-entity server code;
  - `devices`, `pair_codes`, `blobs(sha256, size, path)`, `push_subscriptions`, `google_tokens`, `settings`.
- **Routes:** the API surface table above. LWW via `lww()` from `:shared`.
- **Auth:** an admin password (env `KLEAN_ADMIN_PASSWORD`, argon2-hashed at first start) for the web's first setup; device bearer tokens (random 32 bytes, stored hashed).
- **DAV:**
  - CardDAV/CalDAV built on the `records` of `contact` / `event` / birthdays: PROPFIND, REPORT (addressbook-query, calendar-query, multiget, sync-collection), GET and PUT;
  - vCard/iCal codecs from `:shared`;
  - ETags = sha of the serialised record;
  - tests driving it with raw XML requests; DAVx5 compatibility verified in the device session.
- **Google:** server OAuth (web client type), Calendar read-only events and Tasks list/patch (still never create).
- **Web push:** VAPID keys are generated on first start into the data volume. Library: `nl.martijndwars:web-push`, or implement RFC 8291 with BouncyCastle if that library is unmaintained; check its status.
- **Hermes relay:** proxy `/hermes/*` to the configured Hermes URL with the server-held token.
- **AI proxy:** forward `/ai/v1/chat/completions` to `KLEAN_AI_UPSTREAM` (e.g. Ollama).
- **Ops:** `/health`, structured JSON logs (logback + logstash encoder), and `POST /admin/backup` (a SQLite `VACUUM INTO` / `pg_dump` hint).
- **Packaging:**
  - `./gradlew :backend:jibDockerBuild` → image `klean-backend:dev`;
  - `deploy/docker-compose.yml` (one service, one volume `klean-data`, port 8080);
  - `deploy/caddy/Caddyfile`, `deploy/traefik/` examples;
  - `docs/deploy.md` covering the three access options: Tailscale (default), LAN, reverse proxy with TLS.
- **Tests:** Ktor `testApplication` integration tests for:
  - pair start/claim/revoke;
  - push → pull round trip;
  - LWW conflict (older update rejected);
  - tombstones;
  - blob upload/download with a hash mismatch rejected;
  - CardDAV PROPFIND/PUT/GET;
  - CalDAV REPORT;
  - push subscribe;
  - the AI proxy (a fake upstream).
- **Verification:**
  ```
  flock gradle.lock nix develop -c ./gradlew :backend:test :backend:jibDockerBuild
  docker compose -f deploy/docker-compose.yml up -d && curl -s localhost:8080/api/v1/health
  ```
  Expected: `{"status":"ok"}`.
- Tag `m24`.

## M25 — Android sync, pairing, multi-device (one agent, after `m24` and `m17`)

- **`:core:sync`:**
  - `SyncEngine` collects rows with `change_seq > last_push_seq` from every table through a registry of `SyncTable` adapters (one per entity; generic over Room DAOs), pushes, then pulls and applies with `lww`;
  - blobs are uploaded before records that reference them;
  - WorkManager: periodic every 15 min plus expedited on change (debounced 10 s) when paired;
  - backoff; never blocks local writes;
  - the phone's own unsynced edits win until pushed.
- **Pairing:** a QR scanner (CameraX + ML Kit is not allowed without Play services, so use `zxing-android-embedded` or a CameraX + zxing-core analyzer), with a paste-URL fallback for Waydroid. The token goes in `SecretStore`.
- **Multi-device:**
  - `device` rows sync;
  - alarms ring only on `is_main_phone`;
  - prompts are answered anywhere: when a prompt's `prompt_log` outcome syncs as LOGGED or DISMISSED, the local notification is cancelled;
  - prompts go to the most recently used device (`device.last_seen_at`), with a silent copy elsewhere.
- **DAVx5 switch:** once paired, if a DAVx5 account's server URL equals the backend's DAV URL, set Contacts/Calendar to use that account as the representation (from M19's hook).
- **Tests:**
  - `SyncEngineTest` against an in-process Ktor `testApplication` backend (dependency `testImplementation(project(":backend"))`): two simulated devices converge; tombstones propagate; LWW;
  - stopping the server never breaks local use (fault injection).
- **Waydroid:** run the backend in Docker on the host and pair with the paste-URL fallback. Log on Waydroid → the record appears in `GET /sync/pull`, and in the web after M26.
- Tag `m25`.

## M26 — Web client (Lane C: start when M24's API is frozen, i.e. after its route tests merge; in parallel with M25)

Worktree `../klean-web`, branch `m26-web`, directory `web/`.
- **Stack:** Vite + TypeScript + `@m3e/web` (check the current package name and version on npm). Lit-style components or plain TS with web components; no React needed.
- **Theme:** design tokens from `core/design/tokens/klean-color-tokens.json` (the source of truth) are generated into CSS variables by `web/scripts/tokens.mjs`. Cantarell is bundled (copy the font files from `core/design/src/main/res/font/`).
- **Screens (parity):**
  - Today (cards in the same order and arrangement; arrangement is device-local per H4, so the web has its own);
  - each module's screens;
  - universal capture (the rule classifier is ported to TS in `web/src/capture/rules.ts` with the same test table, kept in sync by a shared JSON fixture `shared/src/commonTest/resources/capture-cases.json` read by both Kotlin and TS tests);
  - Settings;
  - Hermes questions and approvals;
  - backup download.
- **Data:** IndexedDB read cache plus a pending-writes queue (the PWA offline mode per the brief). Sync through the same `/sync` API with the web's own device token.
- **Notifications:** web push (service worker) for prompts, reminders, birthdays, bedtime and approvals, following quiet hours and rotation (server-side schedule mirrors; alarms show as persistent notifications).
- **Login:** admin password on first setup, which creates the web device token. Then the pairing QR for phones.
- **Responsive:** laptop-first layout with a navigation rail plus a two-pane Today; phone width uses a bottom bar.
- **Build and serve:** `pnpm build` → `web/dist`, copied into the backend jar resources by the Gradle task `:backend:processResources` (dependsOn a `pnpmBuild` Exec task), served at `/`.
- **Tests:**
  - Vitest for logic (capture rules, LWW, token storage);
  - Playwright (nix: `playwright-test` + chromium) e2e against `docker compose up`: login, pair, create a task, see it after a phone-simulated push, approve an approval;
  - `web/scripts/check-copy.mjs` greps the built strings for forbidden words and `!`.
- Tag `m26`.

## M27 — Wear OS app (Lane D: after `m25`; can start UI earlier, after `m18`)

- **Module `wear/`:** `com.android.application`, min SDK 30, applicationId `app.klean` (same as the phone, as Wear requires for Data Layer pairing; debug `app.klean.debug`).
- **Tiles** (`androidx.wear.tiles` + protolayout):
  - quick food log (recent-item chips + voice);
  - today's food list;
  - next reminder or task;
  - start a saved workout.
- **Complication:** one-tap food log, showing today's entry count.
- **Notifications:** phone notifications bridge by default. Add actions (voice reply, chips) through `WearableExtender`. When an alarm rings on the main phone, the watch vibrates via a Data Layer message.
- **Workout:** log sets during a phone session (Data Layer sync of the active session); optional on-watch timed/cardio session (foreground service type `health`).
- **Data Layer:** `WearSyncService` on the phone (`WearableListenerService`) and on the watch. The watch never talks to the backend.
- **Play services:** the Data Layer requires Google Play services on both devices. That's acceptable for the watch path; the phone stays fully functional without it.
- **Tests:**
  - Robolectric tile renderer tests (`androidx.wear.tiles:tiles-testing`);
  - complication data source tests;
  - Data Layer message codec unit tests.
- Real-watch checks are listed for M29.
- Tag `m27`.

## M28 — Universal capture everywhere + multi-device polish (one agent)

- **Handlers present for every action:** diet, task (list, due, priority), reminder (time, alarm), event, contact/birthday, workout, sleep, delegation.
- **No-AI rules:** times, dates, "remind me", "ate"; otherwise diet with a "Move to…" chip (H7).
- **Correction chip:** moving the record re-executes it with the chosen handler and undoes the original. Test this per pair.
- **Capture reachable from:** the dashboard, the Today widget, the diet widget, the tile, the status notification, the watch, the web, and the Assistant (M21).
- **Disabling a module** removes it from routing. Test it.
- Tag `m28`.

## M29 — Acceptance (user session)

- Fill `docs/acceptance-p1.md` with every criterion from the brief's "Acceptance criteria" section, grouped as in the brief, with columns Waydroid / Pixel / Watch / Backend+Browser.
- The agent pre-fills Waydroid, backend and browser rows (Docker plus Playwright evidence).
- Pixel and watch rows are completed in the user's session.
- Tag `p1` only after every row passes.

---

## Parallelism plan (what runs at once)

| Time | Lanes running |
|---|---|
| Now | Part F (main); P0 foundation; P0 parsers |
| After P0 merges | **M16** (main) ‖ **M24 backend** (worktree, only needs `:shared`) |
| After M16 + M17 | **M18 Tasks** ‖ **M19 Calendar** ‖ **M19 Contacts** ‖ **M20 Workout & Sleep** ‖ M24 continues |
| After M18 | **M21 Google** ‖ **M23 Delegation** ‖ (M20 done) **M22 Health** |
| After M24 API frozen | **M26 Web** ‖ **M25 Android sync** |
| After M25 | **M27 Wear** ‖ **M28 Capture everywhere** |

Peak: 6 agents at once. Gradle builds serialise on `gradle.lock`, so with Gradle-heavy lanes contending, expect ~50% build waiting. Web builds (pnpm) don't take the lock. If the gate OOMs, cap Gradle-heavy lanes at 4.

**Merge protocol (parent):**
1. Merge one branch at a time.
2. Run the gate after each merge.
3. Push and wait for CI to pass.
4. Tag.
5. Remove the worktree.

**Expected conflict files:**
- `settings.gradle.kts`, `app/build.gradle.kts`: union of includes and deps.
- `TASKS.md`: keep both sides.
- `Channels.kt`: union.
- Debug manifests: union.
- `Dashboard.kt`: these contracts land in M16/M17 before the lanes start, so streams shouldn't edit it.

## Tests and validation (every milestone)

- **Gate** (Android):
  ```
  flock gradle.lock nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest --console=plain
  ```
  Expected: `BUILD SUCCESSFUL`. Add `:backend:test` once it exists, and `:wear:testDebugUnitTest` once that exists.
- **Web:** `cd web && pnpm test && pnpm build && pnpm e2e`. Expected: all pass.
- **`connectedDebugAndroidTest` on Waydroid:** expected pass.
- **CI:** extend `.github/workflows/build.yml` with a `backend` job (`:backend:test`, image build), a `web` job (pnpm test and build), and the backend image published to GHCR on tags (`ghcr.io/kquote03/klean-backend`). The nightly also attaches the Wear APK once it exists.
- **Copy guard:** `ForbiddenCopyTest` (Android) and `web/scripts/check-copy.mjs` (web).

## Risks, tradeoffs, open questions

1. **Persistent notification becomes holistic (H3).** This deviates from the brief's diet-specific description, which isn't marked **Decided**. The food log stays under 5 s through the diet fallback. **Confirm, or keep a diet-only notification alongside.**
2. **The Google Assistant route changes often.** M21 starts with a research timebox and keeps it behind an interface. If no supported route exists for third-party create-task in late 2026, ship app shortcuts plus the capture deep link and report it.
3. **Hermes API shape.** The design uses the API Server adapter plus a structured-message convention. If the user's Hermes version has a better delegation surface (tasks/approvals API), the M23 research step adapts it behind `DelegationAgent`. **Which Hermes URL/token should be used for testing?**
4. **Google OAuth needs the user's own OAuth client IDs** (Android + Web). Until then, Google features are built and tested with fakes and MockEngine. **Please create them when convenient; `docs/google-oauth.md` will explain how.**
5. **No Play services, Health Connect, camera, GPS or Wear on Waydroid.** Those paths use fakes, mock location and the HC-unavailable branch. Real verification is in the device session (Pixel + watch).
6. **Wear Data Layer requires Play services.** Acceptable for the watch only; the phone stays Google-free.
7. **Schema churn.** Parallel lanes don't change the schema; M17 front-loads every entity the brief names. Any lane need becomes a parent-made AutoMigration after merge.
8. **Scale.** This is many agent-hours. Every milestone leaves `main` shippable, and nightly APKs track progress. If a feature threatens a milestone (e.g. full DAV REPORT coverage), ship the simpler version (sync-collection + multiget only) and list the gap in TASKS.md rather than silently cutting it.
9. **Existing users' data** (the user's Pixel): migrations are additive only (`AutoMigration`), and backups restore across versions. Covered by `MigrationV2Test` and `MigrationV3Test`.
