# TASKS.md — Klean Prototype 1 task tracker

Task-level status for `PLAN.md` (§8 has the full instructions and code for each task).
Keep this file current: tick a task in the same commit that finishes it.

Legend:
- `[x]` done and verified by build + tests
- `[~]` partially done or unverified (see the note)
- `[ ]` not started

Where it lives:
- `main`: merged
- `m4-work` / `m9-work`: unmerged branches with worktrees at `../klean-m4` and `../klean-m9`


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

- [x] Task 4.1 — Channels + notification IDs — `m4-work`
- [x] Task 4.2 — DietStatusNotifier — `m4-work`
- [x] Task 4.3 — DietReplyReceiver (instrumented test first) — `m4-work`
- [x] Task 4.4 — CaptureActivity + CaptureSheet + UniversalCaptureService — `m4-work`
- [x] Task 4.5 — Universal capture bar on Today — `m4-work`
- [x] Task 4.6 — Quick Settings tile — `m4-work`
- [x] Task 4.7 — Glance widget — `m4-work`
- [~] Task 4.8 — Module disable plumbing — `m4-work` WIP `adce112`

## M5 — Prompts: quiet hours, scheduling, rotation, learning, reboot

- [ ] Task 5.1 — QuietHoursService
- [ ] Task 5.2 — AlarmScheduler + Reschedulable + RescheduleReceiver
- [ ] Task 5.3 — Phrasing pool
- [ ] Task 5.4 — PromptNotifier
- [ ] Task 5.5 — DietPromptEngine (TDD with fakes)
- [ ] Task 5.6 — Day rollover
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

- [x] Task 9.1 — BackupManager.export (instrumented test first) — `m9-work`
- [x] Task 9.2 — inspect + restore — `m9-work`
- [x] Task 9.3 — Instrumented backup round-trip test — `m9-work`
- [~] Task 9.4 — Backup screen — `m9-work` WIP `cf04d02`
- [x] Task 9.5 — AutoBackupWorker — `m9-work`

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

## Device-only checks still pending (no phone has been connected yet)

Code and JVM/Robolectric tests stand in for these until a phone is available:
- [ ] 0.8: app launches on device (`:app:installDebug`)
- [ ] 1.8 / 3.11: bottom bar shows Today / Diet / Settings on device
- [ ] 4.3: `InlineReplySaveTest` via `connectedDebugAndroidTest`; inline reply from the shade and the lock screen in airplane mode
- [ ] 4.4 / 4.6 / 4.7: capture sheet over the home screen, QS tile, widget update after an inline reply
- [ ] 9.3: `BackupRoundTripTest` via `connectedDebugAndroidTest`; a Robolectric equivalent passes on `m9-work`

