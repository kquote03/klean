# TASKS.md — Klean Prototype 1 task tracker

Task-level status for `PLAN.md` (§8 has the full instructions and code for each task).
Keep this file current: tick a task in the same commit that finishes it.

Legend:
- `[x]` done and verified by build + tests
- `[~]` partially done or unverified (see the note)
- `[ ]` not started

Everything ticked is merged on `main` (milestones M0–M7 and M9 are tagged).


## M0 — Tooling and empty runnable app

- [x] Task 0.1 — git init + .gitignore — main
- [x] Task 0.2 — Nix flake dev shell — main
- [x] Task 0.3 — Gradle wrapper — main
- [x] Task 0.4 — version-check script — main
- [x] Task 0.5 — version catalog — main
- [x] Task 0.6 — root build files — main
- [x] Task 0.7 — convention plugins — main
- [x] Task 0.8 — module skeletons — main

## M1 — Design system, app shell, registry, settings skeleton

- [x] Task 1.1 — Cantarell font — main
- [x] Task 1.2 — colour tokens (source of truth JSON + test) — main
- [x] Task 1.3 — typography + theme — main
- [x] Task 1.4 — shared components — main
- [x] Task 1.5 — common module — main
- [x] Task 1.6 — `ModuleId` in shared — main
- [x] Task 1.7 — Module registry (TDD) — main
- [x] Task 1.8 — App shell navigation — main
- [x] Task 1.9 — Forbidden-copy guard (TDD) — main
- [x] Task 1.10 — Settings skeleton — main

## M2 — `:shared` domain core

- [x] Task 2.1 — `:shared` build — main
- [x] Task 2.2 — UUIDv7 (TDD) — main
- [x] Task 2.3 — name-based UUID for settings (TDD) — main
- [x] Task 2.4 — time helpers + QuietHours (TDD) — main
- [x] Task 2.5 — DayWindows (TDD) — main
- [x] Task 2.6 — Diet enums — main
- [x] Task 2.7 — MealTimeLearner (TDD) — main
- [x] Task 2.8 — PromptPlanner (TDD) — main
- [x] Task 2.9 — FormatRotator + OutcomeResolver (TDD) — main
- [x] Task 2.10 — GapDetector (TDD) — main
- [x] Task 2.11 — QuickRepeatRanker (TDD) — main
- [x] Task 2.12 — DeliveryMatcher (TDD) — main
- [x] Task 2.13 — Capture contracts + DefaultCaptureRouter (TDD) — main
- [x] Task 2.14 — AI DTOs + schema — main

## M3 — Data layer + in-app diet logging

- [x] Task 3.1 — `:core:data` build — main
- [x] Task 3.2 — Entities — main
- [x] Task 3.3 — DAOs — main
- [x] Task 3.4 — Database + transact helper — main
- [x] Task 3.5 — DevicePrefs + DeviceId (TDD with Robolectric) — main
- [x] Task 3.6 — SyncStamper (TDD) — main
- [x] Task 3.7 — Synced SettingsRepository (TDD) — main
- [x] Task 3.8 — ModuleGate impl — main
- [x] Task 3.9 — PhotoStore — main
- [x] Task 3.10 — DietRepository + change dispatcher (TDD) — main
- [x] Task 3.11 — DietModule registration — main
- [x] Task 3.12 — Diet screen (today) + VM (TDD for VM) — main
- [x] Task 3.13 — Entry edit screen — main
- [x] Task 3.14 — Dashboard card — main

## M4 — Notification, inline reply, capture sheet, QS tile, widget

- [x] Task 4.1 — Channels + notification IDs
- [x] Task 4.2 — DietStatusNotifier
- [x] Task 4.3 — DietReplyReceiver (instrumented test first)
- [x] Task 4.4 — CaptureActivity + CaptureSheet + UniversalCaptureService
- [x] Task 4.5 — Universal capture bar on Today
- [x] Task 4.6 — Quick Settings tile
- [x] Task 4.7 — Glance widget
- [x] Task 4.8 — Module disable plumbing

## M5 — Prompts: quiet hours, scheduling, rotation, learning, reboot

- [x] Task 5.1 — QuietHoursService
- [x] Task 5.2 — AlarmScheduler + Reschedulable + RescheduleReceiver
- [x] Task 5.3 — Phrasing pool
- [x] Task 5.4 — PromptNotifier
- [x] Task 5.5 — DietPromptEngine (TDD with fakes)
- [x] Task 5.6 — Day rollover
- [x] Task 5.7 — Verification on device (Diet settings screen + debug tools; phone checks below)

## M6 — Voice, photo, quick repeats

- [x] Task 6.1 — VoiceInput
- [x] Task 6.2 — Photo attach
- [x] Task 6.3 — Quick repeat chips everywhere

## M7 — History and gaps

- [x] Task 7.1 — Day history
- [x] Task 7.2 — Gap affordance
- [x] Task 7.3 — Patterns placeholder

## M8 — Onboarding and permissions

- [x] Task 8.1 — Permission state model
- [x] Task 8.2 — PermissionsRoute
- [x] Task 8.3 — Onboarding graph

## M9 — Backup and restore

- [x] Task 9.1 — BackupManager.export (instrumented test first)
- [x] Task 9.2 — inspect + restore
- [x] Task 9.3 — Instrumented backup round-trip test
- [x] Task 9.4 — Backup screen
- [x] Task 9.5 — AutoBackupWorker

## M10 — AI providers, enrichment, routing

- [x] Task 10.1 — SecretStore
- [x] Task 10.2 — AiProvider interface + gateway (TDD)
- [x] Task 10.3 — Gemini provider (TDD with Ktor MockEngine)
- [x] Task 10.4 — OpenAI-compatible provider (TDD)
- [x] Task 10.5 — FoodEnrichmentWorker (TDD)
- [x] Task 10.6 — AI items UI
- [x] Task 10.7 — AI provider settings screens
- [x] Task 10.8 — AI capture classifier

## M11 — Unlock sheet

- [x] Task 11.1 — Spike on the real phone (timebox 30 min) — done on Waydroid (Android 13), see `feature/diet/src/main/kotlin/app/klean/feature/diet/unlock/NOTES.md`; Android 14+ recheck in M16; main
- [x] Task 11.2 — ScreenStateMonitor + eligibility (TDD) — `UnlockSheetPolicy` (:shared) + `feature/diet/unlock/UnlockSheetTrigger` + runtime-registered `ScreenStateMonitor`; main
- [x] Task 11.3 — UnlockSheetActivity + notification — FSI or heads-up fallback, prompt_log UNLOCK_SHEET row, Diet settings + setup switch, debug "Pretend the phone was locked for 3 h", FSI permission row (API 34+); verified on Waydroid; main

## M12 — Delivery-app trigger

- [x] Task 12.1 — DeliveryListenerService — `feature/diet/delivery/` (DeliveryRules allowlist + match words, DeliveryNotificationHandler, DeliveryPrompter 45 min debounce, prompt_log DELIVERY row with package only); debug builds also read com.android.shell; main
- [x] Task 12.2 — Contextual offer — `feature/diet/delivery/DeliveryOffer.kt` (DeliveryOfferPolicy: ≥3 entries in 30 days + an allowlisted app installed or a delivery-sounding entry today, never when access is on; Set up → listener detail settings on API 30+, Not now = 30 days, No thanks = for good), `DeliveryScreens.kt` (offer card on the Diet screen, DeliveryAppsRoute editor in Diet settings); main
- [x] Task 12.3 — Verify with a simulated notification — on Waydroid, see "Verified on Waydroid (M12)" below; main

## M13 — Polish, disable audit, accessibility, copy audit

- [x] Task 13.1 — Disable audit (main) — instrumented `ModuleDisableTest` passes on Waydroid: no DIET_* notifications, check-in + day-rollover alarms gone (FLAG_NO_CREATE), registry without diet, enrichment cancelled and not re-queued, unlock sheet and delivery prompt inactive, widget/tile disabled states. Gaps fixed: AlarmScheduler.cancel now also cancels the PendingIntent; the toggle re-arms/cancels DayRollover and cancels queued `ai-enrich` work; EnrichmentEnqueuer/Sweep respect the module gate
- [x] Task 13.2 — Accessibility pass (main) — on-device audit on Waydroid (uiautomator: every clickable ≥48 dp and labelled) of Today, Diet, entry, history, capture sheet, unlock sheet, all 7 onboarding pages and Diet/Quiet hours/AI/Permissions/Backup settings: 0 small, 0 unlabelled. Font scale 2.0 screenshots of the same screens: fixed the capture field placeholder cut to "What did y…" (field now wraps up to 4 lines, Enter still saves), onboarding Skip/Next buttons of unequal height when a label wraps, and the entry date/time chips (FlowRow). Widget compact add button 40→48 dp. TalkBack isn't installed on Waydroid, so Compose semantics tests stand in: `CaptureFieldA11yTest` (labels, 48/56 dp, placeholder line limit, Enter submits) and `DietScreenA11yTest` (every clickable labelled with ≥48 dp touch bounds). TalkBack walk-through left for the real phone (M16)
- [x] Task 13.3 — Copy audit (main) — ForbiddenCopyTest PASS (now also `userCopyIsCalm`: no `!`, no missed/streak/behind/forgot/overdue/failed to log in any strings.xml text); plan greps and `scripts/check-copy.sh` (extended with both checks) find nothing; all 8 strings.xml read for tone, no changes needed
- [x] Task 13.4 — Appearance + About (main) — "Use wallpaper colours" (DevicePrefs.dynamicColor, now off by default, hidden below API 31) applied in every activity via `KleanThemeFromPrefs` and in the Glance widget (`DynamicThemeColorProviders`, widgets repainted through `WidgetRefresher`); About shows the version (0.1.0-121-e85bdc0 on Waydroid), the offline note, the Cantarell OFL text from `core/design` assets and a short open-source list. Verified on Waydroid (teal Material You scheme when on); the widget repaint is untested on Waydroid (no widget host placement)

## M14 — On-device model

- [x] Task 14.1 — Runtime choice spike (timebox 1 h) (main) — LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.19.1` (stable, Google Maven, 2026-11-05) over MediaPipe tasks-genai; API checked with javap and compiled behind `InferenceEngine`/`LiteRtLmEngineFactory` (CPU backend). Native libs arm64-v8a + x86_64 only (~+20 MB compressed per universal APK). Default download Qwen3-0.6B `.litertlm` (~614 MB, ungated) because Gemma 3 1B is gated on Hugging Face. Notes in `core/ai/ON_DEVICE.md`
- [x] Task 14.2 — OnDeviceProvider (main) — `core/ai/ondevice/`: `OnDeviceProvider` (OFFLINE, needsNetwork false, 180 s timeout, lazy engine on Dispatchers.Default, schema in the prompt + `extractJsonObject` with one retry, `<think>` stripped, released after 2 min idle or on TRIM_MEMORY_BACKGROUND), `OnDeviceModels` (noBackupFilesDir/models, one model, SAF import), `ModelDownloader` + `ModelDownloadWorker` (WorkManager, unmetered, resumable via Range, refuses files without a model header). Settings > AI providers > On device: Use this provider, model name + size, Try it, Remove model, editable download URL (default Qwen3-0.6B, ~614 MB), Choose a model file. Unit tests: OnDeviceProviderTest 17, ModelDownloaderTest 8, OnDeviceViewModelTest 7, gateway per-provider timeout. Waydroid: download from a host HTTP server (404 → calm "server wouldn't give out that file"; 25-byte file → "isn't a model"; 3 MB headerless file → refused before loading; 3 MB `LITERTLM`+random → installed, native LiteRT-LM loaded on x86_64 and threw, Try it shows "This model can't run on this phone", no crash); On device assignable to Understanding entries and Sorting quick capture; logging "2 manakish and tea" with it assigned saves at once and enrichment fails quietly; Remove model clears it. Found and fixed on Waydroid: a headerless file aborted the process in the native loader (header check + crash marker). APK +53 MB (uncompressed .so, arm64 + x86_64). Real inference on a real model is left for the phone (M16)

## M15 — Place-based prompts

- [x] Task 15.1 — Geofence CRUD (main) — `core/data/place/PlaceRepository` (UUIDv7, SyncStamper in `db.transact`, tombstone delete, radius clamped 100–500 m; 5 Robolectric tests). Diet settings > Places: list with per-place switch, Add a place / edit (name, Use my current location via LocationManager.getCurrentLocation with the fine-location request just in time, or typed lat/lng, radius slider 100–500 m in 50 m steps, Ask when I leave, Delete place). No map, no Play services. `PlaceEditViewModelTest` 4. Waydroid: added "Food court" from a mock GPS fix (`cmd location providers add-test-provider gps`); without a fix the screen says it couldn't find a location and offers typed coordinates; uiautomator audit 0 small / 0 unlabelled
- [x] Task 15.2 — Registration + exit prompt (main) — `feature/diet/geofence/`: `ProximityAlertBackend` (LocationManager.addProximityAlert, per-place mutable PendingIntent), `GeofenceRegistrar` (Reschedulable; registers enabled places only when diet is on and fine location is granted, unregisters the rest incl. tombstoned), `GeofenceExitHandler` (exit only, diet on, place on, not quiet, 60 min debounce per place; prompt_log GEOFENCE + place id) → PromptNotifier "Anything from %1$s?" (id GEOFENCE, reply source GEOFENCE_PROMPT), `GeofenceExitReceiver`. Places screen asks for location, then explains and opens settings for Allow all the time; Settings > Permissions has a Location all the time row. Diet off unregisters every place (DietEnablement + ModuleDisableTest). Tests: GeofenceTest 8, DietEnablementTest +1, PromptNotifierTest +1, PermissionModelTest +1, ModuleDisableTest extended (passes on Waydroid). Waydroid, real path: system GeofencingService listed `Geofence[(25.197197, 55.279638) 150.0m]`; moving the mock GPS ~2.7 km away posted "Anything from Food court?" (id 1005) and wrote prompt_log GEOFENCE/PENDING with the place id; debug Simulate leaving then showed the 60 min debounce; diet off removed the registration and the prompt, on re-registered it. Plan's `adb emu geo fix` doesn't exist on Waydroid; the test provider replaces it. Walking out of a real place is left for the phone (M16)

## Today dashboard (`.hermes/plans/2026-11-09_153251-today-dashboard-and-p1-closeout.md`)

- [x] A1 — Dashboard contracts (`core/ui/module/Dashboard.kt`: `DashboardSummaryProvider`, `DayActivityProvider`, `DashboardContributions` filtered by the module gate; `ModuleId.CORE`) — main
- [x] B1 — Greeting and week model (`app/today/TodayModel.kt`) — main
- [x] C1 — `SummaryTile` and `WeekOverview` in `:core:design` (Robolectric `DashboardComponentsTest`: 48 dp targets, labelled days, no zero badges, future days inert) — main
- [x] D1–D5 — `TodayDashboardViewModel` (4 tests), shell wiring, rebuilt `TodayScreen` (greeting header, tiles, week strip), strings, 3 new `ShellSmokeTest` cases — main
- [x] E1 — Diet tiles and week activity (`feature/diet/ui/dashboard/DietDashboardProviders.kt`, 6 tests; shared `knownCalories` and `observeCountsByLogicalDay` now also used by Diet and History) — main
- [x] E2 — Slim down the Diet dashboard card — nothing to do: the card has no "N today" line (it shows the last 5 entries and the log field)
- [x] E3 — Quiet-hours tile (`app/today/QuietHoursSummary.kt`, `ModuleId.CORE`, bound in `AppBindings.kt`; 3 tests) — main
- [x] E4 — Waydroid check of the dashboard (see "Today dashboard (Waydroid)" under Device checks) — main

## M16 — Acceptance run on a real phone

- [ ] Run every acceptance criterion in PLAN.md §8 M16 on a real phone; record results in `docs/acceptance-p1.md`

## Device checks (Pixel 9a, Android 17 / API 37, connected wirelessly)

Verified on the phone on 2026-11-08:
- [x] `connectedDebugAndroidTest`: 3/3 pass. `InlineReplySaveTest` (the reply saves with no activity in front) and both `BackupRoundTripTest` cases.
- [x] 0.8: the app installs and launches (`am start`, about 1.1 s cold).
- [x] 4.2: the status notification is posted (ongoing, silent, 2 actions, private + public version).
- [x] Lock screen shows "What did you eat? · Nothing logged yet today". This first failed: Pixel hides LOW-importance notifications on the lock screen. Fixed in `2bd24a5` (channel `diet_status_v2`).
- [x] 4.3: the user's inline reply from the notification saved with source `NOTIFICATION_REPLY` and sync columns stamped. Not yet repeated in airplane mode, because adb is wireless.
- [x] 1.8 / 3.11: bottom bar Today / Diet / Settings, dark pastel theme, Cantarell.
- [x] 3.12: logging from the Diet screen updates the list, the quick-repeat chips and the status notification ("2 today · last: … at 7:30 PM"). Neutral gap marker shown.
- [x] 3.13: entry edit screen; delete shows "Entry removed · Undo" and leaves a tombstone (`deleted=1`).
- [x] 4.4 / 4.6: QS tile opens the capture sheet over the launcher with the keyboard up; the save has source `QS_TILE` and the sheet closes itself.
- [x] 4.8: Diet off removes the nav item, dashboard card and status notification; on brings them back. Two bugs found and fixed in `28bf4cd`: the toggle reset navigation to Today, and tapping the disabled module's Settings row crashed (route not found).

Verified on the phone on 2026-11-08 (M5):
- [x] 5.2: after `installDebug` (MY_PACKAGE_REPLACED), `dumpsys alarm` shows `PromptAlarmReceiver` at 2026-11-09 09:00 (inexact, 1 h window) and `DayRolloverReceiver` at 04:00
- [x] 5.2: `appops set app.klean.debug SCHEDULE_EXACT_ALARM allow` re-armed the check-in as exact (`window=0 exactAllowReason=permission`) through the permission-change broadcast
- [x] 5.2: the debug-only `adb shell am broadcast -a app.klean.debug.RESCHEDULE -p app.klean.debug` is accepted (result=0)
- [x] 5.7: "Fire a check-in now" #1 → STANDARD on `diet_prompt` (importance 3), "Anything you'd like to jot down?", timeout 2 h, delete intent, Log food + Capture… actions
- [x] 5.7: firing again (first one unanswered → IGNORED) → HEADS_UP on `diet_prompt_heads_up` (importance 4) with different wording ("What did you eat?")
- [x] Diet settings screen shows "Next check-in around 9:00 AM"
- [x] 5.7: four more fires in a row rotate SILENT_VIBRATE (`diet_prompt_vibrate`) → WIDGET_HIGHLIGHT (no notification; status title became "Quick food note?") → STANDARD → HEADS_UP, with new wording each time
- [x] 4.7: the widget the user placed shows today's entries newest first. Saving through its pill showed up within about 2 s. Its card was green (glance `widgetBackground` = `secondaryContainer`); fixed in `c0982fa` to use the token `surfaceContainer` + a lavender pill, checked by screenshot
- [x] 4.8: with Diet off, the widget shows "Klean · Open" and SystemUI reports the tile as `label=Capture, state=0` (unavailable). After turning it back on: `Log food, state=2`, the status line and alarms return
- [x] 3.12: quick-repeat chips go away after their entry is deleted on another screen; fixed in `3db8bed`, checked on device
- Reboot: covered by Robolectric `RebootRescheduleTest` (BOOT_COMPLETED through the real Hilt graph arms the alarm); `adb reboot` not run because it drops wireless adb

Verified on Waydroid (LineageOS, Android 13 / API 33, 540×1212, `192.168.240.112:5555`) on 2026-11-08 (M6/M7):
- [x] `connectedDebugAndroidTest`: 3/3 pass (`BackupRoundTripTest` ×2, `InlineReplySaveTest`)
- [x] 6.1: first mic tap shows the system RECORD_AUDIO dialog with the one-line rationale under the field; "Don't allow" → "Mic access is off…"
- [x] 6.1: with the permission, the mic starts the recogniser (`KleanVoice: voice start mode=ON_DEVICE`). On Waydroid the on-device service fails to bind (`Bind to system recognition service failed with error 10`) and never calls back, so the screen hung on "Listening…". Fixed in `278b35b`: after 3 s it falls back to the default recogniser (Google offline "Soda" became ready), and a watchdog ends listening calmly. With no audio input it ends with "Didn't catch that…"
- [x] 6.1: Today's mic opens the capture sheet already listening (same path as the widget's mic)
- [x] 6.2: the photo menu (Take photo / Choose from gallery) opens on the capture sheet and the entry screen. The gallery opens the system photo picker; backing out changes nothing. "Take photo" launched the camera app (Aperture), but Waydroid has no camera: the camera hung and then took Waydroid's window system down (`DEAD_OBJECT`), which needed a `waydroid session` restart. Klean didn't crash (crash buffer empty)
- [x] 6.3: chips read "Log … again" and are 48 dp tall (a Compose test caught them at 36 dp)
- [x] 7.1: History (calendar icon) shows a neutral month grid: today outlined, "1" under days with entries, nothing on empty days, next month disabled. Tapping a day opens the pager; swiping moves between days; past-day gaps are closed ("No entries 8:00 AM–12:00 AM")
- [x] 7.2: "Add something" on a gap opens the sheet with "For around 2:45 PM · Change" (the time picker opens on 2:45). Saving logs at 2:45 PM. From a past day it reads "For around Nov 7, 2026, 4:00 PM", and History then shows "November 7, 2026, 1 entry"
- [x] Polish: Delete on the entry screen returns to the list, which shows "Entry removed · Undo". Undo restores the entry. The first try showed no snackbar; fixed in `f262c5a`

Verified on Waydroid on 2026-11-09 (M8):
- [x] `connectedDebugAndroidTest`: 3/3 pass
- [x] 8.3: installing over the M7 install (entries present, quick-repeat chips visible) opens on Today with no wizard
- [x] 8.3: after `pm clear`, launch opens the wizard full screen (no bottom bar): Welcome → What to use → When you sleep → Diet → Home screen widget → Permissions → AI help, "N of 7", Skip and Next side by side at equal width
- [x] 8.3: sleep window set to 11:00 PM–7:00 AM in the wizard is saved (Settings › Quiet hours shows it). Moving it to 10 AM–1 PM moved Diet settings' "Next check-in around 11:34 AM" to 2:00 PM at once; restoring brought it back
- [x] 8.3: "Add widget" opens the launcher's pin dialog (Waydroid supports `requestPinAppWidget`); Cancel returns to the wizard
- [x] 8.3: the permissions page lists Notifications, Alarms and reminders, Battery use only (no Notification access); statuses match `dumpsys package`/`deviceidle`. Done lands on Today; Back from Today leaves the app; relaunch opens Today
- [x] 8.2: Settings › Permissions after `pm revoke POST_NOTIFICATIONS` shows Notifications Off with "Turn on"; Notification access Off
- [x] 8.3: Settings › Run setup again → Welcome → Skip setup → Today

Verified on Waydroid on 2026-11-09 (M10), with a fake OpenAI-compatible server on the host (`~/.hermes/cache/scratch/m10/fake_openai.py`, `http://192.168.240.1:8765/v1`):
- [x] `connectedDebugAndroidTest`: 4/4 pass, including `SecretStoreDeviceTest` (real Android Keystore AES-GCM round trip; stored value is ciphertext with a fresh IV per write)
- [x] 10.7: the wizard's AI page shows "Set up AI providers", which opens Settings › AI providers (three cards, all Off; Use for = None). Gemini screen shows the consent text, masked key field, `gemini-2.5-flash`; Test connection while off → "Switch this provider on and fill in its details first." with no request sent
- [x] 10.4/10.7: Local server with the LAN URL over plain HTTP + model `fake-model`, switched on → card shows On; Test connection → "Works. It read: toast · 2 slices, butter · 1 pat" (server log shows the plan's json_schema body)
- [x] 10.5/10.6: Understanding entries → Local or custom server; logging "shawarma and fries" shows the entry at once, then "shawarma · 1 wrap", "fries · medium" and a "Local server" chip under it; no calories until Diet settings › Show calories is on, then "· ~450 kcal" per item and "~770 kcal so far" under the Diet title
- [x] 10.6: entry screen lists the items with Change / Not right and "Remove AI details"; Not right on fries hides it in the entry, the row and the day total
- [x] 10.5: with the server stopped, "tea with mint" appears in the list at once; the worker logs `Worker result RETRY`; after restarting the server the retry ran 30 s later (exponential backoff) and the items appeared. Crash buffer empty

Verified on Waydroid on 2026-11-09 (M12). Post with the whole command quoted, e.g. `adb shell "cmd notification post -S bigtext -t 'Talabat' tag 'Your order has been delivered'"`: unquoted, `adb shell` splits the words and the text arrives as just "Your":
- [x] `connectedDebugAndroidTest`: 4/4 pass; gate BUILD SUCCESSFUL (427 unit tests, 0 failures)
- [x] 12.2: after `pm clear` + Skip setup, the Diet screen showed no offer; after 3 entries ("talabat burger" among them, since no delivery app is installed on Waydroid) the calm "Food deliveries" card appears under the chips with No thanks / Not now / Set up. No thanks hides it; still hidden after force-stop + relaunch. The wizard never shows it
- [x] 12.2: Set up opens `Settings$NotificationAccessDetailsActivity` for "Klean delivery check-ins" (API 33 detail screen); allowing there connects the listener (`delivery listener connected`), and back on the Diet screen the card is gone
- [x] 12.2: Settings › Permissions shows Notification access On; Diet settings › Delivery apps lists the 4 built-in packages; "notapackage" → "That doesn't look like a package name…"; adding the word "rider dropped" makes "The rider dropped your bag" count (log `delivery from com.android.shell`)
- [x] 12.3: a delivered post → heads-up "What did you order?" within ~1 s (channel `diet_prompt_heads_up`); inline reply "chicken shawarma wrap" → "Saved: …", saved with source `DELIVERY_PROMPT`, prompt_log outcome LOGGED
- [x] 12.3: a second post right away → `asked=false`, no new prompt and no new prompt_log row (45 min debounce); "Your order is on the way" → not a delivery (no log line, no prompt)
- [x] 12.3: package not on the allowlist: a temporary build without the debug `com.android.shell` extra ignored the same post (listener live, no log line, no prompt); the restored build matched it again
- [x] 12.3: debug `EXPORT_DB` broadcast + `sqlite3`: prompt_log holds one row `DELIVERY | com.android.shell | LOGGED`; `grep -a` on the exported file finds no "delivered", "Talabat", "Your order" or "on the way" (the only "talabat" is the hand-typed entry "talabat burger")

Today dashboard (Waydroid, Android 13, 540×1212, en_US so the week starts on Sunday) on 2026-11-09. Screenshots in `~/.hermes/cache/scratch/today-e4/`:
- [x] After Skip setup, Today shows "Good afternoon" / "Monday, November 9, 2026" (no top bar), the capture field, tiles "Today · No entries yet" and "Next check-in · around 9:55 PM", "This week" with 7 cells labelled "<full date>, nothing logged", and the Diet card. uiautomator audit: 0 small, 0 unlabelled
- [x] Logging "toast" and "chicken shawarma" from Today updates the tiles at once ("2 entries", "Last entry · chicken shawarma · 3:56 PM") and today's cell ("Monday, November 9, 2026, 2 things logged"; "2" under the 9, nothing under empty days)
- [x] Moving an entry to Nov 8 on the entry screen puts "1" under Sunday 8; tapping that cell opens the Diet day screen for Nov 8; tapping today's cell opens Diet; Back returns to Today
- [x] Previous week → "Week of Nov 1" with the next arrow enabled; Next → "This week" again with the next arrow disabled
- [x] Settings › Modules › Diet off → Today shows only the greeting, the capture field and "Nothing here yet. Turn on a module in Settings."; no tiles, no week strip, no Diet tab. Diet on brings them back
- [x] Dark mode (`cmd uimode night yes`, `today-dark.png`): pastel dark scheme, readable tiles and day cells, today outlined in lavender
- [x] Font scale 2.0 (`today-font2-top.png`, `today-font2-week.png`): tiles wrap and the day numbers fit (they step down to titleSmall above 1.5×); audit 0 small / 0 unlabelled. Found and fixed (`2683653`): tiles in one FlowRow row had different heights beside a two-line value; rows now share the tallest tile's height
- [x] Crash buffer empty. The quiet-hours tile wasn't seen on the device (it was 4 PM, outside quiet hours); `QuietHoursSummaryTest` covers it, and F2 sets quiet hours over now

Waydroid close-out (Android 13), 2026-11-09 (plan Part F):
- [x] F8 — the four diet VM test classes (`DeliveryAppsViewModelTest`, `PlaceEditViewModelTest`, `DietDashboardViewModelTest`, `DietSettingsViewModelTest`) track their view models in a `ViewModelStore` and clear it in `@After` before `resetMain()`/`db.close()`; `:feature:diet:testDebugUnitTest --rerun` 3× in a row: BUILD SUCCESSFUL, 177 tests, 0 failures
- [x] F5 — compact widget (4.7): pinned from Run setup again › Home screen widget › Add widget (3×2, `widget-3x2.png`); long-press shows the resize frame and the handles drag. One row on this launcher is 137 dp tall, so 3×1 keeps the medium layout (pill, mic, 2 entries, no clipping, `widget-1row.png`). 2×1 (146 dp wide) switches to the compact layout. Found and fixed: it read "3 today · last: b…", hiding the latest entry; now the entry leads (up to 3 lines) with "N today" under it and the add button beside it: "grilled halloumi wrap / 2 today / +" with no clipping (`DietWidgetCompactTextTest`, 3 tests; `widget-compact-fixed.png`). Screenshots in `~/.hermes/cache/scratch/f5/`
- [x] F6 — inline reply in airplane mode (4.3, Waydroid stand-in): `cmd connectivity airplane-mode enable` (adb stayed up; status bar shows Airplane mode), shade › status notification › Log food › "airplane probe" › Send → notification text "Saved: airplane probe"; food_entry row `airplane probe | NOTIFICATION_REPLY`, `created_at` 251 ms after the Send tap; focus stayed on NotificationShade, then `QuickstepLauncher` after collapse (no Klean activity). Airplane mode off again (`~/.hermes/cache/scratch/f6/timing.txt`). The lock-screen reply stays a Pixel check
- [x] F7 — adaptive launcher icon (`app/src/main/res/drawable/ic_launcher_{foreground,background}.xml`, `mipmap-anydpi-v26/ic_launcher{,_round}.xml` with a monochrome layer): a lavender `#4C3F8B` bowl and spoon on `#E6DEFF`; the Waydroid app drawer shows it (`~/.hermes/cache/scratch/f7-drawer.png`)
- [x] F9 — `connectedDebugAndroidTest` on Waydroid: BUILD SUCCESSFUL, 6/6 pass (`BackupRoundTripTest` ×2, `ModuleDisableTest` ×2, `SecretStoreDeviceTest`, `InlineReplySaveTest`); app reinstalled, notifications granted, setup skipped (Diet on, quiet hours 12:00–8:00 AM, both alarms armed)
- [x] F10 — CI flakes: run 42610872340 (`DevicePrefsTest`, "multiple DataStores active for the same file") fixed in `f9ecc5d` (wait for the old DataStore's scope to complete before reopening its file; reproduced 41/300 → 0/300); run 42612864484 (`:feature:onboarding:kspDebugKotlin`, "'DevicePrefs' could not be resolved") fixed in `f3a6114` (concurrent in-daemon KSP2 runs; every ksp* task now shares a `maxParallelUsages = 1` build service; reproduced 4/8 → 0/8)
- [x] F1 — reboot rescheduling (5.2): `waydroid session stop`/`start` with the app idle in the background → logcat `Start proc …:app.klean.debug for broadcast {app.klean.debug/app.klean.core.notifications.RescheduleReceiver}` and no activity start; `dumpsys alarm` lists `PromptAlarmReceiver` (origWhen 2026-11-09 21:56:40, exact) and `DayRolloverReceiver` (2026-11-10 04:00) again (`~/.hermes/cache/scratch/f1/`). Waydroid freezes the container (`waydroid status` → `Container: FROZEN`, adb hangs) after a session restart until `waydroid show-full-ui` runs
- [x] F2 — quiet hours (5.7): Settings › Quiet hours 4:35–6:40 PM at 16:39 + debug RESCHEDULE → check-in still at 21:56 (after the quiet end), Today shows the tile "Quiet hours, until 6:40 PM"; wake moved to 11:00 PM → check-in moved from 21:56 to 2026-11-10 00:00; restored 12:00–8:00 AM → back to 21:56, rollover 04:00 (`~/.hermes/cache/scratch/f2/`)
- [x] F3 — swipe then rotate (5.7): fire #1 → `diet_prompt` (importance 3) "Anything you'd like to jot down?"; swiped away in the shade; fire #2 → `diet_prompt_heads_up` (importance 4) "Quick food note?"; EXPORT_DB prompt_log: `STANDARD|DISMISSED`, then `HEADS_UP|PENDING` (`~/.hermes/cache/scratch/f3/`)
- [x] F4 — backup through the picker (9.4): logged "backup probe", Export → DocumentsUI › Download › `klean-backup-2026-11-09.sqlite` (135 KB); deleted the probe and set Sleep from 1:00 AM; Restore → confirmation "Replace everything on this phone? This deletes the 3 entries, photos and settings currently in Klean and replaces them with the backup from Nov 9, 2026 (4 entries, 0 photos). This can't be undone." (plan text) → Replace → process restarted on Today with "backup probe" back and quiet hours 12:00 AM. Fresh install: attached a gallery photo (pushed PNG, photo picker), exported `klean-photo-probe.sqlite` (176 KB), `adb uninstall` + install, Skip setup, restore → 4 entries and the photo thumbnail back. Found and fixed: the dialog said "1 photos"; counts now use plurals (`RestoreConfirmTextTest`, 2 tests) and the device shows "(4 entries, 1 photo)" (`~/.hermes/cache/scratch/f4/`)

Still needs hands-on checks by the user (airplane mode or a reboot would drop wireless adb):
- [ ] 4.3: inline reply from the lock screen in airplane mode on the Pixel, under 5 s (shade reply in airplane mode passed on Waydroid, F6)
- [ ] 12.3: a real delivery from Talabat/Deliveroo/Careem/noon: its "delivered" wording matches and the offer card appears with the app installed (Waydroid has no delivery apps)
- [ ] 5.2: after a real phone reboot, `dumpsys alarm` shows the check-in alarm (Waydroid container restart passed, F1)
- [ ] 6.1: on a real phone, speak "two manakish" (also in airplane mode) → saved with `via_voice=1`, and Undo shows for 5 s; where offline voice is missing → "Voice isn't available offline on this phone"
- [ ] 6.2: take a real camera photo (capture sheet and entry screen) → thumbnail in the row, EXIF stripped; log one without a photo too
- [ ] 10.3: Gemini with a real API key (covered only by MockEngine tests and the screen rendering; no key was used)
- [ ] 10.5: a photo entry sent to a provider with "Send photos too" / "This model understands images" on (needs a camera photo)
- [x] Polish: the bottom bar highlights Diet on History / Day / Entry screens (diet routes live in a nested `DietGraph`; the bar matches `KleanModule.graphRoute`)
- [x] Polish: after deleting from the entry screen it now goes back to the list, which shows "Entry removed · Undo" for 5 s

## P2 — Life modules (`.hermes/plans/2026-11-09_170500-parallel-life-modules.md`)

Schema v2 is frozen for the streams (decision 1). Each stream ticks only its own subsection, on its own branch.

### P0 — Foundation (branch `p2-foundation`, then main; tag `p2-foundation`)

- [x] P0.1 — skeleton modules `:feature:{tasks,reminders,calendar,contacts,workout,sleep}`, wired into `:app` — p2-foundation
- [x] P0.2 — schema v2: 9 entities + DAOs, `AutoMigration(1 → 2)`, `2.json`, `MigrationV2Test` (v1 fixture migrates) — p2-foundation
- [x] P0.3 — `ReminderRepository` + `ReminderChangeListener` (`@Multibinds`) in `:core:data` — p2-foundation
- [x] P0.4 — new modules start off (`core.modules.known`, `seedNewModules`, `ModuleRegistry.seed()` at startup) — p2-foundation
- [x] P0.5 — bottom bar: Today + 3 modules + More (`NavLayout`, `MoreRoute`/`MoreScreen`) — p2-foundation
- [x] P0.6 — Klean Android account (stub authenticator + calendar/contacts sync adapters, debug `ENSURE_ACCOUNT`) — p2-foundation
- [~] P0.7 — `NaturalTime` parser in `:shared` — branch `p2-parsers`, not merged yet
- [~] P0.8 — `RuleCaptureClassifier` + `CompositeCaptureClassifier`; birthdays never reach AI — branch `p2-parsers`, not merged yet
- [x] P0.9 — this section — p2-foundation

### S1 — Tasks (`feature/tasks`, branch `p2-tasks`)

- [ ] S1.1 — `RecurrenceRule` in `:shared` (S1 owns it)
- [ ] S1.2 — default lists Personal and Work on first enable (`TasksKeys.seeded`)
- [ ] S1.3 — screens: `TasksRoute` (list chips, Done section, undo), `TaskEditRoute` (incl. reminders section)
- [ ] S1.4 — daily "Still want these?" review (`reviewNeeded`, `TaskReviewRoute`, debug `TASK_REVIEW`)
- [ ] S1.5 — capture `add_task`
- [ ] S1.6 — Today card, tile, activity
- [ ] S1 common — `TasksKleanModule`, repository + tests, settings screen, `TasksDisableTest`, Waydroid walk, gate

### S2 — Reminders (`feature/reminders`, branch `p2-reminders`)

- [ ] S2.1 — `ReminderScheduler` (`ReminderChangeListener` + `Reschedulable`), `nextFireTime` tests
- [ ] S2.2 — ping notification: Done, Snooze 10 min / 1 h, Tomorrow, "Mark task done"
- [ ] S2.3 — full alarm: `AlarmActivity`, looping alarm sound, main-phone flag (`MainPhonePrefs`), 10 min auto-stop
- [ ] S2.4 — screens: `RemindersRoute`, `ReminderEditRoute`, settings
- [ ] S2.5 — capture `add_reminder`
- [ ] S2.6 — Today card, tile, activity
- [ ] S2 common — `RemindersKleanModule`, tests, `RemindersDisableTest`, Waydroid walk (incl. restart re-arm), gate

### S3 — Calendar (`feature/calendar`, branch `p2-calendar`)

- [ ] S3.1 — `DeviceCalendarSource` (read-only `Instances`, behind `DeviceCalendarQuery`)
- [ ] S3.2 — `KleanCalendarMirror` (Klean calendar under the Klean account, `provider_event_id`)
- [ ] S3.3 — screens: `CalendarRoute` (agenda + month), `EventEditRoute`
- [ ] S3.4 — capture `add_event`
- [ ] S3.5 — `DayItemsProvider` multibinding (own module; reconciled with S4 at merge)
- [ ] S3.6 — Today card, tile, activity
- [ ] S3 common — `CalendarKleanModule`, tests, `CalendarDisableTest`, Waydroid walk, gate

### S4 — Contacts + Birthdays (`feature/contacts`, branch `p2-contacts`)

- [ ] S4.1 — `ContactRepository` (JSON phones/emails, search, birthday validation)
- [ ] S4.2 — `SystemContactsMirror` (`ContactsProviderOps`)
- [ ] S4.3 — VCF import/export (`vcf/Vcf.kt`, fixtures)
- [ ] S4.4 — `BirthdayScheduler`, `nextBirthday`; no AI anywhere in the module
- [ ] S4.5 — screens: `ContactsRoute`, `ContactEditRoute`, `BirthdaysRoute`, settings
- [ ] S4.6 — capture `add_birthday` (rules only)
- [ ] S4.7 — calendar contribution + BIRTHDAYS activity
- [ ] S4.8 — Today card, tile
- [ ] S4 common — `ContactsKleanModule` + `BirthdaysKleanModule`, tests, `ContactsDisableTest`, Waydroid walk, gate

### S5 — Workout (`feature/workout`, branch `p2-workout`)

- [ ] S5.1 — `WorkoutItem` model
- [ ] S5.2 — `TemplateText` parser/formatter (8-row table test)
- [ ] S5.3 — screens: `WorkoutRoute`, `TemplateEditRoute`, `SessionRoute`, `SessionDetailRoute`
- [ ] S5.4 — capture `log_workout`
- [ ] S5.5 — gentle suggestion after quiet days (off by default)
- [ ] S5.6 — Today card, tile, activity
- [ ] S5.7 — `HealthConnectBridge` no-op (deferred)
- [ ] S5 common — `WorkoutKleanModule`, tests, `WorkoutDisableTest`, Waydroid walk, gate

### S6 — Sleep (`feature/sleep`, branch `p2-sleep`)

- [ ] S6.1 — `SleepRepository` (`goToBed`, `wakeUp`, `duration`, `lastNight`)
- [ ] S6.2 — one-tap surfaces: Today tile via `SleepToggleRoute`, optional ongoing notification, capture `log_sleep`
- [ ] S6.3 — bedtime reminder (off by default)
- [ ] S6.4 — screens: `SleepRoute`, `SleepEditRoute`
- [ ] S6.5 — `SleepImportSource` no-op (deferred)
- [ ] S6 common — `SleepKleanModule`, tests, `SleepDisableTest`, Waydroid walk, gate

## M16 — Holistic re-centre (`.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md`)

- [x] M16.1 — launcher and status icons: a day ring with a check, no food (`7a8b8c3`) — main
- [x] M16.2 — QS tile "Add to Klean"; universal capture placeholder; module sheets keep their own (`KleanModule.capturePlaceholderRes`, `1a69c98`) — main
- [x] M16.3 — `StatusFragment` / `StatusLineProvider` + `DashboardContributions.statusLine(max)` (gate-filtered, priority order; `DashboardContributionsTest` +2) — main
- [x] M16.4 — `KleanStatusNotifier` (`core/capture/status/`): id 1001, channel `status_v1` (`diet_status_v2` retired), title "Today", fragments joined with " · " or "Nothing planned. Add anything here.", public "Today · N items", one "Add something" inline reply → `StatusReplyReceiver` → universal capture (plain text falls back to diet) → subText "Saved to Diet" until the line changes; opens `klean://today`; follows `statusLine()` (debounced 500 ms) and the new `StatusRefresh` hook (`StatusSurface` multibinding). Diet is now `DietStatusLine` ("N food entries", "last: …" only when alone, highlight wording first); `DietReplyReceiver` keeps check-in replies only. Tests: `KleanStatusNotifierTest` 10, `StatusReplyReceiverTest` 2 (Hilt, no activity started), `DietReplyReceiverTest` 2, `ChannelRegistrarTest` +1; `InlineReplySaveTest` now drives `StatusReplyReceiver` — main
- [x] M16.5 — `DashboardSlot` = APPROVALS, TASKS, AGENDA, BIRTHDAYS, SLEEP, WORKOUT, DIET; `arrangeCards` (`core/ui/module/CardLayout.kt`, `CardLayoutTest` 4); `DevicePrefs.cardOrder`/`hiddenCards`; `TodayLayoutStore` (`app/today/TodayLayout.kt`); Today overflow › "Arrange Today" (`ArrangeTodayScreen`: 48 dp up/down arrows "Move <name> up/down", "Show <name>" switch, Done); `ArrangeTodayViewModelTest` 4; `ShellSmokeTest.hidingTheDietCardKeepsTheDietNavItem` — main
- [x] M16.6 — Diet `navOrder` 60 (H5); `seedNewModules` now keeps new modules **on** (replaces P0.4's seed-off) and returns `SeedResult.pending` for existing installs (`known` non-empty); `ModuleGate.seedNew`/`ModuleRegistry.seed()` return it, `KleanApp` stores it in `DevicePrefs.pendingNewModules`; `NewModulesSheet` ("New in Klean", one "Use <name>" switch per module, Done/dismiss clears) on Today; debug `app.klean.debug.NEW_MODULES [--es ids a,b]`. Tests: `ModuleSeedingTest` 5, `SettingsModuleGateTest` updated, `ShellSmokeTest.newInKleanListsNewModulesOnceAndCanTurnOneOff` — main
- [x] M16.7 — `TodayItem` / `TodayItemsProvider` + `TodayItems` (gate-filtered, soonest first) in `Dashboard.kt`; Glance "Klean Today" widget (`app/widget/TodayWidget.kt`: date header, up to 5 rows opening their deep link, "Add anything" pill → universal capture sheet; compact = pill + "N coming up"; empty "Nothing coming up"), `today_widget_info.xml` + preview; `TodayWidgetRefresher` is a `StatusSurface`, so `StatusRefresh` updates it; the wizard's widget page offers the Today widget first, then each enabled module's widget ("Add <label>"). `TodayWidgetStateTest` 3 — main
- [x] M16.8 — wizard order: welcome, modules, sleep schedule (renamed), main phone, permissions, widget, AI, then module pages (diet preferences, order 900) last. `OnboardingStepProvider` (`core/ui/module/OnboardingStepProvider.kt`, `@IntoSet`, orders documented for contacts 600 / Google 800 / pairing 810 / Hermes 820 / watch 830); `MainPhoneStep` (`app/setup/`, order 300, writes `DevicePrefs.mainPhone`). Tests: `OnboardingViewModelTest` +1 (extras by order, diet last), `OnboardingFlowTest` walks the new order — main
- [x] M16.9 — gate BUILD SUCCESSFUL (619 unit tests, 0 failures; `AppearanceViewModelTest` timed out once under load and passed on the re-run); `connectedDebugAndroidTest` on Waydroid 6/6 (`InlineReplySaveTest` through `StatusReplyReceiver`, `ModuleDisableTest` ×2, `BackupRoundTripTest` ×2, `SecretStoreDeviceTest`) — main

Verified on Waydroid (Android 13, 540×1212) on 2026-11-09 (M16), screenshots in `~/.hermes/cache/scratch/m16/`:
- [x] Fresh install wizard: "2 of 8" What to use → Sleep schedule → Main phone ("Is this your main phone? Alarms ring only on your main phone.") → Permissions → Home screen widget ("Add Klean Today", "Add Food today") → AI help → Diet preferences last (`02`, `03`, `05`)
- [x] "Add Klean Today" opened the launcher's pin dialog with the "Klean Today" preview (3 × 2); added, the widget shows "Monday, November 9", the "Add anything" pill and "Nothing coming up" (`04`, `11`); the pill opens the universal capture sheet ("Add anything: a task, a time, a meal…") (`12`)
- [x] App drawer icon: lavender tile, day ring with check, no bowl (`13`); status bar shows the same mark
- [x] Shade: "Today · Nothing planned. Add anything here." on channel `status_v1` (id 1001, importance 3, private + public version, 1 action); "Add something" → "toast" → Send: subText "Saved to Diet", text "1 food entry · last: toast 9:41 PM" (`07`–`09`); exported DB row `toast | NOTIFICATION_REPLY` with sync columns
- [x] QS tile label "Add to Klean" (`10`)
- [x] Today › More options › Arrange Today: "Diet" row with "Move Diet up/down" and "Show Diet" (`14`, `15`); switching it off and Done removed the "Food today" card while the Diet tab stayed (`16`, `17`); switching it back restored it
- [x] Debug `NEW_MODULES` broadcast → Today shows "New in Klean" with "Use Diet" on and Done (`18`); Done clears it and it doesn't come back after a force-stop and relaunch. Crash buffer empty
## M17 — Foundation 2 (`.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md`, branch `m17-foundation2`)

- [x] M17.1 — schema v3 by `AutoMigration(2 → 3, spec = KleanDatabase.V2ToV3)` (tables and columns only; post-migrate sets `task.status = DONE` where `completed_at` is set). New tables + DAOs: `delegation`/`DelegationDao`, `delegation_message`/`DelegationMessageDao`, `approval_request`/`ApprovalRequestDao`, `calendar_source`/`CalendarSourceDao`, `exercise_definition`/`ExerciseDefinitionDao`, `sleep_schedule`/`SleepScheduleDao`, `device`/`DeviceDao`, `sync_cursor`/`SyncCursorDao` (not synced). New columns on task, task_list, contact, event, workout_set, workout_session, sleep_session. `3.json`; `MigrationV3Test` (v2 fixture `backup-v2.sqlite` from `V2FixtureWriter`, plus v1→v3 chain) — m17-foundation2
- [x] M17.2 — `SecretStore.GOOGLE_REFRESH`/`BACKEND_TOKEN`/`HERMES_TOKEN` (`google.refresh`, `backend.token`, `hermes.token`); kept out of backups unless "include keys" is on — m17-foundation2
- [x] M17.3 — `DevicePrefs.isMainPhone` (default true; stored under the existing `mainPhone` key) + `DeviceRepository` (`registerThisDevice()` at startup in `KleanApp`, `setMainPhone`) — m17-foundation2
- [x] M17.4 — `ReminderRepository.snooze(id, until)`, `markDone(id, nextFireAt?)`, `dismiss(id, nextFireAt?)`; dismiss never completes a task, new state `DISMISSED` — m17-foundation2
## M24 — Sync backend (`backend/`, branch `m24-backend`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M24)

- [x] 24.1 — `:backend` JVM module (Kotlin JVM + Jib + `application`), Ktor 3.6.1 (same as `:core:ai`) on CIO
- [x] 24.2 — sync DTOs + `lww()` in `:shared` (`shared/.../sync/SyncProtocol.kt`), 8 tests
- [x] 24.3 — generic `records` store on Exposed 1.6 + JDBC (SQLite default, Postgres via `KLEAN_DB_URL`)
- [x] 24.4 — admin login (argon2id, BouncyCastle), pairing codes + QR payload, device tokens (sha256-hashed), revoke, DAV app passwords
- [x] 24.5 — `POST /sync/push`, `GET /sync/pull` (LWW, tombstones, cursor paging, table filter)
- [x] 24.6 — blobs (`PUT/GET/HEAD /blobs/{sha256}`, streaming hash check, size limit)
- [x] 24.7 — `/health` (DB round trip), call ids, structured JSON logs (logstash encoder)
- [x] 24.8 — Jib image `klean-backend:dev`, `deploy/docker-compose.yml`, Caddy/Traefik examples, `docs/deploy.md`
- [x] 24.9 — CardDAV/CalDAV (PROPFIND, REPORT sync-collection + multiget + query, GET/PUT/DELETE, ETags); vCard/iCal codecs in `:shared` with round-trip tests
- [x] 24.10 — web push (VAPID, RFC 8291/8292) with JDK crypto (`nl.martijndwars:web-push` is barely maintained); keys generated once into `settings`
- [x] 24.11 — Google server OAuth, Calendar read, Tasks list/patch (never create; `GoogleTest.neverCreatesGoogleTasks` checks every request), tested against a MockEngine fake
- [x] 24.12 — Hermes relay (`/hermes/{path...}` → `KLEAN_HERMES_URL` with the server-held token, SSE streamed)
- [x] 24.13 — AI proxy (`/ai/v1/chat/completions`, `/ai/v1/models` → `KLEAN_AI_UPSTREAM`), same streaming proxy
- [x] 24.14 — `POST /admin/backup` (zip: `VACUUM INTO` snapshot + blobs; Postgres → 501 with a `pg_dump` hint)

Verified (2026-11-09): gate + `:backend:test` BUILD SUCCESSFUL (backend 62 tests, `:shared` sync/vcard/ical 18);
`:backend:jibDockerBuild` → `klean-backend:dev`, `docker compose -f deploy/docker-compose.yml up -d`,
`curl -s localhost:8080/api/v1/health` → `{"status":"ok"}`, then an e2e script (login, pair, push, pull, blob,
DAV PUT/PROPFIND, backup zip) inside the container; the same script against Postgres 17 (backup → 501 hint);
real SSE streaming through `/ai/v1/chat/completions` (chunks arrive 0.7 s apart, upstream key substituted).

Known gaps (shipped simpler, per plan risk 8):
- DAV `addressbook-query` / `calendar-query` ignore filters and return every live member (correct superset; DAVx5 uses sync-collection).
- One address book and one calendar; no MKCOL, PROPPATCH, ACL or scheduling (iTIP).
- iCal: RECURRENCE-ID overrides are dropped on PUT; Klean-made times are written in UTC (no VTIMEZONE); unknown TZIDs fall back to the event zone.
- No birthday calendar over CalDAV yet (birthdays travel as vCard BDAY).
- DAVx5 compatibility not yet verified on a device (M29 session).
- No login rate limiting (argon2id cost is the only brake); put the server behind Tailscale or a proxy with limits.
- Push subscription endpoints are any https URL (no allow-list of push services).
- No automated Postgres test in `:backend:test` (verified manually against postgres:17-alpine).
- The image runs as root (Jib default) so the named volume is writable without an init step.


## M18 — Tasks & reminders (branch `m18-tasks`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M18–M20, module plan §S1/§S2)
- [x] 18.1 — `RecurrenceRule` in `:shared` (S1.1 + every N days/weeks, monthly n-th weekday, last day, COUNT/UNTIL, `occurrences` for Calendar)
- [x] 18.2 — Tasks repository: default lists (Personal, Work), subtasks, recurrence on completion, review actions, 30-day auto-archive
- [x] 18.3 — Tasks screens: tabs Today / Upcoming / Lists / Reminders / Done, edit screen (subtasks, repeat incl. custom RRULE, reminders), in-app mic
- [x] 18.4 — "Still want these?" review: daily alarm outside quiet hours, notification, review screen, debug `TASK_REVIEW`
- [x] 18.5 — Tasks capture (`add_task`), Today card (slot TASKS), tile, week activity, status line, Today items, archive worker
- [x] 18.6 — Reminder scheduler (`ReminderChangeListener` + `Reschedulable`), quiet hours for pings, format rotation (`prompt_log` trigger REMINDER)
- [x] 18.7 — Ping notification (Done / Snooze 10 min / 1 h / Tomorrow / Pick a time), full-screen `AlarmActivity` (main phone only, 10-min auto-stop, DND bypass)
- [x] 18.8 — Reminders tab + edit screen, capture (`add_reminder`), status line / Today items, settings for both modules
- [x] 18.9 — `TasksDisableTest` (androidTest), gate, Waydroid walkthrough

Verified (2026-11-10, branch `m18-tasks`): gate BUILD SUCCESSFUL (818 tests, 0 failures, 1 skipped);
`connectedDebugAndroidTest --tests TasksDisableTest` 2/2 on Waydroid. Waydroid walkthrough (screenshots in
`~/.hermes/cache/scratch/m18-tasks/`):
- Tasks is a nav item (Today · Tasks · Diet · Settings); Reminders has none. Today shows the Tasks card and tile.
- Universal capture "pay rent friday" → a Personal task due Friday (Upcoming, week overview "Friday · 1 thing").
- "call the bank" moved to yesterday on its page → Today tab shows "Still want these? / 1 task from earlier days"
  with Keep for today / Pick a date / Someday / Done / Let it go; `am broadcast -a app.klean.debug.TASK_REVIEW`
  posts the same on channel `task_review` (low importance).
- "water plants" set to Every week and checked off → the next one appears under "Tue, Nov 17".
- Reminders tab: "remind me in 2 min to stretch" and "alarm in 1 min wake test" typed into its field.
  The ping arrived on `reminder_ping` ("stretch / Set for 9:19 AM", Done / 10 min / Later…); the status line showed it.
  The alarm rang through the foreground service (`dumpsys audio`: MediaPlayer USAGE_ALARM started); with the app
  open it came as a heads-up; "10 min" stopped the sound and re-armed it exactly (`dumpsys alarm`: 09:30, exact by permission).
- Screen off + an alarm 1 minute ahead → `AlarmActivity` resumed over the lock screen (time, title, Dismiss,
  10 min, 1 hour, Tomorrow, Pick a time) with the alarm sound; Dismiss stopped it. No crashes in `logcat -b crash`.

Still needs a real phone: alarm through Do Not Disturb (with DND access granted), the full-screen intent on
Android 14+ (permission state on a Play-less install), the 10-minute auto-stop, a real reboot re-arming reminders,
voice on the Tasks screen, TalkBack over the Tasks tabs.

## M19 — Calendar (`feature/calendar`, branch `m19-calendar`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M18–M20 + module plan §S3)

- [x] 19C.1 — data: `CalendarRepository`/`DefaultCalendarRepository` (UUIDv7, `SyncStamper` in `db.transact`, tombstones, skip one occurrence = EXDATE, `EventChangeListener` multibinding after writes); `calendar_source` rows (Klean's own by name-based id; one per phone calendar per device, written only on change, selection kept); private `RecurrenceExpander` (DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, BYDAY incl. `2TU`/`-1FR`, COUNT, UNTIL, EXDATE; `TODO(after M18 merge): replace with RecurrenceRule`); ICS import/export via the shared `ICalCodec` (`EventIcs`; import updates by UID). Tests: `RecurrenceExpanderTest` 14, `CalendarRepositoryTest` 9 — m19-calendar
- [x] 19C.2 — `KleanCalendarMirror`: one read-only "Klean" calendar under the Klean account (sync-adapter writes, `_SYNC_ID` = event id, all-day at UTC midnight, recurring = RRULE + DURATION + EXDATE, `HAS_ALARM` 0); per-write updates + full resync as a `Reschedulable`; removed when the module is off. `KleanCalendarMirrorTest` 7 — m19-calendar
- [x] 19C.3 — event reminders through `ReminderRepository` (`EventReminderSync`: name-based reminder id per event, isAlarm = event.alarm (default false), moves to the next occurrence once done/dismissed and on reschedule, removed with the event or the module). `EventReminderSyncTest` 8 — m19-calendar
- [x] 19C.4 — `CalendarAgenda`: Klean events (expanded) + phone calendars read-only (`DeviceCalendarSource` over `CalendarContract.Instances`, Klean's mirror excluded) + `DayItemsProvider` multibinding (defined in `feature/calendar/agenda/`; `TaskDueItems` and `ReminderDayItems` read-only), honouring source selection, the module gate and Show tasks/reminders. `CalendarAgendaTest` 4 — m19-calendar
- [x] 19C.5 — capture `add_event`: `CaptureEventParser` (NaturalTime; 1 h default; date only → all-day; no time → next whole hour), `CalendarCaptureHandler` (undo tombstones). `CaptureEventParserTest` 4 — m19-calendar
- [x] 19C.6 — `CalendarKleanModule` (navOrder 20, slot AGENDA, `Icons.Rounded.CalendarMonth`, settings route, deep links `klean://calendar` and `klean://calendar/day/{isoDate}`; `onEnabledChanged` resyncs the mirror and event reminders, then refreshes the surfaces) and Hilt wiring (`CalendarBindings`) — m19-calendar
- [x] 19C.7 — screens: `CalendarScreen` with Agenda (14 days, default) / Day / 3 days / Week (hour grid, all-day strip, overlap lanes) / Month (whole weeks, dots, today ringed) views, prev/next/today, read-only dialog for phone events, tasks, reminders and birthdays; just-in-time READ/WRITE_CALENDAR card ("Show them" / "Not now"); `EventEditScreen` (title, all day, start/end pickers, repeat, reminder none/at start/10 min/1 h/1 day, place, notes, delete one occurrence or the series); `CalendarSettingsScreen` (calendars shown with toggles, also show tasks/reminders, default reminder, ICS import/export via the system file picker). Tests: `CalendarViewModelTest` 5, `EventEditViewModelTest` 8, `CalendarSettingsViewModelTest` 4, `CalendarScreenTest` 4 (incl. ≥48 dp and labels) — m19-calendar
- [x] 19C.8 — Today: Agenda card (`AgendaCard`: today's events, reminders and birthdays still ahead, max 5, calm empty state), tile "Next · 14:00 Dentist" / "Nothing else today", week activity (events per day, Klean + phone, `dayRoute` → `CalendarDayRoute`), Today-widget rows (`TodayItemsProvider`, deep link per day), status line "Dentist 14:00" (`CalendarStatusLine`, priority 5, lazy agenda, refreshes `StatusRefresh` after every event write). `CalendarTodayTest` 5 — m19-calendar
- [x] 19C.9 — status line and reschedulers read events with one-shot suspend queries (`EventDao.live()`, `CalendarSourceDao.all()`, additive DAO methods, no schema change) instead of holding a Room flow; `AgendaSource.eventSnapshot`. `CalendarCaptureHandlerTest` 2, `CalendarAgendaTest` +1 — m19-calendar
- [x] 19C.10 — `app/src/androidTest/kotlin/app/klean/CalendarDisableTest.kt`: off removes the nav entry, capture handler, Today tile, status fragment, Today-widget rows, event reminders and the provider calendar (and a resync keeps it off); on brings them back — m19-calendar
- [x] 19C.11 — gate in the worktree BUILD SUCCESSFUL (`assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test`; 790 tests, 0 failures, 1 skipped); `CalendarDisableTest` 2/2 on Waydroid — m19-calendar

Verified on Waydroid (Android 13, 540×1212) on 2026-11-10 (M19 Calendar), screenshots in `~/.hermes/cache/scratch/m19-calendar/`:
- [x] Calendar tab (nav order after Today), Agenda card on Today ("Nothing planned today"), tile "Calendar · Nothing else today" (`01`, `02`)
- [x] In-app "Show them" → system dialog "Allow Klean to access your calendar?" → Allow; READ/WRITE_CALENDAR granted; the "Klean" calendar appears under `Account {name=Klean, type=app.klean.debug}` (`03`)
- [x] A phone calendar ("Work", LOCAL account) + event inserted with `content insert` → shows read-only in the agenda, "From Work", live through the content observer (`04`); tapping it opens the read-only dialog
- [x] Capture sheet (QS tile) "dentist friday 9:30" → Save → Fri 13 Nov 9:30–10:30 in the agenda; `content query` shows `title=dentist, calendar_id=1 (Klean), hasAlarm=0, _sync_id=<event id>` (`05`–`07`)
- [x] Week (hour grid), 3 days, Month (dots on 11 and 13, today ringed), day view from a month cell, editor (`09`–`13`); set Every week + 1 hour before → repeats on 13 and 20 Nov (`14`)
- [x] New event today → status line "Today · Lunch with Sara 9:00 AM" (`18`), home-screen Klean Today widget row "9:00 AM Lunch with Sara" (`19`); exported DB: reminders `is_alarm=0`, `SCHEDULED`, at start − 10 min / − 1 h; `calendar_source` rows KLEAN + DEVICE (read-only)
- [x] Settings › Calendar: calendars shown with toggles, also shown, default reminder, files (`20`); Work off → its event leaves the agenda (`21`); Export → system picker → `klean-calendar.ics` with VEVENT/RRULE/VALARM (`22`, `23`)
- [x] Fresh install with stale mirror rows from an earlier install → startup resync removes the orphans; new event mirrored
- [x] Settings › Modules › Calendar off: gone from the bar and Today (no Agenda card, no tile), the provider "Klean" calendar is deleted, status line falls back to "Nothing planned. Add anything here." (`27`–`29`); on again: calendar re-created with the event, Agenda card back. Crash buffer empty
- [~] ICS import through the system picker: the picker hung Waydroid's `system` process (ANR, session restarted); import is covered by `CalendarRepositoryTest` (update by UID, all-day) and `CalendarSettingsViewModelTest`. Check on the Pixel

## M19 — Contacts & birthdays (`feature/contacts`, branch `m19-contacts`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M18–M20 + module plan §S4)

- [x] 19K.1 — data: `ContactRepository`/`DefaultContactRepository` (UUIDv7, `SyncStamper` in `db.transact`, tombstones, `ContactChangeListener` multibinding after writes), JSON phones/emails/addresses/N (`LabeledValue`, `PostalAddress`, `StructuredName`), validated `Birthday` (Feb 29 allowed without a year or in a leap year), default reminder days from the synced `birthdays.defaultReminders` (0,3), find-by-name, VCF 3/4 import (update by UID) and export through the shared `VCardCodec` (`ContactVcf`; unmodelled properties kept in `contact.custom`), merge. Additive `ContactDao.live()`. `ContactRepositoryTest` 11 — m19-contacts
- [x] 19K.2 — birthdays: pure `Birthdays` (next, days until, Feb 29 → Feb 28 in common years, occurrences in a range, age); `BirthdayReminderSync` writes quiet reminders through `ReminderRepository` (name-based id per contact and days-before, 09:00 local by the synced `birthdays.reminderHour`, "It's Sara's birthday today" / "Sara turns 36 today" / "… in 3 days", age behind `birthdays.showAge`), moves to next year once done/dismissed, removed with the contact, its birthday, its reminder days or the Birthdays module; `ContactChangeListener` + `ReminderChangeListener` + `Reschedulable`. `BirthdaysTest` 5, `BirthdayReminderSyncTest` 9 — m19-contacts
- [x] 19K.3 — `KleanContactsMirror`: Klean contacts as raw contacts under the Klean account (sync-adapter writes, `SOURCE_ID` = contact id, so no device-local column is written; StructuredName/Phone/Email/Event birthday + anniversary/Organization/StructuredPostal/Note rows from the pure `ContactRows`), per-write replace + full resync as a `Reschedulable`, removed when Contacts is off; `ContactsProviderOps` (`AndroidContactsProvider` builds the `ContentProviderOperation` batches) incl. `keepTogether` (AggregationExceptions) for merges; `TODO(M25)` hook to stop mirroring when DAVx5 syncs the paired backend. `KleanContactsMirrorTest` 6 — m19-contacts
- [x] 19K.4 — DAVx5 awareness: `AndroidDeviceContacts` finds accounts of type `bitfire.at.davdroid` (AccountManager) and their contacts (also the `bitfire.at.davdroid.address_book` accounts DAVx5 4.x keeps them in) through the provider, and edits them as a normal app so DAVx5 uploads the change; `DavAccounts.syncsOwnBackend` is the `TODO(M25)` hook for "use DAVx5 instead of mirroring" — m19-contacts
- [x] 19K.5 — one-time merge offer: pure `DuplicateFinder` (same normalised name + a shared phone (last 9 digits) or email; Klean ↔ Klean and Klean ↔ DAVx5; other-account pairs left alone; kept-apart pairs skipped), `MergeOffer` (merge per pair: fold + tombstone, or fold the other account's data in and join both raw contacts into one card; keep apart; "Not now" ends it; `contacts.mergeOfferDone`, `contacts.keptApart`). `DuplicatesTest` 5, `MergeOfferTest` 3 — m19-contacts
- [x] 19K.6 — capture `add_birthday`: pure `BirthdayText` ("Sara's birthday is March 3", "Mum's bday 12/4" (day/month), "3 March 1990", "29th of February", ISO, "tomorrow" through NaturalTime, "my" → "Me"); `ContactsCaptureHandler` finds the contact by name or creates it, no AI dependency (constructor test), Undo removes a created contact or restores the old birthday. With Contacts off there is no handler, so the date rule makes it a dated task (test). `BirthdayTextTest` 2, `ContactsCaptureHandlerTest` 4 — m19-contacts
- [x] 19K.7 — `ContactsKleanModule` (navOrder 30, `Icons.Rounded.Contacts`, no Today card, graph with deep links `klean://contacts[/{tab}]` and `klean://contacts/contact/{id}`, settings, capture placeholder; off removes the provider rows) and `BirthdaysKleanModule` (`inNavigation = false`, slot BIRTHDAYS, `Icons.Rounded.Cake`, its own settings; off removes every birthday reminder); Hilt wiring `ContactsBindings` — m19-contacts
- [x] 19K.8 — screens: Contacts (tabs People / Birthdays, search by name/number/email, A–Z sections, DAVx5 contacts by account, the merge card, just-in-time contacts access row), contact detail (call/email, birthday with age and reminders, anniversary, other vCard fields), editor (name, company, numbers/emails with labels, addresses, birthday month/day/optional year (Feb 29 allowed), reminder chips On the day / 1 / 3 / 7 days before, notes, delete), Contacts settings (access, DAVx5 accounts, VCF import, export vCard 3 or 4), Birthday settings (show age, defaults for new contacts, reminder time), setup page "Contacts" (order 600) — m19-contacts
- [x] 19K.9 — Today: Birthdays card (next 3 within 30 days, calm empty state), tile "Next birthday · Sara, Mar 3", week activity, Today-widget rows (today and tomorrow), status line "Sara's birthday" on the day (priority 25), calendar `DayItemsProvider` (kind BIRTHDAY, deep link to the contact) — `BirthdaySurfaces` reads contacts one-shot after every write and at midnight, then `StatusRefresh` — m19-contacts
- [x] 19K.10 — tests for the surfaces and screens: `BirthdayIndexTest` 1, `BirthdaySurfacesTest` 3, `ContactListsTest` 2, `ContactsViewModelsTest` 5 (list + merge + gate, new/edit/delete, DAVx5 save through the provider, VCF import/export), `ContactsScreenTest` 4 (merge card, other account, Birthdays tab via deep link and hidden when off, Today card, ≥48 dp) — m19-contacts
- [x] 19K.11 — `app/src/androidTest/kotlin/app/klean/ContactsDisableTest.kt`: Contacts off removes the nav entry, capture handler and provider rows (a resync keeps them off; a birthday phrase becomes a dated task); Birthdays off removes the Today card, tile, status fragment, Today-widget rows and every birthday reminder; on brings both back. 3/3 on Waydroid (`am instrument`) — m19-contacts
- [x] 19K.12 — fix: a pair merged with a DAVx5 contact is recorded, so the offer doesn't come back for it (found on Waydroid; `MergeOfferTest`) — m19-contacts
- [x] 19K.13 — gate in the worktree BUILD SUCCESSFUL (`assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test`; 953 tests, 0 failures, 1 skipped; `BackupViewModelTest` flaked once and passed on the rerun) — m19-contacts

Verified on Waydroid (Android 13, 540×1212) on 2026-11-10 (M19 Contacts), screenshots in `~/.hermes/cache/scratch/m19-contacts/`:
- [x] Contacts is the 4th bar item (Today · Tasks · Calendar · Contacts · More); empty People tab with a calm empty state and the "Show contacts from this phone" row (`02`); tapping it → system "Allow Klean to access your contacts?" → Allow; READ/WRITE_CONTACTS granted (`03`)
- [x] Today capture "birthday Sara March 3" → contact Sara, tile "Next birthday · Sara, Mar 3" (`04`); `content query raw_contacts` shows Sara under `account_type=app.klean.debug` with `sourceid` = the contact id
- [x] "birthday Omar November 10 1990" (today) → the reminder rang at once as "Omar turns 36 today" (channel of the Reminders module), status line "Omar turns 36 today · Omar's birthday"; Birthdays card on Today ("Omar · Turns 36"), Agenda card and Calendar show "Omar turns 36 · Birthday" (`11`, `12`)
- [x] Birthdays tab: "Coming up" Omar · Today, then "March" Sara · Mar 3 (`08`); detail (birthday, "Turns 36 on Nov 10", reminders "On the day, 3 days before") and editor (`09`, `10`)
- [x] New contact "Lina 0501112222" through the editor + a DAVx5 address-book raw contact "Lina +971501112222, birthday 1992-11-03" inserted with `content insert` → merge card "These look like the same people / Lina, also in me@example.dav" (`13`); Merge → card gone, Lina has the DAVx5 birthday, both raw contacts share one `contact_id` (`14`, `15`); Contacts settings list the DAVx5 account (`16`)
- [x] Settings › Modules: Contacts + Birthdays off → Contacts leaves the bar, the Klean account's raw contacts are deleted (DAVx5's stay), the birthday notification goes and the status line falls back; on again → all three raw contacts back, bar item back (`17`, `18`). Crash buffer empty
- [~] VCF import/export through the system file picker: not run (the picker hung Waydroid's system process in M19 Calendar); covered by `ContactRepositoryTest` (3-card fixture, update by UID, v3/v4 round trip) and `ContactsViewModelsTest`. Check on the Pixel
