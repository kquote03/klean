# Prototype 1 acceptance (M29)

This file has every criterion from "Acceptance criteria for Prototype 1" in the brief (`Klean — Prototype 1 Build Prompt`), grouped the same way.

**Who fills what:**
- The agent pre-filled the **Waydroid** and **Backend+Browser** columns with evidence. References point to `TASKS.md` items, test classes, and screenshot folders under `~/.hermes/cache/scratch/`.
- The **Pixel** and **Watch** columns are for the user's session. Each holds what to check.
- Tag `p1` only when every row passes.

**Legend:**
- ✅ passed, with evidence
- ⚠️ partly passed, or a stand-in passed (see the note)
- ⬜ to do in the user's session
- ❌ not built yet
- — doesn't apply

**Environment:**
- Waydroid: LineageOS, Android 13 / API 33, 540×1212.
- Backend: the Docker `klean-server` image, or the earlier Jib `klean-backend` image.
- Browser: Playwright Chromium, plus manual review.
- Pre-filled 2026-11-11 from branch `next`, at the M28 gate (1120 JVM tests, 0 failures) and M27 (wear: 21 Robolectric tests).

## Foundation

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| F1 | With airplane mode on, no AI, no Google and no backend, every module works on the phone. | ⚠️ Inline reply in airplane mode saved offline (TASKS Part F6). Capture routing is rules-only, no AI (`CaptureEverywhereTest`, `CaptureMoveMatrixTest` on Waydroid). Backend stopped → "Can't reach the server right now. Klean keeps working…", local saves continue (25.8). No full airplane-mode walk of every module yet. | ⬜ Airplane mode, no AI provider, not paired, Google not connected: log food, add a task/reminder/event/contact, start a workout, log sleep, delegate (partial needs AI, so expect the calm "needs AI" path). | — | — |
| F2 | Disabling any module removes it from navigation, the dashboard, widgets, capture routing and all notifications. | ✅ Instrumented on Waydroid: `ModuleDisableTest`, `TasksDisableTest`, `CalendarDisableTest`, `ContactsDisableTest` (3/3), `WorkoutDisableTest`, `SleepDisableTest` (F9, 18.9, 19C.11, 19K.11, 20.8; 15/15 in 25.10). Routing: `CaptureEverywhereTest.switchingAModuleOffRemovesItFromRouting` (all 8 modules, even when a capture is aimed at one). The Today-widget rows need a placed widget, which Waydroid's launcher can't do (25.9a). | ⬜ Toggle each module in Settings › Modules. Check the bottom bar/More, Today cards and tiles, the Today and diet widgets, the QS tile, the status line, and that no notification comes from it. | ⬜ Diet off → the food tiles and complication say "Diet is off on your phone" / show "Klean" (`TileRendererTest`, `ComplicationAndInboxTest` cover the rendering). Needs the phone-side `WatchStatePublisher` (docs/wear.md). | ✅ Web: modules from the synced `core.modules.disabled`; `capture.test.ts` "a switched-off module is never a target" (26.4, 26.5). |
| F3 | No prompt fires during quiet hours except user-set alarms. All schedules survive a reboot. | ✅ Quiet hours move the check-in past the quiet end (F2). Reboot stand-in: `waydroid session stop/start` re-armed the check-in and day rollover (F1). Reminders re-arm after a restart (S2). Alarms bypass quiet hours by design (18.7). | ⬜ A real reboot → `dumpsys alarm` shows the check-in, reminders and bedtime. Set quiet hours over now and wait for a check-in (it must not fire). An alarm reminder in quiet hours still rings. | — | — |
| F4 | A backup exported on one install restores every entity, photo and setting on a fresh install. | ✅ Export through the picker → uninstall → install → restore: 4 entries + the photo back (F4). `BackupRoundTripTest` ×2 instrumented (F9). | ⬜ Export on the Pixel with data in every module (tasks, reminders, events, contacts, workouts, sleep, delegation) → restore on a fresh install (or after `pm clear`). Check every module. | — | ⚠️ Server `POST /admin/backup` zip (24.14). The web Settings download is not yet restored by hand (26 "still needs"). |
| F5 | The theme is Cantarell, pastel Material 3 Expressive, following system light/dark on Android, Wear OS and web. | ✅ Light/dark/font-scale review with seeded data (25.9, `~/.hermes/cache/scratch/m25/visual/`). `ContrastTokensTest`, `NoRawColorsTest`. Note: the UI overhaul (UI plan Phases 1–3) is in progress. | ⬜ Light and dark (system toggle) on every screen after the UI overhaul. | ⬜ Tiles use the dark tokens (`WearColorsTest`); check on the watch. The watch uses the system font, not Cantarell. | ✅ Tokens → CSS, Cantarell bundled, `prefers-color-scheme`. Playwright visual review in light and dark at 1440×900 and 390×844 (26.2, 26.12). WCAG AA tests (26.11). |
| F6 | No user-facing text mentions ADHD, autism or neurodivergence (grep string resources and web copy). | ✅ `ForbiddenCopyTest` scans every module's `res/**/*.xml` and manifests, wear included (runs in the gate). | — (same APK) | ✅ Same test covers `wear/src/main/res`. | ✅ `web/scripts/check-copy.mjs` (src + dist) and `copy.test.ts` (26.8). |

## Diet

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| D1 | The user can log food from the persistent notification's inline reply in under 5 seconds, without opening the app. | ✅ Shade reply in airplane mode saved 251 ms after Send, no activity started (F6). `InlineReplySaveTest` instrumented. | ⚠️ Reply from the shade already saved on the Pixel (Device checks 4.3). ⬜ From the **lock screen** in airplane mode, timed < 5 s. | — | — |
| D2 | The widget updates within seconds of a log from any surface (phone, watch, web). | ⚠️ Waydroid's launcher can't place the widget for screenshots (25.9a). Widget layouts checked by pinning (F5). | ⚠️ Phone: the widget updated about 2 s after a log (Device checks 4.7). ⬜ Log from the web (paired) and from the watch, then time the widget. | ⬜ Install `wear-debug.apk` (nightly) on the watch, same key as the phone. Log a food chip on the watch → the phone widget and the watch's Today's food tile update within a few seconds (phone side 27.7: `PhoneWearInboxTest`, `WatchStatePublisherTest`). | ✅ Web → phone sync within one sync run (≈10 s after a change, 25.8; e2e 26). |
| D3 | Voice and photo entry work. Photo is optional. | ⚠️ Voice: permission + recogniser fallback; no audio on Waydroid (6.1). Photo: picker works; Waydroid has no camera (6.2). Entries without a photo work everywhere. | ⬜ Speak "two manakish" (also in airplane mode) → saved with `via_voice=1` + Undo. Take a camera photo → thumbnail, EXIF stripped. | ⬜ Voice capture on the watch ("two manakish") → a diet entry with source WATCH and `via_voice=1` on the phone. Also "Add something" on the bridged status notification: voice and the ≤ 5 chips (27.7, `KleanStatusNotifierTest`). | — |
| V5 | Voice uses the phone's own speech recognition (system recogniser → on-device only offline → system dialog), on its own full-screen page, and every failure says its real cause (plan 2026-11-11 V1–V4). | ✅ Waydroid 2026-11-11 (no mic): `klean://voice` opens the page full screen, with no bar → the system recogniser (Google) starts with `en-US` → no audio gives "Didn't catch that…", never "offline". "Use Google voice" opens Google's dialog. With Google's speech app disabled, the page says "Voice didn't start. Try again, or type it." and hides "Use Google voice". Back slides it down. The capture sheet's mic opens the same page over the sheet. `VoiceEngineTest` 10, `VoiceSessionTest` 18, `VoiceCaptureScreenTest` 9. Shots: `~/.hermes/cache/scratch/voice-shots/`. | ⬜ Speak → "banana and coffee" → the Food chip is selected → Save → "Saved to Food · Undo". Airplane mode with the English pack installed → it still works. Without the pack → "Voice needs the English pack to work offline · Download" is offered. While testing, read `adb logcat -s KleanVoice:I` (`voice start mode=… lang=…`, `voice error=…`). | — | — |
| D4 | Ignoring a prompt changes the format of the next one, never the frequency. After 7 days, prompts shift toward actual meal times. | ✅ Format rotation STANDARD → HEADS_UP → SILENT_VIBRATE → WIDGET_HIGHLIGHT on fire (5.7, F3). The `MealTimeLearner` and scheduling unit tests run in the gate. | ⬜ Over a week of real use, check-in times move toward the logged meal times. Ignored prompts change their format, not how often they come. | — | — |
| D5 | A simulated delivery notification triggers a prompt. The unlock-after-3-hours sheet works. Gaps show as neutral markers. | ✅ A simulated "delivered" post → "What did you order?" + inline save, with debounce (12.3). Unlock sheet spike + debug "pretend 3 h" (11.1–11.3). Neutral gap markers (7.1, 3.12). | ⬜ A real Talabat/Deliveroo/Careem/noon delivery (12.3). The unlock sheet after a real 3 h lock on Android 17. | — | — |
| D6 | Calories without counting (plan 2026-11-11): `150x100x200` in the kcal slot saves 300 kcal; an empty slot fills from earlier entries; with an AI assigned to Calorie estimates, `rice 200g` gets ≈ kcal within a few seconds, and fills in later after airplane mode. Goal bar and Copy today. | WAYDROID_D6 | ⬜ With an AI assigned to Calorie estimates: log "rice 200g" → ≈ kcal appears within a few seconds; in airplane mode it fills in later. | — | — |

## Tasks and reminders

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| T1 | Recurring tasks, priorities and separate lists work. The daily "Still want these?" review appears for overdue tasks. | ✅ Waydroid walkthrough (M18, 18.4) and the past-due review with synced seed data (25.8). Capture with `#list` and `!!` priority (`TaskCaptureTextTest`; M28). | ⬜ Create a weekly task, complete it → the next one appears. Lists and priorities. The 10:00 review notification. | — | ✅ Web Tasks: Today with "Still want these?", Upcoming, Lists, Done; repeats roll forward (26.4). |
| T2 | An alarm reminder rings full-screen on the main phone only. Dismissing it does not complete the task. | ✅ `AlarmActivity` over the lock screen with sound; Dismiss stops it; snooze re-arms it exactly (M18 walkthrough). Main-phone-only rule (25.5). | ⬜ Full-screen over the lock screen on Android 17, through Do Not Disturb. A second paired phone stays silent. The task stays open after Dismiss. | ⬜ The watch buzzes (three long pulses) when the main phone's alarm rings; Dismiss on the phone or on the watch's bridged notification (Dismiss / 10 min) stops it (27.7: `ReminderEngineTest` alarm-ping tests, `ReminderWearActionsTest`). | — |
| T3 | Google Tasks from all selected lists appear and can be completed and edited from phone and web. Nothing is ever created in Google Tasks. | ⚠️ Fakes only (M21): `GoogleTasksSyncTest`, `GoogleOverlayTest`, `NoTaskCreationTest`. Settings › Tasks › Google Tasks → Connect opens Google's sign-in; declining is calm (21.8). Needs the user's OAuth client. | ⬜ With your OAuth client (docs/google-oauth.md): connect, pick lists, tick off/edit in Klean → visible in Google Tasks; edit in Google → Klean; offline edit → replayed later. Confirm no new tasks appear in Google. | — | ⚠️ Server-side Tasks list/patch with a never-create test (24.11). The web Google › Connect needs the OAuth client (26 "still needs"). |
| T4 | "Remind me tomorrow at 5 to call Ahmed" works by voice in-app and through Google Assistant / Gemini. | ⚠️ The typed phrase routes to Reminders by rule (`CaptureEverywhereTest`, `RuleCaptureClassifierTest`). The deep link `klean://capture?text=…&module=reminders` and the "Remind me" shortcut open the sheet prefilled (21.8). No mic audio on Waydroid. | ⬜ Say it in the capture sheet. Long-press Klean → "Remind me". Try "Hey Google, remind me … in Klean" / Gemini. Expect no third-party create-reminder route under Gemini (docs/assistant.md). Record what happens. | ⬜ Speak it on the watch (needs the phone listener). | — |

## AI delegation

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| A1 | Partial delegation attaches a draft to a task using the selected provider. | ⚠️ `DelegationRunnerTest` (partial + subtasks). The provider path was checked with a fake OpenAI-compatible server in M10. Partial delegation on Waydroid with a provider has not been run. | ⬜ With a real provider (Gemini key or a local server): Delegate → "Draft with AI" → a draft and steps attached. | — | — |
| A2 | Against a running Hermes instance, an interactive email task asks a question, shows an editable approval (draft plus recipients), and sends only after approval. | ✅ Against the scripted Runs API stub (`scripts/device/hermes-stub.py`): question → inline reply → approval with every recipient → edit → "Approve with edits" → the stub got EDITED with the draft (23.6). | ⬜ Against your real Hermes (URL + key): an interactive email task end to end. | — | ✅ Web Approvals screen edits and approves; the phone pulls `decision=EDITED` (26 e2e). Server Hermes relay (24.12). |
| A3 | A task with more than 5 recipients always requires approval. | ✅ `ApprovalPolicyTest` (every mode). `DelegationRunnerTest` (agentic: ≤ 5 auto, 6 waits). Waydroid: 6 recipients → "more than 5 people" approval (23.6). | ⬜ Spot check with real Hermes in agentic mode. | — | ✅ Same shared `ApprovalPolicy`. The web shows every recipient (26.4). |

## Calendar, contacts, birthdays

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| C1 | Klean contacts appear in the system dialer with no server running. | ✅ Raw contacts under the Klean account (`content query raw_contacts`); off removes them, on brings them back (M19 Contacts). | ⬜ Open the Phone/Contacts app with the server stopped: Klean contacts are listed and callable. | — | — |
| C2 | With DAVx5 syncing the Klean backend, there are no duplicate contacts or events. | ⚠️ `DavAccounts.syncsOwnBackend` → the mirror steps aside (25.6, `DavBackendMatchTest`). Merge card for DAVx5 lookalikes (19K.12). DAVx5 against the real backend not run. | ⬜ Install DAVx5, add `<server>/dav/` with an app password, sync contacts and calendar: no duplicates in the dialer or the calendar. | — | ⚠️ CardDAV/CalDAV routes + e2e DAV PUT/PROPFIND (24.9). DAVx5 compatibility is still to verify (24 known gaps). |
| C3 | VCF and ICS import/export round-trips. | ⚠️ ICS export through the picker produced VEVENT/RRULE/VALARM (M19 Calendar 22–23). Import through the picker hung Waydroid's system process, so import is covered by `CalendarRepositoryTest`, `ContactRepositoryTest` (v3/v4 round trip) and `VCardCodec`/iCal round-trip tests. | ⬜ Export contacts (VCF) and the calendar (ICS) → re-import on a fresh install → identical. | — | ✅ Shared vCard/iCal codecs round-trip tested (24.9). |
| C4 | Birthdays appear on the calendar with reminders on the day and 3 days before. | ✅ "birthday Omar November 10 1990" → "Omar turns 36 today" reminder, Agenda/Calendar entry; detail shows "On the day, 3 days before" (M19 Contacts). | ⬜ A birthday 3 days ahead → the reminder 3 days before at 09:00 and on the day. | — | ✅ Web Contacts and Birthdays (26.4). |
| C5 | Google calendars show read-only on the phone and on the web. | ⚠️ Phone calendars (including a Google account synced by Android) show read-only through the calendar provider (`calendar_source` DEVICE, M19 Calendar). Waydroid has no Google account. | ⬜ With the Pixel's Google account: Google calendars show in Klean and can't be edited. | — | ⚠️ Server Google Calendar read against a MockEngine fake (24.11). Needs the OAuth client on the web. |

## Workout and sleep

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| W1 | A saved template can be started, adjusted and logged from the phone and the watch. | ✅ Phone: template "3x15 pushups, 1 min plank, 5 km run" → session, +1 set, extra exercise, Finish → history (M20 walkthrough). | ⬜ Same on the Pixel. | ⚠️ The watch Workout tile lists templates and sends `StartWorkout`/"Log a set" (`TileRendererTest`, `WearCodecTest`); the phone starts the session and adds the set (`PhoneWearInboxTest`, 27.7). ⬜ Start a saved workout from the tile → a session opens on the phone and the tile shows it; "Log a set" adds a set. | ✅ Web Workout "+1 set" (26.4). |
| W2 | A GPS jog or a watch-recorded run appears once, not twice. Sessions are written to Health Connect. | ✅ Mock-GPS 1 km run → 1000 m saved (0% off). Health Connect unavailable on Waydroid → calm row (22.6). Duplicate rule `HealthMergeTest`/`HealthConnectWorkoutBridgeTest` (22.4, 22.5). | ⬜ Real GPS jog. A run recorded on the watch (to Health Connect) overlapping it → one session. Klean sessions visible in Health Connect. | ⬜ Record a run with the watch's fitness app at the same time. | — |
| W3 | Sleep from the watch (via Health Connect) and from manual taps shows on the dashboard. The bedtime reminder fires once. | ✅ Manual taps: "Going to bed" / "I'm up", capture "slept at 11 woke at 7" (M20). Debug BEDTIME broadcast → "Winding down soon?" (M20). HC import is unit-tested (`HealthConnectSleepImportTest`). | ⬜ A night tracked by the watch → it appears via Health Connect (an overlapping manual night is replaced). The bedtime reminder fires once at the real time, also after a reboot. | ⬜ Wear the watch overnight. | ✅ Web Sleep one-tap bed/wake (26.4). |

## Backend, web and multi-device

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| B1 | `docker compose up` starts the backend. A phone pairs by QR, and two phones plus the web client stay in sync. | ✅ compose up → paired by the QR link (pasted) → logs reach the server ≈10 s later; a second device (curl) synced 24 records to the phone (25.8). | ⬜ Pair the Pixel by **scanning** the web's QR. A second phone (or Waydroid) + the web: edit on each, all converge. | — | ✅ `docker compose up`, health, web e2e: web ↔ phone task both ways, approvals (26 e2e). Image smoke + reproducibility (Server image). |
| B2 | The web client has full parity, including approvals and browser notifications. | — | — | — | ⚠️ All modules + approvals + Settings (26.4). Not at parity: subtask/list management, rich event and contact editors, no AI on the web (26 known gaps). Web push needs a real browser on https/localhost (26 "still needs"); server-side push schedules are not built. |
| B3 | Answering a prompt on one device clears it everywhere. | ⚠️ `PromptAnsweredElsewhere` + `PromptNotificationIdsTest` (25.5). Not run on two devices. | ⬜ Two paired phones: answer a check-in on one → the other's notification disappears after sync. | — | ⚠️ The web updates the synced `prompt_log` like any record; the browser-notification side needs web push (B2). |
| B4 | Stopping the backend never breaks the phone. | ✅ `docker compose stop` → saves continue, calm "Can't reach the server…", retry delivered after start (25.8). `SyncEngineTest` server down/500. | ⬜ Stop the server for a day of normal use. | — | ✅ Same evidence (25.8). |

## Tests (brief's last line)

| Kind | Status |
|---|---|
| Unit: prompt scheduling, quiet hours, format rotation, recurrence, capture routing, sync merge | ✅ In the gate (`testDebugUnitTest`, `:shared:jvmTest`). 1120 tests, 0 failures, at M28. Capture routing: `RuleCaptureClassifierTest`, `CaptureRouterWiringTest`, `CaptureEverywhereTest`, `CaptureCasesFixtureTest`. |
| Instrumented: inline reply, alarms, backup/restore, sync adapters | ✅ On Waydroid: `InlineReplySaveTest`, `BackupRoundTripTest`, the module `*DisableTest`s, `SecretStoreDeviceTest`, `CaptureMoveMatrixTest` (3/3, 56 move pairs). Alarms are covered by Robolectric plus the Waydroid walkthrough. Sync adapters are JVM tests against the in-process backend (`SyncEngineTest`). |
| Backend integration: sync, pairing, DAV | ✅ `:backend:test` (62+ tests, M24) plus the Docker e2e scripts. |
| Wear | ✅ `:wear:testDebugUnitTest` (15 Robolectric tests: tiles, complication), the codec in `:shared:jvmTest` (`WearCodecTest` 7), the phone side in `:app:testDebugUnitTest` (`app.klean.watch.*`, 23). Real-watch checks are in the Watch column. |

## Navigation and Back (plan 2026-11-11)

| # | Criterion | Waydroid | Pixel | Watch | Backend+Browser |
|---|---|---|---|---|---|
| N1 | Back works on every screen and overlay and never zooms out inside the app; switching groups fades through, opening a detail slides on the X axis, full-screen menus slide up. | ✅ `BackNavigationTest` 15/15 (every Settings screen, task, event, Diet History › day › entry, workout session, contact › edit, Add menu, ⋮ menu, capture sheet, Arrange Today, setup again, Plan → Today, Today leaves). A back *gesture* (gesture nav switched on for the check) pops detail → Tasks and Plan → Today; Android 13 shows only the arrow mid-swipe, no in-app progress animation (`~/.hermes/cache/scratch/back-shots/e4-*.png`). | ⬜ Gesture nav: swipe slowly from a task's page → the Tasks list peeks behind (our pop transition), not a shrinking window. Plan › + › Task → Back closes only the sheet. People › + → last line is New contact. Tabs fade through; the account menu slides up and down. | — | — |

## Open before `p1` (from the rows above)

1. ~~**Phone-side Wear OS**~~: built in 27.7 (branch `wear-phone`, docs/wear.md "Phone side"). Still open: the optional on-watch `health` session (27.8), and every Watch-column check on real hardware.
2. **OAuth clients** for Google Tasks/Calendar (docs/google-oauth.md). Then T3 and C5.
3. **Real Hermes URL and key** for A2.
4. **Web parity gaps and server-side push schedules** (B2).
5. **UI overhaul** (UI plan Phases 2–3) before the F5 light/dark review on the Pixel.
