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

Still needs hands-on checks by the user (airplane mode or a reboot would drop wireless adb):
- [ ] 4.3: inline reply from the lock screen in airplane mode (needs adb over USB or no adb), under 5 s
- [ ] 4.7: compact widget size (resize the widget to one row)
- [ ] 12.3: a real delivery from Talabat/Deliveroo/Careem/noon: its "delivered" wording matches and the offer card appears with the app installed (Waydroid has no delivery apps)
- [ ] 9.4: export a backup through the picker, restore it, check the confirmation text
- [ ] 5.7: swipe a check-in away, then fire again → the next format differs (a swipe counts as DISMISSED)
- [ ] 5.7: set quiet hours over now, wait for a scheduled check-in → nothing shown (debug "Fire" deliberately skips quiet hours)
- [ ] 5.2: after a real reboot, `adb shell dumpsys alarm | grep -A3 app.klean.debug` shows the check-in alarm
- [ ] Polish: the app uses the default Android launcher icon; none has been designed yet
- [ ] 6.1: on a real phone, speak "two manakish" (also in airplane mode) → saved with `via_voice=1`, and Undo shows for 5 s; where offline voice is missing → "Voice isn't available offline on this phone"
- [ ] 6.2: take a real camera photo (capture sheet and entry screen) → thumbnail in the row, EXIF stripped; log one without a photo too
- [ ] 10.3: Gemini with a real API key (covered only by MockEngine tests and the screen rendering; no key was used)
- [ ] 10.5: a photo entry sent to a provider with "Send photos too" / "This model understands images" on (needs a camera photo)
- [x] Polish: the bottom bar highlights Diet on History / Day / Entry screens (diet routes live in a nested `DietGraph`; the bar matches `KleanModule.graphRoute`)
- [x] Polish: after deleting from the entry screen it now goes back to the list, which shows "Entry removed · Undo" for 5 s
