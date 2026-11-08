# TASKS.md — Klean Prototype 1 task tracker

Task-level status for `PLAN.md` (§8 has the full instructions and code for each task).
Keep this file current: tick a task in the same commit that finishes it.

Legend:
- `[x]` done and verified by build + tests
- `[~]` partially done or unverified (see the note)
- `[ ]` not started

Everything ticked is merged on `main` (milestones M0–M4 and M9 are tagged).


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
- [ ] Task 5.7 — Verification on device

## M6 — Voice, photo, quick repeats

- [ ] Task 6.1 — VoiceInput
- [ ] Task 6.2 — Photo attach
- [ ] Task 6.3 — Quick repeat chips everywhere

## M7 — History and gaps

- [ ] Task 7.1 — Day history
- [ ] Task 7.2 — Gap affordance
- [ ] Task 7.3 — Patterns placeholder

## M8 — Onboarding and permissions

- [ ] Task 8.1 — Permission state model
- [ ] Task 8.2 — PermissionsRoute
- [ ] Task 8.3 — Onboarding graph

## M9 — Backup and restore

- [x] Task 9.1 — BackupManager.export (instrumented test first)
- [x] Task 9.2 — inspect + restore
- [x] Task 9.3 — Instrumented backup round-trip test
- [x] Task 9.4 — Backup screen
- [x] Task 9.5 — AutoBackupWorker

## M10 — AI providers, enrichment, routing

- [ ] Task 10.1 — SecretStore
- [ ] Task 10.2 — AiProvider interface + gateway (TDD)
- [ ] Task 10.3 — Gemini provider (TDD with Ktor MockEngine)
- [ ] Task 10.4 — OpenAI-compatible provider (TDD)
- [ ] Task 10.5 — FoodEnrichmentWorker (TDD)
- [ ] Task 10.6 — AI items UI
- [ ] Task 10.7 — AI provider settings screens
- [ ] Task 10.8 — AI capture classifier

## M11 — Unlock sheet

- [ ] Task 11.1 — Spike on the real phone (timebox 30 min)
- [~] Task 11.2 — ScreenStateMonitor + eligibility (TDD) — pure `UnlockSheetPolicy` + tests done in `:shared` (M2); Android `ScreenStateMonitor` not started
- [ ] Task 11.3 — UnlockSheetActivity + notification

## M12 — Delivery-app trigger

- [ ] Task 12.1 — DeliveryListenerService
- [ ] Task 12.2 — Contextual offer
- [ ] Task 12.3 — Verify with a simulated notification

## M13 — Polish, disable audit, accessibility, copy audit

- [ ] Task 13.1 — Disable audit
- [ ] Task 13.2 — Accessibility pass
- [ ] Task 13.3 — Copy audit
- [ ] Task 13.4 — Appearance + About

## M14 — On-device model

- [ ] Task 14.1 — Runtime choice spike (timebox 1 h)
- [ ] Task 14.2 — OnDeviceProvider

## M15 — Place-based prompts

- [ ] Task 15.1 — Geofence CRUD
- [ ] Task 15.2 — Registration + exit prompt

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

Still needs hands-on checks by the user (adding a widget needs a confirmation tap; airplane mode would drop wireless adb):
- [ ] 4.3: inline reply from the lock screen in airplane mode (needs adb over USB or no adb), under 5 s
- [ ] 4.7: widget shows today's entries and updates within seconds after an inline reply; compact size
- [ ] 4.8: with Diet off, the widget shows its disabled state and the QS tile shows unavailable
- [ ] 9.4: export a backup through the picker, restore it, check the confirmation text
- [ ] Polish: the app uses the default Android launcher icon; none has been designed yet
- [ ] Polish: after deleting from the entry screen it stays on the greyed-out entry; it should go back to the list and show Undo there
