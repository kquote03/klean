# AGENTS.md — Klean

Guidance and current progress for anyone (human or agent) working in this repo.

## What this is

Klean is a local-first personal life-management Android app. Prototype 1 is the app foundation plus a complete diet-awareness module.

- Full plan, which is the source of truth for scope and tasks: `.hermes/plans/2026-11-08_153740-klean-prototype-1.md`
- Internal design context: the user has AuDHD-like traits. This may appear in code comments and internal docs only. It must **never** appear in user-facing strings. `ForbiddenCopyTest` enforces this.
- Product principles (see plan §0):
  - no streaks or guilt wording
  - one-action logging
  - never depend on notifications alone
  - zero upkeep for the user
  - works offline with no account
  - modules can be switched off completely

## Toolchain (NixOS — everything comes from the flake)

- Never install tools globally. Run everything as `nix develop -c <cmd>`.
- Flakes only see git-tracked files, so `git add` new files before `nix develop`.
- Versions: Gradle 9.9.0 (wrapper), AGP 9.5.1 (built-in Kotlin, so don't apply `kotlin-android`), Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Room 2.8.5 + `sqlite-bundled`, Compose BOM 2026.09.00, material3 1.5.0-rc01, kotlinx-datetime 0.8.0.
- SDK: platform `android-37.0` (+36) and build-tools 36.0.0, which is AGP's default. The SDK is read-only in the Nix store; if AGP tries to download anything, add the component to `flake.nix`.
- aapt2 is overridden through `GRADLE_OPTS` in the flake, because aapt2 from Maven doesn't run on NixOS.
- `scripts/latest-versions.sh` checks library versions against Maven. Pick stable releases; `<release>` is often an alpha.

Common commands:
```
nix develop -c ./gradlew assembleDebug                        # build
nix develop -c ./gradlew :shared:jvmTest testDebugUnitTest    # unit tests (JVM + Robolectric)
nix develop -c ./gradlew connectedDebugAndroidTest            # instrumented (phone connected)
nix develop -c ./gradlew :app:installDebug
```
A cold first build takes about 5 minutes, so run long builds in the background with output sent to a log.

## Layout

```
shared/              KMP (jvm + android): models, UUIDv7, quiet hours, day windows, prompt planner/rotator/
                     meal-time learner, gaps, quick repeats, delivery matcher, capture router, AI DTOs
core/common          ApplicationScope, Clock, ZoneProvider
core/design          tokens (tokens/klean-color-tokens.json = source of truth), theme, Cantarell, components
core/data            Room DB, entities, DAOs, SyncStamper, settings, DataStore, photos, (backup in M9)
core/ui              KleanModule + ModuleRegistry (drives nav, dashboard slots, settings)
core/notifications   channels, quiet-hours service, alarms, reschedule (M4/M5)
core/ai              AiProvider/AiGateway (M10; NoAiPolicy until then)
core/capture         universal capture service, CaptureActivity, QS tile (M4)
feature/diet         everything diet
feature/onboarding   setup wizard (M8)
feature/settings     global settings screens
app                  KleanApp, MainActivity, NavHost
build-logic/         convention plugins: klean.android.library / .compose / .feature / klean.hilt / klean.sqlite.jvmtest
```

## Conventions

- **Commit after every task.** Use Conventional Commits (`feat(diet): …`, `test(shared): …`, `build: …`) and tag each milestone `mN`.
- **TDD** for logic: write the test, watch it fail, implement, watch it pass, commit.
- **Sync rules** for every syncable table:
  - UUIDv7 string primary key
  - embedded `SyncColumns` (created/updated UTC ms, deviceId, `deleted` tombstone, `change_seq`)
  - no hard deletes, no SQL foreign keys
  - writes go through repositories that call `SyncStamper` inside `db.transact {}`
- Settings that follow the user across devices go in the `setting` table (`SettingsRepository`). Device-local settings go in DataStore (`DevicePrefs`). Secrets go in the Keystore (M10).
- All user-facing copy lives in each module's `strings.xml`: calm, no exclamation marks, no "missed/streak/behind", no red except for real errors.
- Inject `Clock`/`ZoneProvider`; never call `System.currentTimeMillis()` in logic.
- Robolectric: put `robolectric.properties` with `sdk=36` in test resources. The JDK 21 `--add-opens` args come from the convention plugin.
- In convention-plugin code the version-catalog accessor is `catalog.lib("alias")`. Never name it `libs`, because that shadows the generated accessors.

## Progress

| Milestone | Scope | Status |
|---|---|---|
| M0 | Nix shell, wrapper, version catalog, convention plugins, module skeletons, empty app | ✅ tag `m0` |
| M1 | Design system (pastel tokens, Cantarell, M3 Expressive theme, components), module registry, registry-driven shell, settings skeleton, copy guard | ✅ tag `m1` |
| M2 | `:shared` domain logic, 78 JVM tests | ✅ tag `m2` |
| M3 | Room data layer, sync stamping, settings repo, module gate, photo store, DietRepository, diet screens, dashboard card | ✅ tag `m3` |
| M4 | Status notification + inline reply, capture sheet, universal capture, QS tile, Glance widget, module-disable plumbing | 🚧 Tasks 4.1–4.7 committed on branch `m4-work` (worktree `../klean-m4`); 4.8 is a WIP commit (`adce112`), unverified, not merged |
| M5 | Prompt engine: quiet hours service, exact alarms, rotation, meal-time learning, reboot | ⏳ |
| M6 | Voice, photo attach, quick-repeat chips | ⏳ |
| M7 | Day history, gap affordance | ⏳ |
| M8 | Onboarding + permissions | ⏳ |
| M9 | Single-file SQLite backup/restore, auto backup | 🚧 Tasks 9.1, 9.2, 9.3, 9.5 committed on branch `m9-work` (worktree `../klean-m9`); 9.4 (backup screen) is a WIP commit (`cf04d02`), unverified, not merged |
| M10 | AI providers (Gemini, OpenAI-compatible), enrichment queue, AI items UI | ⏳ |
| M11 | Unlock sheet | ⏳ |
| M12 | Delivery-app listener | ⏳ |
| M13 | Disable audit, accessibility, copy audit | ⏳ |
| M14 | On-device model | ⏳ |
| M15 | Geofence prompts | ⏳ |
| M16 | Acceptance run on a real phone | ⏳ |

### Deviations from the plan so far

- `ForbiddenCopyTest` matches only uppercase `ADD`, so the button text "Add something" passes.
- The copy test declares resource files as test inputs so Gradle can't report a stale cached pass.
- `failOnNoDiscoveredTests=false` in the library convention, because Hilt generates test sources in modules without tests.
- Screens get `ModuleRegistry` through `ShellViewModel` instead of calling `hiltViewModel()` themselves, so the shell can be tested without Hilt.
- `ModuleGate` moved to `:core:data` (`SettingsModuleGate`, stored in the `core.modules.disabled` set). The temporary in-memory gate has been removed.
- Robolectric tests use the real `BundledSQLiteDriver`. The Android artifact can't find `libsqliteJni` on the host, so the `klean.sqlite.jvmtest` convention plugin extracts the linux-x64 JNI library from `sqlite-bundled-jvm` and puts it on `java.library.path` for unit tests only. Apply it to any module whose tests open a database.
- `DietRepository` is an interface; `DefaultDietRepository` is bound through Hilt, so view models are tested with `FakeDietRepository`. It also exposes `observeViewPrefs()`, `observeItems()`, `windowFor()` and `restore()` (the undo for delete, plan 3.13). `restore` un-tombstones the items that `delete` tombstoned (those stamped at or after the entry's tombstone).
- `SettingsRepository.update(key) { … }` does read-modify-write in one transaction; `SettingsModuleGate` uses it. Added the synced key `diet.deliveryMatchWords` from plan §3.
- `DevicePrefs.deviceId()` and the other device-local keys are exposed as `Flow`s plus suspend setters; the DataStore is provided with the `@DevicePrefsStore` qualifier.
- Time/gap labels go through an injected `DietFormatter` (`AndroidDietFormatter`), and the minute ticker is an injectable `MinuteTicker`, so view-model tests are deterministic.
- Diet today screen: the history icon is left out until `DietHistoryRoute` exists (M7). Tapping "Add something" on a gap focuses the capture field. Mic buttons are no-ops until M6. `DietSettingsRoute` is a placeholder screen until M5.
- Entry edit: the time picker is an `AlertDialog` hosting `TimePicker`; photo replace uses the system photo picker and `PhotoStore.import`.
- Open questions from the plan use their defaults: applicationId `app.klean`, sleep window 00:00–08:00, gap threshold 4 h, Gemini model `gemini-2.5-flash`, immediate silent re-post of the status notification.

### Not yet verified on a device

No phone has been connected so far. On-device checks (bottom bar, inline reply from the lock screen, widget) are pending, and Robolectric tests stand in for them where possible: `ShellSmokeTest` checks the Today / Diet / Settings bar, and `DietDashboardViewModelTest.loggingFromDashboardAppearsOnDietScreen` checks that a dashboard log reaches the Diet screen through the shared Room flow.

## Resume here (work paused 2026-11-08: API usage limit until 2026-12-01)

Both parallel branches stopped mid-milestone. Neither is merged into `main`.

1. **M4 (`m4-work`):**
   - Check the WIP commit `adce112` for Task 4.8. It adds `DietEnablement`, `DietModule` changes and a stub `prompt/DietPromptEngine.kt`, and edits `ShellSmokeTest`.
   - Finish Task 4.8, then run `nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest --console=plain` from `../klean-m4`.
   - Squash or reword the wip commit and tag `m4`.
2. **M9 (`m9-work`):**
   - Check the WIP commit `cf04d02` for Task 9.4 (`BackupSettingsScreen`, `BackupViewModel` and its test, edits to `BackupManager`/`BackupContracts`).
   - Finish 9.4 and run the same command from `../klean-m9`, then tag `m9`.
3. **Merge:**
   - Run `git merge m4-work`, then `git merge m9-work` on `main`.
   - Expect conflicts in `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `KleanApp.kt` (WorkManager `Configuration.Provider` from M9 plus channel or status setup from M4) and possibly `HiltTestRunner.kt` (both branches were told to use identical content).
   - Re-run the full build and tests.
   - Update this table, then `git worktree remove ../klean-m4 ../klean-m9`.
4. **Next:** M5 (prompt engine). The `DietPromptEngine` stub from M4 is its starting point.
