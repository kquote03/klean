<!-- PLAN.md: canonical copy of .hermes/plans/2026-11-08_153740-klean-prototype-1.md (the original planning artifact).
     This copy is authoritative for the next agent. Edit it when scope or decisions change.
     Where the code knowingly differs from this plan, AGENTS.md > Deviations wins. Task status lives in TASKS.md. -->

# Klean — Prototype 1 Implementation Plan

Goal: build a runnable, local-first Android app (Kotlin/Compose/M3 Expressive) whose diet-awareness loop is polished enough for two weeks of daily use, on an architecture that later modules, the Ktor sync backend and the web client can plug into without rewrites.

---

## 0. Current context and assumptions

- `/home/kenan1099/Src/klean` is an **empty directory, not a git repo**. Everything is created from scratch.
- Host is **NixOS**. System has `java` 21 and `adb`, and nothing else: no Android SDK, no Gradle. **All tooling comes from a Nix flake dev shell** (`nix develop`). Every build command in this plan is written as `nix develop -c <cmd>`. Never install tools globally.
- Toolchain baseline, checked on 2026-11-08: AGP 9.4.x, Gradle 9.9.0, Kotlin 2.4.20, Compose Material3 1.5.0-beta02 (has `MaterialExpressiveTheme`), Room 2.8.5, compileSdk/targetSdk 37. Task 0.5 checks every other library version against Maven before the first build.
- The user is in Asia/Dubai (UTC+4, no DST). Store everything in UTC and show it in the device zone.
- **Commit after every task** (the user asked for regular commits). Use Conventional Commits (`feat(diet): …`, `test(shared): …`, `build: …`, `chore: …`). Tag the end of each milestone (`git tag m3`).
- Internal design context (AuDHD traits) may appear in code comments and docs. It must **never** appear in user-facing strings. A unit test enforces this (Task 1.9).

### Decisions this plan takes where the brief is silent (conventional choice, noted)

| Topic | Choice | Why |
|---|---|---|
| applicationId / namespace root | `app.klean` | Placeholder. Change before any store release. |
| minSdk | **28 (Android 9)** | Health Connect's floor is Android 9. M3 Expressive and Glance both need ≤23. API 28 also keeps `NotificationChannel`, `requestPinAppWidget` and adaptive icons. |
| target/compileSdk | 37 | Current. Means edge-to-edge and predictive back are mandatory. |
| JVM target | 17 bytecode, compiled with the Nix JDK 21 | AGP default. No toolchain auto-provisioning on NixOS. |
| Navigation | Navigation Compose 2.9+ with type-safe `@Serializable` routes; `NavigationSuiteScaffold` for bottom bar ↔ rail | The brief asked for Navigation Compose. NavigationSuiteScaffold gives the adaptive layout for free. |
| SQLite | Room with **`BundledSQLiteDriver`** | Same SQLite version on every device. Gives atomic `VACUUM INTO` for backups even on API 28–29, whose platform SQLite is 3.22. The same driver also runs on the JVM for the future backend. |
| HTTP | Ktor client (OkHttp engine) + kotlinx.serialization | The future Ktor backend can reuse the DTOs. No Google SDK is needed for Gemini, because it uses REST. |
| Date/time | `kotlin.time.Instant/Clock` + `kotlinx-datetime` in `:shared` | KMP-safe. |
| Secrets | Android Keystore AES-GCM key; ciphertext kept in DataStore | `EncryptedSharedPreferences` is deprecated. |
| Geofences | `LocationManager.addProximityAlert` (AOSP) | Works without Google Play Services, which fits the sovereignty principle. |
| "Day" boundary | Logical day starts at the **midpoint of the sleep window** (sleep 00:00–08:00 → day starts 04:00) | A 00:30 snack stays on "today" before bed. Zero config. |
| Android Auto Backup | `allowBackup="false"` | Klean owns its backups. Keystore ciphertext can't be restored on another device anyway. |
| Repeat chips | Derived by query, no table | Zero maintenance. Nothing to curate. |
| Universal capture with AI routing | Waits at most 1.5 s, and only for an *offline-capable* classifier; otherwise saves to the default module at once and offers a correction chip | Keeps "never block a log on AI". In P1 only diet exists, so routing skips AI completely. |

No decision marked **Decided** in the brief is changed by this plan. Photos go inside the backup file as BLOBs, so the backup really is one SQLite file.

---

## 1. Architecture in brief

A single-activity Compose app (`:app`) is composed from `:core:*` libraries and one `:feature:*` module per life module. Each feature contributes a `KleanModule` into a Hilt `Set` (the **module registry**). The registry drives navigation, dashboard cards, settings and enable/disable. Platform-free logic lives in a KMP `:shared` module (JVM + Android): models, UUIDv7, sync columns, quiet hours, day windows, prompt planning, format rotation, meal-time learning, gap detection, quick-repeat ranking, capture routing contracts and AI DTOs. It is unit-tested on the JVM, and the future Ktor backend will reuse it. Android glue covers Room, AlarmManager, WorkManager, notifications, Glance, the tile and the NotificationListener. All writes go through repositories that stamp sync columns. After each write, a change dispatcher fans out to the widget, the status notification, the prompt rescheduler and the AI enrichment queue.

---

## 2. Gradle module layout and package structure

```
klean/
├── flake.nix / flake.lock            # Nix dev shell: JDK 21, Android SDK 37, build-tools, platform-tools, emulator
├── settings.gradle.kts
├── build.gradle.kts                  # root: plugin aliases apply false
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/…                  # generated via nix
├── build-logic/convention/           # convention plugins (klean.android.library, .compose, .feature, .hilt)
├── scripts/latest-versions.sh        # prints latest release of each lib from Maven metadata
├── scripts/check-copy.sh             # grep guard for forbidden words (also enforced by a unit test)
├── shared/                           # KMP: jvm() + android library target
│   └── src/commonMain/kotlin/app/klean/shared/
│       ├── id/            UuidV7.kt, NameUuid.kt (expect) 
│       ├── sync/          SyncMeta.kt (Syncable interface)
│       ├── module/        ModuleId.kt
│       ├── time/          TimeExt.kt, QuietHours.kt, DayWindows.kt
│       ├── diet/          DietEnums.kt, GapDetector.kt, QuickRepeatRanker.kt, DeliveryMatcher.kt
│       ├── diet/prompt/   PromptEnums.kt, PromptPlanner.kt, FormatRotator.kt, MealTimeLearner.kt, PromptOutcomeResolver.kt
│       ├── capture/       CaptureContracts.kt, DefaultCaptureRouter.kt
│       └── ai/            AiTask.kt, ParsedEntryDto.kt, AiSchemas.kt
├── core/common/          app.klean.core.common       — ApplicationScope, dispatchers, Clock/zone providers
├── core/design/          app.klean.core.design       — tokens, theme, Cantarell, components
├── core/data/            app.klean.core.data         — Room DB, entities, DAOs, SyncStamper, settings, DataStore, SecretStore, PhotoStore, backup
├── core/ui/              app.klean.core.ui           — KleanModule, ModuleRegistry, route contracts, shared screen scaffolds
├── core/notifications/   app.klean.core.notifications— channels, QuietHoursService, AlarmScheduler, RescheduleReceiver, permission state
├── core/ai/              app.klean.core.ai           — AiProvider, AiGateway, providers (Gemini, OpenAI-compat, on-device), config UI models
├── core/capture/         app.klean.core.capture      — UniversalCaptureService, CaptureActivity, CaptureSheet, voice input, QS tile
├── feature/diet/         app.klean.feature.diet      — everything diet (see packages below)
├── feature/onboarding/   app.klean.feature.onboarding
├── feature/settings/     app.klean.feature.settings  — global settings, AI provider screens, backup screen, permissions screen
└── app/                  app.klean                   — KleanApp, MainActivity, KleanNavHost, Hilt aggregation
```

`:feature:diet` packages:

```
app.klean.feature.diet
├── DietModule.kt            (KleanModule impl + Hilt bindings)
├── data/                    DietRepository, DietSettings, DietChangeDispatcher, listeners
├── capture/                 DietCaptureHandler
├── ui/today/                DietScreen, DietViewModel, EntryList (entries + gap markers)
├── ui/entry/                FoodEntryScreen (edit/backdate/photo/AI items), VM
├── ui/history/              DietHistoryScreen (month), DietDayScreen (pager), VMs
├── ui/settings/             DietSettingsScreen, DeliveryAppsScreen, GeofencesScreen, GeofenceEditScreen
├── ui/dashboard/            DietDashboardCard
├── notification/            DietStatusNotifier, DietReplyReceiver, StatusRepostReceiver, PromptNotifier, PromptDismissReceiver, PromptPhrasings
├── prompt/                  DietPromptEngine, PromptAlarmReceiver, DayRolloverReceiver
├── widget/                  DietWidget, DietWidgetReceiver, DietWidgetUpdater
├── unlock/                  ScreenStateMonitor, UnlockSheetActivity
├── delivery/                DeliveryListenerService, DeliveryPrompter
├── geofence/                GeofenceRegistrar, GeofenceExitReceiver
├── ai/                      FoodEnrichmentWorker, EnrichmentEnqueuer, EnrichmentSweepWorker
└── patterns/                README.md only (placeholder for later neutral patterns)
```

Dependency rules (enforced by review). Features depend on core; core never depends on a feature; `:app` depends on everything. `:shared` depends on nothing Android.

```
:app → :feature:* , :core:*
:feature:* → :core:ui, :core:design, :core:data, :core:notifications, :core:ai, :core:capture, :core:common, :shared
:core:capture → :core:ui, :core:design, :core:data, :core:ai, :core:common, :shared
:core:ai → :core:data, :core:common, :shared
:core:notifications → :core:data, :core:common, :shared
:core:ui → :core:design, :core:data, :shared
:core:data → :core:common, :shared
```

Future modules each add `:feature:tasks`, `:feature:reminders`, `:feature:calendar`, `:feature:contacts`, `:feature:workout`, `:feature:sleep`, `:wear`. Each contributes a `KleanModule`, a `CaptureHandler`, `Reschedulable`s and DAOs. The DAOs go in the same `KleanDatabase`, added via migration; `:core:data` hosts all entities so one DB file = one backup.

---

## 3. Room schema (Prototype 1)

Sync rules applied to every syncable table:
- `id TEXT PRIMARY KEY`: a client UUIDv7 string, except `setting`, whose id is a name-based UUID of its key so two devices agree on it.
- Embedded `SyncColumns`: `created_at`, `updated_at` (UTC epoch ms), `device_id`, `deleted` (tombstone), `change_seq`. `change_seq` comes from a local monotonic counter, so a future sync engine sends rows where `change_seq > lastSentSeq`.
- **No hard deletes** of syncable rows. **No SQL foreign keys**, because sync can deliver children before parents. Indexed reference columns are used instead.
- Enums are stored as their `name` (Room native enum support).

| Table | Columns (besides id + SyncColumns) | Indices |
|---|---|---|
| `food_entry` | `raw_text TEXT NOT NULL`, `eaten_at INTEGER` (UTC ms), `eaten_at_zone TEXT` (IANA zone at capture), `photo_file TEXT?` (file name relative to `filesDir/photos/`), `source TEXT` (CaptureSource), `via_voice INTEGER`, `meal_type TEXT?` (AI), `meal_type_provider TEXT?`, `enrichment TEXT` (EnrichmentStatus) | `eaten_at`, `created_at`, `change_seq` |
| `food_item` | `entry_id TEXT`, `position INTEGER`, `name TEXT`, `portion TEXT?`, `calories INTEGER?`, `provider_id TEXT` (`gemini` / `openai_compat` / `on_device` / `user`), `model TEXT?`, `state TEXT` (ACCEPTED default / EDITED / REJECTED) | `entry_id`, `change_seq` |
| `prompt_log` | `module_id TEXT`, `trigger TEXT` (PromptTrigger), `trigger_detail TEXT?` (delivery package or geofence id, never notification content), `format TEXT` (PromptFormat), `phrasing_id TEXT`, `scheduled_for INTEGER`, `sent_at INTEGER`, `outcome TEXT` (PENDING/LOGGED/DISMISSED/IGNORED), `outcome_at INTEGER?` | `sent_at`, `outcome`, `change_seq` |
| `setting` | `key TEXT UNIQUE`, `value_json TEXT` | `key` unique, `change_seq` |
| `geofence` | `label TEXT`, `latitude REAL`, `longitude REAL`, `radius_m INTEGER`, `enabled INTEGER` | `change_seq` |
| `change_counter` (local, not synced) | `id INTEGER PK` (=1), `value INTEGER` | — |

Syncable settings keys (in `setting`): `core.quietHours` (`{"startMinute":0,"endMinute":480}`), `core.modules.disabled` (`["…"]`), `diet.promptsEnabled`, `diet.cadenceHours` (6), `diet.learnMealTimes` (true), `diet.unlockSheet` (true), `diet.statusNotification` (true), `diet.showCalories` (false), `diet.gapThresholdMinutes` (240), `diet.deliveryApps` (list of package names), `diet.deliveryMatchWords` (list).

Device-local (DataStore `device_prefs`): `deviceId`, `onboardingDone`, `dynamicColor`, `lastScreenOffAt`, `nextPromptAt`, `highlightPromptId`, `autoBackupEnabled`, `autoBackupTreeUri`, `autoBackupKeep` (7), `backupIncludeSecrets`, `aiProviders` (JSON configs without keys), `aiTaskAssignment` (JSON), `deliveryOffer` (NOT_OFFERED / SNOOZED_UNTIL:<ms> / DECLINED / ACCEPTED), `mainPhone` (future). Secrets (DataStore `secrets`, Keystore-encrypted): `gemini.apiKey`, `openai.apiKey`.

The backup file adds these tables only inside the exported copy: `backup_meta(key, value)`, `backup_photo(file_name, bytes BLOB)`, `backup_device_pref(key, value)`, and `backup_secret(key, value)` when the user opts in.

---

## 4. Screens and navigation graph

Activities:
- `MainActivity` (`:app`): hosts `KleanNavHost` inside `NavigationSuiteScaffold`.
- `CaptureActivity` (`:core:capture`): translucent, `excludeFromRecents`, own `taskAffinity`. Shows `CaptureSheet` as a bottom sheet. Opened by the QS tile, the widget and the "Capture…" action on the notification, so the full app never opens. The extra `target_module` (null = universal) and `start_voice` control it.
- `UnlockSheetActivity` (`:feature:diet`): translucent. Shows today's list + a capture field + one "Done" button. Opened by the unlock full-screen intent.

Top-level destinations (registry-driven, in this order): **Today** (always), **one item per enabled module** (P1: **Diet**), **Settings** (always). If a module is disabled, its item, routes, dashboard card and notifications disappear.

```
NavHost(start = if onboardingDone TodayRoute else OnboardingGraph)
├── OnboardingGraph
│   ├── WelcomeStep → ModulesStep → SleepWindowStep → DietPromptsStep
│   ├── PermissionsStep (notifications, exact alarms, battery, full-screen intent)
│   ├── WidgetStep (requestPinAppWidget + instructions) → AiStep (optional) → DoneStep → TodayRoute (popUpTo graph inclusive)
├── TodayRoute                 dashboard: universal capture bar + registry cards in DashboardSlot order
├── [diet graph — registered by DietModule.graph()]
│   ├── DietRoute              capture field pinned at top, today's entries + gap markers, delivery offer card
│   ├── FoodEntryRoute(entryId) edit text, time/date (backdate), photo, AI items (edit/reject), delete
│   ├── DietHistoryRoute       month calendar with neutral per-day counts
│   ├── DietDayRoute(isoDate)  HorizontalPager over days, same list component as Today
│   ├── DietSettingsRoute      prompts, unlock sheet, status notification, calories, gap threshold, delivery, geofences, "next check-in around …"
│   ├── DeliveryAppsRoute      editable package list + match words
│   ├── GeofencesRoute → GeofenceEditRoute(id?)  "use current location" + radius slider (no map)
└── SettingsRoute
    ├── per-module entries (from registry.settingsRoute)
    ├── ModulesSettingsRoute   toggle each registered module
    ├── QuietHoursRoute        sleep window
    ├── AppearanceRoute        dynamic colour toggle (theme follows system)
    ├── AiProvidersRoute → AiProviderRoute(providerId); task assignment section on AiProvidersRoute
    ├── BackupRoute            export, restore, auto-backup folder + keep N, include-secrets toggle
    ├── PermissionsRoute       live status of each permission with a fix button
    └── AboutRoute             version, licences (Cantarell OFL)
```

Deep links (for notification taps): `klean://today`, `klean://diet`, `klean://diet/entry/{entryId}`, `klean://settings/permissions`.

---

## 5. Background work design

### 5.1 Write → fan-out
`DietRepository` writes inside `db.transact {}` and then calls `DietChangeDispatcher.dispatch(change)`. The dispatcher runs every `DietChangeListener` from the Hilt multibinding concurrently and **awaits** them, catching and logging each failure. Listeners:
1. `DietWidgetUpdater` → `DietWidget().updateAll(context)` ("updates within seconds").
2. `DietStatusNotifier.refresh()`.
3. `PromptOutcomeMarker` → marks PENDING prompts LOGGED if the entry was created within 60 min of `sent_at`, cancels the active prompt notification and clears `highlightPromptId`.
4. `DietPromptEngine.reschedule()`.
5. `EnrichmentEnqueuer` → enqueues AI work if a provider is assigned to ENTRY_PARSE.

### 5.2 Persistent status notification (no foreground service)
- Channel `diet_status`, `IMPORTANCE_LOW` (no sound, no peek). A regular notification with `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setSilent(true)`, category `STATUS`.
- **Why not a foreground service:** nothing runs continuously, and Android 14+ requires an FGS type. "Show a status line" is not a valid type, so an FGS here would be policy abuse.
- On Android 14+ users *can* swipe ongoing non-FGS notifications away. They still survive "Clear all" and can't be dismissed on the lock screen. To make it dismiss-resistant, we set a `deleteIntent` → `StatusRepostReceiver`, which re-posts silently. The user gets a real off switch: Diet settings → "Show status notification" (setting) and the system channel toggle (respected). The notification is also re-posted on every log, prompt, day rollover, boot and app update.
- Lock screen: `VISIBILITY_PRIVATE` plus a `publicVersion` ("Klean · 3 entries today"). The system shows the full version (with the last item) only if the user allows sensitive lock-screen content. This respects the privacy setting.
- Actions: **Log food** = `RemoteInput` on an explicit broadcast `PendingIntent` (`FLAG_MUTABLE`, which RemoteInput requires), `setAuthenticationRequired(false)`, `setShowsUserInterface(false)`. **Capture…** opens `CaptureActivity`. Tapping the content opens `klean://diet`.
- `DietReplyReceiver` uses `goAsync()`, inserts the entry via the repository (ms), awaits the dispatcher, then re-posts the notification ("Saved: shawarma and fries"), which also clears the reply spinner. There is an 8 s hard timeout. No activity is launched.

### 5.3 Prompts (AlarmManager)
- `DietPromptEngine.reschedule()`:
  1. Resolve PENDING outcomes (`PromptOutcomeResolver`).
  2. Build a `PromptContext` from settings, the last prompt, the last entry and the learned slots (`MealTimeLearner` over the last 21 days).
  3. `PromptPlanner.next()`.
  4. `AlarmScheduler.schedule(at, PromptAlarmReceiver PI)` with a single fixed request code, so there is only ever one pending prompt alarm.
  5. Store `nextPromptAt` in DataStore so settings can show "next check-in around 14:30".
- `PromptAlarmReceiver` → `DietPromptEngine.onAlarm()`. If diet or prompts are disabled, it stops. If it is in quiet hours or an entry was logged in the last 90 min, it reschedules only. Otherwise:
  1. `FormatRotator.nextFormat(history)` and `nextPhrasing()`.
  2. Insert a PENDING `prompt_log` row.
  3. Mark any older PENDING row IGNORED.
  4. Post by format: STANDARD → channel `diet_prompt` (DEFAULT); HEADS_UP → `diet_prompt_heads_up` (HIGH); SILENT_VIBRATE → `diet_prompt_vibrate` (DEFAULT, no sound, vibration `[0,80,120,80]`); WIDGET_HIGHLIGHT → no notification, sets `highlightPromptId` and updates the widget and status text.
  5. Reschedule.
- Every prompt notification has the same inline reply (source `PROMPT_REPLY`) plus `deleteIntent` → `PromptDismissReceiver` (outcome DISMISSED).
- **Frequency never depends on outcomes.** The planner has no outcome input, and a unit test pins this down.
- **Exact alarms:** declare `SCHEDULE_EXACT_ALARM` (API 31+). On 33+ it is denied by default and the user grants it through onboarding/permissions (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). On API 28–30 no permission is needed. If it's denied, fall back to `setAndAllowWhileIdle` (inexact). That is fine for "about every 6 hours". `USE_EXACT_ALARM` is reserved for the future alarms module, which is what Play policy allows it for.
- `RescheduleReceiver` (`:core:notifications`) handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and fans out to `Set<Reschedulable>`: diet prompts, day rollover, status notification, geofences, auto-backup.
- Day rollover: an inexact alarm at the next logical-day start refreshes the widget and the status notification.

### 5.4 Widget (Glance)
- `DietWidget` reads from the repository inside `provideGlance` and collects a Flow in `provideContent`. `SizeMode.Responsive(setOf(DpSize(110.dp, 48.dp), DpSize(200.dp, 120.dp), DpSize(260.dp, 260.dp)))`.
  - Compact: "N today · last item".
  - Larger: a "What did you eat?" pill, mic button and newest-first `LazyColumn`.
- Clicks: `actionStartActivity(CaptureActivity, target=diet [, start_voice=true])`.
- When `highlightPromptId` is set, the prompt pill shows the phrasing on `tertiaryContainer`.
- When diet is disabled, it shows only "Klean" + "Open".
- Updates come from the dispatcher, day rollover, module toggle and theme change.

### 5.5 AI enrichment queue (WorkManager)
- `EnrichmentEnqueuer` → unique work `ai-enrich-<entryId>` (REPLACE), `FoodEnrichmentWorker`. Backoff EXPONENTIAL 30 s. Constraint `CONNECTED` if the assigned provider is remote, none if on-device.
- Worker:
  1. Load the entry; if it's deleted, return success.
  2. Call `AiGateway.run(ENTRY_PARSE, request)`. The result is `Success`, `NoProvider` (mark NO_PROVIDER, success), `Unavailable` (retry until `runAttemptCount >= 8`, then FAILED) or `Failed` (FAILED).
  3. On success, check the entry's `updated_at` is unchanged since enqueue (otherwise re-enqueue). Tombstone previous ACCEPTED items from AI, insert the new items, set `meal_type`, set `enrichment=DONE`. **`raw_text` is never touched.**
- If CALORIE_ESTIMATE is assigned to a different provider than ENTRY_PARSE, a second call fills `calories` only.
- `EnrichmentSweepWorker`: runs when provider config changes, plus a 12 h periodic run. It enqueues entries from the last 14 days in `NOT_REQUESTED/NO_PROVIDER/FAILED`.
- `AiGateway` enforces: provider explicitly enabled (cloud consent), 45 s timeout, image only if the provider supports images *and* the user enabled photo sending, otherwise text only.

### 5.6 Other background pieces
- Auto backup: `PeriodicWorkRequest` 24 h, `AutoBackupWorker`, writes to a SAF tree and keeps the last N.
- Unlock sheet: see §6.3.
- Delivery listener: `NotificationListenerService` that runs only once the user grants access.
- Geofences: proximity-alert `PendingIntent` → `GeofenceExitReceiver`. Re-registered by `Reschedulable`.

---

## 6. Platform decisions that need explanation

### 6.1 Inline reply on the lock screen
RemoteInput actions work on the lock screen when the user allows notification content there. `setAuthenticationRequired(false)` stops Android 12+ from forcing an unlock. The save path is broadcast → receiver → Room, with no UI.

### 6.2 Delivery listener privacy
`onNotificationPosted`:
1. Return immediately unless the package is in the allowlist.
2. Join title/text/bigText into a local variable and pass it to `DeliveryMatcher.isDelivered()`.
3. Discard the text. Persist only a `prompt_log` row (trigger DELIVERY, `trigger_detail` = package name).

Never log the text. Debounce: one prompt per package per 45 min. Quiet hours suppress it (the global rule).

### 6.3 "In your face" unlock sheet — least invasive option
- In Android 10+, background activity launches are blocked. `SYSTEM_ALERT_WINDOW` would bypass that but is invasive, so it is **not used**.
- `ScreenStateMonitor`, registered dynamically in `KleanApp.onCreate`, listens for `SCREEN_OFF` and `SCREEN_ON`:
  - `SCREEN_OFF` stores `lastScreenOffAt`.
  - At process start, if `PowerManager.isInteractive == false`, set `lastScreenOffAt = now` as a conservative guess.
- On `SCREEN_ON` while the keyguard is locked, the sheet fires if all of these hold: `now - lastScreenOffAt ≥ 3 h`, not in quiet hours, diet enabled, `diet.unlockSheet` on, and no sheet already shown in the last 3 h. It posts a notification on channel `unlock_sheet` (HIGH) with a **full-screen intent** to `UnlockSheetActivity`.
- The activity does *not* set `showWhenLocked`, so it appears right after the user unlocks: the "first unlock" sheet. One tap on "Done" dismisses it, and it cancels its notification in `onResume`.
- On Android 14+, `USE_FULL_SCREEN_INTENT` may not be granted. Check `NotificationManager.canUseFullScreenIntent()` and offer `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` in onboarding/permissions. Fallback: the same notification as a heads-up "Today so far: 3 entries" whose tap opens the sheet, plus a widget highlight.
- Known limit: a dynamic receiver only works while the process is alive. Prompt alarms, widget updates and the tile revive the process regularly, and battery-exemption onboarding helps. This is accepted and documented in Risks.
- Milestone 10 starts with an on-device spike (Task 10.1) to confirm FSI + not-`showWhenLocked` behaviour on the user's phone.

### 6.4 Voice
- `SpeechRecognizer.createOnDeviceSpeechRecognizer` when `isOnDeviceRecognitionAvailable` (API 31+). Otherwise `createSpeechRecognizer` with `EXTRA_PREFER_OFFLINE=true`. Otherwise the `ACTION_RECOGNIZE_SPEECH` activity.
- `RECORD_AUDIO` is requested just in time. `<queries>` declares the `RecognitionService` intent.
- The final result fills the field and **auto-saves** (single action), with an "Undo" chip for 5 s.

### 6.5 Photos
- Camera via `TakePicture` into a FileProvider URI. Gallery via `PickVisualMedia`.
- `PhotoStore.import()` downsizes to a 1600 px long edge, JPEG q85, applies EXIF rotation, then strips EXIF (privacy). It writes `filesDir/photos/<uuid>.jpg`. Photos stay optional.

---

## 7. Conventions for every task

- Shell: `cd /home/kenan1099/Src/klean`. Every Gradle command runs as `nix develop -c ./gradlew <task>`.
- TDD for logic: write the test → run it and **see it fail** (compile failure counts) → implement → run and see it pass → commit. UI tasks: ViewModel tests with fakes + Turbine where there is logic, and a manual check on the phone with exact steps.
- Unit tests: `nix develop -c ./gradlew :shared:jvmTest` (pure logic), `nix develop -c ./gradlew testDebugUnitTest` (Android modules, Robolectric). Instrumented: `nix develop -c ./gradlew connectedDebugAndroidTest` with the phone connected (`adb devices` shows it).
- Every task ends with `git add -A && git commit -m "<type(scope): summary>"`.
- Every user-facing string goes in `res/values/strings.xml` of the owning module. Calm tone, no exclamation marks, no "missed/failed/streak/behind" wording, no red except real errors.
- Inject `Clock` and a `ZoneProvider` everywhere time matters. Never call `System.currentTimeMillis()` in logic.

---

## 8. Milestones and tasks

Every milestone ends with a **runnable build** and `git tag mN`. End-of-milestone check, the same each time:
```
nix develop -c ./gradlew assembleDebug :shared:jvmTest testDebugUnitTest
# expected: BUILD SUCCESSFUL
nix develop -c ./gradlew :app:installDebug   # phone connected
```

---

### M0 — Tooling and empty runnable app

**Task 0.1 — git init + .gitignore**
Create `.gitignore`:
```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
captures/
.externalNativeBuild/
.cxx/
result
.direnv/
```
Run: `git init -b main && git add .gitignore .hermes/plans && git commit -m "chore: init repo with plan"` → expected `[main (root-commit) …]`.

**Task 0.2 — Nix flake dev shell**
Create `flake.nix`:
```nix
{
  description = "Klean dev shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
      buildTools = "36.1.0"; # use the newest build-tools nixpkgs offers; must be >= AGP's requirement
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37" "36" ];
        buildToolsVersions = [ buildTools ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeNDK = false;
      };
      sdk = android.androidsdk;
      sdkRoot = "${sdk}/libexec/android-sdk";
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ sdk pkgs.jdk21 pkgs.git pkgs.curl pkgs.sqlite ];
        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk21.home;
        # Maven's aapt2 is dynamically linked and does not run on NixOS:
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildTools}/aapt2";
        shellHook = ''echo "klean shell: $(java -version 2>&1 | head -1)"'';
      };
    };
}
```
Flakes only see git-tracked files: `git add flake.nix`.
Verify: `nix develop -c bash -c 'ls $ANDROID_HOME/platforms $ANDROID_HOME/build-tools && java -version'` → lists `android-36 android-37`, `36.1.0` and openjdk 21. If platform 37 or that build-tools version isn't in nixpkgs, change it to the newest listed by `nix eval --json nixpkgs#androidenv.androidPkgs.platforms --apply builtins.attrNames` and update compileSdk to match. Commit `build: add nix dev shell` (include `flake.lock`).

**Task 0.3 — Gradle wrapper**
`nix develop -c nix shell nixpkgs#gradle -c gradle wrapper --gradle-version 9.9.0 --distribution-type bin`
Verify: `nix develop -c ./gradlew --version` → `Gradle 9.9.0`. Commit `build: add gradle wrapper 9.9.0`.

**Task 0.4 — version-check script**
Create `scripts/latest-versions.sh` (executable):
```bash
#!/usr/bin/env bash
# Prints the latest release version for each coordinate. Google Maven first, then Maven Central.
set -euo pipefail
coords=(
  com.android.tools.build:gradle
  org.jetbrains.kotlin:kotlin-gradle-plugin
  com.google.devtools.ksp:symbol-processing-gradle-plugin
  androidx.compose:compose-bom
  androidx.compose.material3:material3
  androidx.compose.material3:material3-adaptive-navigation-suite
  androidx.activity:activity-compose
  androidx.navigation:navigation-compose
  androidx.lifecycle:lifecycle-runtime-compose
  androidx.room:room-runtime
  androidx.sqlite:sqlite-bundled
  androidx.work:work-runtime-ktx
  androidx.glance:glance-appwidget
  androidx.datastore:datastore-preferences
  androidx.core:core-ktx
  androidx.hilt:hilt-navigation-compose
  androidx.hilt:hilt-work
  com.google.dagger:hilt-android
  org.jetbrains.kotlinx:kotlinx-coroutines-core
  org.jetbrains.kotlinx:kotlinx-serialization-json
  org.jetbrains.kotlinx:kotlinx-datetime
  io.ktor:ktor-client-okhttp
  org.robolectric:robolectric
  app.cash.turbine:turbine
  androidx.test.ext:junit
  androidx.test:runner
  androidx.exifinterface:exifinterface
  androidx.documentfile:documentfile
  com.google.mediapipe:tasks-genai
)
for c in "${coords[@]}"; do
  g=${c%%:*}; a=${c##*:}; p=${g//.//}
  v=$(curl -fs "https://dl.google.com/android/maven2/$p/$a/maven-metadata.xml" 2>/dev/null \
      || curl -fs "https://repo1.maven.org/maven2/$p/$a/maven-metadata.xml")
  echo "$c = $(grep -o '<release>[^<]*' <<<"$v" | sed 's/<release>//')"
done
```
Run `nix develop -c scripts/latest-versions.sh` and keep the output for Task 0.5. For `material3`, pin the newest 1.5.x even if it's beta (Expressive APIs). For KSP, pick the release whose compatibility list includes Kotlin 2.4.20. Commit `chore: add version check script`.

**Task 0.5 — version catalog**
Create `gradle/libs.versions.toml`. Replace each `<…>` with the output of 0.4. The numbers already filled in were verified on 2026-11-08.
```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
ksp = "<ksp>"
composeBom = "<compose-bom>"
material3 = "1.5.0-beta02"
activity = "<activity-compose>"
navigation = "<navigation-compose>"
lifecycle = "<lifecycle>"
room = "2.8.5"
sqlite = "<sqlite-bundled>"
work = "<work>"
glance = "<glance>"
datastore = "<datastore>"
coreKtx = "<core-ktx>"
hilt = "<hilt-android>"
hiltAndroidx = "<androidx.hilt>"
coroutines = "<coroutines>"
serialization = "<serialization>"
datetime = "<datetime>"
ktor = "<ktor>"
robolectric = "<robolectric>"
turbine = "<turbine>"
androidxJunit = "<ext-junit>"
androidxTestRunner = "<test-runner>"
exif = "<exifinterface>"
documentfile = "<documentfile>"
junit = "4.13.2"

[libraries]
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
compose-material3-navsuite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltAndroidx" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltAndroidx" }
hilt-androidx-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltAndroidx" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exif" }
documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```
Commit `build: add version catalog`.

**Task 0.6 — root build files**
`settings.gradle.kts`:
```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "klean"
include(
    ":app", ":shared",
    ":core:common", ":core:design", ":core:data", ":core:ui",
    ":core:notifications", ":core:ai", ":core:capture",
    ":feature:diet", ":feature:onboarding", ":feature:settings",
)
```
`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
```
`gradle.properties`:
```
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```
Commit `build: root gradle setup`.

**Task 0.7 — convention plugins**
`build-logic/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
```
`build-logic/convention/build.gradle.kts`:
```kotlin
plugins { `kotlin-dsl` }
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}
gradlePlugin {
    plugins {
        register("androidLibrary") { id = "klean.android.library"; implementationClass = "AndroidLibraryConventionPlugin" }
        register("androidCompose") { id = "klean.android.compose"; implementationClass = "AndroidComposeConventionPlugin" }
        register("hilt") { id = "klean.hilt"; implementationClass = "HiltConventionPlugin" }
        register("androidFeature") { id = "klean.android.feature"; implementationClass = "AndroidFeatureConventionPlugin" }
    }
}
```
`build-logic/convention/src/main/kotlin/KleanSdk.kt`:
```kotlin
object KleanSdk { const val COMPILE = 37; const val MIN = 28; const val TARGET = 37 }
```
`build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")   // AGP 9: Kotlin is built in, no kotlin-android plugin
        extensions.configure<LibraryExtension> {
            namespace = "app.klean." + path.removePrefix(":").replace(':', '.')
            compileSdk = KleanSdk.COMPILE
            defaultConfig {
                minSdk = KleanSdk.MIN
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                optIn.add("kotlin.time.ExperimentalTime")
            }
        }
    }
}
```
`AndroidComposeConventionPlugin.kt`: applies `org.jetbrains.kotlin.plugin.compose`, sets `buildFeatures { compose = true }`, and adds `platform(libs.compose.bom)` plus `compose-ui`, `compose-material3`, `compose-ui-tooling-preview` to `implementation` and `compose-ui-tooling` to `debugImplementation`. Read the catalog via `extensions.getByType<VersionCatalogsExtension>().named("libs")`.
`HiltConventionPlugin.kt`: applies `com.google.devtools.ksp` and `com.google.dagger.hilt.android`; adds `hilt-android` (implementation) and `hilt-compiler` (ksp).
`AndroidFeatureConventionPlugin.kt`: applies `klean.android.library`, `klean.android.compose`, `klean.hilt`, `org.jetbrains.kotlin.plugin.serialization`. Adds project deps `:core:ui :core:design :core:data :core:common :shared` plus `navigation-compose`, `hilt-navigation-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `serialization-json`. Test deps: `junit`, `robolectric`, `turbine`, `coroutines-test`.
If an AGP 9.4 DSL type has moved (compile error in build-logic), use the type the error message suggests. Do not add the old `kotlin-android` plugin.
Commit `build: convention plugins`.

**Task 0.8 — module skeletons**
For each `core/*` and `feature/*` module, create `build.gradle.kts` (`plugins { id("klean.android.library") }`, or `klean.android.feature` for features) and an empty `src/main/AndroidManifest.xml` containing `<manifest />`. Create `:shared` exactly as in Task 2.1 below, with an empty `commonMain` placeholder file `shared/src/commonMain/kotlin/app/klean/shared/Klean.kt` holding `package app.klean.shared\nobject Klean { const val NAME = "Klean" }`.
Create `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("klean.hilt")
}
android {
    namespace = "app.klean"
    compileSdk = KleanSdk.COMPILE
    defaultConfig {
        applicationId = "app.klean"
        minSdk = KleanSdk.MIN
        targetSdk = KleanSdk.TARGET
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.klean.HiltTestRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":shared"))
    listOf("common", "design", "data", "ui", "notifications", "ai", "capture").forEach { implementation(project(":core:$it")) }
    listOf("diet", "onboarding", "settings").forEach { implementation(project(":feature:$it")) }
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3); implementation(libs.compose.material3.navsuite)
    implementation(libs.activity.compose); implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose); implementation(libs.hilt.work); ksp(libs.hilt.androidx.compiler)
    implementation(libs.work.runtime)
    androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing); kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.coroutines.test)
}
```
(`KleanSdk` is visible because the build-logic classpath is on the build script classpath. If not, inline the numbers.)
`app/src/main/kotlin/app/klean/KleanApp.kt`: `@HiltAndroidApp class KleanApp : Application()`.
`app/src/main/kotlin/app/klean/MainActivity.kt`: `@AndroidEntryPoint class MainActivity : ComponentActivity()` with `enableEdgeToEdge(); setContent { Text("Klean") }`.
Manifest: `application android:name=".KleanApp" android:allowBackup="false" android:label="@string/app_name"`, MainActivity exported with the LAUNCHER filter.
Verify: `nix develop -c ./gradlew assembleDebug` → BUILD SUCCESSFUL. Then `nix develop -c ./gradlew :app:installDebug && adb shell am start -n app.klean.debug/app.klean.MainActivity` → the phone shows "Klean". Commit `feat: empty runnable app skeleton`, then `git tag m0`.

---

### M1 — Design system, app shell, module registry, dashboard slots, settings skeleton

**Task 1.1 — Cantarell font**
```
mkdir -p core/design/src/main/res/font
for f in Regular Bold Italic BoldItalic; do
  curl -fL -o core/design/src/main/res/font/cantarell_$(echo $f | tr A-Z a-z).ttf \
    https://github.com/google/fonts/raw/main/ofl/cantarell/Cantarell-$f.ttf; done
curl -fL -o core/design/src/main/assets/licenses/cantarell-OFL.txt --create-dirs https://github.com/google/fonts/raw/main/ofl/cantarell/OFL.txt
```
(Run inside `nix develop`.) The file names become `cantarell_regular.ttf`, `cantarell_bold.ttf`, `cantarell_italic.ttf`, `cantarell_bolditalic.ttf`. Verify: `file core/design/src/main/res/font/*.ttf` → "TrueType Font data". Commit `feat(design): bundle Cantarell`.

**Task 1.2 — colour tokens (source of truth JSON + test)**
Create `core/design/tokens/klean-color-tokens.json`. The web client will mirror this file. It holds `light` and `dark` objects with exactly these keys and values:

| token | light | dark |
|---|---|---|
| primary | #6B5CA5 | #CDBDFF |
| onPrimary | #FFFFFF | #35297A |
| primaryContainer | #E6DEFF | #4C3F8B |
| onPrimaryContainer | #241A4F | #E6DEFF |
| secondary | #4F7A6A | #B8D3C6 |
| onSecondary | #FFFFFF | #213F34 |
| secondaryContainer | #D4EFE3 | #37564A |
| onSecondaryContainer | #0B2A20 | #D4EFE3 |
| tertiary | #9A5B48 | #F5B9A4 |
| onTertiary | #FFFFFF | #55281A |
| tertiaryContainer | #FFDCCF | #723E2E |
| onTertiaryContainer | #3A170B | #FFDCCF |
| background | #FCF9FF | #141318 |
| onBackground | #1C1B20 | #E6E1E9 |
| surface | #FCF9FF | #141318 |
| onSurface | #1C1B20 | #E6E1E9 |
| surfaceVariant | #E7E1EC | #49454F |
| onSurfaceVariant | #49454F | #CAC4D0 |
| surfaceContainerLowest | #FFFFFF | #0F0E13 |
| surfaceContainerLow | #F7F2FA | #1C1B20 |
| surfaceContainer | #F2EDF6 | #201F25 |
| surfaceContainerHigh | #ECE7F1 | #2B292F |
| surfaceContainerHighest | #E6E1EB | #36343A |
| surfaceDim | #DDD8E1 | #141318 |
| surfaceBright | #FCF9FF | #3A383E |
| outline | #79747E | #948F99 |
| outlineVariant | #CAC4D0 | #49454F |
| inverseSurface | #313036 | #E6E1E9 |
| inverseOnSurface | #F4EFF7 | #313036 |
| inversePrimary | #CDBDFF | #6B5CA5 |
| error | #BA1A1A | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #FFDAD6 | #93000A |
| onErrorContainer | #410002 | #FFDAD6 |
| scrim | #000000 | #000000 |

Write the failing test `core/design/src/test/kotlin/app/klean/core/design/ColorTokensTest.kt`. It parses the JSON (kotlinx.serialization `Json.parseToJsonElement(File("tokens/klean-color-tokens.json").readText())`; the working dir for module unit tests is the module dir). For every key it asserts `KleanColors.light[key] == Color(parseHex(value))` and the same for dark. Also add `core/design/build.gradle.kts` with `klean.android.library`, `klean.android.compose`, the serialization plugin and test deps `junit`, `serialization-json`.
Run `nix develop -c ./gradlew :core:design:testDebugUnitTest` → FAIL (unresolved `KleanColors`).
Implement `core/design/src/main/kotlin/app/klean/core/design/theme/KleanColors.kt`. It contains `object KleanColors { val light: Map<String, Color>; val dark: Map<String, Color>; val lightScheme: ColorScheme; val darkScheme: ColorScheme }`, with schemes built via `lightColorScheme(primary = light.getValue("primary"), …)` for all 35 keys. Run the test → PASS. Commit `feat(design): pastel colour tokens mirrored from JSON`.

**Task 1.3 — typography + theme**
`core/design/src/main/kotlin/app/klean/core/design/theme/Type.kt`:
```kotlin
val Cantarell = FontFamily(
    Font(R.font.cantarell_regular, FontWeight.Normal),
    Font(R.font.cantarell_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.cantarell_bold, FontWeight.Bold),
    Font(R.font.cantarell_bolditalic, FontWeight.Bold, FontStyle.Italic),
)
private val base = Typography()
val KleanTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Cantarell),
    displayMedium = base.displayMedium.copy(fontFamily = Cantarell),
    displaySmall = base.displaySmall.copy(fontFamily = Cantarell),
    headlineLarge = base.headlineLarge.copy(fontFamily = Cantarell),
    headlineMedium = base.headlineMedium.copy(fontFamily = Cantarell),
    headlineSmall = base.headlineSmall.copy(fontFamily = Cantarell),
    titleLarge = base.titleLarge.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Cantarell),
    bodyMedium = base.bodyMedium.copy(fontFamily = Cantarell),
    bodySmall = base.bodySmall.copy(fontFamily = Cantarell),
    labelLarge = base.labelLarge.copy(fontFamily = Cantarell, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Cantarell),
    labelSmall = base.labelSmall.copy(fontFamily = Cantarell),
)
```
(The weights map 500/600 → Bold because Cantarell ships only 400 and 700.)
`Theme.kt`:
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KleanTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> KleanColors.darkScheme
        else -> KleanColors.lightScheme
    }
    MaterialExpressiveTheme(colorScheme = scheme, typography = KleanTypography, motionScheme = MotionScheme.expressive(), content = content)
}
```
Also add `res/values/themes.xml` (`Theme.Klean` parent `android:Theme.Material.NoActionBar`, plus `Theme.Klean.Translucent` with `windowIsTranslucent=true`, transparent background, `windowNoTitle`). Verify `:core:design:assembleDebug` passes. Commit `feat(design): Klean M3 Expressive theme`.

**Task 1.4 — shared components**
Create these in `core/design/src/main/kotlin/app/klean/core/design/component/`:
- `CaptureField(value, onValueChange, onSubmit, onMic, onPhoto?, placeholder, modifier)`. An `OutlinedTextField`, min height 56 dp, `imeAction=Send`, trailing mic `IconButton` (48 dp, contentDescription "Speak") and a send `FilledIconButton` (contentDescription "Save"). It must work with one hand at the bottom of the screen.
- `EntryRow(time: String, text: String, meta: (@Composable () -> Unit)?, onClick)`: 56 dp min, time in `labelLarge` on `onSurfaceVariant`.
- `GapMarker(label: String, actionLabel: String, onAdd)`: a dotted divider in `outlineVariant`, `bodySmall` label on `onSurfaceVariant`, `TextButton` "Add something". **No error/red colours.**
- `ProviderChip(name: String)`: a small `AssistChip` on `surfaceContainerHigh`.
- `KleanCard(title, onOpen, content)`: an `ElevatedCard` with the title row clickable.
- `NeutralEmpty(text)`.
Add `@Preview`s for each. Commit `feat(design): core components`.

**Task 1.5 — common module**
`core/common`: `ApplicationScope.kt`
```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
interface ZoneProvider { fun current(): TimeZone }
@Module @InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides @Singleton @ApplicationScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Provides fun clock(): Clock = Clock.System
    @Provides fun zone(): ZoneProvider = object : ZoneProvider { override fun current() = TimeZone.currentSystemDefault() }
}
```
(Uses `kotlin.time.Clock`, `kotlinx.datetime.TimeZone`. The build applies `klean.hilt` and depends on `:shared` for datetime via `api`.) Commit `feat(common): app scope, clock, zone`.

**Task 1.6 — `ModuleId` in shared**
`shared/src/commonMain/kotlin/app/klean/shared/module/ModuleId.kt`:
```kotlin
@Serializable @JvmInline
value class ModuleId(val value: String) {
    companion object {
        val DIET = ModuleId("diet")
        // Future: reserved so ids never change.
        val TASKS = ModuleId("tasks"); val REMINDERS = ModuleId("reminders"); val CALENDAR = ModuleId("calendar")
        val CONTACTS = ModuleId("contacts"); val BIRTHDAYS = ModuleId("birthdays"); val WORKOUT = ModuleId("workout"); val SLEEP = ModuleId("sleep")
    }
}
```
Commit.

**Task 1.7 — Module registry (TDD)**
`core/ui/src/main/kotlin/app/klean/core/ui/module/KleanModule.kt`:
```kotlin
enum class DashboardSlot { DIET, TASKS, REMINDERS, EVENTS, BIRTHDAYS, SLEEP, WORKOUT }

interface KleanModule {
    val id: ModuleId
    @get:StringRes val labelRes: Int
    val icon: ImageVector
    val navOrder: Int
    val dashboardSlot: DashboardSlot?
    val startRoute: Any
    val settingsRoute: Any?
    fun registerGraph(builder: NavGraphBuilder, nav: NavController)
    @Composable fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
    /** Cancel alarms/notifications/widgets on disable; reschedule on enable. */
    suspend fun onEnabledChanged(enabled: Boolean) {}
}

interface ModuleGate {
    val disabledIds: Flow<Set<ModuleId>>
    suspend fun isEnabled(id: ModuleId): Boolean
    suspend fun setEnabled(id: ModuleId, enabled: Boolean)
}

@Singleton
class ModuleRegistry @Inject constructor(
    modules: Set<@JvmSuppressWildcards KleanModule>,
    private val gate: ModuleGate,
) {
    val all: List<KleanModule> = modules.sortedBy { it.navOrder }
    val enabled: Flow<List<KleanModule>> = gate.disabledIds.map { off -> all.filter { it.id !in off } }
    val dashboardCards: Flow<List<KleanModule>> =
        enabled.map { l -> l.filter { it.dashboardSlot != null }.sortedBy { it.dashboardSlot!!.ordinal } }
    suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        gate.setEnabled(id, enabled)
        all.firstOrNull { it.id == id }?.onEnabledChanged(enabled)
    }
}
```
(`ModuleGate` is declared here; Task 3.9 implements it in `:core:data` and binds it. Until then the test uses a fake.)
Test `core/ui/src/test/kotlin/app/klean/core/ui/module/ModuleRegistryTest.kt`, using a `FakeGate` backed by a `MutableStateFlow`. Cases:
1. `enabled` excludes disabled ids.
2. `dashboardCards` ordered by slot, not by navOrder.
3. `setEnabled(false)` calls `onEnabledChanged(false)` on that module only.
4. A module with `dashboardSlot = null` has no card.

Run fail → implement → pass. Commit `feat(ui): module registry with dashboard slots`.

**Task 1.8 — App shell navigation**
- `app/src/main/kotlin/app/klean/nav/Routes.kt`: `@Serializable object TodayRoute`, `@Serializable object SettingsRoute`, `@Serializable object OnboardingGraph`.
- `KleanAppUi.kt`: collect `registry.enabled`; `NavigationSuiteScaffold` with items Today (icon `Icons.Rounded.Today`), each enabled module (`labelRes`, `icon`, navigates to `startRoute`), Settings (`Icons.Rounded.Settings`). Navigate with `launchSingleTop = true; restoreState = true; popUpTo(TodayRoute) { saveState = true }`.
- `KleanNavHost`: `composable<TodayRoute> { TodayScreen() }`, `composable<SettingsRoute> { SettingsScreen() }`, `registry.all.forEach { if (it in enabled) it.registerGraph(this, nav) }`.
- `TodayScreen` (`:app` for now; it moves to `:feature:today` if it grows): a top area with the universal capture bar (Task 4.x wires it; for now a disabled `CaptureField`), then `registry.dashboardCards` rendered via `module.DashboardCard(onOpen = { nav.navigate(module.startRoute) })`. With no cards, `NeutralEmpty("Nothing here yet. Turn on a module in Settings.")`.
- `MainActivity` wraps everything in `KleanTheme`.
Verify on device: the app shows the bottom bar with Today + Settings. Commit `feat(app): registry-driven shell`.

**Task 1.9 — Forbidden-copy guard (TDD)**
`app/src/test/kotlin/app/klean/ForbiddenCopyTest.kt`:
```kotlin
class ForbiddenCopyTest {
    private val forbidden = Regex("""(?i)\b(adhd|add\b|autis\w*|audhd|neurodiver\w*|neurotypical|executive dysfunction|asperger\w*|spectrum disorder)""")
    @Test fun noForbiddenWordsInUserFacingResources() {
        val root = File("..").canonicalFile
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.path.contains("/src/main/") && (it.extension == "xml" && it.path.contains("/res/") || it.name == "AndroidManifest.xml") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (forbidden.containsMatchIn(l)) "${f.relativeTo(root)}:${i + 1}: $l" else null } }
            .toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
```
Prove the test works: temporarily add `<string name="x">ADHD</string>` to `app/src/main/res/values/strings.xml`, run `nix develop -c ./gradlew :app:testDebugUnitTest --tests '*ForbiddenCopyTest'` → FAIL listing the line. Remove it → PASS. Also add `scripts/check-copy.sh`:
```bash
#!/usr/bin/env bash
! grep -rniE 'adhd|autis|audhd|neurodiver|neurotypical|asperger' --include='*.xml' $(find . -path '*/src/main/res' -type d) ; echo "copy check done"
```
Commit `test: guard user-facing copy`.

**Task 1.10 — Settings skeleton**
In `:feature:settings`: a `SettingsScreen` list with sections "Modules" (one row per `registry.all` → its `settingsRoute`; plus "Turn modules on or off" → `ModulesSettingsRoute`), and "General": Quiet hours, Appearance, AI providers, Backup, Permissions, About. Rows whose screens don't exist yet navigate to empty stub screens that show only their title (each stub is replaced in a later milestone). `ModulesSettingsRoute` shows a `Switch` per registered module that calls `registry.setEnabled`. Commit `feat(settings): settings skeleton`. **Milestone check, `git tag m1`.**

---

### M2 — `:shared` domain core (pure Kotlin, fully unit-tested)

**Task 2.1 — `:shared` build**
`shared/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidLibrary {           // if AGP 9.4 renamed this block to `android {}`, use that
        namespace = "app.klean.shared"
        compileSdk = 37
        minSdk = 28
    }
    jvm()
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime"); optIn.add("kotlin.uuid.ExperimentalUuidApi") }
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.datetime)
            api(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
```
Verify `nix develop -c ./gradlew :shared:jvmTest` → BUILD SUCCESSFUL (no tests). Commit.

**Task 2.2 — UUIDv7 (TDD)**
Test `shared/src/commonTest/kotlin/app/klean/shared/id/UuidV7Test.kt`:
```kotlin
class UuidV7Test {
    @Test fun formatAndVersion() {
        val id = UuidV7.generate(1_762_600_000_000L, Random(1))
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id), id)
    }
    @Test fun timestampPrefixSortsByTime() {
        val a = UuidV7.generate(1_000L, Random(1)); val b = UuidV7.generate(2_000L, Random(1))
        assertTrue(a < b)
    }
    @Test fun extractsTimestamp() { assertEquals(1_762_600_000_123L, UuidV7.timestampOf(UuidV7.generate(1_762_600_000_123L))) }
    @Test fun unique() { assertEquals(10_000, List(10_000) { UuidV7.generate(5L) }.toSet().size) }
}
```
Run → FAIL. Implement `shared/src/commonMain/kotlin/app/klean/shared/id/UuidV7.kt`:
```kotlin
object UuidV7 {
    fun generate(nowMs: Long = Clock.System.now().toEpochMilliseconds(), random: Random = Random.Default): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        for (i in 0 until 6) b[i] = (nowMs ushr (8 * (5 - i))).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
    fun timestampOf(id: String): Long = id.replace("-", "").substring(0, 12).toLong(16)
}
```
Run → PASS. Commit `feat(shared): UUIDv7`.

**Task 2.3 — name-based UUID for settings (TDD)**
Test: `nameUuid("core.quietHours") == nameUuid("core.quietHours")`, it differs for another key, and it matches the UUID regex. Implement `expect fun nameUuid(name: String): String` in `commonMain/.../id/NameUuid.kt`. Add `actual` in both `jvmMain` and `androidMain`: `java.util.UUID.nameUUIDFromBytes(("klean:" + name).encodeToByteArray()).toString()`. Run `:shared:jvmTest` → PASS. Commit.

**Task 2.4 — time helpers + QuietHours (TDD)**
`shared/src/commonMain/kotlin/app/klean/shared/time/TimeExt.kt`:
```kotlin
const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MINUTES = 1440

fun Long.toLocal(zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
fun minuteOfDay(epochMs: Long, zone: TimeZone): Int = epochMs.toLocal(zone).let { it.hour * 60 + it.minute }
fun atMinute(date: LocalDate, minute: Int, zone: TimeZone): Long =
    LocalDateTime(date, LocalTime(minute / 60, minute % 60)).toInstant(zone).toEpochMilliseconds()
```
Test `QuietHoursTest` (zone `Asia/Dubai`, helper `t(h, m, day)` = `atMinute(LocalDate(2026, 11, day), h*60+m, zone)`):
- `QuietHours(23*60, 7*60)`: contains 23:30, contains 03:00, not 07:00, not 12:00.
- `QuietHours(60, 9*60)`: contains 01:00, not 00:59.
- `QuietHours(0,0)` (`isDisabled`) contains nothing.
- `nextAllowed`: (23:00–07:00) at 23:30 day 9 → 07:00 day 10; at 03:00 day 10 → 07:00 day 10; at 12:00 → 12:00 unchanged.
Implement `QuietHours.kt`:
```kotlin
@Serializable
data class QuietHours(val startMinute: Int, val endMinute: Int) {
    init { require(startMinute in 0 until DAY_MINUTES && endMinute in 0 until DAY_MINUTES) }
    val isDisabled: Boolean get() = startMinute == endMinute
    fun containsMinute(m: Int): Boolean = when {
        isDisabled -> false
        startMinute < endMinute -> m in startMinute until endMinute
        else -> m >= startMinute || m < endMinute
    }
    fun contains(epochMs: Long, zone: TimeZone): Boolean = containsMinute(minuteOfDay(epochMs, zone))
    /** First instant >= epochMs outside quiet hours. */
    fun nextAllowed(epochMs: Long, zone: TimeZone): Long {
        if (!contains(epochMs, zone)) return epochMs
        val local = epochMs.toLocal(zone)
        val m = local.hour * 60 + local.minute
        val date = if (startMinute > endMinute && m >= startMinute) local.date.plus(1, DateTimeUnit.DAY) else local.date
        return atMinute(date, endMinute, zone)
    }
    companion object { val DEFAULT = QuietHours(0, 8 * 60) }
}
```
PASS → commit `feat(shared): quiet hours`.

**Task 2.5 — DayWindows (TDD)**
Tests:
- sleep 00:00–08:00 → `boundaryShiftMinutes = 240`. `logicalDate(02:00 Nov 9) == Nov 8`. `forDate(Nov 8)` gives start Nov 8 04:00, end Nov 9 04:00, wakeStart Nov 8 08:00, wakeEnd Nov 9 00:00.
- sleep 23:00–07:00 → shift 180, wakeEnd = Nov 8 23:00.
- sleep 20:00–02:00 → shift −60. `logicalDate(23:30 Nov 8) == Nov 9`. `forDate(Nov 8).wakeStart == Nov 8 02:00`.
- disabled → shift 0, wake = whole day.
Implement `shared/.../time/DayWindows.kt`:
```kotlin
data class DayWindow(val date: LocalDate, val start: Long, val end: Long, val wakeStart: Long, val wakeEnd: Long)

object DayWindows {
    fun boundaryShiftMinutes(q: QuietHours): Int {
        if (q.isDisabled) return 0
        val len = (q.endMinute - q.startMinute + DAY_MINUTES) % DAY_MINUTES
        val mid = (q.startMinute + len / 2) % DAY_MINUTES
        return if (mid <= 720) mid else mid - DAY_MINUTES
    }
    fun logicalDate(epochMs: Long, q: QuietHours, zone: TimeZone): LocalDate =
        (epochMs - boundaryShiftMinutes(q) * MINUTE_MS).toLocal(zone).date
    fun forDate(date: LocalDate, q: QuietHours, zone: TimeZone): DayWindow {
        val shift = boundaryShiftMinutes(q) * MINUTE_MS
        val start = atMinute(date, 0, zone) + shift
        val end = atMinute(date.plus(1, DateTimeUnit.DAY), 0, zone) + shift
        if (q.isDisabled) return DayWindow(date, start, end, start, end)
        val wakeStart = q.nextAllowed(start, zone)
        val wakeEnd = listOf(date.minus(1, DateTimeUnit.DAY), date, date.plus(1, DateTimeUnit.DAY))
            .map { atMinute(it, q.startMinute, zone) }
            .filter { it > wakeStart }.minOrNull()?.coerceAtMost(end) ?: end
        return DayWindow(date, start, end, wakeStart, wakeEnd)
    }
}
```
PASS → commit `feat(shared): logical day windows`.

**Task 2.6 — Diet enums**
`shared/.../diet/DietEnums.kt`:
```kotlin
enum class CaptureSource { NOTIFICATION_REPLY, PROMPT_REPLY, QS_TILE, WIDGET, DASHBOARD, MODULE_SCREEN, UNLOCK_SHEET, DELIVERY_PROMPT, GEOFENCE_PROMPT }
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, DRINK }
enum class EnrichmentStatus { NOT_REQUESTED, PENDING, DONE, NO_PROVIDER, FAILED }
enum class FoodItemState { ACCEPTED, EDITED, REJECTED }
```
`shared/.../diet/prompt/PromptEnums.kt`:
```kotlin
enum class PromptTrigger { CADENCE, LEARNED_MEAL, DELIVERY, GEOFENCE, UNLOCK_SHEET }
enum class PromptFormat { STANDARD, HEADS_UP, SILENT_VIBRATE, WIDGET_HIGHLIGHT }
enum class PromptOutcome { PENDING, LOGGED, DISMISSED, IGNORED }
enum class MealLabel { BREAKFAST, LUNCH, DINNER, LATE }
data class MealSlot(val centerMinute: Int, val promptMinute: Int, val label: MealLabel)
```
Commit.

**Task 2.7 — MealTimeLearner (TDD)**
Tests (`MealTimeLearnerTest`, zone Dubai, `now` = Nov 20 12:00):
1. Seven days (Nov 10–16) of entries at 08:10, 13:20, 20:05 → 3 slots. Centers within ±30 min of 08:10/13:20/20:05. Labels BREAKFAST, LUNCH, DINNER. `promptMinute == (center + 45) % 1440`.
2. Six days of the same data → empty.
3. Case 1 + one random-time snack per day at different times (09:47, 15:02, 17:31, 22:10, 11:44, 16:20, 23:05) → still exactly 3 slots.
4. 7 days with entries at 23:50 and 00:20 → one LATE slot (circular bins), not two.
5. Entries older than 21 days are ignored (7 days of data at Oct 1–7 → empty).
Implement `shared/.../diet/prompt/MealTimeLearner.kt`:
```kotlin
object MealTimeLearner {
    const val MIN_DISTINCT_DAYS = 7
    const val WINDOW_DAYS = 21
    private const val BIN = 30
    private const val BINS = DAY_MINUTES / BIN
    private const val COVERAGE = 0.4
    private const val PROMPT_OFFSET_MIN = 45
    private const val MAX_SLOTS = 4
    private const val MIN_SEPARATION_BINS = 4

    fun learn(entryTimes: List<Long>, now: Long, zone: TimeZone): List<MealSlot> {
        val since = now - WINDOW_DAYS * 24 * HOUR_MS
        val locals = entryTimes.filter { it in since..now }.map { it.toLocal(zone) }
        val days = locals.map { it.date }.toSet()
        if (days.size < MIN_DISTINCT_DAYS) return emptyList()
        val binOf = { l: LocalDateTime -> (l.hour * 60 + l.minute) / BIN }
        val daysPerBin = IntArray(BINS)
        locals.groupBy(binOf).forEach { (b, l) -> daysPerBin[b] = l.map { it.date }.toSet().size }
        fun raw(i: Int) = daysPerBin[(i + BINS) % BINS]
        val smooth = IntArray(BINS) { raw(it - 1) + 2 * raw(it) + raw(it + 1) }
        fun s(i: Int) = smooth[(i + BINS) % BINS]
        fun coverage(i: Int): Int {
            val bins = setOf((i - 1 + BINS) % BINS, i, (i + 1) % BINS)
            return locals.filter { binOf(it) in bins }.map { it.date }.toSet().size
        }
        val minDays = days.size * COVERAGE
        val peaks = (0 until BINS)
            .filter { s(it) > 0 && s(it) >= s(it - 1) && s(it) > s(it + 1) && coverage(it) >= minDays }
            .sortedByDescending { s(it) }
        val chosen = mutableListOf<Int>()
        for (p in peaks) {
            if (chosen.size == MAX_SLOTS) break
            if (chosen.none { circular(it, p) < MIN_SEPARATION_BINS }) chosen += p
        }
        return chosen.sorted().map { b ->
            val c = b * BIN + BIN / 2
            MealSlot(c, (c + PROMPT_OFFSET_MIN) % DAY_MINUTES, labelFor(c))
        }
    }
    private fun circular(a: Int, b: Int) = minOf((a - b + BINS) % BINS, (b - a + BINS) % BINS)
    fun labelFor(minute: Int): MealLabel = when (minute) {
        in 240..659 -> MealLabel.BREAKFAST
        in 660..959 -> MealLabel.LUNCH
        in 960..1319 -> MealLabel.DINNER
        else -> MealLabel.LATE
    }
}
```
If test 4 yields two slots, the plateau rule is wrong. Fix it so only one bin of a plateau counts as a peak, then re-run. PASS → commit `feat(shared): meal-time learner`.

**Task 2.8 — PromptPlanner (TDD)**
Test `PromptPlannerTest` (zone Dubai, quiet 00:00–08:00, `t(h,m=0,day=9)`):
```kotlin
@Test fun noHistory_sixHoursFromNow() = assertEquals(t(16), plan(now = t(10)).at)
@Test fun anchorsOnLatestOfPromptAndEntry() = assertEquals(t(17), plan(now = t(12), lastPrompt = t(9), lastEntry = t(11)).at)
@Test fun baseInQuiet_movesToWakePlusHour() {
    val p = plan(now = t(20), lastEntry = t(20)); assertEquals(t(9, day = 10), p.at); assertEquals(PromptTrigger.CADENCE, p.trigger)
}
@Test fun neverInsideQuietHours() {
    for (m in 0 until 1440 step 7) { val now = t(0) + m * MINUTE_MS
        val p = plan(now = now, lastEntry = now); assertFalse(quiet.contains(p.at, zone)); assertTrue(p.at > now) }
}
@Test fun neverLaterThanCadenceOutsideQuiet() {
    for (m in 8 * 60 until 18 * 60 step 13) { val now = t(0) + m * MINUTE_MS
        val p = plan(now = now, lastPrompt = now - HOUR_MS, slots = lunchSlot); assertTrue(p.at <= now - HOUR_MS + 6 * HOUR_MS) }
}
@Test fun learnedSlotBeforeBase_chosen() {
    val p = plan(now = t(10), lastPrompt = t(9), lastEntry = t(8, 30), slots = lunchSlot)
    assertEquals(t(13, 45), p.at); assertEquals(PromptTrigger.LEARNED_MEAL, p.trigger); assertEquals(MealLabel.LUNCH, p.meal)
}
@Test fun learnedSlotTooSoonAfterLastPrompt_skipped() = assertEquals(t(18), plan(now = t(12, 5), lastPrompt = t(12), slots = lunchSlot).at)
@Test fun learnedSlotSkippedWhenRecentlyLogged() = assertEquals(t(18, 30), plan(now = t(12, 40), lastPrompt = t(9), lastEntry = t(12, 30), slots = lunchSlot).at)
```
Here `lunchSlot = listOf(MealSlot(13*60, 13*60+45, MealLabel.LUNCH))`, and `plan(...)` builds a `PromptContext`.
Implement `shared/.../diet/prompt/PromptPlanner.kt`:
```kotlin
data class PromptContext(
    val now: Long, val zone: TimeZone, val quiet: QuietHours,
    val lastPromptAt: Long?, val lastEntryAt: Long?,
    val learnedSlots: List<MealSlot> = emptyList(),
    val cadenceMs: Long = 6 * HOUR_MS,
    val minGapMs: Long = 3 * HOUR_MS,
    val recentLogGraceMs: Long = 2 * HOUR_MS,
    val wakeDelayMs: Long = HOUR_MS,
)
data class PlannedPrompt(val at: Long, val trigger: PromptTrigger, val meal: MealLabel?)

/** Pure. Deliberately takes no outcome history: ignoring prompts must never change frequency. */
object PromptPlanner {
    fun next(c: PromptContext): PlannedPrompt {
        val anchor = listOfNotNull(c.lastPromptAt, c.lastEntryAt).maxOrNull() ?: c.now
        val base = maxOf(anchor + c.cadenceMs, c.now + MINUTE_MS)
        val floor = maxOf(c.now, (c.lastPromptAt ?: (Long.MIN_VALUE / 2)) + c.minGapMs)
        learned(c, floor, base)?.let { return it }
        val allowed = c.quiet.nextAllowed(base, c.zone)
        return PlannedPrompt(if (allowed != base) allowed + c.wakeDelayMs else base, PromptTrigger.CADENCE, null)
    }
    private fun learned(c: PromptContext, floor: Long, base: Long): PlannedPrompt? {
        if (c.learnedSlots.isEmpty()) return null
        val today = c.now.toLocal(c.zone).date
        return listOf(today, today.plus(1, DateTimeUnit.DAY))
            .flatMap { d -> c.learnedSlots.map { s -> atMinute(d, s.promptMinute, c.zone) to s } }
            .filter { (at, _) -> at >= floor && at < base }
            .filterNot { (at, _) -> c.quiet.contains(at, c.zone) }
            .filterNot { (at, _) -> c.lastEntryAt != null && c.lastEntryAt >= at - c.recentLogGraceMs }
            .minByOrNull { it.first }
            ?.let { (at, s) -> PlannedPrompt(at, PromptTrigger.LEARNED_MEAL, s.label) }
    }
}
```
PASS → commit `feat(shared): prompt planner`.

**Task 2.9 — FormatRotator + OutcomeResolver (TDD)**
Tests:
- empty history → STANDARD.
- last LOGGED with HEADS_UP → HEADS_UP.
- last IGNORED STANDARD → HEADS_UP; IGNORED WIDGET_HIGHLIGHT → STANDARD (wraps); DISMISSED SILENT_VIBRATE → WIDGET_HIGHLIGHT.
- PENDING items are skipped when finding "last".
- `nextPhrasing` never returns any of the last 3 phrasings when the pool has more than 3; with a pool of size 1 it returns that one.
- Resolver: an entry created 30 min after `sentAt` → LOGGED; none and 2 h passed → IGNORED; none and 1 h passed → PENDING.
Implement:
```kotlin
data class PromptHistoryItem(val format: PromptFormat, val phrasingId: String, val outcome: PromptOutcome)

object FormatRotator {
    private val cycle = PromptFormat.entries
    fun nextFormat(newestFirst: List<PromptHistoryItem>): PromptFormat {
        val last = newestFirst.firstOrNull { it.outcome != PromptOutcome.PENDING } ?: return PromptFormat.STANDARD
        return if (last.outcome == PromptOutcome.LOGGED) last.format
        else cycle[(cycle.indexOf(last.format) + 1) % cycle.size]
    }
    fun nextPhrasing(pool: List<String>, newestFirst: List<PromptHistoryItem>, random: Random): String {
        require(pool.isNotEmpty())
        val recent = newestFirst.take(3).map { it.phrasingId }.toSet()
        return pool.filterNot { it in recent }.ifEmpty { pool }.random(random)
    }
}

object PromptOutcomeResolver {
    const val LOG_WINDOW_MS = 60 * MINUTE_MS
    const val IGNORE_AFTER_MS = 2 * HOUR_MS
    /** entryCreatedTimes = when entries were *logged* (created_at), not eaten_at, so backdating doesn't count. */
    fun resolve(sentAt: Long, entryCreatedTimes: List<Long>, now: Long): PromptOutcome = when {
        entryCreatedTimes.any { it in sentAt..(sentAt + LOG_WINDOW_MS) } -> PromptOutcome.LOGGED
        now - sentAt >= IGNORE_AFTER_MS -> PromptOutcome.IGNORED
        else -> PromptOutcome.PENDING
    }
}
```
PASS → commit `feat(shared): format rotation and outcome resolution`.

**Task 2.10 — GapDetector (TDD)**
Tests (sleep 00:00–08:00, Nov 9 window, threshold 4 h):
- entries 09:00, 10:30, 17:00, now 20:00 → one gap 10:30–17:00 (`ongoing=false`).
- no entries, now 13:00 → gap 08:00–13:00 `ongoing=true`.
- entries 08:30, 12:00, 15:30, 19:00, now 21:00 → no gaps.
- now 07:00 (before wake) → empty.
- now after wakeEnd (Nov 10 02:00), last entry 19:00 → gap 19:00–00:00, `ongoing=false`.
Implement:
```kotlin
data class Gap(val start: Long, val end: Long, val ongoing: Boolean)
object GapDetector {
    fun find(entryTimes: List<Long>, window: DayWindow, now: Long, thresholdMs: Long): List<Gap> {
        val upper = minOf(window.wakeEnd, now)
        if (upper <= window.wakeStart) return emptyList()
        val points = buildList {
            add(window.wakeStart)
            addAll(entryTimes.filter { it > window.wakeStart && it < upper }.sorted())
            add(upper)
        }
        return points.zipWithNext()
            .filter { (a, b) -> b - a >= thresholdMs }
            .map { (a, b) -> Gap(a, b, ongoing = b == now && now < window.wakeEnd) }
    }
}
```
PASS → commit `feat(shared): neutral gap detection`.

**Task 2.11 — QuickRepeatRanker (TDD)**
Tests: an item logged 5× this week ranks above an item logged once yesterday. "Shawarma" and " shawarma " collapse to one (keeping the most recent casing). Texts longer than 40 chars are excluded. `limit` is respected. Empty input → empty.
Implement:
```kotlin
data class RepeatCandidate(val text: String, val loggedAt: Long)
object QuickRepeatRanker {
    private const val HALF_LIFE_MS = 3 * 24 * HOUR_MS
    fun rank(history: List<RepeatCandidate>, now: Long, limit: Int = 8): List<String> =
        history.filter { it.text.isNotBlank() && it.text.trim().length <= 40 }
            .groupBy { normalize(it.text) }
            .map { (_, g) -> g.maxBy { it.loggedAt }.text.trim() to g.sumOf { 1.0 / (1.0 + (now - it.loggedAt).toDouble() / HALF_LIFE_MS) } }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }
    fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
}
```
Commit.

**Task 2.12 — DeliveryMatcher (TDD)**
Tests. True for: "Your order has been delivered", "Rider has arrived", "Your food is here", "Enjoy your meal!", "Driver is at your door". False for: "Your order is on the way", "Out for delivery", "Arriving in 5 minutes", "Restaurant is preparing your order", "50% off delivered to you today" (promo: has `%`/"off").
Implement `shared/.../diet/DeliveryMatcher.kt`:
```kotlin
object DeliveryMatcher {
    val DEFAULT_APPS = listOf("com.talabat", "com.deliveroo.orderapp", "com.careem.acma", "com.noon.buyerapp")
    private val positive = Regex("""(?i)\b(delivered|has arrived|have arrived|is here|at your door|enjoy your (meal|food|order))\b""")
    private val negative = Regex("""(?i)(on the way|out for delivery|arriving|preparing|\d+\s*%|\boff\b|promo|discount)""")
    fun isDelivered(text: String): Boolean = positive.containsMatchIn(text) && !negative.containsMatchIn(text)
}
```
Commit.

**Task 2.13 — Capture contracts + DefaultCaptureRouter (TDD)**
`shared/.../capture/CaptureContracts.kt`:
```kotlin
data class CaptureInput(
    val text: String, val source: CaptureSource, val capturedAt: Long,
    val viaVoice: Boolean = false, val photoFile: String? = null, val targetModule: ModuleId? = null,
)
data class ActionSpec(val type: String, val description: String)
data class CaptureOption(val moduleId: ModuleId, val action: ActionSpec)
data class CandidateAction(val moduleId: ModuleId, val actionType: String, val confidence: Float, val payloadJson: String? = null)

interface CaptureHandler {
    val moduleId: ModuleId
    val actionSpecs: List<ActionSpec>
    fun defaultAction(input: CaptureInput): CandidateAction
    /** Must persist immediately and return the created record id. Never call AI here. */
    suspend fun execute(input: CaptureInput, action: CandidateAction): String
    suspend fun undo(recordId: String)
}
/** AI-backed classifier. Return null when unavailable. */
interface CaptureClassifier { suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? }
interface CaptureRouter { suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>): List<CandidateAction> }
```
Tests (`DefaultCaptureRouterTest`, with fake handlers `diet` and `tasks` and a fake classifier):
1. No handlers → empty.
2. A single handler → `[diet default]`, and the classifier is **never called** (assert the call count is 0).
3. `targetModule = tasks` → `[tasks default]` without calling the classifier.
4. Classifier null → `[diet default]`.
5. Classifier returns tasks 0.9, diet 0.2 → tasks first, diet second.
6. Classifier returns tasks 0.3 → diet default first, tasks after (as a correction chip).
7. Classifier throws → diet default.
8. Classifier is slow (`delay(10_000)`) → diet default within the 1500 ms timeout (use `runTest` virtual time).
9. Classifier returns an unknown module → filtered out.
Implement `DefaultCaptureRouter.kt`:
```kotlin
class DefaultCaptureRouter(
    private val classifier: CaptureClassifier?,
    private val defaultModule: ModuleId = ModuleId.DIET,
    private val timeoutMs: Long = 1_500,
    private val minConfidence: Float = 0.5f,
) : CaptureRouter {
    override suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>): List<CandidateAction> {
        if (handlers.isEmpty()) return emptyList()
        input.targetModule?.let { t -> handlers.firstOrNull { it.moduleId == t }?.let { return listOf(it.defaultAction(input)) } }
        val fallback = (handlers.firstOrNull { it.moduleId == defaultModule } ?: handlers.first()).defaultAction(input)
        if (handlers.size == 1 || classifier == null) return listOf(fallback)
        val options = handlers.flatMap { h -> h.actionSpecs.map { CaptureOption(h.moduleId, it) } }
        val known = handlers.map { it.moduleId }.toSet()
        val ai = try {
            withTimeoutOrNull(timeoutMs) { classifier.classify(input.text, options) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            ?.filter { it.moduleId in known }?.sortedByDescending { it.confidence }.orEmpty()
        if (ai.isEmpty() || ai.first().confidence < minConfidence)
            return listOf(fallback) + ai.filterNot { it.moduleId == fallback.moduleId }
        return ai
    }
}
```
Careful: `withTimeoutOrNull` inside the try. A `TimeoutCancellationException` is not thrown by `withTimeoutOrNull`, so the rethrow is safe. PASS → commit `feat(shared): capture router`.

**Task 2.14 — AI DTOs + schema**
`shared/.../ai/AiTask.kt`: `enum class AiTask { ENTRY_PARSE, CALORIE_ESTIMATE, CAPTURE_ROUTING }`, `enum class ProviderId(val wire: String) { GEMINI("gemini"), OPENAI_COMPAT("openai_compat"), ON_DEVICE("on_device") }`.
`ParsedEntryDto.kt`:
```kotlin
@Serializable data class ParsedItemDto(val name: String, val portion: String? = null, val calories: Int? = null)
@Serializable data class ParsedEntryDto(val items: List<ParsedItemDto>, val mealType: String? = null) {
    fun sanitized(): ParsedEntryDto? {
        val clean = items.filter { it.name.isNotBlank() }.map {
            it.copy(name = it.name.trim().take(80), portion = it.portion?.trim()?.take(60), calories = it.calories?.takeIf { c -> c in 0..5000 })
        }
        if (clean.isEmpty()) return null
        return ParsedEntryDto(clean, mealType?.lowercase()?.takeIf { it in setOf("breakfast", "lunch", "dinner", "snack", "drink") })
    }
}
```
`AiSchemas.kt`: `val ENTRY_PARSE_SCHEMA: JsonObject` = the JSON schema (items array of {name string, portion string|null, calories integer|null}, required all, additionalProperties false; mealType enum + null), built with `buildJsonObject`. Add the system prompt constant `ENTRY_PARSE_SYSTEM`:
"You turn a short food log into structured data. Split it into separate foods. Estimate a typical portion if none is given. Estimate calories per item for the whole portion as an integer. Choose mealType from breakfast, lunch, dinner, snack, drink using the foods and the local time. Never add foods that are not mentioned. Reply with JSON only."
Test: `sanitized()` drops blanks, clamps calories > 5000 to null, and lowercases mealType. Commit `feat(shared): AI parse DTOs`. **Milestone check, `git tag m2`** (the app still runs as in M1).

---

### M3 — Data layer + in-app diet logging (first daily-usable build)

**Task 3.1 — `:core:data` build**
`core/data/build.gradle.kts`: plugins `klean.android.library`, `klean.hilt`, `alias(libs.plugins.room)`, serialization. `room { schemaDirectory("$projectDir/schemas") }`. Deps: `api(project(":shared"))`, `:core:common`, `room-runtime`, `ksp(room-compiler)`, `sqlite-bundled`, `datastore-preferences`, `exifinterface`, `documentfile`. Tests: `junit`, `robolectric`, `coroutines-test`, `turbine`, `room-testing`. Commit.

**Task 3.2 — Entities**
`core/data/src/main/kotlin/app/klean/core/data/db/entity/SyncColumns.kt`:
```kotlin
data class SyncColumns(
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
)
```
`FoodEntryEntity.kt`:
```kotlin
@Entity(tableName = "food_entry", indices = [Index("eaten_at"), Index("created_at"), Index("change_seq")])
data class FoodEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "eaten_at") val eatenAt: Long,
    @ColumnInfo(name = "eaten_at_zone") val eatenAtZone: String,
    @ColumnInfo(name = "photo_file") val photoFile: String?,
    @ColumnInfo(name = "source") val source: CaptureSource,
    @ColumnInfo(name = "via_voice") val viaVoice: Boolean,
    @ColumnInfo(name = "meal_type") val mealType: MealType?,
    @ColumnInfo(name = "meal_type_provider") val mealTypeProvider: String?,
    @ColumnInfo(name = "enrichment") val enrichment: EnrichmentStatus,
    @Embedded val sync: SyncColumns,
)
```
`FoodItemEntity` (`food_item`, indices `entry_id`, `change_seq`): `id`, `entry_id`, `position: Int`, `name`, `portion?`, `calories: Int?`, `provider_id: String`, `model: String?`, `state: FoodItemState`, `sync`.
`PromptLogEntity` (`prompt_log`, indices `sent_at`, `outcome`, `change_seq`): `id`, `module_id: String`, `trigger: PromptTrigger`, `trigger_detail: String?`, `format: PromptFormat`, `phrasing_id`, `scheduled_for: Long`, `sent_at: Long`, `outcome: PromptOutcome`, `outcome_at: Long?`, `sync`.
`SettingEntity` (`setting`, unique index `key`): `id`, `key`, `value_json`, `sync`.
`GeofenceEntity` (`geofence`): `id`, `label`, `latitude: Double`, `longitude: Double`, `radius_m: Int`, `enabled: Boolean`, `sync`.
`ChangeCounterEntity` (`change_counter`): `@PrimaryKey id: Int = 1`, `value: Long`.
A file header comment explains the sync rules (UUID PK, tombstones, change_seq, no FKs).
Commit `feat(data): sync-ready entities`.

**Task 3.3 — DAOs**
`ChangeCounterDao`:
```kotlin
@Dao interface ChangeCounterDao {
    @Query("INSERT OR IGNORE INTO change_counter(id, value) VALUES (1, 0)") suspend fun ensure()
    @Query("UPDATE change_counter SET value = value + 1 WHERE id = 1") suspend fun increment()
    @Query("SELECT value FROM change_counter WHERE id = 1") suspend fun current(): Long
    @Transaction suspend fun next(): Long { ensure(); increment(); return current() }
}
```
`FoodEntryDao`:
```kotlin
data class RepeatRow(val text: String, val loggedAt: Long)
@Dao interface FoodEntryDao {
    @Upsert suspend fun upsert(e: FoodEntryEntity)
    @Query("SELECT * FROM food_entry WHERE id = :id") suspend fun get(id: String): FoodEntryEntity?
    @Query("SELECT * FROM food_entry WHERE id = :id") fun observe(id: String): Flow<FoodEntryEntity?>
    @Query("SELECT * FROM food_entry WHERE deleted = 0 AND eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at ASC")
    fun observeRange(from: Long, to: Long): Flow<List<FoodEntryEntity>>
    @Query("SELECT * FROM food_entry WHERE deleted = 0 AND eaten_at >= :from AND eaten_at < :to ORDER BY eaten_at ASC")
    suspend fun range(from: Long, to: Long): List<FoodEntryEntity>
    @Query("SELECT eaten_at FROM food_entry WHERE deleted = 0 AND eaten_at >= :since") suspend fun eatenSince(since: Long): List<Long>
    @Query("SELECT created_at FROM food_entry WHERE deleted = 0 AND created_at >= :since") suspend fun createdSince(since: Long): List<Long>
    @Query("SELECT MAX(eaten_at) FROM food_entry WHERE deleted = 0 AND eaten_at <= :now") suspend fun lastEatenAt(now: Long): Long?
    @Query("SELECT raw_text AS text, created_at AS loggedAt FROM food_entry WHERE deleted = 0 AND created_at >= :since")
    suspend fun repeatRows(since: Long): List<RepeatRow>
    @Query("SELECT COUNT(*) FROM food_entry WHERE deleted = 0") suspend fun count(): Int
    @Query("SELECT id FROM food_entry WHERE deleted = 0 AND created_at >= :since AND enrichment IN ('NOT_REQUESTED','NO_PROVIDER','FAILED')")
    suspend fun awaitingEnrichment(since: Long): List<String>
    @Query("SELECT photo_file FROM food_entry WHERE photo_file IS NOT NULL") suspend fun allPhotoFiles(): List<String>
}
```
`FoodItemDao`: `upsertAll`, `observeForEntry(entryId)` (deleted=0, ORDER BY position), `forEntry(entryId)`, `observeForEntries(ids: List<String>)`, `acceptedNamesSince(since)` returning `RepeatRow` (name AS text, created_at AS loggedAt, state != 'REJECTED').
`PromptLogDao`: `upsert`, `pending()` (outcome='PENDING' AND deleted=0), `recent(limit)` ORDER BY sent_at DESC, `lastSentAt()`, `lastForTrigger(trigger, detail)`, `get(id)`.
`SettingDao`: `upsert`, `get(key)`, `observe(key): Flow<SettingEntity?>`, `all()`.
`GeofenceDao`: `upsert`, `observeAll()` (deleted=0), `enabled()`, `get(id)`.
Commit.

**Task 3.4 — Database + transact helper**
`KleanDatabase.kt`:
```kotlin
@Database(
    entities = [FoodEntryEntity::class, FoodItemEntity::class, PromptLogEntity::class, SettingEntity::class, GeofenceEntity::class, ChangeCounterEntity::class],
    version = KleanDatabase.VERSION, exportSchema = true,
)
abstract class KleanDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun promptLogDao(): PromptLogDao
    abstract fun settingDao(): SettingDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun changeCounterDao(): ChangeCounterDao
    companion object { const val VERSION = 1; const val FILE_NAME = "klean.db" }
}
suspend fun <T> KleanDatabase.transact(block: suspend () -> T): T =
    useWriterConnection { it.immediateTransaction { block() } }
```
`DatabaseModule.kt`:
```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun db(@ApplicationContext ctx: Context): KleanDatabase =
        Room.databaseBuilder(ctx, KleanDatabase::class.java, KleanDatabase.FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    @Provides fun foodEntryDao(db: KleanDatabase) = db.foodEntryDao()
    // … one @Provides per DAO
}
```
Verify `:core:data:assembleDebug` and that `core/data/schemas/app.klean.core.data.db.KleanDatabase/1.json` exists. Commit (including the schema JSON) `feat(data): Room database v1`.

**Task 3.5 — DevicePrefs + DeviceId (TDD with Robolectric)**
`DevicePrefs.kt`: DataStore `device_prefs`, with typed accessors for each key listed in §3. `deviceId()` generates a UUIDv7 on first call and persists it.
Test (Robolectric, temp DataStore file): `deviceId()` is stable across two calls and survives a new instance on the same file. Commit.

**Task 3.6 — SyncStamper (TDD)**
```kotlin
class SyncStamper @Inject constructor(private val db: KleanDatabase, private val prefs: DevicePrefs, private val clock: Clock) {
    /** Call inside db.transact {}. */
    suspend fun stamp(previous: SyncColumns? = null, deleted: Boolean = previous?.deleted ?: false): SyncColumns {
        val now = clock.now().toEpochMilliseconds()
        return SyncColumns(previous?.createdAt ?: now, now, prefs.deviceId(), deleted, db.changeCounterDao().next())
    }
}
```
Test (Robolectric, in-memory DB with the bundled driver): two stamps → `changeSeq` 1 then 2. An update keeps `createdAt` and advances `updatedAt`. `deleted=true` sets the tombstone. Commit.

**Task 3.7 — Synced SettingsRepository (TDD)**
```kotlin
class SettingKey<T>(val name: String, val default: T, val serializer: KSerializer<T>)
object SyncedKeys {
    val QuietHoursKey = SettingKey("core.quietHours", QuietHours.DEFAULT, QuietHours.serializer())
    val DisabledModules = SettingKey("core.modules.disabled", emptySet<String>(), SetSerializer(String.serializer()))
    val DietPromptsEnabled = SettingKey("diet.promptsEnabled", true, Boolean.serializer())
    val DietCadenceHours = SettingKey("diet.cadenceHours", 6, Int.serializer())
    val DietLearnMealTimes = SettingKey("diet.learnMealTimes", true, Boolean.serializer())
    val DietUnlockSheet = SettingKey("diet.unlockSheet", true, Boolean.serializer())
    val DietStatusNotification = SettingKey("diet.statusNotification", true, Boolean.serializer())
    val DietShowCalories = SettingKey("diet.showCalories", false, Boolean.serializer())
    val DietGapThresholdMinutes = SettingKey("diet.gapThresholdMinutes", 240, Int.serializer())
    val DietDeliveryApps = SettingKey("diet.deliveryApps", DeliveryMatcher.DEFAULT_APPS, ListSerializer(String.serializer()))
}
class SettingsRepository @Inject constructor(private val db: KleanDatabase, private val stamper: SyncStamper) {
    fun <T> observe(key: SettingKey<T>): Flow<T> = db.settingDao().observe(key.name)
        .map { e -> e?.takeUnless { it.sync.deleted }?.let { Json.decodeFromString(key.serializer, it.valueJson) } ?: key.default }
        .distinctUntilChanged()
    suspend fun <T> get(key: SettingKey<T>): T = observe(key).first()
    suspend fun <T> set(key: SettingKey<T>, value: T) = db.transact {
        val prev = db.settingDao().get(key.name)
        db.settingDao().upsert(SettingEntity(nameUuid(key.name), key.name, Json.encodeToString(key.serializer, value), stamper.stamp(prev?.sync)))
    }
}
```
Tests: the default is returned when the row is missing; set → observe emits the new value; `id == nameUuid(key)`; `change_seq` increments. Commit.

**Task 3.8 — ModuleGate impl**
`core/data/.../module/SettingsModuleGate.kt` implements `ModuleGate` (move the `ModuleGate` interface into `:core:data`, package `app.klean.core.data.module`, so `:core:ui` imports it). It uses the `DisabledModules` set. Bind it in Hilt `@Binds`. Update `ModuleRegistryTest` imports. Run all tests → PASS. Commit.

**Task 3.9 — PhotoStore**
`PhotoStore.kt`: `photosDir = File(ctx.filesDir, "photos")`; `suspend fun import(uri: Uri): String` (decode with inSampleSize to ≤1600 px long edge, rotate per EXIF, compress JPEG 85 to `<uuidv7>.jpg`, return the file name); `fun file(name): File`; `fun newCaptureUri(): Pair<String, Uri>` via FileProvider `${applicationId}.photos` (add `res/xml/photo_paths.xml` with `<files-path name="photos" path="photos/"/>` and a provider in the `:core:data` manifest). Robolectric test: importing a 4000×3000 generated bitmap writes a JPEG ≤1600 px. Commit.

**Task 3.10 — DietRepository + change dispatcher (TDD)**
In `:feature:diet` `data/`:
```kotlin
sealed interface DietChange { data class Logged(val id: String, val text: String) : DietChange; data class Edited(val id: String) : DietChange; data class Deleted(val id: String) : DietChange; data object Refresh : DietChange }
fun interface DietChangeListener { suspend fun onChange(change: DietChange) }

class DietChangeDispatcher @Inject constructor(private val listeners: Set<@JvmSuppressWildcards DietChangeListener>) {
    suspend fun dispatch(change: DietChange) = coroutineScope {
        listeners.map { l -> launch { try { l.onChange(change) } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Klean", "listener failed", e) } } }.joinAll()
    }
}
```
Provide an empty multibinding with `@Multibinds abstract fun listeners(): Set<DietChangeListener>` so the empty set is legal.
`DietRepository`:
```kotlin
data class TodaySummary(val count: Int, val lastText: String?, val lastAt: Long?)

@Singleton
class DietRepository @Inject constructor(
    private val db: KleanDatabase, private val stamper: SyncStamper, private val clock: Clock,
    private val zone: ZoneProvider, private val settings: SettingsRepository,
    private val dispatcher: DietChangeDispatcher, private val enrichmentPolicy: EnrichmentPolicy,
) {
    suspend fun logEntry(text: String, source: CaptureSource, eatenAt: Long? = null, photoFile: String? = null, viaVoice: Boolean = false): String {
        val clean = text.trim(); require(clean.isNotEmpty()) { "text required" }
        val now = clock.now().toEpochMilliseconds()
        val id = UuidV7.generate(now)
        db.transact {
            db.foodEntryDao().upsert(FoodEntryEntity(id, clean, eatenAt ?: now, zone.current().id, photoFile, source, viaVoice, null, null,
                if (enrichmentPolicy.hasProviderFor(AiTask.ENTRY_PARSE)) EnrichmentStatus.PENDING else EnrichmentStatus.NOT_REQUESTED, stamper.stamp()))
        }
        dispatcher.dispatch(DietChange.Logged(id, clean))
        return id
    }
    suspend fun edit(id: String, text: String? = null, eatenAt: Long? = null, photoFile: String? = null, clearPhoto: Boolean = false) { /* load, copy changed fields, stamp(prev.sync), enrichment=PENDING if text changed & provider, dispatch Edited */ }
    suspend fun delete(id: String) { /* tombstone entry + its items via stamp(prev, deleted = true); dispatch Deleted */ }
    fun observeDay(date: LocalDate): Flow<List<FoodEntryEntity>> = settings.observe(SyncedKeys.QuietHoursKey).flatMapLatest { q ->
        val w = DayWindows.forDate(date, q, zone.current()); db.foodEntryDao().observeRange(w.start, w.end) }
    suspend fun today(): List<FoodEntryEntity>
    suspend fun todaySummary(): TodaySummary
    fun todayDate(): LocalDate   // DayWindows.logicalDate(now, quiet, zone), uses the last-known quiet hours (cached StateFlow)
    suspend fun quickRepeats(): List<String> // QuickRepeatRanker over repeatRows(30d) + acceptedNames(30d)
}
```
Write out the `edit` and `delete` bodies in full when implementing. The comments above describe exactly what they do.
`EnrichmentPolicy` is an interface in `:core:ai` (`suspend fun hasProviderFor(task): Boolean`). Until M9, bind a `NoAiPolicy` returning false in `:core:ai`.
Tests (Robolectric, in-memory DB, fake clock at Nov 9 12:00 Dubai):
- `logEntry` stores trimmed text, `source`, `eatenAt==now`, zone `Asia/Dubai`, and calls the listener once with `Logged`.
- Blank text throws.
- `edit(eatenAt = 14:00)` changes `eatenAt` only, `raw_text` unchanged, `changeSeq` advanced.
- `delete` sets `deleted=1`; `observeDay` no longer emits it; the row still exists.
- `observeDay(Nov 8)` includes a 02:00 Nov 9 entry (logical day).
Commit `feat(diet): repository with fan-out`.

**Task 3.11 — DietModule registration**
`DietModule.kt`: `class DietKleanModule @Inject constructor(...) : KleanModule` with id DIET, label "Diet", icon `Icons.Rounded.Restaurant`, navOrder 10, slot DIET, startRoute `DietRoute`, settingsRoute `DietSettingsRoute`. `registerGraph` adds composables for `DietRoute`, `FoodEntryRoute`, `DietHistoryRoute`, `DietDayRoute`, `DietSettingsRoute` (screens added in the next tasks; start with `DietRoute` only). Hilt: `@Binds @IntoSet abstract fun module(m: DietKleanModule): KleanModule`. Verify on device: the bottom bar shows Today / Diet / Settings. Commit.

**Task 3.12 — Diet screen (today) + VM (TDD for VM)**
`DietViewModel`: exposes `uiState: StateFlow<DietUiState>` (`date`, `rows: List<DietRow>` where `DietRow` = `Entry(id, timeLabel, text, itemsSummary?, providerName?, hasPhoto)` | `GapRow(label, startMs)`, `repeats: List<String>`, `showCalories`), `onSubmit(text)`, `onRepeat(text)`. Rows are built by merging entries with `GapDetector.find(entries.map{eatenAt}, window, now, threshold)`. The time label uses `DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)` in the device zone. Gap label: `"No entries %1$s–%2$s"`, or ongoing `"Nothing since %1$s"`. A ticker flow re-evaluates every minute so ongoing gaps grow.
VM test (Turbine, fake repo): submitting "toast" calls `repo.logEntry("toast", MODULE_SCREEN)`. Rows interleave a gap between 10:30 and 17:00 entries. Gap labels contain no "missed".
Screen: a `Scaffold` whose top bar has the title "Today's food" and a history icon. **The `CaptureField` is pinned under the top bar**, quick-repeat chips (`FlowRow` of `SuggestionChip`s, one tap = log) sit under it, then a `LazyColumn` of rows. Tapping an entry goes to `FoodEntryRoute`. Empty: "Nothing yet today. What did you eat?"
Strings in `feature/diet/src/main/res/values/strings.xml`. Commit `feat(diet): today screen`.

**Task 3.13 — Entry edit screen**
`FoodEntryScreen`:
- Text field (saves on back or "Save").
- "Eaten at": date + `TimePicker` dialog (backdating).
- Photo thumbnail with remove/replace.
- AI items section (hidden until M9).
- "Delete" `TextButton`, with no confirmation dialog because a 5 s Undo snackbar restores it via `edit` un-tombstone (add `restore(id)` to the repo).
VM test: changing the time calls `repo.edit(id, eatenAt = …)`. Commit.

**Task 3.14 — Dashboard card**
`DietDashboardCard`: a `KleanCard("Food today")` with the last 5 entries (time + text) and an inline `CaptureField` that logs with source DASHBOARD. Tapping the title opens `DietRoute`. Verify on device: logging from Today shows up on the Diet tab immediately. Commit. **Milestone check, `git tag m3`. The user can start using the app in-app.**

---

### M4 — Notification, inline reply, capture sheet, QS tile, widget (the core loop)

**Task 4.1 — Channels + notification IDs**
`core/notifications/.../Channels.kt`: `object Channels { const val DIET_STATUS="diet_status"; const val DIET_PROMPT="diet_prompt"; const val DIET_PROMPT_HEADS_UP="diet_prompt_heads_up"; const val DIET_PROMPT_VIBRATE="diet_prompt_vibrate"; const val UNLOCK_SHEET="unlock_sheet"; const val BACKUP="backup" }` and `object NotificationIds { const val DIET_STATUS=1001; const val DIET_PROMPT=1002; const val UNLOCK=1003; const val DELIVERY=1004; const val GEOFENCE=1005; const val BACKUP=1006 }`. `ChannelRegistrar.ensure(context)` creates all of them. Names: "Food status", "Food check-ins", "Food check-ins (pop-up)", "Food check-ins (vibrate only)", "Today so far", "Backups". Importances as in §5. The vibrate channel uses `setSound(null, null)`, `enableVibration(true)`, `vibrationPattern = longArrayOf(0, 80, 120, 80)`. Call it from `KleanApp.onCreate`. Commit.

**Task 4.2 — DietStatusNotifier**
`feature/diet/.../notification/DietStatusNotifier.kt` implements `DietChangeListener` (bind `@IntoSet`) and has `refresh(savedText: String? = null)`. Build it exactly per §5.2:
- Title `diet_status_title` = "What did you eat?"
- Text `diet_status_saved` = "Saved: %1$s", `diet_status_empty` = "Nothing logged yet today", plurals `diet_status_summary` = "%1$d today · last: %2$s at %3$s" (one: "%1$d entry today · …").
- Public version: plurals `diet_status_public` = "%1$d entries today".
- Action 1 "Log food" with RemoteInput `KEY_TEXT="klean_text"`, label "What did you eat?", PendingIntent → `DietReplyReceiver` with extra `source=NOTIFICATION_REPLY`, request code 10, `FLAG_UPDATE_CURRENT or FLAG_MUTABLE`.
- Action 2 "Capture…" → `CaptureActivity` (Task 4.4) via `PendingIntent.getActivity` (immutable).
- Content intent → `Intent(ACTION_VIEW, "klean://diet".toUri())` to `MainActivity`.
- Delete intent → `StatusRepostReceiver` (immutable).
It cancels the notification when diet is disabled or the setting is off. It returns early without crashing if `POST_NOTIFICATIONS` isn't granted (`NotificationManagerCompat.areNotificationsEnabled()`).
Also `StatusRepostReceiver` (Hilt receiver, `goAsync`, calls `refresh()`) and manifest entries (`exported=false`). Call `refresh()` from `KleanApp.onCreate` via the app scope. Commit.

**Task 4.3 — DietReplyReceiver (instrumented test first)**
Test `feature/diet/src/androidTest/kotlin/app/klean/feature/diet/InlineReplySaveTest.kt` (Hilt test). It needs `app.klean.HiltTestRunner` in `:app` androidTest (`class HiltTestRunner : AndroidJUnitRunner() { override fun newApplication(cl: ClassLoader?, n: String?, c: Context?) = super.newApplication(cl, HiltTestApplication::class.java.name, c) }`) and a `@TestInstallIn(replaces = [DatabaseModule::class])` module providing an in-memory DB with the bundled driver. The instrumented test lives in `:app/src/androidTest` because Hilt tests need the app's component; the package stays `app.klean.diet`.
```kotlin
@HiltAndroidTest
class InlineReplySaveTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: KleanDatabase
    @Before fun setUp() = hilt.inject()
    @Test fun replySavesEntryWithoutLaunchingUi() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(ctx, DietReplyReceiver::class.java).putExtra(DietReplyReceiver.EXTRA_SOURCE, CaptureSource.NOTIFICATION_REPLY.name)
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder(DietReplyReceiver.KEY_TEXT).build()), intent,
            Bundle().apply { putCharSequence(DietReplyReceiver.KEY_TEXT, "shawarma and fries") })
        val started = System.currentTimeMillis()
        ctx.sendBroadcast(intent)
        val entry = withTimeout(5_000) {
            db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first { it.isNotEmpty() }.single()
        }
        assertEquals("shawarma and fries", entry.rawText)
        assertEquals(CaptureSource.NOTIFICATION_REPLY, entry.source)
        assertTrue(System.currentTimeMillis() - started < 5_000)
        var resumed = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
        }
        assertEquals(0, resumed)
    }
}
```
Run `nix develop -c ./gradlew :app:connectedDebugAndroidTest --tests '*InlineReplySaveTest'` → FAIL (no receiver).
Implement:
```kotlin
@AndroidEntryPoint
class DietReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: DietRepository
    @Inject lateinit var status: DietStatusNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Hilt injection
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim()
        val source = intent.getStringExtra(EXTRA_SOURCE)?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() } ?: CaptureSource.NOTIFICATION_REPLY
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    if (!text.isNullOrEmpty()) repo.logEntry(text, source) // dispatcher refreshes widget, prompt, etc.
                    status.refresh(savedText = text?.takeIf { it.isNotEmpty() })
                }
            } catch (e: Exception) { Log.w("Klean", "reply save failed", e); status.refresh() }
            finally { pending.finish() }
        }
    }
    companion object { const val KEY_TEXT = "klean_text"; const val EXTRA_SOURCE = "source" }
}
```
If Hilt's `@AndroidEntryPoint` on a receiver complains that `onReceive` is abstract, remove the `super` call; Hilt ≥2.4x injects in a generated `onReceive`. Register it in the manifest (`exported=false`). Run → PASS.
Manual check on the phone, airplane mode on: pull down → "Log food" → type "shawarma and fries" → send → the notification shows "Saved: shawarma and fries" within ~1 s and the app doesn't open. Repeat from the lock screen (Settings → Notifications → show all content). Commit `feat(diet): inline reply logging`.

**Task 4.4 — CaptureActivity + CaptureSheet + UniversalCaptureService**
`:core:capture`:
- `UniversalCaptureService`:
```kotlin
sealed interface CaptureResult { data class Saved(val moduleId: ModuleId, val recordId: String, val alternatives: List<CandidateAction>, val input: CaptureInput) : CaptureResult; data object NoModule : CaptureResult }
@Singleton class UniversalCaptureService @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards CaptureHandler>, private val gate: ModuleGate, private val router: CaptureRouter) {
    suspend fun enabledHandlers() = handlers.filter { gate.isEnabled(it.moduleId) }
    suspend fun submit(input: CaptureInput): CaptureResult {
        val hs = enabledHandlers(); val ranked = router.route(input, hs)
        val top = ranked.firstOrNull() ?: return CaptureResult.NoModule
        val id = hs.first { it.moduleId == top.moduleId }.execute(input, top)
        return CaptureResult.Saved(top.moduleId, id, ranked.drop(1), input)
    }
    suspend fun correct(saved: CaptureResult.Saved, to: CandidateAction): CaptureResult.Saved { /* undo old, execute new */ }
}
```
  `CaptureRouter` is provided as `DefaultCaptureRouter(classifier = null)` until M9.
- `DietCaptureHandler` (in `:feature:diet`, `@IntoSet CaptureHandler`): `actionSpecs = listOf(ActionSpec("log_food", "Something the user ate or drank"))`, `execute` → `repo.logEntry(input.text, input.source, photoFile = input.photoFile, viaVoice = input.viaVoice)`, `undo` → `repo.delete`. Also `suggestions(): List<String>` = `repo.quickRepeats()`. Add an optional `suspend fun suggestions(): List<String> = emptyList()` to `CaptureHandler` in shared.
- `CaptureSheet` composable: `ModalBottomSheet`, autofocus `CaptureField`, repeat chips from the enabled handlers' suggestions, a photo button, a mic button. On save, a 1.5 s "Saved" confirmation state, then dismiss. If `alternatives` isn't empty, show "Saved to Diet · Move to Tasks" chips instead and stay open 4 s.
- `CaptureActivity`: `Theme.Klean.Translucent`, `excludeFromRecents="true"`, `taskAffinity="app.klean.capture"`, `launchMode="singleInstance"`, `windowSoftInputMode="adjustResize|stateVisible"`. Extras: `EXTRA_TARGET_MODULE`, `EXTRA_START_VOICE`, `EXTRA_SOURCE`. `finish()` when the sheet is dismissed.
Unit test `UniversalCaptureServiceTest` with fakes: a disabled module's handler isn't used; NoModule when all are disabled; correct() undoes and re-executes. Manual: `adb shell am start -n app.klean.debug/app.klean.core.capture.CaptureActivity` → the sheet appears over the home screen, type → save → the entry exists. Commit.

**Task 4.5 — Universal capture bar on Today**
Wire `TodayScreen`'s capture bar to `UniversalCaptureService.submit(source = DASHBOARD)`, plus a mic button. Commit.

**Task 4.6 — Quick Settings tile**
`:core:capture` `CaptureTileService : TileService`:
- `onStartListening` sets the tile state ACTIVE, label "Log food" (if only diet is enabled; otherwise "Capture"), or UNAVAILABLE when no handler is enabled.
- `onClick` → `CaptureActivity` with `EXTRA_SOURCE=QS_TILE`. On API 34+ use `startActivityAndCollapse(PendingIntent)`; below that, `startActivityAndCollapse(Intent)` with `FLAG_ACTIVITY_NEW_TASK`.
- Manifest: `<service android:name=… android:permission="android.permission.BIND_QUICK_SETTINGS_TILE" android:exported="true" android:icon="@drawable/ic_tile" android:label="@string/tile_label"><intent-filter><action android:name="android.service.quicksettings.action.QS_TILE"/></intent-filter><meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="false"/></service>`.
Manual: add the tile → tap → the sheet opens → log → the entry is source QS_TILE (check the Diet screen). Commit.

**Task 4.7 — Glance widget**
Add deps `glance-appwidget`, `glance-material3` to `:feature:diet`. `DietWidget : GlanceAppWidget`:
- `sizeMode = SizeMode.Responsive(setOf(COMPACT=DpSize(110.dp,48.dp), MEDIUM=DpSize(200.dp,120.dp), LARGE=DpSize(260.dp,260.dp)))`.
- `provideGlance`: get a Hilt `EntryPoint` (`DietWidgetEntryPoint { repo(); gate(); prefs() }`), then `provideContent { val state by produceState(...) collect repo.observeDay(today) + gate + highlight; GlanceTheme(colors = KleanGlanceColors) { Content(state) } }`.
- COMPACT: one row "3 today · last: toast" + a "+" button.
- MEDIUM/LARGE: a pill "What did you eat?" (or the highlight phrasing on `tertiaryContainer`) → `actionStartActivity<CaptureActivity>(target diet, source WIDGET)`; a mic icon → same + `EXTRA_START_VOICE`; then `LazyColumn` newest first with "HH:mm  text".
- Disabled state: "Klean" + "Open".
- `DietWidgetReceiver : GlanceAppWidgetReceiver`, `res/xml/diet_widget_info.xml` (`minWidth 110dp`, `minHeight 48dp`, `resizeMode horizontal|vertical`, `targetCellWidth 3`, `targetCellHeight 2`, `widgetCategory home_screen`, `previewLayout`, `description "Today's food at a glance"`).
- `DietWidgetUpdater : DietChangeListener` (`@IntoSet`) → `DietWidget().updateAll(context)`.
- `KleanGlanceColors` = `ColorProviders(light = KleanColors.lightScheme, dark = KleanColors.darkScheme)` from `glance-material3`.
Manual: place the widget, log via inline reply → the widget updates within ~2 s. Resize to compact → the compact layout shows. Commit `feat(diet): home-screen widget`.

**Task 4.8 — Module disable plumbing**
Implement `DietKleanModule.onEnabledChanged(enabled)`:
- false: cancel the DIET_STATUS, DIET_PROMPT, UNLOCK, DELIVERY and GEOFENCE notifications; `promptEngine.cancel()` (stub until M5); `DietWidget().updateAll`; `TileService.requestListeningState`.
- true: `status.refresh()`, `promptEngine.reschedule()`, widget update.
Manual: Settings → Modules → turn off Diet → the bottom bar loses Diet, the Today card disappears, the notification disappears, the widget shows "Klean / Open" and the tile becomes unavailable. Commit. **Milestone check, `git tag m4`.**

---

### M5 — Prompts: quiet hours service, scheduling, rotation, learning, reboot

**Task 5.1 — QuietHoursService**
`core/notifications/.../QuietHoursService.kt`:
```kotlin
@Singleton class QuietHoursService @Inject constructor(private val settings: SettingsRepository, private val zone: ZoneProvider, private val clock: Clock) {
    val quietHours: Flow<QuietHours> = settings.observe(SyncedKeys.QuietHoursKey)
    /** exactUserAlarm = future reminders/alarms; they are exempt from quiet hours. */
    suspend fun isQuiet(at: Long = clock.now().toEpochMilliseconds(), exactUserAlarm: Boolean = false): Boolean =
        !exactUserAlarm && settings.get(SyncedKeys.QuietHoursKey).contains(at, zone.current())
    suspend fun nextAllowed(at: Long): Long = settings.get(SyncedKeys.QuietHoursKey).nextAllowed(at, zone.current())
}
```
Robolectric test: 03:00 is quiet with defaults; with `exactUserAlarm=true`, not quiet. Commit.

**Task 5.2 — AlarmScheduler + Reschedulable + RescheduleReceiver**
```kotlin
interface Reschedulable { suspend fun reschedule() }
class AlarmScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    fun schedule(at: Long, pi: PendingIntent) =
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    fun cancel(pi: PendingIntent) = am.cancel(pi)
}
```
`RescheduleReceiver` (Hilt, `goAsync`, iterates `Set<Reschedulable>` and also `ChannelRegistrar.ensure`). Manifest: `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`; the receiver is `exported="true"` (system broadcasts) with filters for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. `@Multibinds` declares the empty set. Commit.

**Task 5.3 — Phrasing pool**
`feature/diet/.../notification/PromptPhrasings.kt`:
```kotlin
data class Phrasing(val id: String, @StringRes val res: Int)
object PromptPhrasings {
    val generic = listOf(
        Phrasing("g1", R.string.prompt_g1), Phrasing("g2", R.string.prompt_g2), Phrasing("g3", R.string.prompt_g3), Phrasing("g4", R.string.prompt_g4),
        Phrasing("g5", R.string.prompt_g5), Phrasing("g6", R.string.prompt_g6), Phrasing("g7", R.string.prompt_g7), Phrasing("g8", R.string.prompt_g8))
    fun sinceMeal(label: MealLabel) = when (label) {
        MealLabel.BREAKFAST -> listOf(Phrasing("m_b1", R.string.prompt_since_breakfast), Phrasing("m_b2", R.string.prompt_since_breakfast_2))
        MealLabel.LUNCH -> listOf(Phrasing("m_l1", R.string.prompt_since_lunch), Phrasing("m_l2", R.string.prompt_since_lunch_2))
        MealLabel.DINNER -> listOf(Phrasing("m_d1", R.string.prompt_since_dinner), Phrasing("m_d2", R.string.prompt_since_dinner_2))
        MealLabel.LATE -> listOf(Phrasing("m_n1", R.string.prompt_late), Phrasing("m_n2", R.string.prompt_late_2))
    }
    fun byId(id: String) = (generic + MealLabel.entries.flatMap(::sinceMeal)).firstOrNull { it.id == id }
}
```
Strings:
- g1 "What did you eat?"
- g2 "Anything to add to today?"
- g3 "Quick food note?"
- g4 "Eaten anything lately?"
- g5 "Add a snack or a meal?"
- g6 "Food check: anything new?"
- g7 "Anything you'd like to jot down?"
- g8 "Had something? Add it here."
- since_breakfast "Anything since breakfast?" / _2 "Anything after breakfast to add?"
- since_lunch "Anything since lunch?" / _2 "Lunch and after: anything to add?"
- since_dinner "Anything since dinner?" / _2 "Anything this evening?"
- late "Anything late-night to add?" / _2 "A late snack to note?"
- prompt_delivery "What did you order?"
- prompt_geofence "Anything from %1$s?"
Commit.

**Task 5.4 — PromptNotifier**
`PromptNotifier.post(logId, format, phrasing, trigger)`: builds a notification on the channel for that format, with the same RemoteInput reply as the status notification (source PROMPT_REPLY, request code 11) and deleteIntent → `PromptDismissReceiver` with `logId` (request code = `logId.hashCode()`). HEADS_UP sets `PRIORITY_HIGH`. `setTimeoutAfter(2h)` so stale prompts clean themselves up. WIDGET_HIGHLIGHT: no notification, `prefs.setHighlightPromptId(logId)`, `DietWidget().updateAll`, and `status.refresh()`. The status notifier shows the highlight phrasing as its title while highlighted. `cancelActive()` cancels DIET_PROMPT and clears the highlight. `PromptDismissReceiver` marks DISMISSED (if still PENDING) via a stamped upsert. Commit.

**Task 5.5 — DietPromptEngine (TDD with fakes)**
```kotlin
@Singleton class DietPromptEngine @Inject constructor(
    private val db: KleanDatabase, private val stamper: SyncStamper, private val settings: SettingsRepository,
    private val gate: ModuleGate, private val quiet: QuietHoursService, private val clock: Clock, private val zone: ZoneProvider,
    private val scheduler: PromptAlarmScheduling,  // interface wrapping AlarmScheduler + PendingIntent so tests can fake
    private val notifier: PromptPresenter,          // interface implemented by PromptNotifier
    private val prefs: DevicePrefs, private val random: Random = Random.Default,
) : Reschedulable, DietChangeListener {
    override suspend fun onChange(change: DietChange) { if (change is DietChange.Logged) markLogged(); reschedule() }
    override suspend fun reschedule() {
        if (!gate.isEnabled(ModuleId.DIET) || !settings.get(SyncedKeys.DietPromptsEnabled)) { cancel(); return }
        resolvePending()
        val now = now()
        val learn = settings.get(SyncedKeys.DietLearnMealTimes)
        val slots = if (learn) MealTimeLearner.learn(db.foodEntryDao().eatenSince(now - 21 * 24 * HOUR_MS), now, zone.current()) else emptyList()
        val plan = PromptPlanner.next(PromptContext(now, zone.current(), settings.get(SyncedKeys.QuietHoursKey),
            db.promptLogDao().lastSentAt(), db.foodEntryDao().lastEatenAt(now), slots, settings.get(SyncedKeys.DietCadenceHours) * HOUR_MS))
        scheduler.schedule(plan); prefs.setNextPrompt(plan)
    }
    suspend fun onAlarm(plan: PlannedPromptExtras) {
        if (!gate.isEnabled(ModuleId.DIET) || !settings.get(SyncedKeys.DietPromptsEnabled)) return
        val now = now()
        val recent = db.foodEntryDao().lastEatenAt(now)
        if (quiet.isQuiet(now) || (recent != null && now - recent < 90 * MINUTE_MS)) { reschedule(); return }
        resolvePending(forceIgnoreAll = true)
        val history = db.promptLogDao().recent(10).map { PromptHistoryItem(it.format, it.phrasingId, it.outcome) }
        val format = FormatRotator.nextFormat(history)
        val pool = (if (plan.trigger == PromptTrigger.LEARNED_MEAL && plan.meal != null) PromptPhrasings.sinceMeal(plan.meal) else PromptPhrasings.generic)
        val phrasingId = FormatRotator.nextPhrasing(pool.map { it.id }, history, random)
        val id = UuidV7.generate(now)
        db.transact { db.promptLogDao().upsert(PromptLogEntity(id, "diet", plan.trigger, null, format, phrasingId, plan.at, now, PromptOutcome.PENDING, null, stamper.stamp())) }
        notifier.present(id, format, phrasingId, plan.trigger)
        reschedule()
    }
    suspend fun cancel() { scheduler.cancel(); notifier.cancelActive() }
    private suspend fun resolvePending(forceIgnoreAll: Boolean = false) { /* for each pending: outcome = resolver(...) (or IGNORED if forceIgnoreAll && still PENDING); upsert stamped when changed */ }
    private suspend fun markLogged() { /* pending prompts with sentAt within LOG_WINDOW → LOGGED; notifier.cancelActive() */ }
    private fun now() = clock.now().toEpochMilliseconds()
}
```
`PromptAlarmReceiver` (Hilt, `goAsync`) reads `trigger`/`meal` extras and calls `engine.onAlarm`. The PendingIntent uses request code 20 with `FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE`, and the extras carry the trigger + meal. Bind the engine `@IntoSet` as both `Reschedulable` and `DietChangeListener`.
Tests (Robolectric + in-memory DB + `FakeScheduler`, `FakePresenter`, `MutableClock` at Nov 9 10:00 Dubai):
1. `reschedule()` with no data schedules 16:00.
2. Advance to 16:00, `onAlarm` → presenter gets STANDARD, a log row is PENDING, the next schedule is 22:00.
3. Advance to 22:00 without logging → `onAlarm` marks the previous row IGNORED and presents HEADS_UP with a different phrasing id; the next schedule is 04:00 → quiet → 09:00 Nov 10.
4. **Same schedule regardless of outcome**: run scenario 2–3 twice, once where a `DISMISSED` precedes and once `LOGGED`-with-no-entry (set manually). The scheduled times are identical.
5. `onAlarm` at 02:00 (quiet) presents nothing.
6. A log at 16:20 after the 16:00 prompt → that row is LOGGED, the next format stays STANDARD, and the next schedule is 22:20.
7. After 7 days of seeded entries at 08:10/13:20/20:05, `reschedule()` at 10:00 picks 14:05 (lunch center 13:15 + 45) with trigger LEARNED_MEAL.
8. Diet disabled → `cancel()` called, nothing scheduled.
Run → fail → implement → pass. Commit `feat(diet): prompt engine`.

**Task 5.6 — Day rollover**
`DayRolloverScheduler : Reschedulable, DietChangeListener`: an inexact alarm at the next logical-day start → `DayRolloverReceiver` → `status.refresh()`, widget update, re-arm. Commit.

**Task 5.7 — Verification on device**
Debug-only `DebugMenu` in Diet settings (shown when `BuildConfig.DEBUG`): "Fire prompt now" (calls `onAlarm`) and "Show next check-in time". Manual:
1. Fire prompt → STANDARD notification. Swipe it away → fire again → heads-up with different wording.
2. `adb reboot` → after boot, `adb shell dumpsys alarm | grep -A3 app.klean.debug` shows a pending alarm at the expected time.
3. Set quiet hours to cover now, fire → no notification.
Commit, then **Milestone check, `git tag m5`.**

---

### M6 — Voice, photo, quick repeats polish

**Task 6.1 — VoiceInput**
`:core:capture` `VoiceRecognizer` with `start(onPartial, onFinal, onError)` per §6.4, `rememberVoiceInput()` composable handling the `RECORD_AUDIO` permission request with a one-line rationale ("Klean uses the mic only while you hold or tap it"). Manifest `RECORD_AUDIO` and `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`. The final result is put into the field and auto-submitted with `viaVoice=true`; an "Undo" chip shows for 5 s. Manual: airplane mode, mic → "two manakish" → the entry is saved (on-device recognition where available; otherwise a neutral message "Voice isn't available offline on this phone"). Commit.

**Task 6.2 — Photo attach**
The capture sheet and the entry screen get a photo button → a menu "Take photo" / "Choose from gallery". Camera: `PhotoStore.newCaptureUri()` + `TakePicture` → on success re-encode via `import`. Gallery: `PickVisualMedia(ImageOnly)` → `import`. Attach `photoFile` to the input. Thumbnails in rows use `BitmapFactory` downsampled through a tiny in-memory cache (no Coil, YAGNI). Manual: log with a photo and without one, and both work. Commit.

**Task 6.3 — Quick repeat chips everywhere**
Chips appear in the capture sheet, the diet screen and the unlock sheet (later), with `contentDescription` "Log %s again". Commit. **`git tag m6`.**

---

### M7 — History and gaps polish

**Task 7.1 — Day history**
`DietHistoryRoute`: a month grid (a custom `LazyVerticalGrid` with 7 columns). Each day shows its number and a small neutral entry count (`labelSmall`, `onSurfaceVariant`). Days with zero entries show nothing: no colours, no heat map. Swipe or arrows change the month. Data: `repo.countsByLogicalDay(month)` (query `eaten_at` in range, group in Kotlin by `DayWindows.logicalDate`). VM test: counts grouped correctly across the 04:00 boundary.
`DietDayRoute(isoDate)`: `HorizontalPager` (initial page = that date, up to today), reusing the same row list component as Today, including gaps (with `now` = end of day for past days). Commit.

**Task 7.2 — Gap affordance**
"Add something" on a gap opens the capture sheet with `eatenAt` preset to the gap midpoint (pass `EXTRA_EATEN_AT`; the sheet shows "for around 14:00 · change"). Commit.

**Task 7.3 — Patterns placeholder**
`feature/diet/src/main/kotlin/app/klean/feature/diet/patterns/README.md` says: "Placeholder. Future neutral patterns ('most entries between 22:00 and 01:00'). No UI in P1." Commit. **`git tag m7`.**

---

### M8 — Onboarding and permissions

**Task 8.1 — Permission state model**
`core/notifications/.../PermissionStatus.kt`: `data class PermissionStatus(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean, val fullScreenIntent: Boolean, val notificationListener: Boolean, val backgroundLocation: Boolean)` plus a `PermissionChecker.current()` built from `NotificationManagerCompat.areNotificationsEnabled()`, `AlarmScheduler.canExact()`, `PowerManager.isIgnoringBatteryOptimizations`, `NotificationManager.canUseFullScreenIntent()` (API 34+, else true), `NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(pkg)`, and `ACCESS_BACKGROUND_LOCATION` granted. Intents: `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (manifest `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, `ACTION_NOTIFICATION_LISTENER_SETTINGS`. Commit.

**Task 8.2 — PermissionsRoute**
One row per permission with plain status ("On" / "Off") and a "Turn on" button. Each has a one-sentence why:
- Notifications: "So Klean can show today's food in your status bar and check in now and then."
- Exact alarms: "So check-ins arrive on time instead of hours late."
- Battery: "Some phones stop apps in the background. This keeps check-ins and the widget working. On some phones you also need to allow 'auto-start'." (Add a link to `https://dontkillmyapp.com/` + `Build.MANUFACTURER.lowercase()`.)
- Full-screen: "Lets Klean show today's list right after you unlock, if you leave that on."
Status refreshes `onResume`. Commit.

**Task 8.3 — Onboarding graph**
`:feature:onboarding` with steps per §4. Each step is a full-screen page with "Next" and "Skip" (equal visual weight, no guilt copy). Steps write settings immediately:
- ModulesStep: switches for registered modules.
- SleepWindowStep: two `TimePicker`s, default 00:00 → 08:00, writing `QuietHoursKey`.
- DietPromptsStep: toggles for check-ins, learn meal times, unlock sheet, status notification, show calories.
- PermissionsStep: reuses the PermissionsRoute rows; `POST_NOTIFICATIONS` via `rememberLauncherForActivityResult(RequestPermission())`.
- WidgetStep: if `AppWidgetManager.isRequestPinAppWidgetSupported`, a "Add widget" button → `requestPinAppWidget(ComponentName(ctx, DietWidgetReceiver::class.java), null, null)`. Otherwise text instructions ("Long-press your home screen → Widgets → Klean").
- AiStep: buttons to the AI provider screens (M9; until then "You can set this up later in Settings").
- DoneStep sets `onboardingDone=true`.
`MainActivity` start destination depends on `onboardingDone`. Settings has "Run setup again". Manual: fresh install (`adb uninstall app.klean.debug`, install) → the wizard runs; skipping everything still yields a working app. Commit. **`git tag m8`.**

---

### M9 — Backup and restore

**Task 9.1 — BackupManager.export (instrumented test first)**
`core/data/.../backup/BackupManager.kt`:
```kotlin
data class BackupInfo(val exportedAt: Long, val schemaVersion: Int, val appVersion: String, val entryCount: Int, val photoCount: Int, val hasSecrets: Boolean)

@Singleton class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val db: KleanDatabase, private val photos: PhotoStore,
    private val prefs: DevicePrefs, private val secrets: SecretStore, private val clock: Clock,
) {
    suspend fun exportTo(out: OutputStream, includeSecrets: Boolean = prefs.backupIncludeSecrets()) = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "export-${clock.now().toEpochMilliseconds()}.db").apply { delete() }
        db.useWriterConnection { c -> c.usePrepared("VACUUM INTO ?") { it.bindText(1, tmp.absolutePath); it.step() } }
        BundledSQLiteDriver().open(tmp.absolutePath).use { c ->
            c.execSQL("CREATE TABLE backup_meta(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            c.execSQL("CREATE TABLE backup_photo(file_name TEXT PRIMARY KEY NOT NULL, bytes BLOB NOT NULL)")
            c.execSQL("CREATE TABLE backup_device_pref(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            meta(c, "format", "klean-backup-v1"); meta(c, "exportedAt", clock.now().toEpochMilliseconds().toString())
            meta(c, "appVersion", BuildConfigProvider.versionName); meta(c, "schemaVersion", KleanDatabase.VERSION.toString())
            photos.photosDir.listFiles()?.forEach { f -> c.usePrepared("INSERT INTO backup_photo VALUES (?, ?)") { s -> s.bindText(1, f.name); s.bindBlob(2, f.readBytes()); s.step() } }
            prefs.exportable().forEach { (k, v) -> c.usePrepared("INSERT INTO backup_device_pref VALUES (?, ?)") { s -> s.bindText(1, k); s.bindText(2, v); s.step() } }
            if (includeSecrets) { c.execSQL("CREATE TABLE backup_secret(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)"); secrets.exportPlain().forEach { (k, v) -> /* insert */ } }
        }
        tmp.inputStream().use { it.copyTo(out) }; tmp.delete()
    }
    private fun meta(c: SQLiteConnection, k: String, v: String) = c.usePrepared("INSERT INTO backup_meta VALUES (?, ?)") { s -> s.bindText(1, k); s.bindText(2, v); s.step() }
}
```
(`execSQL` here is the `androidx.sqlite.execSQL` extension. `prefs.exportable()` returns every non-secret device pref **except** `deviceId`, `autoBackupTreeUri` and `lastScreenOffAt`. `BuildConfigProvider` is a small interface bound in `:app`.)
Commit after the test in 9.3 passes.

**Task 9.2 — inspect + restore**
```kotlin
suspend fun inspect(input: InputStream): BackupInfo   // copy to cache/restore-candidate.db, validate, read counts
suspend fun restore(input: InputStream) = withContext(Dispatchers.IO) {
    val cand = copyToCache(input, "restore-candidate.db"); val info = validate(cand) // throws BackupInvalid with a calm message
    val staging = File(ctx.filesDir, "photos-restore").apply { deleteRecursively(); mkdirs() }
    val devicePrefs = mutableMapOf<String, String>(); val secretMap = mutableMapOf<String, String>()
    BundledSQLiteDriver().open(cand.absolutePath).use { c ->
        c.prepare("SELECT file_name, bytes FROM backup_photo").use { s -> while (s.step()) File(staging, s.getText(0)).writeBytes(s.getBlob(1)) }
        c.prepare("SELECT key, value FROM backup_device_pref").use { s -> while (s.step()) devicePrefs[s.getText(0)] = s.getText(1) }
        if (tableExists(c, "backup_secret")) c.prepare("SELECT key, value FROM backup_secret").use { s -> while (s.step()) secretMap[s.getText(0)] = s.getText(1) }
        listOf("backup_meta", "backup_photo", "backup_device_pref", "backup_secret").forEach { c.execSQL("DROP TABLE IF EXISTS $it") }
    }
    db.close()
    listOf("", "-wal", "-shm").forEach { ctx.getDatabasePath(KleanDatabase.FILE_NAME + it).delete() }
    cand.copyTo(ctx.getDatabasePath(KleanDatabase.FILE_NAME), overwrite = true); cand.delete()
    photos.photosDir.deleteRecursively(); staging.renameTo(photos.photosDir)
    prefs.importAll(devicePrefs); secrets.importPlain(secretMap)
}
```
`validate`: the file opens; `backup_meta.format == "klean-backup-v1"`; `PRAGMA user_version` is in `1..KleanDatabase.VERSION` (newer → "This backup is from a newer version of Klean. Update the app first."); the `food_entry` table exists. Older versions are fine: Room runs migrations on next open.
After restore, the UI calls `ProcessRestarter.restart(ctx)`: start `MainActivity` with `FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK`, then `Runtime.getRuntime().exit(0)`. This rebuilds every Hilt singleton on the new DB.

**Task 9.3 — Instrumented backup round-trip test**
`app/src/androidTest/kotlin/app/klean/BackupRoundTripTest.kt` (Hilt, uses the **real file DB** under a test-specific name via a `@TestInstallIn` module):
1. Log 3 entries (one with a photo written via PhotoStore from a generated bitmap). Set `DietShowCalories=true` and `QuietHours(23*60, 7*60)`.
2. `exportTo(ByteArrayOutputStream())`.
3. Wipe: `db.clearAllTables()`, delete the photos dir, reset settings.
4. `restore(ByteArrayInputStream(bytes))`. Then build a **new** Room instance on the same file (simulating restart) and assert: 3 entries with identical ids/text/eatenAt, the photo file exists with identical bytes, both settings are restored, and `backup_*` tables are absent (`SELECT name FROM sqlite_master WHERE name LIKE 'backup_%'` returns no rows).
5. `inspect` on a file with `user_version=99` throws `BackupInvalid`.
Run `nix develop -c ./gradlew :app:connectedDebugAndroidTest --tests '*BackupRoundTripTest'` → fail first (before 9.1/9.2), then PASS. Save the exported bytes as `core/data/src/androidTest/assets/backup-v1.sqlite` (pull with `adb pull`) for a future-migration test: "restoring backup-v1 still works". Commit `feat(data): single-file SQLite backup and restore`.

**Task 9.4 — Backup screen**
- "Export backup" → `CreateDocument("application/vnd.sqlite3")` with the name `klean-backup-YYYY-MM-DD.sqlite`.
- "Restore from backup" → `OpenDocument(arrayOf("*/*"))` → `inspect` → `AlertDialog`: "Replace everything on this phone? This deletes the N entries, photos and settings currently in Klean and replaces them with the backup from <date> (M entries, P photos). This can't be undone." Buttons "Replace" / "Cancel" → restore → restart.
- "Include API keys in backups" switch (default off) with the warning "Keys will be stored unencrypted in the backup file."
- Auto-backup section: "Back up every day" switch → `OpenDocumentTree` → `takePersistableUriPermission`; "Keep last N" stepper (default 7). Commit.

**Task 9.5 — AutoBackupWorker**
`@HiltWorker class AutoBackupWorker`:
1. Resolve the tree with `DocumentFile.fromTreeUri`.
2. Create `klean-backup-<yyyyMMdd-HHmm>.sqlite` → `exportTo(contentResolver.openOutputStream(uri))`.
3. List children whose name starts with `klean-backup-` and ends with `.sqlite`, sort by name descending, delete beyond `keep`.
4. On failure `Result.retry()` (max 3), then post a quiet notification on BACKUP: "Daily backup couldn't be saved. Check the backup folder in Settings."
Enqueue with `PeriodicWorkRequestBuilder<AutoBackupWorker>(1, DAYS)` + `ExistingPeriodicWorkPolicy.UPDATE`, constraint `requiresBatteryNotLow`. `:app` provides `Configuration.Provider` with `HiltWorkerFactory`; remove the default WorkManager initializer in the manifest (`tools:node="remove"` on `androidx.work.WorkManagerInitializer` meta-data within `androidx.startup.InitializationProvider`). Robolectric test with `TestListenableWorkerBuilder`, a temp tree via a fake `BackupTarget` interface: 9 runs with keep=7 → 7 files remain, the newest kept. Commit. **`git tag m9`.**

---

### M10 — AI provider layer + enrichment + routing

**Task 10.1 — SecretStore**
`core/data/.../secret/SecretStore.kt`:
```kotlin
@Singleton class SecretStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val ds = PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("secrets") }
    private val alias = "klean_secrets_v1"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    suspend fun put(name: String, value: String?) { ds.edit { p -> val k = stringPreferencesKey(name)
        if (value == null) p.remove(k) else { val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            p[k] = Base64.encodeToString(c.iv + c.doFinal(value.encodeToByteArray()), Base64.NO_WRAP) } } }
    suspend fun get(name: String): String? = ds.data.first()[stringPreferencesKey(name)]?.let { b64 ->
        val raw = Base64.decode(b64, Base64.NO_WRAP); val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12)); c.doFinal(raw, 12, raw.size - 12).decodeToString() }
    suspend fun exportPlain(): Map<String, String>; suspend fun importPlain(m: Map<String, String>)
}
```
Instrumented test (Keystore isn't available in Robolectric): put/get round trip; the stored value isn't the plaintext. Commit.

**Task 10.2 — AiProvider interface + gateway (TDD)**
`core/ai/.../AiProvider.kt`:
```kotlin
enum class Capability { IMAGE_INPUT, OFFLINE }
data class StructuredRequest(val system: String, val user: String, val schema: JsonObject, val imageJpeg: ByteArray? = null)
sealed interface AiOutcome {
    data class Success(val json: JsonElement, val provider: ProviderId, val model: String?) : AiOutcome
    data object NoProvider : AiOutcome
    data class Unavailable(val reason: String) : AiOutcome   // retryable
    data class Failed(val reason: String) : AiOutcome        // not retryable
}
interface AiProvider {
    val id: ProviderId
    suspend fun capabilities(): Set<Capability>
    suspend fun isConfiguredAndEnabled(): Boolean
    val needsNetwork: Boolean
    suspend fun generate(req: StructuredRequest): AiOutcome
}
@Singleton class AiGateway @Inject constructor(private val providers: Set<@JvmSuppressWildcards AiProvider>, private val config: AiConfigStore) : EnrichmentPolicy {
    suspend fun providerFor(task: AiTask): AiProvider? = config.assignment(task)?.let { id -> providers.firstOrNull { it.id == id && it.isConfiguredAndEnabled() } }
    override suspend fun hasProviderFor(task: AiTask) = providerFor(task) != null
    suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean): AiOutcome {
        val p = providerFor(task) ?: return AiOutcome.NoProvider
        val r = if (req.imageJpeg != null && (!sendImage || Capability.IMAGE_INPUT !in p.capabilities())) req.copy(imageJpeg = null) else req
        return withTimeoutOrNull(45_000) { p.generate(r) } ?: AiOutcome.Unavailable("timeout")
    }
}
```
`AiConfigStore` (DataStore): per-provider config (`GeminiConfig(enabled=false, model="gemini-2.5-flash", sendPhotos=false)`, `OpenAiConfig(enabled=false, baseUrl="http://192.168.1.10:11434/v1", model="llama3.2", acceptsImages=false)`, `OnDeviceConfig(enabled=false, modelPath=null)`) + `assignment(task): ProviderId?`. Replace the `NoAiPolicy` binding with `AiGateway`.
Tests with fake providers: unassigned → NoProvider; assigned but disabled → NoProvider (**cloud consent**); an image is stripped for a provider without IMAGE_INPUT; a slow provider → Unavailable after 45 s (virtual time). Commit.

**Task 10.3 — Gemini provider (TDD with Ktor MockEngine)**
`GeminiProvider(httpClient, config, secrets)`:
- POST `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, header `x-goog-api-key`.
- Body: `{"systemInstruction":{"parts":[{"text":system}]},"contents":[{"role":"user","parts":[{"text":user},{"inlineData":{"mimeType":"image/jpeg","data":b64}}?]}],"generationConfig":{"responseMimeType":"application/json","responseJsonSchema":schema,"temperature":0.2}}`.
- Parse `candidates[0].content.parts[0].text` as JSON.
- Status mapping: 2xx parsed → Success; 429/5xx/IOException → Unavailable; 400/401/403 → Failed (message "Gemini rejected the request. Check the key in Settings.").
- Capabilities: IMAGE_INPUT, and needsNetwork = true.
Tests with `MockEngine`: the request has the header and JSON body; a 200 response parses; 429 → Unavailable; 401 → Failed. Commit.

**Task 10.4 — OpenAI-compatible provider (TDD)**
- POST `{baseUrl.trimEnd('/')}/chat/completions`, optional `Authorization: Bearer`.
- Body: `{"model":m,"temperature":0.2,"messages":[{"role":"system","content":sys},{"role":"user","content":[{"type":"text","text":user},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,…"}}?]}],"response_format":{"type":"json_schema","json_schema":{"name":"klean","schema":schema,"strict":true}}}`.
- On HTTP 400, retry once with `"response_format":{"type":"json_object"}`. Parse `choices[0].message.content`; if it isn't pure JSON, extract the first `{…}` block.
- Capabilities: IMAGE_INPUT only if `acceptsImages`.
Tests: the 400 → json_object retry path; content wrapped in prose still parses. Commit.

**Task 10.5 — FoodEnrichmentWorker (TDD)**
Implement per §5.5 (`@HiltWorker`; input `entryId`, `enqueuedUpdatedAt`).
- User text: `"Logged at ${localTime} local time: ${entry.rawText}"`.
- Image: if `photoFile` exists, read it and downscale to 1024 px.
- Write items with `provider_id = outcome.provider.wire`, `model`, state ACCEPTED, `position` = index. `meal_type` comes from DTO mapping, `meal_type_provider` likewise.
- If the CALORIE_ESTIMATE provider differs from ENTRY_PARSE's and is available, make a second call with the user text `"Estimate calories for: " + items` and schema `{items:[{name, calories}]}`, matching by position.
`EnrichmentEnqueuer : DietChangeListener` (Logged/Edited → enqueue when `hasProviderFor(ENTRY_PARSE)`). `EnrichmentSweepWorker` triggers on config change + 12 h periodic.
Robolectric tests with `TestListenableWorkerBuilder` + a fake gateway:
- Success writes 2 items and DONE, and `raw_text` is unchanged.
- NoProvider → NO_PROVIDER, success.
- Unavailable → `Result.retry()`; on attempt 8 → FAILED.
- Entry edited after enqueue → re-enqueued, no items written.
- Deleted entry → success, nothing written.
Commit.

**Task 10.6 — AI items UI**
On the entry screen and in rows: items listed under the raw text (`bodySmall`): "shawarma · 1 wrap", "fries · medium", plus a `ProviderChip("Gemini" | "Local server" | "On device")`. Calories show only when `DietShowCalories` is on ("· ~450 kcal", and the day total in the Diet top bar subtitle "~1,850 kcal so far" only when enabled). Item actions: edit (name/portion/calories → state EDITED, `provider_id` kept), "Not right" (REJECTED, hidden), and "Remove AI details" (all REJECTED). There is no accept step; accepted is the default. Commit.

**Task 10.7 — AI provider settings screens**
- `AiProvidersRoute`: three cards (Gemini, Local or custom server, On-device) each with an On/Off status. A "Use for" section has three dropdowns: "Understanding entries" (ENTRY_PARSE), "Calorie estimates" (CALORIE_ESTIMATE), "Sorting quick capture" (CAPTURE_ROUTING); options = configured providers + "None".
- `AiProviderRoute(gemini)`: API key field (masked, stored via SecretStore), model field, "Send photos too" switch, and an **enable switch with the consent text**: "When on, the text of your food entries (and photos, if allowed) is sent to Google to be understood. Nothing is sent while this is off."
- "Test connection" button → sends "toast with butter" and shows the parsed result or the error.
- OpenAI-compat: base URL, model, optional key, "This model understands images" switch, plus a test button.
Commit.

**Task 10.8 — AI capture classifier**
`AiCaptureClassifier : CaptureClassifier` uses `AiGateway.providerFor(CAPTURE_ROUTING)`. It is used only when that provider has `Capability.OFFLINE` (per the "never block" decision); otherwise it returns null. Schema `{candidates:[{moduleId, actionType, confidence}]}`; the system prompt lists the options. Bind `CaptureRouter` = `DefaultCaptureRouter(classifier)`. Since P1 has one handler, routing short-circuits (covered by Task 2.13 test 2). Commit. **`git tag m10`.**

---

### M11 — Unlock sheet

**Task 11.1 — Spike on the real phone (timebox 30 min)**
A throwaway debug button posts a full-screen-intent notification while the screen is off (`adb shell input keyevent 26`, then trigger via `adb shell am broadcast` to a debug receiver). Note whether the activity appears after unlock (not `showWhenLocked`), and record the result in `feature/diet/src/main/kotlin/app/klean/feature/diet/unlock/NOTES.md`. If it appears *over* the lock screen instead, set `setShowWhenLocked(false)` explicitly and call `KeyguardManager.requestDismissKeyguard` in `onCreate` so the unlock prompt shows first. Commit notes.

**Task 11.2 — ScreenStateMonitor + eligibility (TDD)**
Pure function in shared `UnlockSheetPolicy.shouldShow(now, lastScreenOffAt, lastShownAt, quiet, zone, enabled): Boolean`: true iff enabled, `lastScreenOffAt != null`, `now - lastScreenOffAt >= 3h`, not quiet, and `lastShownAt == null || now - lastShownAt >= 3h`. Tests cover each branch. `ScreenStateMonitor` registers `SCREEN_OFF`/`SCREEN_ON` with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` in `KleanApp.onCreate`; at start, `if (!powerManager.isInteractive) prefs.setLastScreenOff(now)`. Commit.

**Task 11.3 — UnlockSheetActivity + notification**
- The activity reuses `CaptureSheet` content (today's list newest first + capture field + repeat chips) with a big "Done" `Button` at the bottom (one tap = finish). Title "Today so far".
- The notification is on channel UNLOCK_SHEET with `setFullScreenIntent(pi, true)` if `canUseFullScreenIntent`, otherwise as a plain heads-up with a content intent; `setTimeoutAfter(10 min)`, and the activity cancels it in `onResume`.
- Manifest: `USE_FULL_SCREEN_INTENT`; the activity has `excludeFromRecents`, translucent theme and `taskAffinity` like CaptureActivity.
- Record a `prompt_log` row with trigger UNLOCK_SHEET (format HEADS_UP) so outcome tracking covers it.
Manual: set a debug override of the threshold to 1 min (debug menu), lock, wait, unlock → the sheet appears, and one tap on Done closes it. Commit. **`git tag m11`.**

---

### M12 — Delivery-app trigger

**Task 12.1 — DeliveryListenerService**
Implement per §6.2.
- The allowlist comes from `DietDeliveryApps`, cached in a `StateFlow` collected in `onListenerConnected`. Debug builds also include `com.android.shell` (inject via a `BuildConfig` flag in `:app` → `DebugFlags` interface).
- Gate checks: diet enabled, the user accepted the offer, not quiet.
- `DeliveryPrompter.onDelivered(pkg)`: debounce via `promptLogDao.lastForTrigger(DELIVERY, pkg)` < 45 min → skip. Otherwise insert a log row and post on DIET_PROMPT_HEADS_UP with id DELIVERY: title "What did you order?" + inline reply (source DELIVERY_PROMPT).
- Manifest:
```xml
<service android:name=".delivery.DeliveryListenerService" android:exported="true"
    android:label="@string/delivery_listener_label"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
  <intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter>
</service>
<queries>
  <package android:name="com.talabat"/><package android:name="com.deliveroo.orderapp"/>
  <package android:name="com.careem.acma"/><package android:name="com.noon.buyerapp"/>
</queries>
```
Commit.

**Task 12.2 — Contextual offer**
On the Diet screen, show a card once when `deliveryOffer == NOT_OFFERED`, there are ≥ 3 entries, and any allowlisted package is installed (`packageManager.getPackageInfo` succeeds). Copy: "Get a quick 'What did you order?' when a delivery arrives? Klean only checks notifications from delivery apps, on this phone, and keeps nothing but the time it happened." Buttons "Set up" (→ `ACTION_NOTIFICATION_LISTENER_SETTINGS`, state ACCEPTED), "Not now" (SNOOZED 30 days), "No thanks" (DECLINED). Also add the `DeliveryAppsRoute` editor (list + add package + match words) in Diet settings. Commit.

**Task 12.3 — Verify with a simulated notification**
On a debug build, after granting access:
```
adb shell cmd notification post -S bigtext -t "Talabat" klean_test "Your order has been delivered"
```
Expected: within ~2 s a heads-up "What did you order?" appears. Replying logs an entry with source DELIVERY_PROMPT. Running it again within 45 min → no second prompt. `adb shell cmd notification post -t "Talabat" t2 "Your order is on the way"` → no prompt. Verify that no notification text is stored: `adb shell run-as app.klean.debug sqlite3 databases/klean.db "select trigger_detail from prompt_log where trigger='DELIVERY'"` → only `com.android.shell`. Commit. **`git tag m12`.**

---

### M13 — Polish, module disable end-to-end, accessibility, copy audit

**Task 13.1 — Disable audit**
Write an instrumented test `ModuleDisableTest`: disable diet → assert no DIET_* notifications are active (`NotificationManager.activeNotifications`), the prompt alarm is cancelled (`PendingIntent.getBroadcast(... FLAG_NO_CREATE) == null`), and `registry.enabled.first()` has no diet. Then do a manual widget check. Fix gaps. Commit.

**Task 13.2 — Accessibility pass**
- Every icon button has a `contentDescription`.
- Touch targets ≥ 48 dp (the capture send button is 56 dp).
- Check at font scale 2.0 (`adb shell settings put system font_scale 2.0`): the capture field and lists remain usable; fix any clipping.
- TalkBack walk-through of log from the Diet screen, the capture sheet and the widget.
Commit.

**Task 13.3 — Copy audit**
Run `nix develop -c ./gradlew :app:testDebugUnitTest --tests '*ForbiddenCopyTest'` → PASS. Grep for `!` in strings: `grep -rn '!' */*/src/main/res/values/strings.xml app/src/main/res/values/strings.xml` should find none in user copy. Grep for guilt words: `grep -rniE 'missed|streak|behind|failed to log|forgot' --include=strings.xml .` → none. Commit.

**Task 13.4 — Appearance + About**
- AppearanceRoute: a "Use wallpaper colours" switch (Material You, off by default, hidden below API 31) stored in DevicePrefs, applied in `KleanTheme`.
- About: version, the Cantarell OFL licence text from assets, and a note that "Klean works offline and keeps your data on this phone".
Commit. **`git tag m13`.**

---

### M14 — On-device model (late Prototype 1)

**Task 14.1 — Runtime choice spike (timebox 1 h)**
Check whether `com.google.mediapipe:tasks-genai` (LLM Inference API) is still current, or whether Google AI Edge now recommends LiteRT-LM (`com.google.ai.edge.litertlm`). Pick the current one and record the choice in `core/ai/ON_DEVICE.md`. Model: Gemma 3 1B instruction-tuned, int4 (~0.5–0.6 GB). Distribution: the user imports a downloaded `.task`/`.litertlm` file via SAF ("Choose model file"), because Hugging Face gating prevents an in-app anonymous download. Optionally a "Download from URL" field. Nothing is bundled.

**Task 14.2 — OnDeviceProvider**
- Copy the picked file into `noBackupFilesDir/models/`.
- Lazily create the inference engine on first use, on `Dispatchers.Default`, with `maxTokens 512`.
- The prompt = system + user + "Reply with JSON matching this schema: <schema>". Parse the first JSON object, validate via `ParsedEntryDto.sanitized()`, retry once on a parse failure.
- Capabilities: OFFLINE; needsNetwork false.
- Release the engine on `onTrimMemory(TRIM_MEMORY_BACKGROUND)`.
Settings card: "On this phone" with the model file name and size, plus "Remove model".
Manual: airplane mode, assign On device to "Understanding entries", log "2 manakish and tea" → items appear within ~10–30 s with an "On device" chip. Commit. **`git tag m14`.**

---

### M15 — Place-based prompts (late Prototype 1)

**Task 15.1 — Geofence CRUD**
`GeofencesRoute` lists the user's places. `GeofenceEditRoute`: label field, "Use my current location" (`LocationManager.getCurrentLocation` with the `ACCESS_FINE_LOCATION` request), a radius slider 100–500 m, and an enable switch. Persist via the stamped repo. Commit.

**Task 15.2 — Registration + exit prompt**
`GeofenceRegistrar : Reschedulable` calls, for each enabled geofence, `locationManager.addProximityAlert(lat, lng, radius, -1, pi)` with a per-id PendingIntent (`FLAG_MUTABLE` is required, because the system adds the `KEY_PROXIMITY_ENTERING` extra). Requests `ACCESS_BACKGROUND_LOCATION` with a separate explanation screen ("So Klean can ask 'anything from the food court?' as you leave. Location stays on this phone."). `GeofenceExitReceiver`: if `!intent.getBooleanExtra(KEY_PROXIMITY_ENTERING, true)` and not quiet and diet enabled → a prompt "Anything from %1$s?" with inline reply (source GEOFENCE_PROMPT) and a log row (trigger GEOFENCE, detail = geofence id). Debounce 60 min per geofence. Manual: create a 100 m place at home, walk out → prompt. You can also test with emulator mock locations via `adb emu geo fix`. Commit. **`git tag m15`.**

---

### M16 — Acceptance run on the real phone

Run each item and record PASS/FAIL with notes in `docs/acceptance-p1.md`. Commit the file.

| # | Criterion | How to verify |
|---|---|---|
| 1 | Airplane-mode inline reply < 5 s, app doesn't open | Airplane on, no AI → reply "shawarma and fries" from the shade and from the lock screen; time it with a stopwatch; `InlineReplySaveTest` passes |
| 2 | Widget updates within seconds | Log via notification → watch the widget |
| 3 | QS tile + universal capture create entries | Tile → log; Today bar → log; check source labels in DB: `adb shell run-as app.klean.debug sqlite3 databases/klean.db "select source from food_entry"` |
| 4 | Voice works; photo optional | Voice entry (offline); entry with and without a photo |
| 5 | Prompts ~6 h outside quiet hours, survive reboot | `DietPromptEngineTest` passes; `adb reboot` then `dumpsys alarm` shows the alarm; live for 2 days |
| 6 | Ignored prompt changes format, not frequency | Engine tests 3–4; manual swipe-away via the debug "fire now" |
| 7 | Learned meal times after 7 days | Engine test 7; after a week of real use, settings shows a next check-in near a real meal time |
| 8 | Background AI parsing; calories hidden by default | Configure Gemini or local server; log → items appear; calories hidden until the toggle is on |
| 9 | Delivery listener | M12 Task 12.3 commands |
| 10 | Neutral gaps; history browsable | Today shows "No entries 11:00–17:00" in neutral colours; history month → day pager |
| 11 | Unlock sheet, one-tap dismiss | M11 manual test with the real 3 h threshold overnight |
| 12 | Backup → fresh install restore | Export → `adb uninstall app.klean.debug` → install → Restore → all entries, photos and settings present; `BackupRoundTripTest` passes |
| 13 | Disabling diet removes it everywhere | `ModuleDisableTest` + manual widget/tile check |
| 14 | Cantarell, pastel M3E, follows system dark | Toggle system dark mode; inspect |
| 15 | No forbidden words | `ForbiddenCopyTest` + `scripts/check-copy.sh` |
| 16 | Tests cover scheduling, routing, inline reply, backup | `nix develop -c ./gradlew :shared:jvmTest testDebugUnitTest connectedDebugAndroidTest` → BUILD SUCCESSFUL |

`git tag p1`.

---

## 9. Risks, tradeoffs, open questions

**Risks**
- **Toolchain on NixOS:** the aapt2 override is mandatory. If the nixpkgs Android SDK lags (no platform 37 or no new enough build-tools), use the newest available compileSdk, or fall back to `sdkmanager` inside an FHS env (`buildFHSEnv`). Task 0.2 has the check.
- **AGP 9 / KMP DSL drift:** the plugin block names (`androidLibrary {}` vs `android {}`, built-in Kotlin) changed during the AGP 9 line. The plan flags each spot. Use what the compiler error says; don't downgrade AGP.
- **Process death vs unlock sheet:** dynamic screen receivers only work while the process is alive. Mitigations: battery exemption, frequent process revival (alarms, widget, tile), and a heads-up fallback. If this proves unreliable in two weeks of use, the next option is an opt-in "display over other apps" mode. That is more invasive, so ask the user first.
- **OEM battery killers** can still delay alarms. Mitigations: exact alarms + battery exemption + dontkillmyapp link + reschedule on every app open.
- **Android 14+ users can swipe the status notification away:** it is re-posted via deleteIntent. If the user finds the re-post annoying, the off switch is in settings. Watch this during the two-week trial.
- **`USE_FULL_SCREEN_INTENT`** may be revoked for non-alarm/call apps installed from Play. Sideloaded builds are fine; the fallback is heads-up.
- **Delivery package names** for Careem and Noon Food are best guesses. Verify on the phone with `adb shell pm list packages | grep -iE 'talabat|deliveroo|careem|noon'` and update `DeliveryMatcher.DEFAULT_APPS`. The list is user-editable anyway.
- **Gemini API surface** (`responseJsonSchema` vs `responseSchema`, model names) changes often. The model name is a setting, and Task 10.3 tests the request shape with MockEngine. Fix it against live docs when connecting.
- **On-device runtime churn** (MediaPipe vs LiteRT-LM) is isolated behind `AiProvider`, so it doesn't block anything else.

**Tradeoffs**
- Room entities live in Android `:core:data` and mirror the `:shared` enums/DTOs instead of moving Room into KMP. This is simpler today. The backend may use Postgres anyway, and the shared logic is what it really needs.
- One backup file holds photos as BLOBs. It's big but truly single-file, per the decision.
- No FKs, because sync robustness matters more than referential enforcement. Repositories handle cascades (tombstone the items with the entry).
- The logical day boundary is the sleep midpoint, not midnight.

**Open questions (defaults chosen; tell me to change)**
1. applicationId `app.klean`: do you own a domain you'd prefer (e.g. `com.spicymarrows.klean`)?
2. Status-notification re-post on swipe: immediate silent re-post (planned), or only at the next change?
3. Default sleep window 00:00–08:00 before onboarding. Fine?
4. Gemini default model `gemini-2.5-flash`. Use something else?
5. Gap marker threshold default 4 h. Fine?
