# AGENTS.md — Klean

The handover guide for anyone, human or agent, picking up this repo. Read it in full before touching code.

## Handover documents

| File | What it is | When to update it |
|---|---|---|
| `AGENTS.md` (this file) | How to work here: toolchain, conventions, gotchas, current state, deviations, what to do next | Every milestone merge, and whenever you deviate from the plan |
| `PLAN.md` | The full Prototype 1 plan: architecture, Room schema, screens/nav, background work, platform decisions, and every task with code and verification commands. Canonical copy of `.hermes/plans/2026-11-08_153740-klean-prototype-1.md` | Only when scope or a decision changes. Record known code-vs-plan differences under "Deviations" below |
| `TASKS.md` | One checkbox per plan task, with where it lives (main or branch) | In the same commit that finishes a task |

Precedence when they disagree: user instructions > AGENTS.md "Deviations" > PLAN.md.
Decisions marked **Decided** in PLAN.md need user approval before you change them.

## What this is

Klean is a local-first personal life-management Android app. Prototype 1 = the app foundation (module registry, Today dashboard, settings, onboarding, backup, AI layer, design system) plus a complete, daily-usable **diet-awareness** module. Later prototypes add tasks, reminders, calendar, contacts, workout, sleep, a Ktor sync backend and a web client. The architecture must leave room for these, but they must not be built now (see PLAN.md "Future modules" context and §2).

- Internal design context: the user has AuDHD-like traits. This may appear in code comments and internal docs only. It must **never** appear in user-facing strings. `ForbiddenCopyTest` (in `app/src/test`) enforces this.
- Product principles that every feature must follow:
  - never rely on notifications alone; there is always a passive surface too
  - logging is a single action and never blocked by AI or network
  - zero guilt: no streaks, scores or red badges, and no "missed" wording
  - zero upkeep for the user
  - when a prompt is ignored, change its format rather than its frequency
  - works offline with no account
  - modules can be switched off completely

## Toolchain (NixOS — everything comes from the flake)

- Never install tools globally. Run everything as `nix develop -c <cmd>`. One-off tools: `nix develop -c nix shell nixpkgs#<pkg> -c <cmd>`.
- Flakes only see git-tracked files, so `git add` new files before `nix develop`.
- Versions: Gradle 9.9.0 (wrapper), AGP 9.5.1 (built-in Kotlin, so don't apply `kotlin-android`), Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Room 2.8.5 + `sqlite-bundled` 2.6.2, Compose BOM 2026.09.00, material3 1.5.0-rc01 (Expressive APIs), Glance 1.2.0, WorkManager 2.12.0, kotlinx-datetime 0.8.0 (uses `kotlin.time.Instant/Clock`). All are pinned in `gradle/libs.versions.toml`.
- SDK: platform `android-37.0` (+36) and build-tools 36.0.0, which is AGP 9.5's default. compileSdk/targetSdk 37, minSdk 28 (`build-logic/.../KleanSdk.kt`).
- The SDK lives read-only in the Nix store. If AGP tries to download a component, add it to `flake.nix`; don't hack around it.
- aapt2 is overridden through `GRADLE_OPTS` in the flake, because aapt2 from Maven doesn't run on NixOS.
- `scripts/latest-versions.sh` checks library versions against Maven. `<release>` is often an alpha/beta, so choose stable unless you need otherwise.

Common commands (run from the repo root):
```
nix develop -c ./gradlew assembleDebug                                   # build
nix develop -c ./gradlew :shared:jvmTest testDebugUnitTest               # unit tests (JVM + Robolectric)
nix develop -c ./gradlew :app:assembleDebugAndroidTest                   # compile instrumented tests
nix develop -c ./gradlew connectedDebugAndroidTest                       # instrumented (phone connected)
nix develop -c ./gradlew :app:installDebug                               # debug appId: app.klean.debug
```
The milestone gate (must be BUILD SUCCESSFUL before tagging):
```
nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest --console=plain
```
A cold build takes about 5 minutes and a warm one about 30 s. Run long builds in the background, writing to a log under `~/.hermes/cache/scratch/`, because foreground tool timeouts are shorter than a cold build.

## Layout

```
shared/              KMP (jvm + android): models, UUIDv7, nameUuid, QuietHours, DayWindows, PromptPlanner,
                     FormatRotator, MealTimeLearner, PromptOutcomeResolver, GapDetector, QuickRepeatRanker,
                     DeliveryMatcher, capture contracts + DefaultCaptureRouter, AI DTOs, UnlockSheetPolicy, ModuleId
core/common          ApplicationScope, Clock, ZoneProvider
core/design          tokens (tokens/klean-color-tokens.json = source of truth for web too), KleanTheme, Cantarell,
                     components (CaptureField, EntryRow, GapMarker, ProviderChip, KleanCard, NeutralEmpty)
core/data            Room DB (KleanDatabase v1, schema JSON in core/data/schemas), entities, DAOs, SyncStamper,
                     SettingsRepository + SyncedKeys, SettingsModuleGate, DevicePrefs, PhotoStore, backup (M9 branch)
core/ui              KleanModule + ModuleRegistry (drives nav, dashboard slots, settings)
core/notifications   channels/IDs (M4 branch); quiet-hours service, alarms, reschedule (M5)
core/ai              EnrichmentPolicy + NoAiPolicy; AiProvider/AiGateway (M10)
core/capture         UniversalCaptureService, CaptureActivity/CaptureSheet, QS tile (M4 branch)
feature/diet         everything diet: repository, screens, dashboard card, notification, widget (M4 branch), prompts (M5) …
feature/onboarding   setup wizard (M8), still empty
feature/settings     global settings screens (stubs for Quiet hours/Appearance/AI/Permissions/About); backup screen (M9 branch)
app                  KleanApp, MainActivity, KleanNavHost, ShellViewModel, TodayScreen
build-logic/         convention plugins: klean.android.library / .compose / .feature / klean.hilt / klean.sqlite.jvmtest
```

## Conventions

- **Commit after every task.** The user asked for this explicitly. Use Conventional Commits (`feat(diet): …`, `test(shared): …`, `build: …`, `docs: …`). Tag each milestone `mN` on `main` after it passes the gate, and tick the task in `TASKS.md` in the same commit.
- **TDD** for logic: write the test, watch it fail (a compile failure counts), implement, watch it pass, commit. UI: view-model tests with fakes + Turbine, plus Robolectric/Compose tests where useful.
- **Sync rules** for every syncable table:
  - UUIDv7 string primary key; settings use `nameUuid(key)`
  - embedded `SyncColumns` (created/updated UTC ms, deviceId, `deleted` tombstone, `change_seq`)
  - no hard deletes, no SQL foreign keys
  - every write goes through a repository that calls `SyncStamper` inside `db.transact {}`
- Settings that follow the user across devices go in the `setting` table (`SettingsRepository` / `SyncedKeys`). Device-local settings go in DataStore (`DevicePrefs`). Secrets go in the Keystore (`SecretStore`, M10).
- After a diet write, `DietChangeDispatcher` fans out to every `DietChangeListener`, each bound with `@IntoSet`: widget, status notification, prompt engine, enrichment. Add new reactions as listeners; don't call them directly from the repository.
- New life modules plug in through a `KleanModule` (`@IntoSet`), a `CaptureHandler` (`@IntoSet`) and `Reschedulable`s. Never hard-code a module into nav or the dashboard.
- All user-facing copy lives in the owning module's `strings.xml`: calm tone, no exclamation marks, no "missed/streak/behind/forgot", no red except for real errors.
- Inject `Clock`/`ZoneProvider`; never call `System.currentTimeMillis()` in logic. Store UTC and display in the device zone (the user is in Asia/Dubai).

## Gotchas learned so far

- In convention-plugin code the version-catalog accessor is `catalog.lib("alias")`. Naming it `libs` shadows Gradle's generated accessors in every build script.
- Robolectric: put `robolectric.properties` with `sdk=36` in each module's test resources (Robolectric doesn't support 37 yet). The JDK 21 `--add-opens/--add-exports java.base/jdk.internal.access` args come from the library convention plugin. `:app` sets them itself.
- `failOnNoDiscoveredTests=false` is set in the library convention, because Hilt generates test sources even in modules with no tests, and Gradle 9 then fails.
- Room in tests uses the real `BundledSQLiteDriver`. Apply the `klean.sqlite.jvmtest` plugin to any module whose unit tests open a database: it extracts the linux-x64 JNI library from `sqlite-bundled-jvm` onto `java.library.path`.
- Room driver mode: DAOs must be suspend or Flow. Transactions go through `db.transact {}` (`useWriterConnection { immediateTransaction { } }`), not `withTransaction`.
- `ForbiddenCopyTest` declares all `res/` XML and manifests as test-task inputs, so it can't return a stale cached pass after a copy change.
- When parallelising milestones with git worktrees, any file both branches create (e.g. `app/src/androidTest/.../HiltTestRunner.kt`, `shared/.../ModuleId.kt`) must have byte-identical content specified in both prompts. Don't run two Gradle builds in the same checkout at once.

## Current state (2026-11-08)

`main` is at tag `m3` plus docs commits. It builds and all unit tests pass.

| Milestone | Scope | Status |
|---|---|---|
| M0 | Nix shell, wrapper, version catalog, convention plugins, module skeletons, empty app | ✅ tag `m0` |
| M1 | Design system, module registry, registry-driven shell, settings skeleton, copy guard | ✅ tag `m1` |
| M2 | `:shared` domain logic, 78 JVM tests | ✅ tag `m2` |
| M3 | Room data layer, sync stamping, settings repo, module gate, photo store, DietRepository, diet screens, dashboard card | ✅ tag `m3` |
| M4 | Status notification + inline reply, capture sheet, universal capture, QS tile, Glance widget, module-disable plumbing | 🚧 4.1–4.7 committed on `m4-work`; 4.8 WIP (`adce112`), unverified, **not merged** |
| M5 | Prompt engine: quiet hours, exact alarms, rotation, meal-time learning, reboot | ⏳ |
| M6 | Voice, photo attach, quick-repeat chips | ⏳ |
| M7 | Day history, gap affordance | ⏳ |
| M8 | Onboarding + permissions | ⏳ |
| M9 | Single-file SQLite backup/restore, auto backup | 🚧 9.1/9.2/9.3/9.5 committed on `m9-work`; 9.4 (backup screen) WIP (`cf04d02`), unverified, **not merged** |
| M10 | AI providers, enrichment queue, AI items UI, capture classifier | ⏳ |
| M11 | Unlock sheet (pure `UnlockSheetPolicy` already in `:shared`) | ⏳ |
| M12 | Delivery-app listener | ⏳ |
| M13 | Disable audit, accessibility, copy audit | ⏳ |
| M14 | On-device model | ⏳ |
| M15 | Geofence prompts | ⏳ |
| M16 | Acceptance run on a real phone | ⏳ |

Work paused because the previous agent hit its API usage limit (resets 2026-12-01). See `TASKS.md` for per-task status.

### What's on the unmerged branches

- **`m4-work`** (worktree `../klean-m4`, about 62 files):
  - `core/notifications`: `Channels.kt` (channels, IDs, registrar) and its test.
  - `feature/diet/notification`: `DietStatusNotifier`, `DietStatusActions`, `DietReplyReceiver`, `StatusRepostReceiver`, `DietNotificationModule`.
  - `core/capture`: `UniversalCaptureService`, `CaptureActivity`/sheet and view model, QS tile and tile model.
  - `feature/diet`: `DietCaptureHandler`, and the Glance widget `widget/DietWidget*` (preview layout, `diet_widget_info.xml`).
  - The universal capture bar on Today.
  - Tests: `DietReplyReceiverTest` (Robolectric), `DietStatusNotifierTest`, `UniversalCaptureServiceTest`, `CaptureViewModelTest`, `DietWidgetStateTest`, `ChannelRegistrarTest`, plus `TestHiltModules`.
  - WIP for 4.8: `DietEnablement.kt` with its test, `DietModule` changes, a stub `prompt/DietPromptEngine.kt`, and a `ShellSmokeTest` edit.
- **`m9-work`** (worktree `../klean-m9`, about 25 files):
  - `core/data/backup`: `BackupContracts`, `BackupManager` (VACUUM INTO + `backup_*` tables, inspect/restore with files split from restart), and `auto/AutoBackup*` (worker + rotation through a target abstraction).
  - `DevicePrefs` export/import.
  - Tests: Robolectric `BackupExportTest`, `BackupRestoreTest`, `AutoBackupWorkerTest`, and the fixture `core/data/src/test/resources/backup-v1.sqlite`.
  - `:app` changes: `AppBindings.kt`, `KleanApp` as WorkManager `Configuration.Provider` with `HiltWorkerFactory`, the WorkManager initializer removed from the manifest, `HiltTestRunner`, and the instrumented `BackupRoundTripTest`.
  - WIP for 9.4: `feature/settings/backup/BackupSettingsScreen`, `BackupViewModel` with its test.
  - Secrets backup goes through a `SecretBackup` interface with a no-op binding until M10's `SecretStore` implements it.

## Resume here — next steps in order

1. **Finish M4** in `../klean-m4`:
   - Review the WIP commit `adce112` and complete Task 4.8 (PLAN.md §8 M4). When diet is disabled: cancel the diet notifications, update the widget to its disabled state, and set the QS tile unavailable; re-enable reverses this.
   - Run the milestone gate, reword or squash the wip commit, and tick 4.8 in `TASKS.md`.
2. **Finish M9** in `../klean-m9`:
   - Review `cf04d02` and complete Task 9.4: export/restore pickers, a confirmation dialog with the exact copy from the plan, the include-keys toggle, and the auto-backup folder and keep-N setting.
   - Run the gate and tick 9.4.
3. **Merge into `main`:**
   - `git merge m4-work`, then `git merge m9-work`.
   - Expect conflicts in `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `KleanApp.kt` (M9's `Configuration.Provider` plus any M4 startup work like `ChannelRegistrar.ensure`/status refresh), `ShellSmokeTest`, and possibly `HiltTestRunner.kt`.
   - Run the gate on `main` and tag `m4` and `m9`.
   - Clean up with `git worktree remove ../klean-m4 && git worktree remove ../klean-m9`, then delete the branches.
   - Update the table above and `TASKS.md`.
4. **M5 (prompt engine)** is next on the critical path. Start from the M4 `DietPromptEngine` stub and PLAN.md Tasks 5.1–5.7. After M5, these can run in parallel worktrees with little overlap: M6+M7 (diet UI), M8 (onboarding), M10 (AI).
5. **Device checks:** once the user connects a phone (`adb devices`), run `connectedDebugAndroidTest` and the manual checks listed at the bottom of `TASKS.md`.

## Deviations from the plan

- `ForbiddenCopyTest` matches only uppercase `ADD`, so the button text "Add something" passes. A second test confirms that "Add something"/"Address" pass and ADHD/ADD/autistic fail.
- Screens get `ModuleRegistry` through `ShellViewModel` instead of calling `hiltViewModel()` themselves, so the shell can be tested without Hilt. `hiltViewModel` comes from `androidx.hilt.lifecycle.viewmodel.compose`.
- `CaptureField` has an extra `enabled` parameter.
- `ModuleGate` lives in `:core:data` (`SettingsModuleGate`, stored in the synced `core.modules.disabled` set). `:core:ui` exposes `:core:data` as `api`. The temporary in-memory gate has been removed.
- `DietRepository` is an interface with `DefaultDietRepository` bound through Hilt, so view models are tested with `FakeDietRepository`.
  - Extra methods: `observeViewPrefs()`, `observeItems()`, `windowFor()`, and `restore()`, the undo for delete.
  - `restore` un-tombstones the items that `delete` tombstoned (those stamped at or after the entry's tombstone).
- `SettingsRepository.update(key) { … }` does read-modify-write in one transaction. The synced key `diet.deliveryMatchWords` from plan §3 was added.
- `DevicePrefs` exposes `Flow`s plus suspend setters; its DataStore is provided with the `@DevicePrefsStore` qualifier.
- Time/gap labels go through an injected `DietFormatter` (`AndroidDietFormatter`), and the minute ticker is an injectable `MinuteTicker`, so view-model tests are deterministic.
- Diet today screen:
  - The history icon is left out until `DietHistoryRoute` exists (M7).
  - "Add something" on a gap focuses the capture field; M7 adds the `eatenAt` preset.
  - Mic buttons do nothing until M6.
  - `DietSettingsRoute` is a placeholder until M5.
- Entry edit: the time picker is an `AlertDialog` hosting `TimePicker`. Photo replace uses the system photo picker and `PhotoStore.import`; the camera capture path isn't wired in the UI yet.
- Backup (M9 branch): secrets go through a `SecretBackup` interface with a no-op binding until M10. Restore is split into a testable "replace files" step and a separate process restart.
- Unlock-sheet eligibility (`UnlockSheetPolicy`) was implemented early in `:shared` (M2), with 8 tests.
- Open questions from the plan use their defaults; the user hasn't overridden them yet:
  - applicationId `app.klean`
  - sleep window 00:00–08:00
  - gap threshold 4 h
  - Gemini model `gemini-2.5-flash`
  - immediate silent re-post of the swiped status notification

## Not yet verified on a device

No phone has been connected so far. Everything is verified by builds and JVM/Robolectric tests only. Stand-ins:
- `ShellSmokeTest` covers the Today / Diet / Settings bar.
- `DietDashboardViewModelTest.loggingFromDashboardAppearsOnDietScreen` covers a dashboard log reaching the Diet screen.
- `DietReplyReceiverTest` covers the inline-reply save path.
- `BackupRestoreTest` covers the backup round trip.

The instrumented tests (`InlineReplySaveTest`, `BackupRoundTripTest`) compile but have never run. The list of pending manual checks is at the bottom of `TASKS.md`.
