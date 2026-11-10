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

## M20 — Workout & sleep (branch `m20-body`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M18–M20, module plan §S5/§S6)
- [x] 20.1 — Sleep schedule is the source of quiet hours (`core/data/sleep/SleepScheduleRepository`, one-time migration of `core.quietHours`, key kept as a mirror; `QuietHoursService` API unchanged; quiet-hours editor writes the schedule)
- [x] 20.2 — Workout model + `TemplateText` parser/formatter, `WorkoutRepository` (templates, sessions, sets, freeform log, auto-created exercises, history)
- [x] 20.3 — Sleep logic (`SleepMath`, `SleepText`) + `SleepRepository` (go to bed / wake up / toggle / log / edit)
- [x] 20.4 — Workout module: screens (home, template editor, session, exercises + history), capture `log_workout`, Today card, tile, activity, status line, Today items, settings
- [x] 20.5 — Gentle workout suggestion: format rotation (`prompt_log` trigger WORKOUT), quiet hours, every N days, off by default
- [x] 20.6 — Sleep module: screens (nights list, edit, settings with the schedule editor), capture `log_sleep`, Today card with one tap, tile, activity, status line, Today items (widget toggle)
- [x] 20.7 — Bedtime reminder (30 min before, once) and the "Sleeping since" notification with "I'm up"
- [x] 20.8 — `WorkoutDisableTest` / `SleepDisableTest` (androidTest), gate, Waydroid walkthrough

Verified (2026-11-10, branch `m20-body`): gate BUILD SUCCESSFUL (937 tests, 0 failures, 1 skipped);
`connectedDebugAndroidTest` `WorkoutDisableTest` + `SleepDisableTest` 2/2 on Waydroid. Waydroid walkthrough
(screenshots in `~/.hermes/cache/scratch/m20-body/`, no crashes in `logcat -b crash`):
- Today shows Sleep and Workout tiles and cards. "Going to bed" on the Sleep card → "Sleeping since 10:59 AM" on the
  card, on the status line, and as a quiet `sleep_session` notification; its "I'm up" action closed the night.
- Universal capture: "slept at 11 woke at 7" → a night of 8 h (23:00–07:00) on the Sleep screen; "did 30 pushups"
  → "Last workout · Today".
- Saved workout "3x15 pushups, 1 min plank, 5 km run" (live preview), Start → session with +1 set per item;
  "3x10 squats" + one more set → four sets; the status line showed "Workout in progress"; Finish → Recent
  ("squats · 1 item"); Exercises → squats history "Today 12:26 PM · 10 · 10 · 10 · 10".
- `am broadcast -a app.klean.debug.BEDTIME` → "Winding down soon? / Bedtime is at 12:00 AM." with "Going to bed";
  `app.klean.debug.WORKOUT_SUGGEST` → "Fancy a short workout?" on `workout_suggest` with "Start a workout".
- Sleep settings show the schedule (12:00 AM–8:00 AM = quiet hours), bedtime reminder 30 min before, while-asleep switch.

Still needs a real phone: the bedtime alarm at its real time and after a reboot, the Today widget's sleep item
(widget placement), the suggestion at 18:00 after N quiet days, quiet-hours migration on the existing install.

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

## M25 — Android sync, pairing, multi-device (`core/sync`, branch `m25-sync`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M25)

- [x] 25.1 — `:core:sync` module (feature convention + `klean.sqlite.jvmtest`), in `settings.gradle.kts` and `:app`; ktor-client-okhttp, WorkManager, zxing-android-embedded 4.3.0 — m25-sync
- [x] 25.2 — `SyncEngine` over `SyncTable` adapters: one generic `RoomSyncTable` per entity (`SELECT *` → `SyncRecord` json, snake_case, minus `change_seq`; booleans as JSON booleans; upsert of known columns, `change_seq = 0` for pulled rows) and the `SyncTables` registry of all 21 synced tables (`SyncTablesTest` checks it against Room's exported schema and the entity types). Push `change_seq > last_push_seq` in pages of 500, then pull with `lww`; blobs first (`HEAD`, `PUT` only when missing; hashes ride in `blob_sha256`, downloaded on the other side); an unpushed local edit wins until pushed (the page's cursor isn't saved, the next run settles it); `dav_` keys and unknown tables ignored; device-local `provider_event_id`/`provider_raw_id` never sent and kept on apply; restore clears `sync_cursor`; `/pair/claim` sends this phone's `device_id` (falls back to a server id after a 409) — m25-sync
- [x] 25.3 — WorkManager: unique periodic work every 15 min (network, exponential backoff 30 s) while paired; an expedited run 10 s after the last local change (`change_counter` invalidation, debounced; pulled rows don't trigger it); `SyncWorker` retries network/500 errors, stops on 401/403; local writes never wait for it. After applied remote rows: `Rescheduler.rescheduleAll()` + `StatusRefresh` — m25-sync
- [x] 25.4 — pairing: QR scan (zxing-android-embedded, shown only with a camera), paste-URL fallback parsing `klean://pair?url=…&code=…` (`PairLink`), token in `SecretStore.BACKEND_TOKEN`, URL/last sync/problem in DataStore `sync_prefs`; Settings › Sync (`klean://settings/sync`: pair, paired with, last sync, calm problem line, Sync now, Unpair with confirm, devices list); setup page "Sync with your server" (order 810) — m25-sync
- [x] 25.5 — multi-device: `device` rows sync; alarms still ring only where `isMainPhone`; a synced `prompt_log` outcome LOGGED/DISMISSED from another device cancels this phone's notification (`PromptAnsweredElsewhere`, ids checked by `PromptNotificationIdsTest`); prompts are loud only on the most recently used phone (`PromptAudience`, `device.last_seen_at` refreshed on foreground at most every 10 min), silent elsewhere (reminder pings, diet check-ins) — m25-sync
- [x] 25.6 — DAVx5: `DavAccounts.syncsOwnBackend(ctx, backendUrl, kleanIds)` (collection URL under `<backend>/dav/`, or member file names = Klean contact ids) drives `KleanContactsMirror.useDavInstead` through `PairedBackend` (`:core:data`) — m25-sync
- [x] 25.7 — tests: `SyncEngineTest` 7 against the real `:backend` in-process (`testApplication`): two phones converge, tombstones (an older offline edit doesn't resurrect), LWW + tie-break, unpushed edit wins until pushed, blobs once + delivered, `dav_`/unknown tables ignored, server down / 500 → local use unaffected and the next run catches up. `SyncTablesTest` 3, `PairLinkAndPromptsTest` 4, `DavBackendMatchTest` 2, `SyncStamperTest` +1 — m25-sync
- [x] 25.8 — Waydroid end to end with the Docker backend (2026-11-10, screenshots `~/.hermes/cache/scratch/m25/e2e/`): `:backend:jibDockerBuild`, `docker compose -p klean-m25 -f deploy/docker-compose.yml up -d` with `KLEAN_BIND=8095`, `KLEAN_PUBLIC_URL=http://192.168.240.1:8095` (waydroid0 bridge); admin login + `/pair/start` with curl; `klean://settings/sync` → pasted the QR link → "Paired with http://192.168.240.1:8095", devices "This phone · Main phone"; the server's device id = the phone's `device_id` (`01`–`03`). Logged "hummus and flatbread" and "buy batteries tomorrow" on Today → both in `GET /sync/pull` ~10 s later with no tap (`pull2.json`). A second device claimed with curl pushed 24 seed records (tasks, list, reminders, events, contacts, sleep, workout, food) → after Sync now they show in Tasks, incl. the past-due review (`08`). `docker compose stop` → added "pick up parcel" (saved, shown), Sync now → "Can't reach the server right now. Klean keeps working and tries again on its own.", worker `RETRY`, crash buffer empty (`09`, `10`); `start` → the retry delivered the parcel task within 75 s. `docker compose down` — m25-sync
- [x] 25.9 — Android visual review on Waydroid with realistic seeded data (pushed through sync as a second device: tasks incl. a weekly and a past-due one, reminders, events, a contact with a birthday in 3 days, two nights, a workout, food). Screenshots in `~/.hermes/cache/scratch/m25/visual/` (`light-*`/`dark-*` = before, `after-*` = after, `font15-*` = font scale 1.5 before/after, `light-70-onboarding-01…10`, `*-63-capture-sheet`, `*-61/62-status*`; contact sheets in `sheets/`). Fixes: Today's list stops below the status bar (cards ran under its icons); Today tiles go to one column at large font scales (values were cut: "Sara Haddad,…"); every Today tile has an icon (Calendar, diet tiles and Quiet hours had none, so text didn't line up); `EntryRow` has one sp-sized time column so titles line up, and calendar ranges stack on two lines ("3:00 PM / 3:30 PM") instead of breaking mid-range; sleep's empty nights read "Nothing logged" on one line; Tasks/Reminders/Contacts lists end with FAB clearance (`FabClearance`); bottom-bar labels stay on one line at 1.5× ("Calenda r" → auto-sized "Calendar"); permission copy no longer diet-only ("today's food" → "your day", reminders). Sync screen: "Just now" instead of "0 minutes ago". No token values changed. Tests: `ContrastTokensTest` (every on-X/X pair, inverse, onSurface/onSurfaceVariant on every surface, primary text on surface…containerHigh ≥ 4.5:1 in both themes; all pass), `NoRawColorsTest` (no `Color(0x…)`/named colours outside `core/design`) — m25-sync
- [~] 25.9a — Today widget screenshot: Waydroid's launcher can't place widgets; check on the Pixel. Known, not fixed here: week-view titles break mid-word in narrow columns; the Tasks/Calendar tab rows scroll (last tab cut at the edge, "Month" off-screen at 540 px); "Log again" chips are being fixed on main (parent)
- [x] 25.10 — gate in the worktree BUILD SUCCESSFUL (`assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test`; 1020 tests, 0 failures, 1 skipped). `connectedDebugAndroidTest` 15/15 on Waydroid. `ModuleDisableTest` had stale pre-M18 expectations (failed with no M25 code involved: the holistic status line and the "Add to Klean" tile stay while other modules are on); it now checks that diet's own notifications, line and capture handler go — m25-sync


## M26 — Web client (`web/`, branch `m26-web`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M26)

- [x] 26.1 — Stack: Vite 8 + TypeScript 5.9 + `@m3e/web` 2.12.0 (M3E web components; lit-html for rendering, no React); Node 22 + pnpm 12 from the flake; `nix develop .#web` adds Playwright browsers from `nixpkgs#playwright-driver.browsers` (`@playwright/test` pinned to 1.64.1 = the driver)
- [x] 26.2 — Theme: `web/scripts/tokens.mjs` turns `core/design/tokens/klean-color-tokens.json` into `--md-sys-color-*` (light; dark through `prefers-color-scheme`); `scripts/assets.mjs` bundles Cantarell from `core/design/src/main/res/font/` and converts the day-ring launcher vector into `public/icon.svg` + `icon-maskable.svg`
- [x] 26.3 — Data layer, generic over sync records (`json` = the Room row, snake_case): IndexedDB cache + pending-writes queue (`src/data/idb.ts`, `store.ts`), LWW port (`sync-types.ts`, same table as `:shared`), push-then-pull engine (`sync.ts`), the web's own device token from `POST /auth/login` (`session.ts`); writes stamp like `SyncStamper` (`created_at` kept, `updated_at` always moves forward); name-based ids (`nameUuid`, MD5 v3) match Android's seeded rows (task lists, settings, sleep schedule)
- [x] 26.4 — Screens, laptop-first with a navigation rail and a two-pane Today (cards + "Your day"); phone width gets a bottom bar (Today, Tasks, Calendar, Contacts, More): login / first setup, pairing QR (drawn client-side with `qrcode-generator`), Today (APPROVALS → DIET, device-local Arrange Today in `localStorage`), Tasks (Today with "Still want these?", Upcoming, Lists, Reminders, Done; repeats roll forward with a `RecurrenceRule` port), Calendar (agenda, week, month; repeats expanded with MissingDay SKIP), Contacts and Birthdays, Workout ("+1 set"), Sleep (one-tap bed/wake, schedule = quiet hours), Diet (today's list + log field), Approvals (editable draft and recipients; Approve → APPROVED or EDITED, Not now → REJECTED, `decided_at`), Settings (modules via synced `core.modules.disabled`, quiet hours via the sleep schedule + `core.quietHours`, devices, notifications, Google, backup download from `POST /admin/backup`, sign out)
- [x] 26.5 — Universal capture: `src/capture/rules.ts` + `natural-time.ts` port `RuleCaptureClassifier` and `NaturalTime`; the shared fixture `shared/src/commonTest/resources/capture-cases.json` (35 capture + 40 time cases) is read by `CaptureCasesFixtureTest` (`:shared` jvmTest) and by `rules.test.ts`; no rule → diet with Move to… and Undo
- [x] 26.6 — Web push: `public/sw.js` shows `{title, body, tag?, url?}` and opens `url` on click (alarm tags stay until handled); Settings subscribes through `/push/vapid-public-key` + `/push/subscribe`, with a test send
- [x] 26.7 — PWA: `manifest.webmanifest`, day-ring SVG icons, network-first shell cache in the service worker
- [x] 26.8 — Copy: all strings in `src/copy.ts`; `scripts/check-copy.mjs` (src: copy.ts literals, html`` text, index.html, manifest; dist: built HTML/manifest + medical labels in the bundle), plus `copy.test.ts`
- [x] 26.9 — Backend serves `web/dist` at `/` with an SPA fallback outside `/api`, `/dav`, `/.well-known` (`backend/.../web/WebRoutes.kt`, `WebRoutesTest` 8); `:backend:pnpmBuild` (Exec) feeds `processResources`, skipped without pnpm or `web/` or with `-Pklean.web=false`; documented in `docs/backend-api.md`
- [x] 26.10 — CI: `web` job in `.github/workflows/build.yml` (pnpm install, test, build, check-copy); gate and backend jobs unchanged
- [x] 26.11 — Tests: Vitest 182 (capture rules + NaturalTime on the shared fixture, LWW, queue/store/sync with fake-indexeddb, device token storage, RecurrenceRule, capture execution, tokens + WCAG AA for every text/background pair in both themes, copy guard); Playwright e2e 2 against `docker compose` (`web/e2e/run.sh`: own project `klean-m26e2e` on port 18080, `down -v` at the end); Playwright visual 12 (`toHaveScreenshot` for Today, Tasks, Calendar × light/dark × laptop/phone)
- [x] 26.12 — Visual review: every screen in light and dark at 1440×900 and 390×844 with seeded data (`e2e/review.spec.ts`, 78 screenshots + a measurements report: sideways scroll, clipping, text sizes, font), looked at one by one, issues fixed (see below)

Gate (2026-11-10): BUILD SUCCESSFUL, 1010 tests, 0 failures, 1 skipped (`TasksModuleTest` and `ReminderSurfacesTest` Turbine timeouts on the first, loaded run passed on the re-run; unchanged code).
Verified (2026-11-10): `pnpm test && pnpm build` pass (182 tests, copy check src + dist); `:shared:jvmTest` `CaptureCasesFixtureTest` 2/2;
`:backend:jibDockerBuild -Djib.to.image=klean-backend:m26` (runs `pnpmBuild`), then `pnpm e2e`: sign in with the admin password → pairing
QR rendered with a `klean://pair?url=…&code=…` payload → a phone claims the code with curl → a task created on the web reaches the
phone's pull → a task pushed with curl as the phone appears on the web → a seeded `approval_request` is edited and approved, and the
phone pulls `decision=EDITED` with the new draft and a `decided_at`; `/calendar` serves the app, `/api/v1/nope` stays a 404. 2/2 passed.

Visual review fixes (before: `~/.hermes/cache/scratch/m26/visual/before/`, after: `…/after/`):
- navigation labels were 12 px and faint, the active item barely visible → labels 14 px on `on-surface-variant`, active item on `secondary-container`, rail colour full height
- the phone bottom bar overflowed (only 3 of 5 items visible, "More" clipped) → items share the width
- Today and Settings left large holes beside short cards → balanced masonry columns, Settings in three columns at 1440
- inline fields sat higher than their Add button (reserved hint row) → hint row only for fields with a hint
- "Still want these?" rows squeezed the task title into one letter per line on a phone → actions wrap under the title
- the week view's today circle pushed its row out of line → fixed 32 px day heads
- month view showed "2 more" on a phone where events are dots → hidden there
- date fields' labels overlapped the browser's dd/mm/yyyy → labels float for date and time fields
- contact birthdays were ragged → one right-aligned column; history dates in a fixed column
- single-column screens spanned 1300 px → 960 px reading width; Today's "Your day" pane only beside the cards (≥ 1200 px)
- the phone header wrapped "Arrange Today" onto its own row → icon-only there
- the Approvals card showed the draft's first line ("Hi Khalid,") → the action and the task it belongs to

Still needs the user's browser or phone:
- [ ] Web push end to end: allow notifications in a real browser on `https` (or `localhost`), Settings › Send a test, and a notification click opening Klean
- [ ] Install as a PWA (desktop Chrome/Edge, Android Chrome); the maskable icon on the Android launcher
- [ ] Pair the Pixel by scanning the QR (needs M25's scanner) and see edits flow both ways
- [ ] Google › Connect with the user's own OAuth client
- [ ] The backup zip downloaded from Settings opens and restores
- [ ] Screen reader pass (labels are set on icon buttons, switches, tabs and the QR)

Known gaps:
- No AI on the web: capture uses the rules and falls back to diet (AI routing stays on the phone).
- The web has no task subtask editor or list management, no event editor beyond the one-line add, no contact editor beyond add.
- Server-side push schedules (reminders, birthdays, bedtime, approvals mirrored by the server) are not built: the web subscribes, the backend's `PushService.send` hook still has no scheduler.
- "Last night" uses calendar days, not Android's logical day.

## Server image (`klean-server`, Dockerfile, GHCR; plan `.hermes/plans/2026-11-10_193000-ghcr-image-and-next-milestones.md`, revised by the user: Dockerfiles, image name `klean-server`)

- [x] Repo-root `Dockerfile` (multi-stage): web stage (Node 22 + pnpm via corepack → `web/dist`), server stage (JDK 21, `./gradlew :backend:installDist -Pklean.serverOnly=true -Pklean.web=prebuilt`, no Android SDK), runtime stage (JRE 21, uid 10001, `/data` volume, port 8080, OCI labels). Base images pinned by digest; build stages run on `$BUILDPLATFORM`, the runtime stage has no RUN, so amd64 + arm64 need no emulation. Jib removed
- [x] `settings.gradle.kts`: `-Pklean.serverOnly=true` includes only `:shared` and `:backend`. `backend/build.gradle.kts`: `klean.web` = auto | require | prebuilt | false; reproducible archives (no timestamps, fixed order)
- [x] `scripts/image-smoke.sh` (health, web app at `/`, SPA fallback, API 404, non-root, `/data` writable) and `scripts/image-repro.sh` (two `--no-cache` buildx builds with `SOURCE_DATE_EPOCH` + `rewrite-timestamp=true`, same digest)
- [x] CI `.github/workflows/docker.yml` (replaces build.yml's `backend` job): backend tests → amd64 build + smoke + reproducibility → on pushes to main and `m*`/`p*` tags, push `ghcr.io/kquote03/klean-server` (linux/amd64 + linux/arm64; tags `latest`, `main`, `sha-<short>`, `0.1.0-<count>-<short>`, milestone tag)
- [x] `deploy/docker-compose.yml` defaults to `ghcr.io/kquote03/klean-server:latest` with a `build:` fallback (`up -d --build`); `docs/deploy.md` rewritten (run, tags, build from source, reproducibility); `web/e2e/run.sh` uses `docker build -t klean-server:e2e .`
- [ ] The user sets the GHCR package to public once (package settings → Change visibility); no API for it

## UI polish after the M25 visual review (branch `next`)

- [x] P.1 — Calendar: one "View: …" picker button (dropdown of all views) replaces the scrolling chip row that ran off 540 px; `CalendarScreenTest.viewPickerFitsANarrowPhone` (w360dp) — next
- [x] P.2 — Calendar week blocks: one ellipsized title line (`softWrap = false`), no time line (`compact`); Waydroid `~/.hermes/cache/scratch/next/shots/0b-week.png` ("Quar…", "Den…", "Phys…") — next
- [x] P.3 — Tasks tabs: fixed `PrimaryTabRow` with tight tab padding and auto-sized one-line labels instead of `PrimaryScrollableTabRow` (five tabs at their natural width need ~420 dp, so `edgePadding = 0` alone still cut "Done" at 384 dp); unselected tabs in `onSurfaceVariant`; `TasksScreenTest.tabsFitANarrowPhone` (w360dp, every tab inside the screen); Waydroid `…/shots/0c-tasks-done.png` — next

## M22 — Health Connect + GPS (`core/health`, branch `next`; plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md` §M22)

- [x] 22.1 — `:shared` `health/RouteMath` (haversine, `RouteFilter`: accuracy ≤ 50 m, steps ≥ 2 m, ≤ 12 m/s, re-anchors after 3 consistent jumps so a bad first fix can't swallow a run; pace; Google polyline; `klean-route/1` blob with times) and `HealthMerge` (overlap vs the shorter span; sleep > 50% → replace the manual night; workouts > 70% → keep the one with a route, link the other). `RouteMathTest` 8, `HealthMergeTest` 5 — next
- [x] 22.2 — `:core:health`: `HealthConnectGateway` (availability AVAILABLE / NEEDS_UPDATE / NOT_INSTALLED / UNAVAILABLE, granted access, read sleep/exercise, write exercise with Klean's session id as client record id), `AndroidHealthConnectGateway` (connect-client 1.1.0; every call best-effort), `FakeHealthConnectGateway`, `HealthConnectRow` for module settings (calm unavailable text, Get it / Update / Connect / Manage), `HealthPrivacyActivity` (rationale + Android 14 permission-usage alias). Permissions: READ_SLEEP, READ_EXERCISE, WRITE_EXERCISE only. `HealthConnectRowTest` 5 — next
- [x] 22.3 — Sleep: `HealthConnectSleepImport` replaces `NoSleepImportSource` (30 days, once per `hc_record_id`, source `HEALTH_CONNECT`; an overlapping manual night is tombstoned with a note); runs on every Sleep screen visit; row in Sleep settings. `HealthConnectSleepImportTest` 3 — next
- [x] 22.4 — Workout: `HealthConnectWorkoutBridge` replaces `NoHealthConnectBridge` (finished sessions written once and linked; other apps' sessions imported once; a > 70% duplicate of a Klean session is linked by `hc_record_id`, not imported); write on Finish, import on every Workout visit; row in Workout settings — next
- [x] 22.5 — GPS runs: "Start a run" on Workout → just-in-time location permission → `JogTrackingService` (foreground service type `location`, no background location; notification 5102 on channel `workout_run` with the distance and Finish) fed by `AndroidLocationFeed` (LocationManager GPS plus fused/network, GPS preferred while it flows; no Play services) → `JogTracker` (live distance/time/pace) → Finish saves a `GPS` session with `distance_m`, a route blob under `files/routes/` (synced as `BlobDir.ROUTES`) and a "Run" cardio set, then writes it to Health Connect. `HealthConnectWorkoutBridgeTest` 5 (incl. a 1 km track with a poor fix and a jump, and a watch duplicate) — next
- [x] 22.6 — Waydroid (2026-11-10, `~/.hermes/cache/scratch/next/shots/22-*`): Workout settings shows "Health Connect isn't on this phone yet…" (Waydroid has the Play Store but no Health Connect; nothing else changes); mock GPS (`cmd location providers add-test-provider gps`, 101 fixes 10 m apart every ~1.7 s) → the run card showed 1.00 km, 2:53, 2:53 /km; Finish → the DB (debug EXPORT_DB) has `GPS|1000|run-….route|176` = 1000 m for an expected 1000 m (0% off; limit 5%). Crash buffer empty. Note: fixes faster than 12 m/s are dropped by design, so the first try (fixes 0.6 s apart = 16 m/s) recorded 0 m
- [x] 22.8 — gate BUILD SUCCESSFUL (`assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test`): 1060 tests, 0 failures, 1 skipped. The UI-polish gate before it: 1034 tests, 0 failures
- [ ] 22.7 — Diet `NutritionRecord` write (H2 parity): not built, diet is feature-frozen. Real Health Connect (permissions dialog, a watch's sleep and runs) needs the Pixel

## M23 — AI delegation + Hermes (`feature/delegation`, branch `next`; plan §M23, protocol `docs/hermes.md`)

- [x] 23.1 — Research (local Hermes source `gateway/platforms/api_server*.py`): the API Server's Runs API (`POST /v1/runs`, SSE `GET /v1/runs/{id}/events` with `message.delta` / `clarify.request` / `approval.request` / `run.completed` / `run.failed`, `POST …/clarify`, `POST …/approval`), Bearer `API_SERVER_KEY`, sessions across runs. Written up in `docs/hermes.md` with the Klean block protocol — next
- [x] 23.2 — `:shared` `delegation/ApprovalPolicy` (> 5 distinct recipients always need approval in every mode; INTERACTIVE/PARTIAL ask for everything; AGENTIC acts alone up to 5) and `KleanBlocks` (find/strip `{"klean":…}` blocks anywhere, incl. code fences and braces inside strings; answer/decision writers; Hermes instructions; `delegateTitle`), `ModuleId.DELEGATION`, `AiTask.DELEGATION` ("Drafting delegated tasks"), `RuleCaptureClassifier.DELEGATE` ("ask Hermes to…", "Hermes, …", "delegate: …"; a task when Delegation is off). `ApprovalPolicyTest` 3, `KleanBlocksTest` 5, `RuleCaptureClassifierTest` +1 — next
- [x] 23.3 — `:feature:delegation`: `DelegationAgent` (+ `DelegationPayload` with no credential fields, `AgentEvent`, `ApprovalDecision`), `HermesAgent` (Ktor; one Hermes session `klean-<id>` per delegation; native clarify/approval answered on the same run, Klean blocks by the next run), `PartialAgent` (one structured `AiService` call: draft, subtasks, notes), `DelegationRepository` (delegation, messages, approval requests; stamped), `DelegationRunner` (follows events, applies `ApprovalPolicy`, auto-complete or user confirmation), `HermesSettings` (URL + token in `SecretStore`, Test = `GET /v1/health`) — next
- [x] 23.4 — Surfaces: "Delegate" on the task screen through the new `core/ui` `TaskExtension` contract (Tasks never depends on Delegation); mode dialog (draft with AI / ask Hermes / let Hermes do it, auto-complete switch, "Set up Hermes" when unset); delegation thread (`klean://delegation/{id}`: messages, reply, result, "Add N steps as subtasks", "Mark the task done"); `ApprovalScreen` (`klean://delegation/approval/{id}`: full draft and every recipient, Edit / Approve / Approve with edits / Don't send); Today APPROVALS card (first slot) and `klean://delegation`; notifications: channel `approvals` (high) id 6001 tagged by approval, channel `delegation` id 6002 tagged by delegation with an inline Reply (`DelegationReplyReceiver`); capture handler for `delegate`; Settings › Delegation (`klean://delegation/settings`). Module `DELEGATION` navOrder 70, not in the bar, arrives through "New in Klean" — next
- [x] 23.5 — Tests: `HermesAgentTest` 3 (scripted Runs API via MockEngine: question → answer → approval with 6 recipients → EDITED decision → result; native clarify answered on the same run; no URL), `DelegationRunnerTest` 5 (interactive flow end to end on Room, agentic ≤ 5 auto-approved but 6 wait, partial + subtasks, failure keeps the task, payload has no credential fields), `ApprovalScreenTest` 1 — next
- [x] 23.6 — Waydroid (2026-11-10, `~/.hermes/cache/scratch/next/shots/23-*`, stub log `…/next/hermes-stub.log`) against `scripts/device/hermes-stub.py` on the host (`http://192.168.240.1:8642`, key `stub-key`): Settings › Delegation → Test → "Hermes answered. You're set."; task "Invite the team to the offsite" → Delegate → "Ask Hermes…" → thread "Has a question for you" + notification "A question about …"; **replied inline from the notification shade** ("The 20th" → stub got `{"klean":"answer","text":"The 20th"}`); notification "Ready for your OK … 6 recipients", Delegation shows "Waiting for your OK"; approval screen lists the draft and all 6 recipients with the "more than 5 people" line; Edit → removed person6 → "Approve with edits" → stub got `decision EDITED` with 5 recipients and the draft; "Finished" notification; thread shows the result "Sent the invitation to 5 people: …" and "Mark the task done". Crash buffer empty
- [x] 23.8 — gate BUILD SUCCESSFUL: 1078 tests, 0 failures, 1 skipped
- [ ] 23.7 — Not built: resuming a delegation's event stream after the app process dies (Hermes keeps the session; Klean would need to re-attach with `Last-Event-ID`); the web has no delegation start (it edits approvals, M26). A real Hermes needs the user's URL and key

## M21 — Google Tasks overlay + Assistant (`core/google`, branch `next`; plan §M21, docs `docs/google-oauth.md`, `docs/assistant.md`)

- [x] 21.1 — `:core:google`: `GoogleAuth` (connect / consent / silent access token / invalidate / disconnect; tokens in memory only) with `PlayGoogleAuth` (Play services `AuthorizationClient`, scope `…/auth/tasks`, no client id in the app: Google matches the Android OAuth client by package + SHA-1; `available()` false without Play services) and `FakeGoogleAuth`; `GoogleLocalState` (DataStore `google`, never synced: connected flag, last sync, outbox) — next
- [x] 21.2 — `GoogleTasksClient` (Ktor; `users/@me/lists`, `lists/{id}/tasks` with completed + hidden, paged; `PATCH lists/{id}/tasks/{id}` for title, notes, due **date**, complete/uncomplete with `completed` cleared; one retry with a fresh token on 401). The `GoogleTasksApi` interface has no create or delete. `NoTaskCreationTest` 3: no `.post(`/`HttpMethod.Post` in the module's sources; a full sync with every kind of edit sends only GET/PATCH; every request any Google test made (logged by `FakeGoogleServer`, which fails any POST) — next
- [x] 21.3 — Cache + outbox: `GoogleTasksSync` replays the outbox first (PATCH, newest edit per task wins, 404/4xx dropped, 5xx/offline kept), then caches lists (`task_list.source=GOOGLE`, name-based ids, position 1000+ so after Klean lists, `selected` kept) and tasks of selected lists (`task.origin=GOOGLE`, `remote_id`, `remote_list_id`, `remote_etag`, parent → `parent_id`, done → DONE + `completed_at`); Klean-only fields kept (priority, a time on an unchanged date, archive); rows Google dropped or in a hidden list are tombstoned; nothing is restamped when unchanged. Disconnect tombstones Google rows and clears the outbox. `GoogleTasksWorker`/`GoogleTasksScheduler` (hourly while connected + after each edit / Tasks visit, network required). No schema change (M17 front-loaded the columns). `GoogleTasksSyncTest` 8, `GoogleTasksClientTest` 4 — next
- [x] 21.4 — Tasks side: `GoogleTaskEdits` multibinding; `DefaultTasksRepository` queues every edit of a Google task (complete, reopen, undo, move date, someday, edit), keeps a Google task in its list, never deletes one, and sends a new task (or step) aimed at a Google list to the default list; `refreshRemote()` on Tasks visits. UI (minimal, functional): "G" chip on Google task rows, Google lists after Klean's in the Lists tab without rename/delete, hidden lists and their tasks filtered out, no Delete and no list picker on a Google task's page, list pickers show Klean lists only, Settings › Tasks › Google Tasks (Connect / calm unavailable text / list checkboxes / Sync now / Disconnect). `GoogleOverlayTest` 5 — next
- [x] 21.5 — Assistant research (`docs/assistant.md`, 2026-11-10): Assistant removed from phones from 2026-09-04 in favour of Gemini; the BII catalogue has no CREATE_TASK/CREATE_REMINDER any more; AppFunctions (Android 16+) is the future route but Gemini integration is in private preview (library 1.0.0-rc01). Shipped: `shortcuts.xml` (main `app.klean` + debug twin `app.klean.debug`) with capabilities `CREATE_THING`, `UPDATE_ITEM_LIST` (Tasks), `RECORD_FOOD_OBSERVATION` (Diet) and static shortcuts Capture / Log food / New task / Remind me, all → exported `CaptureActivity` (prefill only, never auto-save); deep link `klean://capture?text=…&module=…` (`CaptureLinks`, aliases, 500-char cap; `MainActivity` forwards plain links), source `ASSISTANT` (new `CaptureSource` values `ASSISTANT`, `WATCH`, `WEB`). Klean never registers as the assistant. `CaptureLinksTest` 4, `CaptureDeepLinkTest` 3, `ShortcutsXmlTest` 3 — next
- [x] 21.6 — `docs/google-oauth.md`: own Cloud project, Tasks API, consent screen, Android client per package (+ optional Web client for a future server refresh token), testing-mode 7-day refresh tokens and how to avoid them, what is stored where, sync rules, gaps — next
- [x] 21.8 — Waydroid (2026-11-10, `~/.hermes/cache/scratch/next/shots/21-*`): `am start -d 'klean://capture?text=Buy%20batteries%20tomorrow&module=tasks'` → the capture sheet over Today, prefilled → Save → Tasks › Upcoming "Buy batteries · Tomorrow · Personal"; `cmd shortcut get-shortcuts` lists capture / log_food / new_task / remind_me; the shortcut intent (`-n …/CaptureActivity -d klean://capture?module=diet`) opens the sheet with "What did you eat?"; Settings › Tasks › Google Tasks shows "Show your Google Tasks … Connect" (Waydroid has Play services) → Connect opens Google's account sign-in (consent IntentSender works) → Back → "Not connected. You can connect any time."; crash buffer empty. Note: a second link while the sheet is open is delivered to the open sheet (singleInstance) and ignored
- [x] 21.9 — gate BUILD SUCCESSFUL: 1108 tests, 0 failures, 1 skipped
- [ ] 21.7 — Needs the user: OAuth clients (Android `app.klean` / `app.klean.debug` + SHA-1) and the Pixel session (real sign-in, list pick, tick off in Klean → Google, edit in Google → Klean, offline edit replay; long-press shortcuts; "Hey Google"/Gemini phrases). Not built: `calendar.readonly`, AppFunctions (waits for Gemini's public rollout), sign-in without Play services, sending Google-task edits made on a second, unconnected Klean phone

## M28 — Capture everywhere (`core/capture`, branch `next`; plan §M28)

- [x] 28.1 — Handlers for every action (already present from M18–M23, now checked together): diet, task, reminder (time; "alarm"/"wake me" → alarm), event, contact/birthday, workout, sleep, delegation. Tasks capture now also reads **list** (`#work`, "in my Home stuff list"; Klean lists only, an unknown tag stays text) and **priority** (standalone `!`/`!!`/`!!!`, "urgent"/"high priority", "medium priority", "low priority"). `TaskCaptureTextTest` 4 — next
- [x] 28.2 — No-AI rules route a phrase per module (times, dates, "remind me", "slept…", "30 pushups", birthdays, "ask Hermes to…"); anything else goes to Diet (H7) and is now one tap from every other enabled module: `CaptureResult.Saved.others` (fallback saves only; order Tasks, Reminders, Calendar, Diet, Workout, Sleep, Contacts, Birthdays, Delegation) behind one bounded "Move to…" chip with a menu in the capture sheet, and "Saved to Diet · Move to…" under Today's capture bar (6 s; both hold open while the menu is open). `CaptureEverywhereTest` 5 (every phrase without AI; module off → never routed, even when aimed at; 56 move pairs with the way back offered; fallback lists the others; diet alone stays quiet), `CaptureViewModelTest` +2, `TodayViewModelTest` +1 — next
- [x] 28.3 — "Move to…" per pair with the real handlers and Room: `CaptureMoveMatrixTest` (instrumented, Hilt): every module has a handler, every phrase routes to its module, and all 56 ordered pairs save the new record, undo the old one and offer the way back. **Waydroid 2026-11-11: OK (3 tests, 6.4 s)** — next
- [x] 28.4 — Module off removes it from routing: JVM above for all 8 modules, plus the existing instrumented `*DisableTest`s (Tasks, Calendar, Contacts, Workout, Sleep, Module) — next
- [x] 28.5 — Reachable from: Today bar (DASHBOARD), Today widget and diet widget (WIDGET), QS tile (QS_TILE), status notification reply + "Capture…" (NOTIFICATION_REPLY), web (`web/src/capture`, rules + alternatives + module-off), Assistant/shortcuts/deep link (ASSISTANT, M21). Watch: the watch app sends `WatchMessage.Capture`/`LogFood` (M27); the phone-side Data Layer listener that feeds them into `UniversalCaptureService` (source `WATCH`) is not built yet (see M27) — next
- [x] 28.6 — Waydroid (2026-11-11, `~/.hermes/cache/scratch/next/shots/28-*`): capture sheet with "Lamb kofta wrap" → "Saved to Diet" + "Move to…" → menu Tasks / Reminders / Calendar / Workout / Sleep / Contacts / Delegation, still open after 5 s → Tasks → Tasks › Lists › Personal has "Lamb kofta wrap"; Today bar "Falafel bowl" → "Saved to Diet · Move to…" → Tasks → Today's diet tile went from 3 to 2 entries; crash buffer empty
- [x] 28.7 — gate BUILD SUCCESSFUL: 1120 tests, 0 failures, 1 skipped
- [ ] 28.8 — Not built: "Move to…" after a notification inline reply (the reply saves silently; correct it in the module); the phone-side watch listener (M27). From here on lane NEXT leaves `feature/**/ui/**`, `core/design/**` and `app/…/{nav,today}/**` to the UI overhaul

## M27 — Wear OS app (`wear/`, branch `next`; plan §M27, docs `docs/wear.md`)

- [x] 27.1 — `:wear` app module (min SDK 30, target 37, applicationId `app.klean` / debug `app.klean.debug` like the phone, `standalone=false`); tiles 1.6.2 + protolayout 1.4.2, complications data source 1.3.0, wear-input 1.2.0, play-services-wearable 20.0.1; in `settings.gradle.kts` (not in server-only mode) — next
- [x] 27.2 — Tiles (plain protolayout, dark Klean tokens, ≤ 3 rows, 48 dp chips): Log food (recent-food chips → `LogFood`, Speak → voice capture for Diet), Today's food (count + latest 3 with times, Add), Next up (next reminder/task + Capture), Workout (saved workouts → `StartWorkout`; during a session the set count + Log a set); Diet off → "Diet is off on your phone". `KleanTileService` renders the stored `WatchState` with no network — next
- [x] 27.3 — Complication `FoodLogComplicationService` (SHORT_TEXT: today's entry count, "N food entries today. Tap to log.", tap → food capture; Diet off → "Klean" universal capture; preview data) — next
- [x] 27.4 — Data Layer: `WearProtocol`/`WearCodec` (pure Kotlin: `/klean/state` DataItem phone→watch with a versioned `WatchState`, trimmed oldest-food-first under 90 KB; messages `/klean/capture`, `/food/log`, `/workout/start`, `/workout/set`, `/alarm` as JSON with a `t` discriminator, path-checked, text capped at 500), `WatchListenerService` (stores newer snapshots only, refreshes tiles + complication, vibrates on the alarm ping), `DataLayerPhoneLink` (capability `klean_phone`, nearest node, 10 s timeout), `WatchCaptureActivity` (system voice/keyboard input) and `SendActivity` (chip taps) with calm toasts "Sent to your phone" / "Couldn't reach your phone. Try again when it's nearby." — next
- [x] 27.5 — Tests (Robolectric, `:wear:testDebugUnitTest` 21): `WearCodecTest` 6, `TileRendererTest` 7 (layout texts and click targets per tile, Diet off, `TestTileClient` with and without stored state), `ComplicationAndInboxTest` 8 (complication text/description/tap, Diet off, SHORT_TEXT only, newer-state-only store, only the alarm accepted from the phone, chip/voice → messages, chip tap sends + toast, `WearColors` = the dark tokens in `core/design/tokens/klean-color-tokens.json`) — next
- [x] 27.6 — gate BUILD SUCCESSFUL with `:wear:testDebugUnitTest` added (`assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test :wear:testDebugUnitTest`): 1141 tests, 0 failures, 1 skipped. **AGENTS.md's gate command needs `:wear:testDebugUnitTest` appended (parent)**
- [ ] 27.7 — Not built (lane NEXT was limited to `wear/`; the steps are in `docs/wear.md`): the phone side (`WearProtocol` → `:shared`, `WearSyncService` feeding `UniversalCaptureService` with source `WATCH`, `WatchStatePublisher` putting `/klean/state` after diet/task/reminder/workout/module changes, `klean_phone` capability, alarm ping from `AlarmActivity`, `WearableExtender` actions on the status and reminder notifications), the optional on-watch `health` foreground session, CI attaching `wear-debug.apk` (`.github/workflows` is the parent's). Real-watch checks are listed in `docs/wear.md` and `docs/acceptance-p1.md`

## M29 prep — acceptance sheet (`docs/acceptance-p1.md`)

- [x] 29.0 — Every acceptance criterion from the brief, grouped as in the brief (Foundation, Diet, Tasks and reminders, AI delegation, Calendar/contacts/birthdays, Workout and sleep, Backend/web/multi-device, Tests), with Waydroid / Pixel / Watch / Backend+Browser columns; Waydroid and Backend+Browser pre-filled with evidence (TASKS refs, tests, screenshot folders), Pixel and Watch cells say what to check; an "Open before p1" list (phone-side Wear, OAuth clients, real Hermes, web parity/push, UI overhaul) — next
- [ ] 29.1 — The user's session: Pixel and watch columns; tag `p1` after every row passes
