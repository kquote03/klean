# AGENTS.md — Klean

The handover guide for anyone, human or agent, picking up this repo. Read it in full before touching code.

## Handover documents

| File | What it is | When to update it |
|---|---|---|
| `AGENTS.md` (this file) | How to work here: toolchain, conventions, gotchas, current state, deviations, what to do next | Every milestone merge, and whenever you deviate from the plan |
| `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` | **Governing plan**: the holistic re-centre (decisions H1–H9), repository layout, sync protocol and API, milestones M16–M29 | Only when scope or a decision changes |
| `.hermes/plans/2026-11-09_170500-parallel-life-modules.md` | Per-module "Common checklist" and module specs S1–S6, used by M18–M20 with the governing plan's amendments | — |
| `PLAN.md` | The original plan, now the record of the foundation and diet build (M0–M15). Canonical copy of `.hermes/plans/2026-11-08_153740-klean-prototype-1.md` | Historical |
| `TASKS.md` | One checkbox per task, device-check records, and the remaining hands-on checks | In the same commit that finishes a task |
| `docs/handover-prompt.md` | A prompt to paste into a fresh agent session to resume the work | When the next steps change |
| `docs/handover-YYYY-MM-DD.md` | Dated snapshots of state and lane reports (latest: `docs/handover-2026-11-10.md`) | At the end of each working session |
| `docs/backend-api.md` | The backend's real API reference | With backend changes |
| `~/.hermes/cache/scratch/klean-subagent-brief.md` | The rules handed to every subagent; its "State update" sections are the latest contracts | Before dispatching a new wave |

Precedence when they disagree: user instructions > AGENTS.md "Deviations" > governing plan > PLAN.md.
Decisions marked **Decided** in the brief or a plan need the user's approval before you change them.
Source brief: `~/Downloads/Klean — Prototype 1 Build Prompt (1).md`. In this revised brief, Prototype 1 is the whole product.

## What this is

Klean is a local-first **holistic life-management** system for one person.
- **Modules:** diet, tasks and reminders, calendar, contacts and birthdays, workout, sleep, and AI delegation (Hermes).
- **Parts:** the Android app (the source of truth; works fully offline with no account), a Wear OS app, an optional self-hosted Ktor sync backend (Docker), and a web client served by that backend.

**User direction (2026-11-09):** diet is the feature they need most, but it is **not** the app's focus.
- Prioritise the other modules.
- Keep the app's identity holistic: no food icon, no diet-first copy on shared surfaces.
- Diet is finished and feature-frozen. Its remaining brief items (watch tile, Health Connect, web parity) arrive with the platform milestones.

Design context:
- **Internal only:** the user has AuDHD-like traits. This may appear in code comments and internal docs. It must **never** appear in user-facing strings. `ForbiddenCopyTest` (`app/src/test`) enforces this, together with `scripts/check-copy.sh`.
- **Product principles every feature follows:**
  - never rely on notifications alone; there is always a passive surface too (widget, status line, Today)
  - logging is a single action and is never blocked by AI or the network
  - zero guilt: no streaks, scores or red badges; past-due tasks are "Still want these?", never "overdue"
  - zero upkeep for the user
  - when a prompt is ignored, change its format, not its frequency
  - works offline with no account; cloud features are opt-in
  - every module can be switched off and disappears completely

## Toolchain (NixOS — everything comes from the flake)

- Never install tools globally. Run everything as `nix develop -c <cmd>`. One-off tools: `nix develop -c nix shell nixpkgs#<pkg> -c <cmd>`.
- Flakes only see git-tracked files, so `git add` new files before `nix develop`. This applies in worktrees too.
- **Versions** (all pinned in `gradle/libs.versions.toml`): Gradle 9.9.0 (wrapper), AGP 9.5.1 (built-in Kotlin, so don't apply `kotlin-android`), Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Room 2.8.5 + `sqlite-bundled` 2.6.2, Compose BOM 2026.09.00, material3 1.5.0-rc01 (Expressive APIs), Glance 1.2.0, WorkManager 2.12.0, kotlinx-datetime 0.8.0 (uses `kotlin.time.Instant/Clock`), Ktor 3.6.1, Exposed 1.x, LiteRT-LM 0.19.1.
- **SDK:** platform `android-37.0` (+36), build-tools 36.0.0. compileSdk/targetSdk 37, minSdk 28 (`build-logic/.../KleanSdk.kt`). The SDK is read-only in the Nix store; add missing components to `flake.nix`.
- aapt2 is overridden through `GRADLE_OPTS` in the flake, because aapt2 from Maven doesn't run on NixOS.
- `scripts/latest-versions.sh` checks Maven versions. Choose stable releases.
- Docker 29 and podman are available on the host without sudo.

**Every Gradle call goes through the shared lock.** The machine has 14 GB of RAM, and several agents may be working in worktrees at once:
```
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew <tasks> --console=plain -Pkotlin.compiler.execution.strategy=in-process
```
The milestone gate must be BUILD SUCCESSFUL before you tag:
```
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test --console=plain -Pkotlin.compiler.execution.strategy=in-process
```
- Count test results with `nix shell nixpkgs#python3 -c python3 ~/.hermes/skills/software-development/android-app-development/scripts/count-test-results.py .` (the host has no `python3`).
- A cold build takes about 5–6 minutes. Run long builds in the background, logging under `~/.hermes/cache/scratch/`.
- Backend image: `./gradlew :backend:jibDockerBuild` → `klean-backend:dev`, then `docker compose -f deploy/docker-compose.yml up -d` and `curl localhost:8080/api/v1/health`.

## CI and releases

- **Repo:** `github.com/kquote03/klean` (public). `gh` is logged in.
- **`.github/workflows/build.yml`:** the Android gate job, plus a separate backend job.
- **Every push to `main`:** builds and refreshes the rolling `nightly` pre-release. The latest APK is always at `releases/download/nightly/klean-debug.apk`.
- **`nightly.yml`:** a dated `nightly-YYYY-MM-DD` release at 00:00 Dubai time when there are new commits.
- **Signing and versioning:** APKs are debug builds signed with the user's debug key (secret `DEBUG_KEYSTORE_B64`, passed via `KLEAN_DEBUG_KEYSTORE`; CI checks the certificate SHA-256 `105be221…`). versionCode is the commit count, so each APK upgrades over the last.
- **Check CI:** `gh run list -R kquote03/klean`.

## Layout

```
shared/              KMP (jvm + android): models, UUIDv7, QuietHours, DayWindows, PromptPlanner, FormatRotator,
                     MealTimeLearner, GapDetector, QuickRepeatRanker, DeliveryMatcher, UnlockSheetPolicy, ModuleId,
                     capture contracts + DefaultCaptureRouter + RuleCaptureClassifier/CompositeCaptureClassifier,
                     time/NaturalTime, sync/ (SyncRecord, Push/Pull DTOs, lww), vcard/, ical/, AI DTOs
core/common          ApplicationScope, Clock, ZoneProvider
core/design          tokens (tokens/klean-color-tokens.json = source of truth for web too), KleanTheme, Cantarell,
                     components (CaptureField, EntryRow, GapMarker, ProviderChip, KleanCard, NeutralEmpty,
                     SummaryTile, WeekOverview, RepeatChips, PhotoThumbnail, SwitchListItem)
core/data            Room KleanDatabase v3 (schemas 1–3.json; fixtures backup-v1/-v2.sqlite), entities + DAOs for every
                     module, SyncStamper, SettingsRepository/SyncedKeys, SettingsModuleGate + module seeding,
                     DevicePrefs, DeviceRepository, PhotoStore, SecretStore, ReminderRepository, PlaceRepository,
                     sleep/SleepScheduleRepository (the quiet-hours source),
                     account/ (Klean Android account, stub authenticator + calendar/contacts sync adapters), backup/
core/ui              KleanModule (+ inNavigation) + ModuleRegistry; Dashboard.kt (summary tiles, day activity, status
                     line, Today items, StatusRefresh); DayItems.kt (DayItemsProvider: dated items on the calendar);
                     CardLayout; OnboardingStepProvider
core/notifications   channels/IDs, QuietHoursService (reads the sleep schedule), QuietHoursMigration, Alarms
                     (Reschedulable, AlarmScheduler), RescheduleReceiver, permission model
core/ai              AiProvider/AiGateway/AiService, Gemini, OpenAI-compatible, ondevice/ (LiteRT-LM), AiCaptureClassifier
core/capture         UniversalCaptureService, CaptureActivity/Sheet, QS tile ("Add to Klean"), voice/, photo/,
                     status/ (KleanStatusNotifier "Today" line, StatusReplyReceiver)
feature/diet         the complete diet module (frozen)
feature/tasks        Today/Upcoming/Lists/Reminders/Done tabs, subtasks, recurrence, "Still want these?" review,
                     30-day archive worker (depends on :feature:reminders)
feature/reminders    scheduler, pings (quiet hours + format rotation), alarms (mediaPlayback FGS + AlarmActivity,
                     main phone only), Reminders tab UI, repeat picker (inNavigation = false)
feature/calendar     day/3-day/week/month/agenda views, editor, ICS, read-only "Klean" calendar mirror, Agenda card;
                     data/Recurrence.kt wraps RecurrenceRule (MissingDay.SKIP) with time/UNTIL/EXDATE
feature/contacts     contacts + birthdays: data/ (VCF), birthday/, mirror/ (phone contacts), device/ (DAVx5), merge/,
                     capture/, today/, ui/. Two modules: CONTACTS (bar) and BIRTHDAYS (inNavigation = false)
feature/workout      quick start, sessions ("+1 set"), per-exercise history, gentle suggestions (off by default)
feature/sleep        one-tap bed/wake, nights, bedtime reminder, schedule editor (= quiet hours)
feature/onboarding   setup wizard (holistic order: modules, sleep, main phone, permissions, widget, contacts, AI, …, diet last)
feature/settings     global settings: modules, permissions, quiet hours, appearance, AI providers, backup, about
app                  KleanApp, MainActivity, nav (KleanNavHost, More overflow, NewModulesSheet), today/ (dashboard,
                     Arrange Today), widget/ (Klean Today widget), setup/ (main phone step)
backend/             Ktor server: pairing, sync, blobs, CardDAV/CalDAV, Google (fakes), web push, Hermes relay,
                     AI proxy, backup; Jib image. API: docs/backend-api.md
deploy/              docker-compose.yml, Caddy/Traefik examples; docs/deploy.md
build-logic/         convention plugins: klean.android.library / .compose / .feature / klean.hilt / klean.sqlite.jvmtest
```

## Conventions

- **Commit after every task, then push.** The user asked for this explicitly.
  - Use Conventional Commits.
  - Tag each milestone `mN` on `main` after the gate passes, and tick its tasks in `TASKS.md` in the same commit.
- **TDD for logic:** write the test, watch it fail (a compile failure counts), implement, watch it pass, commit.
- **UI tests:** view-model tests with fakes and Turbine, plus Robolectric/Compose tests.
- **Sync rules for every syncable table:**
  - UUIDv7 string primary key
  - embedded `SyncColumns`
  - no hard deletes, no SQL foreign keys
  - every write goes through a repository that calls `SyncStamper` inside `db.transact {}`
  - schema changes use Room `AutoMigration` (only additive), plus an exported schema JSON and a migration test from the previous version's fixture
- **Where settings live:**
  - synced settings: the `setting` table (`SettingsRepository`)
  - device-local settings: `DevicePrefs` (card layout, `isMainPhone`, …)
  - secrets: `SecretStore` (Keystore), excluded from backups unless the user opts in
- **How a module plugs in** (never hard-code a module into nav, the dashboard or surfaces): every life module contributes, with `@IntoSet`:
  - `KleanModule`
  - `CaptureHandler`, using the `RuleCaptureClassifier` action constants
  - `DashboardSummaryProvider`, `DayActivityProvider`, `TodayItemsProvider`
  - `StatusLineProvider`, which takes repositories through `dagger.Lazy`
  - `Reschedulable` for alarms, and optionally an `OnboardingStepProvider`

  It must also call `StatusRefresh` after writes.
- **Copy:** user-facing text lives in the owning module's `strings.xml`. Calm tone, no `!`, no missed/streak/behind/forgot/overdue, no red except for real errors.
- **Time:** inject `Clock`/`ZoneProvider`; never call `System.currentTimeMillis()` in logic. Store UTC and display in the device zone (the user is in Asia/Dubai).

## Working with parallel agents

- **One lane per worktree:** each lane works in `../klean-<lane>` on its own branch, and the parent merges the branches one at a time, running the gate after each.
- **Commit only your own paths** (`git commit -- <paths>`), never `git add -A`/`-a`. `git stash` is shared across all worktrees, so don't use it.
- **Lanes never change the Room schema.** If a lane needs a column, it reports it, and the parent adds one AutoMigration after merging.
- **Expected merge conflicts:**
  - `TASKS.md`: keep both sides
  - `KleanApp.kt`: union of startup steps
  - `Channels.kt` and the debug manifests: union
- **Usage limits are the main constraint** (both the 5-hour and the weekly limit were hit on 2026-11-10).
  - A five-lane wave hit the 5-hour limit within two minutes, so run at most 2 lanes at once.
  - Agents should commit early and small, because only commits survive an interruption.
  - An interrupted lane can be resumed with its context intact (SendMessage to its agent id) once the limit resets; check its worktree's `git log`/`git status` first.
- **Merging lanes:** if `main` hasn't moved, the merge tree equals the lane's gated tree; otherwise run the gate after each merge. Never name a tag the same as a branch (`m19-calendar` clashed; it became `m19-cal`).
- **Lane shared-file edits so far** are small and commented: `KleanModule.inNavigation`, one-shot DAO reads, `PromptTrigger.REMINDER`/`WORKOUT`, `PromptLogDao` diet queries filtered to diet rows. Modules register their own notification channels, so `Channels.kt` and `KleanApp.kt` stayed untouched.
- **`pgrep -f`/`pkill -f`:** never use a pattern that also appears in the calling shell's command line. It matches, or kills, itself. Use the flock lock files instead of process polling.

## Devices

- **Waydroid is the test device** (`adb -s 192.168.240.112:5555`, Android 13, x86_64, 540×1212). It holds no real data, so `pm clear` is fine.
  - Hold `~/.hermes/cache/scratch/waydroid.lock` for every session.
  - Install with `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`; `installDebug` can time out.
  - Then grant notifications: `adb shell pm grant app.klean.debug android.permission.POST_NOTIFICATIONS`.
- **Waydroid has no:** camera, Play services, Health Connect, GPS, TalkBack or real microphone. Verify that those paths degrade gracefully.
  - **Never trigger a camera intent**: it crashes the whole container.
  - `run-as` fails on Waydroid. Use the debug broadcast `app.klean.debug.EXPORT_DB`, then pull `/sdcard/Android/data/app.klean.debug/files/klean-debug.db`.
  - Mock location: `cmd location providers add-test-provider gps`.
  - Restart with `waydroid session stop` then `start` (as a persistent background process). If the container is FROZEN, run `waydroid show-full-ui`.
  - **Do each whole device check inside one `waydroid.lock` hold:** other lanes install, `pm clear` and uninstall between holds.
  - **Avoid the system file picker (SAF):** it froze Waydroid's system process twice. Test file import/export with unit tests.
  - No on-screen keyboard: Back closes the screen instead of a keyboard.
- **`python3` is not on the host:** run `ui.py`, `wshot` and the count script as `nix shell nixpkgs#python3 -c python3 …`.
- **Debug helpers:**
  - `~/.hermes/cache/scratch/bin/ui.py dump|tap "<text>"|has "<text>"` reads and taps the UI by text.
  - `~/.hermes/cache/scratch/bin/wshot <png>` takes a screenshot (it strips Waydroid's amdgpu warning).
  - Debug broadcasts:
    - `app.klean.debug.RESCHEDULE`
    - `ENSURE_ACCOUNT`
    - `EXPORT_DB`
    - `NEW_MODULES`
    - `TASK_REVIEW`, `BEDTIME`, `WORKOUT_SUGGEST`
  - In UI scripts, tapping "Today" hits the diet "Today" tile first; tap the last match.
- **The user's Pixel 9a** holds real entries and is used only in testing sessions the user starts.
  - Never reboot it, toggle airplane mode while adb is wireless, uninstall the app, or clear its data.
  - Don't drive its UI while the user is using it.
  - Delete any test entries you create.

## Gotchas learned so far

- **Convention plugins:** the version-catalog accessor in convention-plugin code is `catalog.lib("alias")`. Naming it `libs` shadows Gradle's generated accessors.
- **KSP:**
  - KSP tasks share one classloader per daemon, so they are serialised by `KspSerialService` (`maxParallelUsages = 1`) in `HiltConventionPlugin`. Keep it, or parallel builds produce partial Hilt output. Reproduce with `./gradlew kspDebugKotlin --rerun`.
  - KSP outputs are never build-cached.
  - Gradle compiles untracked files in the working tree, so don't write the implementation while the red-step build is still running.
- **Robolectric:**
  - `robolectric.properties` must have `sdk=36` in each module's test resources.
  - The JDK 21 add-opens come from the library convention.
  - Lazy items below the fold aren't composed; use `@Config(qualifiers = "w411dp-h891dp")`.
  - After a write lands on Room's background thread, call `rule.waitForIdle()` before `rule.waitUntil`, or view-model callbacks never fire.
- **Room:**
  - Tests use the real `BundledSQLiteDriver`, so apply `klean.sqlite.jvmtest` to modules whose unit tests open a database.
  - DAOs must be suspend or Flow, and transactions go through `db.transact {}`.
  - `:app` Robolectric tests have no SQLite JNI. That's why startup work in `KleanApp` is wrapped in `runCatching`, and why `StatusLineProvider`s take repositories through `dagger.Lazy`: a live Room flow there fails in a scope no catch can reach.
- **Test teardown:**
  - VM tests on a real Room/DataStore must keep their view models in a `ViewModelStore` and `clear()` it in `@After`, before `resetMain()` and `db.close()`.
  - DataStore tests that reopen a file must use `scope.coroutineContext.job.cancelAndJoin()`.
  - Both were CI flakes. `BackupViewModelTest` failed CI twice on 2026-11-10 (`UncaughtExceptionsBeforeTest`); its teardown now joins the DataStore scope (`3c6a855`), not yet proven over many runs. `AppearanceViewModelTest` may still flake.
  - Turbine over a real Room flow can exceed the default 3 s timeout during a loaded full gate; use `test(timeout = 10.seconds)`.
- **DataStore:** use one DataStore per file per process, via the top-level `preferencesDataStore` delegate.
- **Notifications:**
  - A channel's importance can't be raised after creation, so use a new id and retire the old one in `ChannelRegistrar`.
  - Pixel hides IMPORTANCE_LOW notifications from the lock screen.
  - The status line uses `status_v1` at DEFAULT importance, silenced at the channel level.
- **Nav:** register every module's graph always and hide only the entry points when a module is off. Rebuilding the graph reset the back stack, and dropped routes crashed.
- **Glance:** `colors.widgetBackground` maps to `secondaryContainer` (green), so use token `surfaceContainer`.
- **M3 Expressive sizes:**
  - `IconButton` measures 40 dp, so set `Modifier.size(48.dp)` where a 48 dp target is required.
  - `SuggestionChip` is 36 dp tall.
- **material3 1.5.0-rc01:** `ExposedDropdownMenu` doesn't resolve, so use `DropdownMenu`. `ExtendedFloatingActionButton` doesn't merge its text into semantics, so set `contentDescription` explicitly.
- **Kotlin:** a nullable property of a data class from another module can't be smart-cast; use `?.let`.
- **Notifications allow at most three action buttons;** reminder pings use Done / 10 min / Later… (a dialog with the rest).
- **Speech:** a speech recogniser may never connect, so always time it out.
- **Backend:**
  - Exposed 1.x imports come from `org.jetbrains.exposed.v1.core` and `org.jetbrains.exposed.v1.jdbc`.
  - SQLite `VACUUM INTO` needs an autocommit connection outside a transaction.
  - A Ktor 3 streaming proxy must keep the upstream response open until the body writer finishes (`proxy/StreamingProxy.kt`).
- **adb shell quoting:** quote the whole `adb shell "…"` command when its text contains spaces.
- **Waydroid installs:** on the shared Waydroid, a newer schema from another agent's install means downgrading with `-d`, then running `pm clear`.

## Current state (2026-11-10 afternoon)

`main` is clean, pushed and green on CI (last code commit `3c6a855`). Gate: 1000 tests, 0 failures, 1 skipped (a fixture writer). There are no branches or worktrees besides `main`. Lane reports and per-module details: `docs/handover-2026-11-10.md`.

| Milestone | Scope | Status |
|---|---|---|
| M0–M15 | Foundation and the complete diet module: status notification + inline reply, tile, widget, check-ins (rotation, learning, reboot), voice, photos, history and gaps, onboarding and permissions, backup/restore, AI providers and enrichment, unlock sheet, delivery listener, polish, on-device model, places | ✅ tags `m0`–`m15` |
| Today dashboard | Greeting, summary tiles, week overview; Waydroid close-out; CI flake fixes | ✅ (no tag) |
| P2 foundation | Schema v2, module skeletons, More overflow, Klean account, NaturalTime, rule capture classifier | ✅ tag `p2-foundation` |
| M16 | Holistic re-centre: day-ring icon, "Add to Klean" tile, holistic "Today" status notification ("Add something" → universal capture; plain text falls back to diet), card order APPROVALS→DIET + Arrange Today, new modules arrive on with a one-time "New in Klean" sheet, Klean Today widget, holistic wizard order | ✅ tag `m16` |
| M17 | Schema v3: delegation, approvals, calendar sources, exercises, sleep schedule, devices, sync cursor, plus new task/contact/event/workout columns; `DeviceRepository`, `isMainPhone`, SecretStore keys, reminder snooze/done/dismiss | ✅ tag `m17` |
| M24 | Sync backend: pairing, sync with LWW, blobs, CardDAV/CalDAV (simplified), Google (fakes), web push, Hermes relay, AI proxy, backup, Docker | ✅ tag `m24` (gaps under "## M24" in TASKS.md) |
| M18 | Tasks and reminders: `RecurrenceRule`, tasks, review, archive, reminders, alarms | ✅ tag `m18` |
| M19 | Calendar (tag `m19-cal`); Contacts and birthdays | ✅ tag `m19` |
| M20 | Workout and sleep (the sleep schedule drives quiet hours; `QuietHoursService` API unchanged) | ✅ tag `m20` |
| M21–M23 | Google Tasks and Assistant; Health Connect and GPS; AI delegation and Hermes | ⏳ |
| M25–M28 | Android sync; web client; Wear OS; capture everywhere | ⏳ M25 ‖ M26 next |
| M29 | Acceptance on the Pixel, watch, Docker backend and browser → tag `p1` | ⏳ |

## Resume here — next steps in order

1. **M25 Android sync ‖ M26 web client:** two worktree lanes (`../klean-sync` on `m25-sync`, `../klean-web` on `m26-web`). Specs: the governing plan's §M25 and §M26, plus `docs/backend-api.md`. The backend must start serving `web/dist`. M25 also owns the `DavAccounts.syncsOwnBackend` hook (`TODO(M25)` in `feature/contacts`). Merge, gate, tag `m25`, `m26`.
2. **Then:** M21 Google, M22 Health Connect and GPS (replaces the no-op `HealthConnectBridge`/`SleepImportSource`), M23 delegation and Hermes, M27 Wear OS, M28 capture everywhere.
3. **M29 acceptance with the user:** record it in `docs/acceptance-p1.md`, then tag `p1`.

**Small follow-ups** (fold into a later lane, not urgent):
- the status line repeats a birthday on the day (the reminder plus Birthdays' own line);
- calendar week-view titles break mid-word in narrow columns;
- calendar task rows have no deep link into Tasks yet;
- `reminder.event_id` would be cleaner than calendar's name-derived reminder ids (needs a schema change);
- DAVx5 contacts' birthdays don't show on the Birthdays tab or calendar; contact photos can't be edited;
- the sleep schedule has one every-day row (no per-weekday UI).

**Open decisions** (defaults in use; the user hasn't overridden them):
- the holistic status notification replaces the diet-only one;
- Hermes is tested against a local stub (no URL or token supplied);
- Google is tested with fakes (the user's own OAuth client IDs are needed for real use);
- a birthday phrase while Contacts is off becomes a dated task (never AI).

## Deviations from the plans

- **Copy guard:** `ForbiddenCopyTest` matches only uppercase `ADD`, so "Add something" passes. It also rejects `!` and guilt words.
- **Shell:** screens get registry and services through `ShellViewModel`, so the shell can be tested without Hilt.
- **Module gate:** `ModuleGate` lives in `:core:data` (`SettingsModuleGate`, synced `core.modules.disabled`).
  - `seedNew` keeps new modules **on** and records them in `core.modules.known`.
  - Existing installs get them through `DevicePrefs.pendingNewModules` and the "New in Klean" sheet.
- **Diet:**
  - `DietRepository` is an interface (`DefaultDietRepository`), tested with `FakeDietRepository`.
  - Diet's status notification was replaced by `KleanStatusNotifier` (`core/capture/status`); diet now contributes `DietStatusLine`.
  - `DietReplyReceiver` handles check-in replies only.
  - The status line still follows the `diet.statusNotification` setting, and is removed when no module contributes a line.
- **Renames:** `NotificationIds.STATUS` (1001); channel `Channels.STATUS` = `status_v1` (`diet_status_v2` retired).
- **`DashboardSlot` order:** APPROVALS, TASKS, AGENDA, BIRTHDAYS, SLEEP, WORKOUT, DIET.
- **`navOrder`:** Tasks 10, Calendar 20, Contacts 30 (Birthdays 35), Workout 40, Sleep 50, Diet 60.
- **Bottom bar:** Today + 3 modules + More, i.e. Today, Tasks, Calendar, Contacts, More. Reminders and Birthdays set `KleanModule.inNavigation = false`: they keep their own gate and settings, and live as tabs inside Tasks and Contacts.
- **Today slots:** Calendar owns AGENDA (events + reminders + birthdays); its tile, activity and Today items count events only, so reminders aren't counted twice. Reminders has no card but contributes a "Next reminder" tile, activity and status line.
- **Recurrence:** `RecurrenceRule` clamps a missing month day by default (tasks); calendar passes `MissingDay.SKIP` (RFC 5545).
- **Reminders:**
  - pings respect quiet hours and format rotation (the governing plan's amendment, over the module plan);
  - alarms ring through a `mediaPlayback` foreground service (no special permission), only when `isMainPhone`; there is no sound picker or default-snooze setting;
  - Done completes an attached one-off task (M17 `markDone` contract); Dismiss never does.
- **Tasks:** the review sits at the top of the Today tab (no separate screen); a week-overview day opens Upcoming, not a date filter.
- **Calendar:** the mirror is read-only with `HAS_ALARM=0` (Klean rings, not the system calendar); default event reminder 10 min before timed events (`calendar.defaultReminderMinutes`).
- **Contacts:** only Contacts has a birthday capture handler; birthday reminders are ordinary quiet reminders via `ReminderRepository`; the phone-contacts mirror matches by `SOURCE_ID` (no `provider_raw_id` write).
- **Sleep and quiet hours:** schedule writes also mirror the old `core.quietHours` key, because frozen diet code reads it; quiet hours keep working with Sleep off. The Sleep tile opens Sleep (it doesn't toggle); "Last night" is the longest night ending in the logical day; the bedtime reminder is on by default, the workout suggestion off.
- **Schema v3 naming:**
  - `task.origin` (KLEAN/GOOGLE), because `task.source` already means MANUAL/CAPTURE.
  - `photo_blob` / `route_blob` hold file-name keys.
  - Reminders have a `DISMISSED` state.
  - `DevicePrefs.isMainPhone` keeps the stored key `mainPhone`.
- **Capture routing:** the `RuleCaptureClassifier` runs before AI. Birthday text never reaches AI. With no rule match, capture falls back to diet with "Move to…" chips.
- **Backend:**
  - The `records` column is `table_name`.
  - Web push uses JDK crypto; argon2id comes from BouncyCastle.
  - `POST /auth/login` gives the web's admin token.
  - DAV query filters are ignored, and recurring-event exceptions sent over DAV are dropped.
- **Places:** geofencing uses `LocationManager` proximity alerts only (no Play services).
- **Unlock sheet:** uses a full-screen intent, with a heads-up fallback; never `SYSTEM_ALERT_WINDOW`.
- **Plan defaults still in effect:** applicationId `app.klean`, sleep window 00:00–08:00, gap threshold 4 h, Gemini `gemini-2.5-flash`.

## Verification status

- **Waydroid:** verified for:
  - the diet loop
  - check-in rotation and reboot rescheduling
  - quiet hours
  - backup/restore, including a fresh install
  - airplane-mode inline reply (251 ms)
  - the Today dashboard and holistic surfaces
  - the schema upgrades
  - the Klean account
  - the delivery listener (simulated)
  - places (mock GPS)
  - the AI enrichment path (fake local server)
  - **M18–M20 (2026-11-10):**
    - tasks: capture, the review notification, recurrence on completion
    - reminders: the ping, and the alarm ringing in the app and over the lock screen
    - calendar: permission, the Klean mirror, phone events read-only, capture, module off/on
    - contacts: capture, the phone-contacts mirror, a today birthday, the DAVx5 merge, module off/on
    - sleep and workout: one-tap bed/wake, capture, sessions, bedtime and suggestion notifications

  `connectedDebugAndroidTest` passes 6/6, plus the new per-module `*DisableTest`s (Tasks, Calendar, Contacts, Workout, Sleep). Evidence is in TASKS.md, under each milestone's section.
- **Pixel 9a** (2026-11-08): verified for the inline reply, lock-screen visibility, the QS tile, the widget, module on/off, and check-in rotation.
- **Still needs the user's devices** (full list in TASKS.md):
  - lock-screen inline reply in airplane mode
  - a real reboot
  - a real delivery notification
  - voice and camera
  - walking out of a real place
  - TalkBack
  - a real on-device model
  - Gemini with a key
  - the holistic surfaces on the user's existing install
  - M18–M20:
    - an alarm through DND, the full-screen permission on Android 14+, the 10-minute auto-stop
    - reminders, birthdays and bedtime at their real times and after a reboot
    - voice on Tasks
    - ICS and VCF import/export through the system picker
    - the Klean calendar in a third-party app; Klean contacts in the dialer; a real DAVx5 round trip
    - the widget's sleep item; the workout suggestion at 18:00
    - the quiet-hours migration on the existing install
    - TalkBack on the new tabs and calendar grids
